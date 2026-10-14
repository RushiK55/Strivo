import { describe, expect, it, vi } from 'vitest';
import { CARD_WIDTH, GAP, MARGIN, NARROW, createAnchorStore, maxCardHeight, placeCallout } from './callout';

const stage = { width: 1100, height: 720 };
const card = { height: 260 };
const inset = 272; // the controls on the right

describe('placeCallout beside the body', () => {
  it('puts the box in the empty air on the left, with the line running from the muscle to its right edge', () => {
    const layout = placeCallout({ x: 550, y: 360, visible: true }, stage, card, inset);
    expect(layout.docked).toBe(false);
    expect(layout.left).toBe(MARGIN);
    expect(layout.width).toBe(CARD_WIDTH);
    expect(layout.line?.from).toMatchObject({ x: 550, y: 360 });
    expect(layout.line?.to.x).toBe(MARGIN + CARD_WIDTH);
    expect(layout.line!.to.y).toBeGreaterThanOrEqual(layout.top + 24);
    expect(layout.line!.to.y).toBeLessThanOrEqual(layout.top + card.height - 24);
  });

  it('centres the box on the muscle up and down, but keeps it inside the stage', () => {
    expect(placeCallout({ x: 550, y: 360, visible: true }, stage, card, inset).top).toBe(360 - 130);
    expect(placeCallout({ x: 550, y: 10, visible: true }, stage, card, inset).top).toBe(MARGIN);
    const low = placeCallout({ x: 550, y: 715, visible: true }, stage, card, inset);
    expect(low.top + card.height).toBeLessThanOrEqual(stage.height - 40);
  });

  it('moves to the right of the body, clear of the controls, when the muscle is too far left for a gap', () => {
    const layout = placeCallout({ x: 300, y: 360, visible: true }, stage, card, inset);
    expect(layout.left + layout.width).toBeLessThanOrEqual(stage.width - inset);
    expect(layout.left).toBeGreaterThan(300 + GAP - 1);
    expect(layout.line?.to.x).toBe(layout.left); // the line meets the box's left edge
  });

  it('never overlaps the muscle it is about when there is room on either side', () => {
    for (const x of [100, 250, 400, 550, 700, 800, 900]) {
      const layout = placeCallout({ x, y: 300, visible: true }, stage, card, inset);
      const inside = x > layout.left && x < layout.left + layout.width;
      // (a muscle right at the edge of a tiny stage could not avoid it, but in this stage there is always an end that clears it)
      if (x > 300 && x < 800) expect(inside, `x ${x}`).toBe(false);
    }
  });

  it('keeps the box and its line when the muscle is off the screen, with no line', () => {
    const layout = placeCallout({ x: 5000, y: 300, visible: false }, stage, card, inset);
    expect(layout.line).toBeNull();
    expect(layout.left).toBe(MARGIN);
    expect(placeCallout(null, stage, card, inset).line).toBeNull();
  });
});

describe('placeCallout on a narrow stage', () => {
  const phone = { width: NARROW - 100, height: 640 };

  it('docks the box along the bottom, full width, with the line running down to it', () => {
    const layout = placeCallout({ x: 200, y: 200, visible: true }, phone, card, 0);
    expect(layout.docked).toBe(true);
    expect(layout.left).toBe(MARGIN);
    expect(layout.width).toBe(phone.width - MARGIN * 2);
    expect(layout.top + card.height).toBeLessThanOrEqual(phone.height);
    expect(layout.line?.to.y).toBe(layout.top);
    expect(layout.line?.to.x).toBe(200);
  });

  it('keeps the line on the box even when the muscle is near the edge', () => {
    const layout = placeCallout({ x: 2, y: 100, visible: true }, phone, card, 0);
    expect(layout.line!.to.x).toBeGreaterThanOrEqual(MARGIN);
    expect(layout.line!.to.x).toBeLessThanOrEqual(phone.width - MARGIN);
  });

  it('never lets a very tall box start above the top margin', () => {
    expect(placeCallout(null, phone, { height: 5000 }, 0).top).toBe(MARGIN);
  });
});

describe('maxCardHeight', () => {
  it('leaves room for the margins and the footer, and never gets absurdly small', () => {
    expect(maxCardHeight({ width: 1100, height: 720 })).toBe(720 - MARGIN - 40);
    expect(maxCardHeight({ width: 1100, height: 100 })).toBe(160);
  });
});

describe('the anchor store', () => {
  it('holds the latest place and tells listeners only when it really changed', () => {
    const store = createAnchorStore();
    const listener = vi.fn();
    const stop = store.subscribe(listener);
    expect(store.get()).toBeNull();
    store.set({ x: 10, y: 20, visible: true });
    store.set({ x: 10.1, y: 20.1, visible: true }); // a hair of movement: not worth a redraw
    expect(listener).toHaveBeenCalledTimes(1);
    store.set({ x: 14, y: 20, visible: true });
    expect(listener).toHaveBeenCalledTimes(2);
    store.set({ x: 14, y: 20, visible: false });
    expect(listener).toHaveBeenCalledTimes(3);
    store.set(null);
    expect(store.get()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(4);
    stop();
    store.set({ x: 1, y: 1, visible: true });
    expect(listener).toHaveBeenCalledTimes(4);
  });
});
