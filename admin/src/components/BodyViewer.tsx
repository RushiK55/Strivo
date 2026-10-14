'use client';
// Puts the 3D body (lib/bodyScene.ts) in a page: makes it once, hands it what the page chooses (body, muscle, fade, skeleton, skin),
// and reports what the user does in it (hover, click, loaded, failed). Everything it draws is in the scene class.
import { useEffect, useRef } from 'react';
import { BodyScene, type PickedMuscle } from '@/lib/bodyScene';
import type { BodyKey, Catalogue } from '@/lib/muscles';

export type ViewerStatus = 'loading' | 'ready' | 'error';

export interface BodyViewerProps {
  catalogue: Catalogue;
  body: BodyKey;
  selected: PickedMuscle | null;
  fade: boolean;
  skeleton: boolean;
  skin: boolean;
  /** Asks for the whole body from the front or the back; a new `n` is a new request. */
  view: { side: 'front' | 'back'; n: number } | null;
  onSelect: (picked: PickedMuscle | null) => void;
  onHover: (id: string | null) => void;
  onStatus: (status: ViewerStatus, message?: string) => void;
}

export default function BodyViewer({ catalogue, body, selected, fade, skeleton, skin, view, onSelect, onHover, onStatus }: BodyViewerProps) {
  const host = useRef<HTMLDivElement>(null);
  const scene = useRef<BodyScene | null>(null);
  // The latest of what the page chose, so the scene (made once) is always set up from current values.
  const latest = useRef({ body, selected, fade, skeleton, skin });
  const callbacks = useRef({ onSelect, onHover, onStatus });
  // First of the effects, so the others always read the current values.
  useEffect(() => {
    latest.current = { body, selected, fade, skeleton, skin };
    callbacks.current = { onSelect, onHover, onStatus };
  });

  // The scene lives as long as the catalogue it was made for.
  useEffect(() => {
    const element = host.current;
    if (!element) return undefined;
    let made: BodyScene;
    try {
      made = new BodyScene(element, catalogue, { onSelect: (picked) => callbacks.current.onSelect(picked), onHover: (id) => callbacks.current.onHover(id) });
    } catch {
      callbacks.current.onStatus('error', 'This browser cannot show 3D (WebGL is switched off or not available). The muscle list on the right still works.');
      return undefined;
    }
    scene.current = made;
    const start = latest.current;
    made.setFade(start.fade);
    made.setSkeleton(start.skeleton);
    made.setSkin(start.skin);
    made.select(start.selected?.id ?? null, start.selected?.side ?? null);
    return () => { made.dispose(); scene.current = null; };
  }, [catalogue]);

  // A body is loaded when it is chosen (and again if the scene was made anew).
  useEffect(() => {
    const current = scene.current;
    if (!current) return undefined;
    let cancelled = false;
    callbacks.current.onStatus('loading');
    current.load(body).then(
      () => {
        if (cancelled) return;
        host.current?.setAttribute('data-loaded', body); // which body is on screen now (the page and the tests wait on it)
        callbacks.current.onStatus('ready');
      },
      (error: unknown) => { if (!cancelled) callbacks.current.onStatus('error', error instanceof Error ? `The 3D body could not be loaded: ${error.message}` : 'The 3D body could not be loaded.'); },
    );
    return () => { cancelled = true; };
  }, [body, catalogue]);

  useEffect(() => { scene.current?.select(selected?.id ?? null, selected?.side ?? null); }, [selected]);
  useEffect(() => { scene.current?.setFade(fade); }, [fade]);
  useEffect(() => { scene.current?.setSkeleton(skeleton); }, [skeleton]);
  useEffect(() => { scene.current?.setSkin(skin); }, [skin]);
  useEffect(() => { if (view) scene.current?.showBody(view.side); }, [view]);

  return (
    <div
      ref={host}
      className="size-full touch-none"
      role="img"
      aria-label="3D model of the human body. Turn it with the mouse or a finger and click a muscle, or use the muscle list."
      data-testid="body-viewer"
      data-body={body}
      data-selected={selected?.id ?? ''}
      data-skin={skin ? 'on' : 'off'}
    />
  );
}
