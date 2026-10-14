// The floating text box on the 3D body and the line that joins it to the muscle you picked: where the box goes and where the line
// runs. Pure arithmetic on pixels (no React, no three.js), so it is all covered by callout.test.ts.
//
// The box floats in the empty air beside the body, at the edge of the stage; the line runs from the muscle on the screen to the
// nearest edge of the box. When the stage is narrow (a phone) the box docks along the bottom instead.

export interface Point { x: number; y: number }
/** Where the picked muscle is on the screen, in pixels from the stage's top left; `visible` is false when it is behind the camera. */
export interface ScreenAnchor extends Point { visible: boolean }
export interface Size { width: number; height: number }

export interface CalloutLayout {
  /** The box sits along the bottom of a narrow stage instead of beside the body. */
  docked: boolean;
  left: number;
  top: number;
  width: number;
  /** The line from the muscle to the box, or null when the muscle cannot be placed on the screen. */
  line: { from: Point; to: Point } | null;
}

/** Space kept clear around the box and between it and the edges of the stage, in pixels. */
export const MARGIN = 16;
/** The least gap between the muscle and the box before the box moves to the other side. */
export const GAP = 48;
/** Below this width the box docks along the bottom. */
export const NARROW = 720;
/** The width of the box beside the body. */
export const CARD_WIDTH = 352;
/** Room kept free at the bottom for the hover label and the hint. */
export const FOOTER = 40;

const clamp = (value: number, low: number, high: number): number => Math.min(Math.max(value, low), Math.max(low, high));

/**
 * Where the box and the line go. [stage] is the size of the 3D view, [card] the box's own height (its width is set here), and
 * [rightInset] how much of the right edge the controls take up, which the box keeps out of.
 */
export function placeCallout(anchor: ScreenAnchor | null, stage: Size, card: { height: number }, rightInset: number): CalloutLayout {
  const placed = anchor?.visible ? anchor : null;

  if (stage.width < NARROW) {
    const width = Math.max(stage.width - MARGIN * 2, 0);
    const top = Math.max(stage.height - card.height - MARGIN, MARGIN);
    const to = placed ? { x: clamp(placed.x, MARGIN * 2, stage.width - MARGIN * 2), y: top } : null;
    return { docked: true, left: MARGIN, top, width, line: placed && to ? { from: placed, to } : null };
  }

  const width = CARD_WIDTH;
  const leftEdge = MARGIN;
  const rightEdge = Math.max(stage.width - rightInset - width, leftEdge);
  // Beside the body, on the side with more room: the left unless the muscle is too far over to the left to leave a gap.
  const x = placed?.x ?? stage.width / 2;
  const roomLeft = x - (leftEdge + width);
  const roomRight = rightEdge - x;
  const onLeft = roomLeft >= GAP || roomLeft >= roomRight;
  const left = onLeft ? leftEdge : rightEdge;
  const top = clamp((placed?.y ?? stage.height / 2) - card.height / 2, MARGIN, stage.height - card.height - FOOTER);
  const edgeX = onLeft ? left + width : left;
  const edgeY = clamp(placed?.y ?? top, top + 24, top + card.height - 24);
  return { docked: false, left, top, width, line: placed ? { from: placed, to: { x: edgeX, y: edgeY } } : null };
}

/** The tallest the box may be so that it always fits the stage with its margins. */
export const maxCardHeight = (stage: Size): number => Math.max(stage.height - MARGIN - FOOTER, 160);

// ---- Sharing the muscle's place on the screen with the box ----

/**
 * A tiny store for the muscle's screen place, which changes on every frame while the camera moves: only the box (which reads it
 * with useSyncExternalStore) redraws, not the whole page.
 */
export function createAnchorStore() {
  let current: ScreenAnchor | null = null;
  const listeners = new Set<() => void>();
  return {
    get: (): ScreenAnchor | null => current,
    set(next: ScreenAnchor | null): void {
      const same = current === next || (current && next && current.visible === next.visible && Math.abs(current.x - next.x) < 0.25 && Math.abs(current.y - next.y) < 0.25);
      if (same) return;
      current = next;
      for (const listener of listeners) listener();
    },
    subscribe(listener: () => void): () => void {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
  };
}
