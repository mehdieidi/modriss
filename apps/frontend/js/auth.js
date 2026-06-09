import {state} from './state.js';
import {el} from './dom.js';
import {api} from './api.js';

const AUTH_TOKEN_KEY = "modless.authToken";

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
  if (el.authLoginTabBtn) {
    el.authLoginTabBtn.classList.toggle("active",
        !registerMode);
  }
  if (el.authRegisterTabBtn) {
    el.authRegisterTabBtn.classList.toggle("active",
        registerMode);
  }
  if (el.authDisplayNameInput) {
    el.authDisplayNameInput.classList.toggle("hidden", !registerMode);
  }
  if (el.authConfirmPasswordInput) {
    el.authConfirmPasswordInput.classList.toggle("hidden", !registerMode);
  }
  if (el.authTitle) {
    el.authTitle.textContent = registerMode ? "Create your Modless account"
        : "Sign in to Modless";
  }
  if (el.authSubtitle) {
    el.authSubtitle.textContent = registerMode
        ? "Register to create and manage projects."
        : "Use your account to continue.";
  }
  if (el.authPasswordInput) {
    el.authPasswordInput.autocomplete = registerMode ? "new-password"
        : "current-password";
  }
  if (el.authSubmitBtn) {
    el.authSubmitBtn.textContent = registerMode ? "Register" : "Login";
  }
}

function showAuthError(message) {
  if (!el.authError) {
    return;
  }
  el.authError.textContent = message;
  el.authError.classList.remove("hidden");
}

function clearAuthError() {
  if (!el.authError) {
    return;
  }
  el.authError.textContent = "";
  el.authError.classList.add("hidden");
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

  if (!emailRegex.test(email)) {
    throw new Error("Enter a valid email address");
  }
  if (password.length < 8) {
    throw new Error("Password must be at least 8 characters");
  }
  if (mode === "register") {
    if (!displayName) {
      throw new Error("Display name is required");
    }
    if (displayName.length > 80) {
      throw new Error("Display name must be at most 80 characters");
    }
    if (password !== confirmPassword) {
      throw new Error("Password confirmation does not match");
    }
  }
  return {email, password, displayName};
}

async function showAuthDialog() {
  if (!el.authOverlay) {
    throw new Error("Authentication UI is unavailable");
  }
  let mode = "login";
  setAuthMode(mode);
  clearAuthError();
  clearAuthSuccess();
  el.authEmailInput.value = "";
  el.authPasswordInput.value = "";
  if (el.authDisplayNameInput) {
    el.authDisplayNameInput.value = "";
  }
  if (el.authConfirmPasswordInput) {
    el.authConfirmPasswordInput.value = "";
  }
  el.authOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");

  return new Promise((resolve) => {
    const setBusy = (busy) => {
      if (el.authSubmitBtn) {
        el.authSubmitBtn.disabled = busy;
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
    };

    const cleanup = () => {
      el.authOverlay.classList.add("hidden");
      document.body.classList.remove("modal-open");
      el.authLoginTabBtn?.removeEventListener("click", onLoginMode);
      el.authRegisterTabBtn?.removeEventListener("click", onRegisterMode);
      el.authSubmitBtn?.removeEventListener("click", onSubmit);
      el.authEmailInput?.removeEventListener("keydown", onKeyDown);
      el.authPasswordInput?.removeEventListener("keydown", onKeyDown);
      el.authDisplayNameInput?.removeEventListener("keydown", onKeyDown);
      el.authConfirmPasswordInput?.removeEventListener("keydown", onKeyDown);
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
      el.authEmailInput?.focus();
    };

    const onRegisterMode = () => {
      mode = "register";
      clearAuthError();
      clearAuthSuccess();
      setAuthMode(mode);
      el.authDisplayNameInput?.focus();
    };

    const onSubmit = async () => {
      clearAuthError();
      clearAuthSuccess();
      let payload;
      try {
        payload = validateAuthForm(mode);
      } catch (error) {
        showAuthError(error.message);
        return;
      }
      try {
        setBusy(true);
        if (mode === "register") {
          const result = await api("/auth/register", {
            method: "POST",
            body: JSON.stringify({
              email: payload.email,
              password: payload.password,
              displayName: payload.displayName
            })
          });
          showAuthSuccess("Registration successful. Signing you in…");
          resolveSession(result);
          return;
        }
        const result = await api("/auth/login", {
          method: "POST",
          body: JSON.stringify({
            email: payload.email,
            password: payload.password
          })
        });
        resolveSession(result);
      } catch (error) {
        showAuthError(error.message || "Authentication failed");
        setBusy(false);
      }
    };

    const onKeyDown = (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onSubmit();
      }
    };

    el.authLoginTabBtn?.addEventListener("click", onLoginMode);
    el.authRegisterTabBtn?.addEventListener("click", onRegisterMode);
    el.authSubmitBtn?.addEventListener("click", onSubmit);
    el.authEmailInput?.addEventListener("keydown", onKeyDown);
    el.authPasswordInput?.addEventListener("keydown", onKeyDown);
    el.authDisplayNameInput?.addEventListener("keydown", onKeyDown);
    el.authConfirmPasswordInput?.addEventListener("keydown", onKeyDown);

    el.authEmailInput?.focus();
  });
}

export async function ensureAuthenticated() {
  const existingToken = getAuthToken();
  if (existingToken) {
    try {
      const me = await api("/auth/me", {
        headers: {"X-Auth-Token": existingToken}
      });
      state.auth.token = existingToken;
      state.auth.user = me;
      return {user: me, promptedLogin: false};
    } catch {
      setAuthToken("");
    }
  }
  const result = await showAuthDialog();
  setAuthToken(result.token);
  state.auth.token = result.token;
  state.auth.user = result.user;
  return {user: result.user, promptedLogin: true};
}

export async function logout() {
  const token = getAuthToken();
  if (token) {
    try {
      await api("/auth/logout", {
        method: "POST",
        headers: {"X-Auth-Token": token}
      });
    } catch (error) {
      console.warn("Logout request failed; clearing local session anyway.",
          error);
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
    headers: {"X-Auth-Token": token},
    body: JSON.stringify({displayName: normalized})
  });
  state.auth.user = updated;
  return updated;
}
