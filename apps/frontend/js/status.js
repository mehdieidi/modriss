import {el} from './dom.js';

// ── Status bar helpers ─────────────────────────────────────────────────────────
export function setStatus(message, {busy = false, error = false} = {}) {
  // Status chip was intentionally removed from the navbar.
  void el;
  void message;
  void busy;
  void error;
}

export function setBusy(message) {
  setStatus(message, {busy: true});
}

export function setError(message) {
  setStatus(message, {error: true});
}
