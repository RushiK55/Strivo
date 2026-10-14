// The 3D body: loads male.glb or female.glb (the muscles, the skeleton and a skin layer over them), draws it, lets you turn it, hover
// and click a muscle, and flies the camera to the one you pick. Plain three.js with no React in it; components/BodyViewer.tsx holds
// it in a page.
//
// The skin is a see-through layer over the muscles that fades away when asked (the page turns it off when a muscle is picked).
// Picking looks straight through it: it is left out when the muscles are drawn for picking.
//
// Picking is done on the graphics card: the muscles are drawn once into a 1-pixel buffer, each in a colour that stands for
// its number, and the pixel under the pointer says which one it is. That costs the same however many triangles there are,
// where testing rays against 130,000 triangles on every mouse move would make hovering sluggish.
import {
  Color, DirectionalLight, HemisphereLight, LinearSRGBColorSpace, Mesh, MeshBasicMaterial, MeshStandardMaterial, PerspectiveCamera, Raycaster, Scene,
  Vector3, WebGLRenderer, WebGLRenderTarget, type Object3D,
} from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import {
  MODELS_URL, SKIN_OPACITY, blendCamera, bodyCamera, cameraFor, isPickable, lightnessShift, lookOf, type BodyKey, type Box, type Catalogue, type CameraSpot, type DrawState, type MuscleLayer,
} from './muscles';

export interface PickedMuscle { id: string; side: string }

export interface SceneEvents {
  /** The user clicked a muscle (or, with null, empty space). */
  onSelect: (picked: PickedMuscle | null) => void;
  /** The pointer moved onto a muscle or off it. */
  onHover: (id: string | null) => void;
  /** Where the selected muscle is on the screen (pixels from the top left of the view), reported as the camera moves; null when none is selected. */
  onAnchor: (anchor: { x: number; y: number; visible: boolean } | null) => void;
}

interface Part { mesh: Mesh; id: string; side: string; layer: MuscleLayer; pick: MeshBasicMaterial }
interface Look { material: MeshStandardMaterial; layer: MuscleLayer }

const FOV = 35;
const FLY_MS = 800;
/** How long the skin takes to fade in or out. */
const SKIN_MS = 350;
const GLOW = new Color('#ffb800');
const SKIN_COLOR = new Color('#9aa5bd');

const easeInOut = (t: number): number => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);

function disposeObject(root: Object3D): void {
  root.traverse((object) => {
    const mesh = object as Mesh;
    if (!mesh.isMesh) return;
    mesh.geometry.dispose();
    for (const material of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) material.dispose();
  });
}

export class BodyScene {
  private readonly renderer: WebGLRenderer;
  private readonly scene = new Scene();
  private readonly camera = new PerspectiveCamera(FOV, 1, 0.05, 30);
  private readonly controls: OrbitControls;
  private readonly pickTarget = new WebGLRenderTarget(1, 1);
  private readonly pixel = new Uint8Array(4);
  private readonly resizer: ResizeObserver;

  private body: BodyKey = 'male';
  private model: Object3D | null = null;
  private parts: Part[] = [];
  private looks = new Map<string, Look>();
  private skeleton: Object3D[] = [];
  private skin: Mesh | null = null;
  private others: Mesh[] = []; // meshes that cannot be picked (the skeleton and the skin)
  private draw: DrawState = { selected: null, hovered: null, fade: false };
  private selectedSide: string | null = null;
  /** A point on the selected muscle's surface that the page draws its line to. */
  private anchor: Vector3 | null = null;
  private anchorShown = false;
  private showSkeleton = false;
  private skinOn = true;
  private skinOpacity = SKIN_OPACITY;
  private skinFade: { from: number; to: number; start: number } | null = null;
  private loadTicket = 0;
  private disposed = false;
  private framed = false;

  private raf = 0;
  private dirty = true;
  private fly: { from: CameraSpot; to: CameraSpot; start: number; duration: number } | null = null;
  private pointer: { x: number; y: number } | null = null;
  private hoverDue = false;
  private down: { x: number; y: number; at: number } | null = null;

  constructor(private readonly host: HTMLElement, private readonly catalogue: Catalogue, private readonly events: SceneEvents) {
    // Throws when the browser has no WebGL; the page shows that as a message instead of a body.
    this.renderer = new WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    const canvas = this.renderer.domElement;
    canvas.style.cssText = 'display:block;width:100%;height:100%;cursor:grab;outline:none';
    host.appendChild(canvas);

    // The lights ride with the camera, so the side you look at is always the lit one.
    this.scene.add(this.camera);
    this.camera.add(new HemisphereLight(0xffffff, 0x3a3a44, 1.1));
    const key = new DirectionalLight(0xffffff, 2.2);
    key.position.set(1.5, 2, 3);
    this.camera.add(key);

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.minDistance = 0.3;
    this.controls.maxDistance = 6;
    this.controls.rotateSpeed = 0.8;
    this.controls.addEventListener('start', () => { this.fly = null; }); // the user took over: stop any flight
    this.controls.addEventListener('change', () => { this.dirty = true; });

    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerleave', this.onPointerLeave);
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointerup', this.onPointerUp);

    this.resizer = new ResizeObserver(() => this.resize());
    this.resizer.observe(host);
    this.resize();
    this.raf = requestAnimationFrame(this.loop);
  }

