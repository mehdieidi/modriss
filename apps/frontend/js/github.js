import { state } from "./state.js";
import { el } from "./dom.js";
import { api, isPlannedFeatureError } from "./api.js";
import { backendOrigin } from "./config.js";
import { setError, setStatus } from "./status.js";

const OAUTH_MESSAGE_SOURCE = "modless-github-oauth";
const OAUTH_POPUP_TIMEOUT_MS = 180000;
const POPUP_CLOSED_CHECK_INTERVAL_MS = 400;

function setDeployButtonBusy(busy, label = "Deploy to GitHub") {
  if (!el.deployGithubBtn) {
    return;
  }
  el.deployGithubBtn.disabled = busy;
  const labelEl = el.deployGithubBtn.querySelector(".topbar-btn-label");
  if (labelEl) {
    labelEl.textContent = busy ? label : "Deploy to GitHub";
  } else {
    el.deployGithubBtn.textContent = busy ? label : "Deploy to GitHub";
  }
}

function renderGithubPanel() {
  if (!el.githubDeployStatus || !el.githubDeployMeta || !el.deployGithubBtn) {
    return;
  }

  const g = state.github;
  if (g.lastDeploymentStatus === "UNAVAILABLE") {
    el.githubDeployStatus.textContent = "GitHub integration is unavailable in this backend build";
  } else if (g.connected) {
    el.githubDeployStatus.textContent = g.selectedRepository
      ? `Connected as ${g.githubLogin || "GitHub user"} - ${g.selectedRepository}`
      : `Connected as ${g.githubLogin || "GitHub user"} - repository not selected`;
  } else {
    el.githubDeployStatus.textContent = "Not connected to GitHub";
  }

  const meta = [];
  if (g.selectedBranch) {
    meta.push(`Branch: ${g.selectedBranch}`);
  }
  if (g.lastDeploymentStatus) {
    meta.push(`Last: ${g.lastDeploymentStatus}`);
  }
  if (g.lastDeploymentAt) {
    meta.push(`At: ${new Date(g.lastDeploymentAt).toLocaleString()}`);
  }
  if (g.lastDeploymentMessage) {
    meta.push(g.lastDeploymentMessage);
  }
  el.githubDeployMeta.textContent = meta.join(" - ");

  if (el.githubRepoLink) {
    const hasRepoUrl = !!g.repositoryUrl;
    el.githubRepoLink.classList.toggle("hidden", !hasRepoUrl);
    if (hasRepoUrl) {
      el.githubRepoLink.href = g.repositoryUrl;
    }
  }
  if (el.githubCommitLink) {
    const hasCommitUrl = !!g.commitUrl;
    el.githubCommitLink.classList.toggle("hidden", !hasCommitUrl);
    if (hasCommitUrl) {
      el.githubCommitLink.href = g.commitUrl;
    }
  }
}

function applyConnectionState(payload) {
  state.github.available = true;
  state.github.connected = !!payload.connected;
  state.github.githubLogin = payload.githubLogin || "";
  state.github.selectedRepository = payload.selectedRepository || "";
  state.github.selectedBranch = payload.selectedBranch || "";
  state.github.lastDeploymentStatus = payload.lastDeploymentStatus || "";
  state.github.lastDeploymentMessage = payload.lastDeploymentMessage || "";
  state.github.lastDeploymentAt = payload.lastDeploymentAt || "";
  state.github.repositoryUrl = payload.repositoryUrl || "";
  state.github.commitUrl = payload.commitUrl || "";
  renderGithubPanel();
}

function setGithubUnavailable(message) {
  state.github.available = false;
  state.github.connected = false;
  state.github.githubLogin = "";
  state.github.selectedRepository = "";
  state.github.selectedBranch = "";
  state.github.lastDeploymentStatus = "UNAVAILABLE";
  state.github.lastDeploymentMessage = message;
  state.github.lastDeploymentAt = "";
  state.github.repositoryUrl = "";
  state.github.commitUrl = "";
  renderGithubPanel();
}

export async function refreshGithubConnection() {
  if (state.github.available === false) {
    renderGithubPanel();
    return false;
  }
  try {
    const connection = await api("/github/connection");
    applyConnectionState(connection || {});
    return true;
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      setGithubUnavailable("GitHub integration is not available in this backend build.");
      return false;
    }
    state.github.connected = false;
    state.github.githubLogin = "";
    state.github.selectedRepository = "";
    state.github.selectedBranch = "";
    state.github.lastDeploymentStatus = "ERROR";
    state.github.lastDeploymentMessage = error.message;
    renderGithubPanel();
    return false;
  }
}

