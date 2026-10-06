import { state } from "./state.js";
import { el } from "./dom.js?v=email-verification-20261006";
import { api } from "./api.js";
import { formatUserError } from "./errors.js";

const AUTH_TOKEN_KEY = "modriss.authToken";

export function getAuthToken() {
  return window.localStorage.getItem(AUTH_TOKEN_KEY) || "";
}

function setAuthToken(token) {
  if (!token) {
    window.localStorage.removeItem(AUTH_TOKEN_KEY);
    return;
  }
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
}

export function clearAuthSession() {
  setAuthToken("");
  state.auth.token = null;
  state.auth.user = null;
}

function setAuthMode(mode) {
  const registerMode = mode === "register";
  const resetMode = mode === "reset";
  const title = registerMode
    ? "Create your MODRISS account"
    : resetMode
      ? "Set a new password"
      : "Sign in to MODRISS";
  const subtitle = registerMode
    ? "Register to create and manage projects."
    : resetMode
      ? "Choose a new password for your account."
      : "Use your account to continue.";
  if (el.authLoginTabBtn) {
    el.authLoginTabBtn.classList.toggle("active", !registerMode);
    el.authLoginTabBtn.classList.toggle("hidden", resetMode);
  }
  if (el.authRegisterTabBtn) {
    el.authRegisterTabBtn.classList.toggle("active", registerMode);
    el.authRegisterTabBtn.classList.toggle("hidden", resetMode);
  }
  if (el.authModeSwitch) {
    el.authModeSwitch.classList.toggle("hidden", resetMode);
  }
  if (el.authEmailInput) {
    el.authEmailInput.classList.toggle("hidden", resetMode);
    el.authEmailInput.required = !resetMode;
  }
  if (el.authDisplayNameInput) {
    el.authDisplayNameInput.classList.toggle("hidden", !registerMode);
  }
  if (el.authConfirmPasswordInput) {
    el.authConfirmPasswordInput.classList.toggle("hidden", !registerMode && !resetMode);
  }
  if (el.authForgotPasswordBtn) {
    el.authForgotPasswordBtn.classList.toggle("hidden", registerMode || resetMode);
  }
  if (el.authBackToLoginBtn) {
    el.authBackToLoginBtn.classList.toggle("hidden", !resetMode);
  }
  if (el.authTitle && el.authChoice?.classList.contains("hidden")) {
    el.authTitle.textContent = title;
  }
  if (el.authSubtitle && el.authChoice?.classList.contains("hidden")) {
    el.authSubtitle.textContent = subtitle;
  }
  if (el.authPasswordInput) {
    el.authPasswordInput.classList.remove("hidden");
    el.authPasswordInput.autocomplete =
      registerMode || resetMode ? "new-password" : "current-password";
    el.authPasswordInput.placeholder = resetMode
      ? "New password (min 8 chars)"
      : "Password (min 8 chars)";
  }
  if (el.authSubmitBtn) {
    el.authSubmitBtn.textContent = registerMode
      ? "Register"
      : resetMode
        ? "Update password"
        : "Login";
  }
}

function setAuthChoiceVisible(visible) {
  el.authChoice?.classList.toggle("hidden", !visible);
  el.authModeSwitch?.classList.toggle("hidden", visible);
  el.authForm?.classList.toggle("hidden", visible);
  if (el.authSubmitBtn) {
    el.authSubmitBtn.classList.toggle("hidden", visible);
  }
  if (visible && el.authTitle) {
    el.authTitle.textContent = "How would you like to continue?";
  }
  if (visible && el.authSubtitle) {
    el.authSubtitle.textContent = "Use a guest workspace or access your account.";
  }
}

function showAuthError(message) {
  if (!el.authError) {
    return;
  }
  el.authError.textContent = message;
  el.authError.classList.remove("hidden");
}

function showAuthInfo(message) {
  if (!el.authInfo) {
    return;
  }
  el.authInfo.textContent = message;
  el.authInfo.classList.remove("hidden");
}

function clearAuthInfo() {
  if (!el.authInfo) {
    return;
  }
  el.authInfo.textContent = "";
  el.authInfo.classList.add("hidden");
}

function clearAuthError() {
  if (!el.authError) {
    clearAuthInfo();
    return;
  }
  el.authError.textContent = "";
  el.authError.classList.add("hidden");
  clearAuthInfo();
}

