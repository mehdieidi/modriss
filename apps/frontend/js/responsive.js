import {MOBILE_BREAKPOINT} from "./config.js";

export function isMobileViewport() {
  return window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`).matches;
}
