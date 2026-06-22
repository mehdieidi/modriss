import {
  COMPACT_BREAKPOINT,
  MOBILE_BREAKPOINT,
  PHONE_BREAKPOINT,
  TABLET_BREAKPOINT,
} from "./config.js";

export function isMobileViewport() {
  return window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`).matches;
}

export function isTabletViewport() {
  return window.matchMedia(`(max-width: ${TABLET_BREAKPOINT}px)`).matches;
}

export function isCompactViewport() {
  return window.matchMedia(`(max-width: ${COMPACT_BREAKPOINT}px)`).matches;
}

export function isPhoneViewport() {
  return window.matchMedia(`(max-width: ${PHONE_BREAKPOINT}px)`).matches;
}
