function isSvgIconImage(node) {
  if (!(node instanceof HTMLImageElement)) {
    return false;
  }
  if (!node.classList.contains("icon-svg")) {
    return false;
  }
  const src = node.getAttribute("src") || "";
  return /\.svg(?:[?#].*)?$/i.test(src);
}

function copyAccessibility(img, icon) {
  const alt = img.getAttribute("alt");
  const ariaHidden = img.getAttribute("aria-hidden");
  const ariaLabel = img.getAttribute("aria-label");
  const title = img.getAttribute("title");

  if (ariaHidden !== null) {
    icon.setAttribute("aria-hidden", ariaHidden);
  }
  if (ariaLabel) {
    icon.setAttribute("aria-label", ariaLabel);
  }
  if (!ariaLabel && alt && alt.trim()) {
    icon.setAttribute("aria-label", alt.trim());
  }
  if (title) {
    icon.setAttribute("title", title);
  }
}

function toIconMask(img) {
  if (!isSvgIconImage(img) || !img.parentElement) {
    return;
  }

  const src = img.getAttribute("src");
  if (!src) {
    return;
  }

  const icon = document.createElement("span");
  icon.className = `${img.className} icon-mask`;
  const styleText = img.getAttribute("style") || "";
  if (styleText) {
    icon.setAttribute("style", styleText);
  }
  const safeSrc = src.replace(/"/g, '\\"');
  icon.style.setProperty("--icon-src", `url("${safeSrc}")`);
  copyAccessibility(img, icon);

  if (img.id) {
    icon.id = img.id;
  }
  if (img.getAttribute("role")) {
    icon.setAttribute("role", img.getAttribute("role"));
  }
  if (!icon.getAttribute("role") && icon.getAttribute("aria-label")) {
    icon.setAttribute("role", "img");
  }

  img.replaceWith(icon);
}

function convertTree(root) {
  if (!root) {
    return;
  }
  if (isSvgIconImage(root)) {
    toIconMask(root);
    return;
  }
  if (!(root instanceof Element)) {
    return;
  }
  root.querySelectorAll("img.icon-svg").forEach(toIconMask);
}

export function initSvgIconMasks() {
  convertTree(document);

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      mutation.addedNodes.forEach((node) => convertTree(node));
    });
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
  });
}