function showAuthSuccess(message) {
  if (!el.authSuccess) {
    return;
  }
  el.authSuccess.textContent = message;
  el.authSuccess.classList.remove("hidden");
}

function clearAuthSuccess() {
  if (!el.authSuccess) {
    return;
  }
  el.authSuccess.textContent = "";
  el.authSuccess.classList.add("hidden");
}

function validateAuthForm(mode) {
  const email = (el.authEmailInput?.value || "").trim();
  const password = el.authPasswordInput?.value || "";
  const displayName = (el.authDisplayNameInput?.value || "").trim();
  const confirmPassword = el.authConfirmPasswordInput?.value || "";
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (mode !== "reset" && !emailRegex.test(email)) {
    throw new Error("Enter a valid email address");
  }
  if (password.length < 8) {
    throw new Error("Password must be at least 8 characters");
  }
  if (mode === "register" || mode === "reset") {
    if (mode === "register" && !displayName) {
      throw new Error("Display name is required");
    }
    if (mode === "register" && displayName.length > 80) {
      throw new Error("Display name must be at most 80 characters");
    }
    if (password !== confirmPassword) {
      throw new Error("Password confirmation does not match");
    }
  }
  return { email, password, displayName };
}

async function showAuthDialog(initialNotice = null) {
  if (!el.authOverlay) {
    throw new Error("Authentication UI is unavailable");
  }
  let mode = "login";
  let resetToken = new URLSearchParams(window.location.search).get("resetToken") || "";
  if (resetToken) {
    mode = "reset";
  }
  setAuthMode(mode);
  setAuthChoiceVisible(!resetToken && !initialNotice);
  if (resetToken || initialNotice) {
    setAuthMode(mode);
  }
  clearAuthError();
  clearAuthSuccess();
  if (initialNotice) {
    if (initialNotice.type === "error") {
      showAuthError(initialNotice.message);
    } else {
      showAuthSuccess(initialNotice.message);
    }
  }
  el.authEmailInput.value = "";
  el.authPasswordInput.value = "";
  if (el.authDisplayNameInput) {
    el.authDisplayNameInput.value = "";
  }
  if (el.authConfirmPasswordInput) {
    el.authConfirmPasswordInput.value = "";
  }
  el.authResendVerificationBtn?.classList.add("hidden");
  el.authOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");

  return new Promise((resolve) => {
    let verificationPending = false;
    const updateResendVerificationVisibility = () => {
      el.authResendVerificationBtn?.classList.toggle(
        "hidden",
        !verificationPending || mode !== "login",
      );
    };

    const setBusy = (busy) => {
      if (el.authSubmitBtn) {
        el.authSubmitBtn.disabled = busy;
      }
      if (el.authGuestBtn) {
        el.authGuestBtn.disabled = busy;
      }
      if (el.authContinueLoginBtn) {
        el.authContinueLoginBtn.disabled = busy;
      }
      if (el.authLoginTabBtn) {
        el.authLoginTabBtn.disabled = busy;
      }
      if (el.authRegisterTabBtn) {
        el.authRegisterTabBtn.disabled = busy;
      }
      if (el.authEmailInput) {
        el.authEmailInput.disabled = busy;
      }
      if (el.authPasswordInput) {
        el.authPasswordInput.disabled = busy;
      }
      if (el.authDisplayNameInput) {
        el.authDisplayNameInput.disabled = busy;
      }
      if (el.authConfirmPasswordInput) {
        el.authConfirmPasswordInput.disabled = busy;
      }
      if (el.authForgotPasswordBtn) {
        el.authForgotPasswordBtn.disabled = busy;
      }
      if (el.authResendVerificationBtn) {
        el.authResendVerificationBtn.disabled = busy;
      }
      if (el.authBackToLoginBtn) {
        el.authBackToLoginBtn.disabled = busy;
      }
    };

    const cleanup = () => {
      el.authOverlay.classList.add("hidden");
      document.body.classList.remove("modal-open");
      el.authLoginTabBtn?.removeEventListener("click", onLoginMode);
      el.authRegisterTabBtn?.removeEventListener("click", onRegisterMode);
      el.authForm?.removeEventListener("submit", onFormSubmit);
      el.authEmailInput?.removeEventListener("keydown", onKeyDown);
      el.authEmailInput?.removeEventListener("input", onEmailInput);
      el.authPasswordInput?.removeEventListener("keydown", onKeyDown);
      el.authDisplayNameInput?.removeEventListener("keydown", onKeyDown);
      el.authConfirmPasswordInput?.removeEventListener("keydown", onKeyDown);
      el.authGuestBtn?.removeEventListener("click", onGuest);
      el.authContinueLoginBtn?.removeEventListener("click", onContinueLogin);
      el.authForgotPasswordBtn?.removeEventListener("click", onForgotPassword);
      el.authResendVerificationBtn?.removeEventListener("click", onResendVerification);
      el.authBackToLoginBtn?.removeEventListener("click", onBackToLogin);
    };

    const resolveSession = (result) => {
      cleanup();
      resolve(result);
    };

    const onLoginMode = () => {
      mode = "login";
      clearAuthError();
      clearAuthSuccess();
      setAuthMode(mode);
      updateResendVerificationVisibility();
      el.authEmailInput?.focus();
    };

    const onRegisterMode = () => {
      mode = "register";
      clearAuthError();
      clearAuthSuccess();
      setAuthMode(mode);
      updateResendVerificationVisibility();
      el.authDisplayNameInput?.focus();
    };

    const clearResetTokenFromUrl = () => {
      const url = new URL(window.location.href);
      url.searchParams.delete("resetToken");
      window.history.replaceState({}, "", url);
    };

    const onBackToLogin = () => {
      resetToken = "";
      clearResetTokenFromUrl();
      mode = "login";
      clearAuthError();
      clearAuthSuccess();
      setAuthChoiceVisible(false);
      setAuthMode(mode);
      updateResendVerificationVisibility();
      el.authEmailInput?.focus();
    };

    const onForgotPassword = async () => {
      clearAuthError();
      clearAuthSuccess();
      const email = (el.authEmailInput?.value || "").trim();
      if (!email) {
        showAuthInfo("Enter your email address below to request a password reset.");
        el.authEmailInput?.focus();
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showAuthError("Enter a valid email address");
        el.authEmailInput?.focus();
        return;
      }
      try {
        setBusy(true);
        const result = await api("/auth/password-reset/request", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        showAuthSuccess(
          result?.message ||
            "If an account with that email exists, a password reset link has been sent.",
        );
      } catch (error) {
        showAuthError(formatUserError(error));
      } finally {
        setBusy(false);
      }
    };

    const onResendVerification = async () => {
      clearAuthError();
      clearAuthSuccess();
      const email = (el.authEmailInput?.value || "").trim();
      if (!email) {
        showAuthInfo("Enter your email address below to resend the verification link.");
        el.authEmailInput?.focus();
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showAuthError("Enter a valid email address");
        el.authEmailInput?.focus();
        return;
      }
      try {
        setBusy(true);
        const result = await api("/auth/email-verification/request", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        showAuthSuccess(
          result?.message ||
            "If an unverified account with that email exists, a verification link has been sent.",
        );
        verificationPending = true;
        updateResendVerificationVisibility();
      } catch (error) {
        showAuthError(formatUserError(error));
      } finally {
        setBusy(false);
      }
    };

    const onContinueLogin = () => {
      setAuthChoiceVisible(false);
      setAuthMode(mode);
      el.authEmailInput?.focus();
    };

    const onGuest = async () => {
      clearAuthError();
      try {
        setBusy(true);
        const result = await api("/auth/guest", { method: "POST" });
        resolveSession(result);
      } catch (error) {
        showAuthError(formatUserError(error));
        setBusy(false);
      }
    };

    const onSubmit = async () => {
      clearAuthError();
      clearAuthSuccess();
      let payload;
      try {
        payload = validateAuthForm(mode);
      } catch (error) {
        showAuthError(formatUserError(error));
        return;
      }
      try {
        setBusy(true);
        if (mode === "reset") {
          await api("/auth/password-reset/complete", {
            method: "POST",
            body: JSON.stringify({ token: resetToken, password: payload.password }),
          });
          resetToken = "";
          clearResetTokenFromUrl();
          mode = "login";
          setAuthMode(mode);
          el.authPasswordInput.value = "";
          el.authConfirmPasswordInput.value = "";
          showAuthSuccess("Password updated. You can now sign in.");
          el.authEmailInput?.focus();
          setBusy(false);
          return;
        }
        if (mode === "register") {
          const result = await api("/auth/register", {
            method: "POST",
            body: JSON.stringify({
              email: payload.email,
              password: payload.password,
              displayName: payload.displayName,
            }),
          });
          mode = "login";
          setAuthMode(mode);
          verificationPending = true;
          updateResendVerificationVisibility();
          el.authPasswordInput.value = "";
          el.authDisplayNameInput.value = "";
          el.authConfirmPasswordInput.value = "";
          showAuthSuccess(
            result?.message ||
              "Account created. Check your email for a verification link before signing in.",
          );
          setBusy(false);
          el.authEmailInput?.focus();
          return;
        }
        const result = await api("/auth/login", {
          method: "POST",
          body: JSON.stringify({
            email: payload.email,
            password: payload.password,
          }),
        });
        resolveSession(result);
      } catch (error) {
        if (
          mode === "login" &&
          error?.status === 403 &&
          /verify your email|verification email/i.test(error.message || "")
        ) {
          verificationPending = true;
          updateResendVerificationVisibility();
        }
        showAuthError(formatUserError(error));
        setBusy(false);
      }
    };

    const onFormSubmit = (event) => {
      event.preventDefault();
      onSubmit();
    };

    const onKeyDown = (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onSubmit();
      }
    };

    const onEmailInput = () => clearAuthInfo();

    el.authLoginTabBtn?.addEventListener("click", onLoginMode);
    el.authRegisterTabBtn?.addEventListener("click", onRegisterMode);
    el.authForm?.addEventListener("submit", onFormSubmit);
    el.authEmailInput?.addEventListener("keydown", onKeyDown);
    el.authEmailInput?.addEventListener("input", onEmailInput);
    el.authPasswordInput?.addEventListener("keydown", onKeyDown);
    el.authDisplayNameInput?.addEventListener("keydown", onKeyDown);
    el.authConfirmPasswordInput?.addEventListener("keydown", onKeyDown);
    el.authGuestBtn?.addEventListener("click", onGuest);
    el.authContinueLoginBtn?.addEventListener("click", onContinueLogin);
    el.authForgotPasswordBtn?.addEventListener("click", onForgotPassword);
    el.authResendVerificationBtn?.addEventListener("click", onResendVerification);
    el.authBackToLoginBtn?.addEventListener("click", onBackToLogin);

    if (mode === "reset") {
      el.authPasswordInput?.focus();
    } else if (initialNotice) {
      el.authEmailInput?.focus();
    } else {
      el.authGuestBtn?.focus();
    }
  });
}

export async function ensureAuthenticated() {
  const url = new URL(window.location.href);
  const hasPasswordResetToken = Boolean(url.searchParams.get("resetToken"));
  const verificationToken = url.searchParams.get("verifyToken") || "";
  if (hasPasswordResetToken || verificationToken) {
    clearAuthSession();
  }
  let initialNotice = null;
  if (verificationToken) {
    try {
      await api("/auth/email-verification/complete", {
        method: "POST",
        body: JSON.stringify({ token: verificationToken }),
      });
      initialNotice = {
        type: "success",
        message: "Your email is verified. Sign in with your account to continue.",
      };
    } catch (error) {
      initialNotice = { type: "error", message: formatUserError(error) };
    }
    url.searchParams.delete("verifyToken");
    window.history.replaceState({}, "", url);
  }
  const existingToken = getAuthToken();
  if (existingToken && !hasPasswordResetToken && !verificationToken) {
    try {
      const me = await api("/auth/me", {
        headers: { "X-Auth-Token": existingToken },
      });
      state.auth.token = existingToken;
      state.auth.user = me;
      return { user: me, promptedLogin: false };
    } catch {
      setAuthToken("");
    }
  }
  const result = await showAuthDialog(initialNotice);
  setAuthToken(result.token);
  state.auth.token = result.token;
  state.auth.user = result.user;
  return { user: result.user, promptedLogin: true };
}

export async function logout() {
  const token = getAuthToken();
  if (token) {
    try {
      await api("/auth/logout", {
        method: "POST",
        headers: { "X-Auth-Token": token },
      });
    } catch (error) {
      console.warn("Logout request failed; clearing local session anyway.", error);
    }
  }
  clearAuthSession();
}

export async function updateDisplayName(displayName) {
  const token = getAuthToken();
  if (!token) {
    throw new Error("You are not authenticated");
  }
  const normalized = (displayName || "").trim();
  if (!normalized) {
    throw new Error("Display name is required");
  }
  const updated = await api("/auth/me", {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ displayName: normalized }),
  });
  state.auth.user = updated;
  return updated;
}