async function openGithubOAuthPopup() {
  const oauthStart = await api("/github/oauth/start");
  if (!oauthStart?.authorizeUrl) {
    throw new Error("GitHub OAuth URL was not provided");
  }

  const popup = window.open(
    oauthStart.authorizeUrl,
    "modless-github-oauth",
    "popup=yes,width=640,height=760,noopener,noreferrer",
  );
  if (!popup) {
    throw new Error("Popup blocked. Allow popups and try again.");
  }

  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      cleanup();
      reject(new Error("GitHub authentication timed out"));
    }, OAUTH_POPUP_TIMEOUT_MS);

    const closedCheck = window.setInterval(() => {
      if (!popup.closed) {
        return;
      }
      cleanup();
      reject(new Error("GitHub authentication popup was closed"));
    }, POPUP_CLOSED_CHECK_INTERVAL_MS);

    const onMessage = (event) => {
      if (event.origin !== backendOrigin) {
        return;
      }
      const payload = event.data || {};
      if (payload.source !== OAUTH_MESSAGE_SOURCE) {
        return;
      }
      cleanup();
      if (payload.success) {
        resolve();
      } else {
        reject(new Error(payload.message || "GitHub authentication failed"));
      }
    };

    const cleanup = () => {
      window.clearTimeout(timeout);
      window.clearInterval(closedCheck);
      window.removeEventListener("message", onMessage);
    };

    window.addEventListener("message", onMessage);
  });
}

async function chooseOrCreateRepository() {
  const repos = await api("/github/repositories");
  if (!Array.isArray(repos) || repos.length === 0) {
    const createName = window.prompt("No repositories found. Enter a new repository name:");
    if (!createName || !createName.trim()) {
      throw new Error("Repository selection canceled");
    }
    const created = await api("/github/repositories", {
      method: "POST",
      body: JSON.stringify({ name: createName.trim(), privateRepo: false }),
    });
    return created?.fullName || "";
  }

  const defaultRepo = state.github.selectedRepository || repos[0].fullName;
  const picked = window.prompt(
    `Choose repository (owner/repo).\nRecent: ${repos
      .slice(0, 8)
      .map((r) => r.fullName)
      .join(", ")}`,
    defaultRepo,
  );
  if (!picked || !picked.trim()) {
    throw new Error("Repository selection canceled");
  }
  return picked.trim();
}

export async function deployToGithubFromArtifacts() {
  const artifactId = state.artifact.id || state.project?.activeModelIds?.artifact;
  if (!artifactId) {
    setError("Load the current artifact before deploying");
    return;
  }
  if (state.github.available === false) {
    setStatus("GitHub integration is not available in this backend build.");
    return;
  }

  setDeployButtonBusy(true, state.github.connected ? "Deploying..." : "Connecting...");
  try {
    if (!state.github.connected) {
      await openGithubOAuthPopup();
      await refreshGithubConnection();
    }

    let repositoryFullName = state.github.selectedRepository;
    if (!repositoryFullName) {
      repositoryFullName = await chooseOrCreateRepository();
    }

    const payload = {
      artifactId,
      repositoryFullName,
      commitMessage: null,
    };
    const deployResult = await api("/github/deploy", {
      method: "POST",
      body: JSON.stringify(payload),
    });

    state.github.selectedRepository = deployResult.repositoryFullName || repositoryFullName;
    state.github.selectedBranch = deployResult.branch || state.github.selectedBranch;
    state.github.lastDeploymentStatus = deployResult.noChanges ? "NO_CHANGES" : "SUCCESS";
    state.github.lastDeploymentMessage = deployResult.message || "Deployment completed";
    state.github.lastDeploymentAt = deployResult.deployedAt || "";
    state.github.repositoryUrl = deployResult.repositoryUrl || state.github.repositoryUrl;
    state.github.commitUrl = deployResult.commitUrl || state.github.commitUrl;
    renderGithubPanel();
    setStatus(deployResult.message || "Deployment completed");
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      setGithubUnavailable("GitHub integration is not available in this backend build.");
      setStatus("GitHub integration is not available in this backend build.");
      return;
    }
    state.github.lastDeploymentStatus = "ERROR";
    state.github.lastDeploymentMessage = error.message || "GitHub deployment failed";
    renderGithubPanel();
    setError(`GitHub deployment failed: ${error.message}`);
  } finally {
    setDeployButtonBusy(false);
    await refreshGithubConnection();
  }
}
