const RTL_CHARACTER = /[\u0590-\u08ff\ufb1d-\ufdff\ufe70-\ufefc]/u;
const LTR_CHARACTER = /[A-Za-z\u00c0-\u02af]/u;

export function textDirection(value, fallback = "ltr") {
  for (const character of String(value || "")) {
    if (RTL_CHARACTER.test(character)) {
      return "rtl";
    }
    if (LTR_CHARACTER.test(character)) {
      return "ltr";
    }
  }
  return fallback;
}

export function applyTextDirection(element, value, fallback = "ltr") {
  if (!element) {
    return fallback;
  }
  const direction = textDirection(value, fallback);
  element.dir = direction;
  element.dataset.textDirection = direction;
  return direction;
}
