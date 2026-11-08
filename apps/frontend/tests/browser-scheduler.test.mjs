import assert from "node:assert/strict";
import test from "node:test";
import { waitForAnimationFrame, yieldToMain } from "../js/utils.js";

function installBrowser(t, { hidden = false } = {}) {
  const document = new EventTarget();
  document.hidden = hidden;
  let callback;
  const cancelled = [];
  const originals = Object.fromEntries(["document", "requestAnimationFrame", "cancelAnimationFrame"]
    .map((key) => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  Object.assign(globalThis, {
    document,
    requestAnimationFrame: (fn) => { callback = fn; return 7; },
    cancelAnimationFrame: (id) => cancelled.push(id),
  });
  t.after(() => {
    for (const [key, descriptor] of Object.entries(originals)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else delete globalThis[key];
    }
  });
  return { document, cancelled, frame: () => callback?.(), hasFrame: () => !!callback };
}

test("foreground work waits for a paint and cleans up its fallback", async (t) => {
  const browser = installBrowser(t);
  let done = false;
  const pending = waitForAnimationFrame().then(() => { done = true; });
  await Promise.resolve();
  assert.equal(done, false);
  browser.frame();
  await pending;
  assert.deepEqual(browser.cancelled, [7]);
});

test("hidden work yields without requesting an animation frame", async (t) => {
  const browser = installBrowser(t, { hidden: true });
  await yieldToMain();
  assert.equal(browser.hasFrame(), false);
});

test("hiding a window releases a paint already in flight", async (t) => {
  const browser = installBrowser(t);
  const pending = waitForAnimationFrame();
  browser.document.hidden = true;
  browser.document.dispatchEvent(new Event("visibilitychange"));
  await pending;
  assert.deepEqual(browser.cancelled, [7]);
  browser.document.dispatchEvent(new Event("visibilitychange"));
  assert.deepEqual(browser.cancelled, [7], "The visibility listener is removed after completion");
});

test("occluded windows progress even when frames stop without a visibility event", async (t) => {
  const browser = installBrowser(t);
  await yieldToMain();
  assert.equal(browser.hasFrame(), true);
  assert.deepEqual(browser.cancelled, [7]);
});
