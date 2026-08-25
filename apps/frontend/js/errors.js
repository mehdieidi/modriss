const SERVER_ERROR_DEFAULT = "An unexpected server error occurred.";
const GENERIC_ERROR = "Something went wrong. Please try again.";
const MAX_CLIENT_ISSUES = 3;

export class ApiError extends Error {
  constructor(
    userMessage,
    { status = 0, path = "", method = "GET", issues = [], errorId = "" } = {},
  ) {
    super(userMessage);
    this.name = "ApiError";
    this.userMessage = userMessage;
    this.status = status;
    this.path = path;
    this.method = method;
    this.issues = Array.isArray(issues) ? issues : [];
    this.errorId = String(errorId || "");
    this.featureUnavailable = status === 501;
  }
}

export function isServerApiError(error) {
  return error instanceof ApiError && error.status >= 500 && error.status !== 501;
}

export function isPlannedFeatureError(error) {
  return error instanceof ApiError && error.featureUnavailable;
}

export function appendErrorReference(message, errorId) {
  const text = String(message || "").trim() || SERVER_ERROR_DEFAULT;
  const id = String(errorId || "").trim();
  if (!id) {
    return text;
  }
  return `${text} Reference: ${id}`;
}

export function isServerErrorStatus(status) {
  return status >= 500 && status !== 501;
}

export function buildClientErrorMessage(message, issues) {
  const summary = String(message || "Request failed.").trim();
  if (!issues.length) {
    return summary;
  }
  const visibleIssues = issues.slice(0, MAX_CLIENT_ISSUES).join("; ");
  if (issues.length > MAX_CLIENT_ISSUES) {
    return `${summary} ${visibleIssues}; and ${issues.length - MAX_CLIENT_ISSUES} more issue(s).`;
  }
  return `${summary} ${visibleIssues}`;
}

export function buildUserMessage(status, bodyMessage, issues, errorId) {
  if (isServerErrorStatus(status)) {
    return appendErrorReference(bodyMessage || SERVER_ERROR_DEFAULT, errorId);
  }
  return buildClientErrorMessage(bodyMessage, issues);
}

export function formatUserError(error, { prefix = "" } = {}) {
  let message = GENERIC_ERROR;
  if (error instanceof ApiError) {
    message = error.userMessage || error.message || GENERIC_ERROR;
  } else if (error instanceof Error) {
    message = error.userMessage || GENERIC_ERROR;
  } else if (typeof error === "string" && error.trim()) {
    message = error.trim();
  }

  const lead = String(prefix || "").trim();
  if (!lead) {
    return message;
  }
  return `${lead} ${message}`;
}