  // ---- What the page can ask for ----

  /** Loads a body and puts it on the stage. The camera is left where it is, so switching body keeps the view. */
  async load(body: BodyKey): Promise<void> {
    const ticket = ++this.loadTicket;
    const gltf = await new GLTFLoader().loadAsync(`${MODELS_URL}${this.catalogue.bodies[body].file}`);
    if (this.disposed || ticket !== this.loadTicket) { disposeObject(gltf.scene); return; }
    this.removeModel();
    this.body = body;
    this.model = gltf.scene;
    this.scene.add(gltf.scene);
    this.indexModel(gltf.scene);
    this.applyLooks();
    this.skinFade = null;
    this.setSkinOpacity(this.skinOn ? SKIN_OPACITY : 0);
    // The first body puts the camera straight in place (on the selection, if one was made before it loaded); later ones keep the view.
    const first = !this.framed;
    this.framed = true;
    if (first || this.draw.selected) this.flyToSelection(false);
    this.placeAnchor();
  }

  /** Selects a muscle (or, with null, none) and flies the camera to it, or back to the whole body. */
  select(id: string | null, side: string | null = null): void {
    this.draw = { ...this.draw, selected: id };
    this.selectedSide = side;
    this.applyLooks();
    if (this.framed) { this.flyToSelection(true); this.placeAnchor(); } // before a body is loaded there is nowhere to fly from: load() frames it
  }

  setFade(fade: boolean): void {
    this.draw = { ...this.draw, fade };
    this.applyLooks();
  }

  setSkeleton(show: boolean): void {
    this.showSkeleton = show;
    for (const object of this.skeleton) object.visible = show;
    this.dirty = true;
  }

