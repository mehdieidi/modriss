import { el } from "./dom.js";

export function confirmAction({
  title = "Confirm Action",
  message = "Are you sure?",
  confirmLabel = "Confirm",
  danger = false,
} = {}) {
  if (
    !el.confirmActionOverlay ||
    !el.confirmActionTitle ||
    !el.confirmActionMessage ||
    !el.confirmActionCancelBtn ||
    !el.confirmActionConfirmBtn
  ) {
    return Promise.resolve(window.confirm(message));
  }

  el.confirmActionTitle.textContent = title;
  el.confirmActionMessage.textContent = message;
  el.confirmActionConfirmBtn.textContent = confirmLabel;
  el.confirmActionConfirmBtn.classList.toggle("btn-danger", danger);
  el.confirmActionConfirmBtn.classList.toggle("btn-primary", !danger);
  el.confirmActionOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");

  return new Promise((resolve) => {
    const close = (result) => {
      el.confirmActionOverlay.classList.add("hidden");
      document.body.classList.remove("modal-open");
      el.confirmActionConfirmBtn.removeEventListener("click", onConfirm);
      el.confirmActionCancelBtn.removeEventListener("click", onCancel);
      el.confirmActionOverlay.removeEventListener("click", onOverlayClick);
      document.removeEventListener("keydown", onKeyDown);
      resolve(result);
    };

    const onConfirm = () => close(true);
    const onCancel = () => close(false);
    const onOverlayClick = (event) => {
      if (event.target === el.confirmActionOverlay) {
        close(false);
      }
    };
    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        close(false);
      } else if (event.key === "Enter") {
        event.preventDefault();
        close(true);
      }
    };

    el.confirmActionConfirmBtn.addEventListener("click", onConfirm);
    el.confirmActionCancelBtn.addEventListener("click", onCancel);
    el.confirmActionOverlay.addEventListener("click", onOverlayClick);
    document.addEventListener("keydown", onKeyDown);
  });
}
