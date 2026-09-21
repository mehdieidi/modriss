(() => {
  const dialog = document.createElement("dialog");
  dialog.className = "doc-image-viewer";
  dialog.setAttribute("aria-label", "Diagram preview");

  const closeButton = document.createElement("button");
  closeButton.className = "doc-image-viewer__close";
  closeButton.type = "button";
  closeButton.setAttribute("aria-label", "Close diagram preview");
  closeButton.textContent = "×";

  const image = document.createElement("img");
  image.className = "doc-image-viewer__image";

  const caption = document.createElement("p");
  caption.className = "doc-image-viewer__caption";

  dialog.append(closeButton, image, caption);
  document.body.append(dialog);

  closeButton.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });

  document.addEventListener("click", (event) => {
    if (!(event.target instanceof Element)) return;
    const link = event.target.closest(".doc-diagram__link");
    if (!link) return;

    const source = link.querySelector("img");
    if (!source) return;

    event.preventDefault();
    image.src = link.href;
    image.alt = source.alt;
    caption.textContent =
      link.closest("figure")?.querySelector("figcaption")?.textContent?.trim() ?? source.alt;
    dialog.showModal();
    closeButton.focus();
  });
})();
