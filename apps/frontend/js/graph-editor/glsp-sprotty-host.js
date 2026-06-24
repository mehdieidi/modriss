/**
 * Keeps the Sprotty diagram focusable so GLSP mouse tools receive pointer events.
 */
export function bindSprottyHostFocus(host) {
  if (!host || host.dataset.sprottyFocusBound === "true") {
    return;
  }
  host.dataset.sprottyFocusBound = "true";
  host.addEventListener(
    "pointerdown",
    () => {
      const root =
        host.querySelector(".modless-glsp-diagram-root") || host.querySelector(".sprotty") || host;
      const focusTarget =
        root.querySelector?.(".sprotty-graph") || root.querySelector?.("svg") || root;
      if (focusTarget && typeof focusTarget.focus === "function") {
        focusTarget.focus({ preventScroll: true });
      }
    },
    true,
  );
}
