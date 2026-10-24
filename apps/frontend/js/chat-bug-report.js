const ISSUE_URL = "https://github.com/mehdieidi/modriss/issues/new";
const MAX_ISSUE_URL_LENGTH = 7500;

export function buildChatBugReportUrl(context = {}) {
  // Keep the draft small enough for a URL, even with percent-encoded Unicode.
  const fields = [
    ["Reported at (UTC)", context.timestamp],
    ["Editor origin", context.origin],
    ["Browser", context.userAgent],
    ["Language", context.language],
    ["Viewport", context.viewport],
    ["Modeling level", context.level],
    ["Project ID", context.projectId],
    ["Model ID", context.modelId],
    ["Model revision", context.modelRevision],
    ["Conversation ID", context.sessionId],
    ["Latest turn ID", context.turnId],
    ["Turn state", context.turnState],
    ["Language model", context.modelName],
    ["Unsaved model changes", context.unsavedChanges],
  ];
  for (let limit = 240; limit >= 15; limit /= 2) {
    const diagnostics = fields
      .map(([label, value]) => {
        const text = String(value ?? "Not available").replace(/[\r\n]+/g, " ");
        return `${label}: ${text.length > limit ? `${text.slice(0, limit)}…` : text}`;
      })
      .join("\n");
    const body = [
      "## What went wrong?",
      "<!-- Describe the problem and what you were trying to do. -->",
      "",
      "## Steps to reproduce",
      "1. ",
      "2. ",
      "3. ",
      "",
      "## Expected behavior",
      "<!-- What should have happened? -->",
      "",
      "## Actual behavior",
      "<!-- What happened instead? Include any error message. -->",
      "",
      "## Relevant conversation or screenshots",
      "<!-- Add the relevant prompt/response or screenshots, removing private information. -->",
      "",
      "## Debugging context",
      "<!-- Automatically collected metadata. Review before submitting this public issue. -->",
      "<!-- Conversation text, model contents, attachments, and account details are not included. -->",
      diagnostics,
    ].join("\n");
    const url = new URL(ISSUE_URL);
    url.searchParams.set("title", "[Chatbot] Describe the problem");
    url.searchParams.set("body", body);
    if (url.href.length <= MAX_ISSUE_URL_LENGTH) return url.href;
  }
}