  /** Fades the skin layer in or out. */
  setSkin(on: boolean): void {
    if (this.skinOn === on) return;
    this.skinOn = on;
    const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || !this.skin) { this.skinFade = null; this.setSkinOpacity(on ? SKIN_OPACITY : 0); return; }
    this.skinFade = { from: this.skinOpacity, to: on ? SKIN_OPACITY : 0, start: performance.now() };
    this.dirty = true;
  }

  /** The whole body from the front or the back. */
  showBody(side: 'front' | 'back'): void {
    this.flyTo(bodyCamera(this.catalogue.bodies[this.body].box, side, this.size()), true);
  }

  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.raf);
    this.resizer.disconnect();
    const canvas = this.renderer.domElement;
    canvas.removeEventListener('pointermove', this.onPointerMove);
    canvas.removeEventListener('pointerleave', this.onPointerLeave);
    canvas.removeEventListener('pointerdown', this.onPointerDown);
    canvas.removeEventListener('pointerup', this.onPointerUp);
    this.controls.dispose();
    this.removeModel();
    this.pickTarget.dispose();
    this.renderer.dispose();
    canvas.remove();
  }

  // ---- The model ----

  private removeModel(): void {
    if (!this.model) return;
    this.scene.remove(this.model);
    disposeObject(this.model);
    for (const part of this.parts) part.pick.dispose();
    this.model = null;
    this.parts = [];
    this.looks.clear();
    this.skeleton = [];
    this.skin = null;
    this.others = [];
  }

  /** Sorts the meshes of a loaded body into pickable muscles, the skeleton and the skin. */
  private indexModel(root: Object3D): void {
    const known = new Map(this.catalogue.groups.map((group) => [group.id, group]));
    root.traverse((object) => {
      const mesh = object as Mesh;
      if (!mesh.isMesh) return;
      const extras = mesh.userData as { group?: string; side?: string; layer?: MuscleLayer };
      const id = extras.group ?? '';
      const material = mesh.material as MeshStandardMaterial;
      if (id === 'skeleton') {
        this.skeleton.push(mesh);
        this.others.push(mesh);
        mesh.visible = this.showSkeleton;
        return;
      }
      if (id === 'skin') {
        // See-through, drawn after the muscles, and never in the way of the depth test (so the muscles under it are not cut off).
        material.color.copy(SKIN_COLOR);
        material.roughness = 0.6;
        material.metalness = 0;
        material.transparent = true;
        material.depthWrite = false;
        mesh.renderOrder = 2;
        this.skin = mesh;
        this.others.push(mesh);
        return;
      }
      if (!known.has(id)) return;
      const layer: MuscleLayer = known.get(id)?.layer ?? 'surface';
      if (!this.looks.has(id)) {
        // One material per group (the two sides share it), tinted a little so neighbouring muscles can be told apart.
        material.roughness = 0.62;
        material.metalness = 0;
        material.color.offsetHSL(0, 0, lightnessShift(id));
        material.emissive.copy(GLOW);
        material.emissiveIntensity = 0;
        this.looks.set(id, { material, layer });
      }
      const index = this.parts.length + 1; // 0 is "nothing" in the picking buffer
      const pick = new MeshBasicMaterial({ toneMapped: false });
      pick.color.setRGB(((index >> 16) & 255) / 255, ((index >> 8) & 255) / 255, (index & 255) / 255, LinearSRGBColorSpace);
      this.parts.push({ mesh, id, side: extras.side ?? 'C', layer, pick });
    });
  }

  /** Sets the opacity and glow of every group from what is selected, hovered and faded. */
  private applyLooks(): void {
    for (const [id, { material, layer }] of this.looks) {
      const look = lookOf({ id, layer }, this.draw);
      const transparent = look.opacity < 0.999;
      if (material.transparent !== transparent) { material.transparent = transparent; material.needsUpdate = true; }
      material.opacity = look.opacity;
      material.depthWrite = !transparent;
      material.emissiveIntensity = look.glow;
    }
    this.dirty = true;
  }

  private setSkinOpacity(opacity: number): void {
    this.skinOpacity = opacity;
    if (this.skin) {
      (this.skin.material as MeshStandardMaterial).opacity = opacity;
      this.skin.visible = opacity > 0.005;
    }
    this.dirty = true;
  }

  private stepSkin(): void {
    if (!this.skinFade) return;
    const { from, to, start } = this.skinFade;
    const t = Math.min((performance.now() - start) / SKIN_MS, 1);
    this.setSkinOpacity(from + (to - from) * easeInOut(t));
    if (t >= 1) this.skinFade = null;
  }

  // ---- The camera ----

  private size(): { fov: number; aspect: number } {
    return { fov: FOV, aspect: this.camera.aspect };
  }

  private resize(): void {
    const width = Math.max(this.host.clientWidth, 1);
    const height = Math.max(this.host.clientHeight, 1);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.dirty = true;
  }

  private jump(spot: CameraSpot): void {
    this.camera.position.set(...spot.position);
    this.controls.target.set(...spot.target);
    this.controls.update();
    this.dirty = true;
  }

  private flyTo(spot: CameraSpot, animate: boolean): void {
    const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!animate || reduced) { this.fly = null; this.jump(spot); return; }
    const here: CameraSpot = {
      target: this.controls.target.toArray() as CameraSpot['target'],
      position: this.camera.position.toArray() as CameraSpot['position'],
    };
    this.fly = { from: here, to: spot, start: performance.now(), duration: FLY_MS };
  }

  /** Where the camera goes for the selected muscle (its clicked side when there is one), or null when none is selected or it is not in this body. */
  private selectionSpot(): { spot: CameraSpot; box: Box } | null {
    const id = this.draw.selected;
    const group = id ? this.catalogue.groups.find((candidate) => candidate.id === id) : undefined;
    const info = group?.[this.body];
    if (!group || !info) return null;
    const box = (this.selectedSide && info.sides[this.selectedSide]) || info.box;
    return { spot: cameraFor(box, group.view, this.size()), box };
  }

  /** To the selected muscle, or back to the front of the whole body. */
  private flyToSelection(animate: boolean): void {
    const target = this.selectionSpot();
    this.flyTo(target ? target.spot : bodyCamera(this.catalogue.bodies[this.body].box, 'front', this.size()), animate);
  }

  /**
   * Finds the point the page's line runs to: on the selected muscle's own surface, where the camera will be looking at it. A ray
   * is shot from where the camera is going toward the middle of the muscle and the first place it meets the muscle is used (the
   * middle of the muscle's box itself can float beside a thin muscle).
   */
  private placeAnchor(): void {
    const target = this.selectionSpot();
    if (!target) { this.anchor = null; return; }
    const { spot, box } = target;
    const middle = new Vector3(...spot.target);
    const origin = new Vector3(...spot.position);
    const meshes = this.parts.filter((part) => part.id === this.draw.selected).map((part) => part.mesh);
    const ray = new Raycaster(origin, middle.clone().sub(origin).normalize(), 0.01, 10);
    this.model?.updateMatrixWorld(true);
    const hit = ray.intersectObjects(meshes, false)[0];
    this.anchor = hit ? hit.point.clone() : new Vector3((box.min[0] + box.max[0]) / 2, (box.min[1] + box.max[1]) / 2, (box.min[2] + box.max[2]) / 2);
    this.anchorShown = false; // report it on the next frame even if the screen place happens to match
  }

  /** Tells the page where the anchor is on the screen now. */
  private reportAnchor(): void {
    if (!this.anchor) {
      if (this.anchorShown) { this.anchorShown = false; this.events.onAnchor(null); }
      return;
    }
    this.camera.updateMatrixWorld();
    const projected = this.anchor.clone().project(this.camera);
    this.anchorShown = true;
    this.events.onAnchor({
      x: (projected.x * 0.5 + 0.5) * this.host.clientWidth,
      y: (-projected.y * 0.5 + 0.5) * this.host.clientHeight,
      visible: projected.z > -1 && projected.z < 1,
    });
  }

  private stepFly(): void {
    if (!this.fly) return;
    const { from, to, start, duration } = this.fly;
    const t = Math.min((performance.now() - start) / duration, 1);
    const spot = blendCamera(from, to, easeInOut(t)); // swings round the body, not through it
    this.camera.position.set(...spot.position);
    this.controls.target.set(...spot.target);
    if (t >= 1) this.fly = null;
    this.dirty = true;
  }

  // ---- Picking ----

  /** The muscle under a point of the page, or null. Draws the clickable muscles into a 1-pixel buffer, one colour each. */
  private pickAt(clientX: number, clientY: number): Part | null {
    const canvas = this.renderer.domElement;
    const rect = canvas.getBoundingClientRect();
    const x = Math.floor(clientX - rect.left), y = Math.floor(clientY - rect.top);
    if (!this.model || x < 0 || y < 0 || x >= rect.width || y >= rect.height) return null;

    const shown = new Map<Mesh, boolean>();
    for (const mesh of this.others) { shown.set(mesh, mesh.visible); mesh.visible = false; } // the skin and the bones are looked through
    const originals = new Map<Part, MeshStandardMaterial>();
    for (const part of this.parts) {
      originals.set(part, part.mesh.material as MeshStandardMaterial);
      shown.set(part.mesh, part.mesh.visible);
      part.mesh.material = part.pick;
      part.mesh.visible = isPickable(part.layer, this.draw.fade);
    }
    this.camera.setViewOffset(rect.width, rect.height, x, y, 1, 1);
    const clearAlpha = this.renderer.getClearAlpha();
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.setRenderTarget(this.pickTarget);
    this.renderer.render(this.scene, this.camera);
    this.renderer.readRenderTargetPixels(this.pickTarget, 0, 0, 1, 1, this.pixel);
    this.renderer.setRenderTarget(null);
    this.renderer.setClearColor(0x000000, clearAlpha);
    this.camera.clearViewOffset();
    for (const [part, material] of originals) part.mesh.material = material;
    for (const [mesh, visible] of shown) mesh.visible = visible;

    if (this.pixel[3] === 0) return null;
    const index = (this.pixel[0]! << 16) | (this.pixel[1]! << 8) | this.pixel[2]!;
    return this.parts[index - 1] ?? null;
  }

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (event.buttons) return; // turning or panning the body: no hovering
    this.pointer = { x: event.clientX, y: event.clientY };
    this.hoverDue = true;
  };

  private readonly onPointerLeave = (): void => {
    this.pointer = null;
    this.hoverDue = false;
    this.setHovered(null);
  };

  private readonly onPointerDown = (event: PointerEvent): void => {
    this.down = { x: event.clientX, y: event.clientY, at: performance.now() };
  };

  /** A press and release that hardly moved is a click; one that moved was a turn of the body. */
  private readonly onPointerUp = (event: PointerEvent): void => {
    const down = this.down;
    this.down = null;
    if (!down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > 5 || performance.now() - down.at > 700) return;
    const part = this.pickAt(event.clientX, event.clientY);
    this.events.onSelect(part ? { id: part.id, side: part.side } : null);
  };

  private setHovered(id: string | null): void {
    if (this.draw.hovered === id) return;
    this.draw = { ...this.draw, hovered: id };
    this.renderer.domElement.style.cursor = id ? 'pointer' : 'grab';
    this.applyLooks();
    this.events.onHover(id);
  }

  // ---- The frame loop ----

  /** Draws only when something changed (a turn, a flight, a new look), so a body at rest costs nothing. */
  private readonly loop = (): void => {
    this.raf = requestAnimationFrame(this.loop);
    this.stepFly();
    this.stepSkin();
    const moved = this.controls.update();
    if (this.hoverDue && this.pointer) {
      this.hoverDue = false;
      this.setHovered(this.pickAt(this.pointer.x, this.pointer.y)?.id ?? null);
    }
    if (moved || this.dirty) {
      this.reportAnchor();
      this.dirty = false;
      this.renderer.render(this.scene, this.camera);
    } else if (!this.anchor && this.anchorShown) {
      this.reportAnchor();
    }
  };
}
