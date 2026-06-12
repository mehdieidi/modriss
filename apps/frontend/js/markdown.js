const SAFE_LINK_PROTOCOLS = new Set(["http:", "https:", "mailto:"]);

function appendInlineMarkdown(parent, text) {
  const pattern = /(`[^`\n]+`|\[([^\]]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)|\*\*([^*\n]+)\*\*|__([^_\n]+)__|\*([^*\n]+)\*|_([^_\n]+)_|~~([^~\n]+)~~)/g;
  let cursor = 0;
  let match;

  while ((match = pattern.exec(text)) !== null) {
    parent.append(document.createTextNode(text.slice(cursor, match.index)));
    const token = match[0];
    let element;

    if (token.startsWith("`")) {
      element = document.createElement("code");
      element.textContent = token.slice(1, -1);
    } else if (token.startsWith("[")) {
      element = document.createElement("a");
      element.textContent = match[2];
      try {
        const url = new URL(match[3], window.location.href);
        if (SAFE_LINK_PROTOCOLS.has(url.protocol)) {
          element.href = url.href;
          element.target = "_blank";
          element.rel = "noopener noreferrer";
        }
      } catch {
        // Leave invalid links as plain text.
      }
      if (!element.href) {
        element = document.createTextNode(match[2]);
      }
    } else if (token.startsWith("**") || token.startsWith("__")) {
      element = document.createElement("strong");
      element.textContent = match[4] || match[5];
    } else if (token.startsWith("~~")) {
      element = document.createElement("del");
      element.textContent = match[8];
    } else {
      element = document.createElement("em");
      element.textContent = match[6] || match[7];
    }

    parent.append(element);
    cursor = pattern.lastIndex;
  }

  parent.append(document.createTextNode(text.slice(cursor)));
}

function appendParagraph(container, lines) {
  if (!lines.length) {
    return;
  }
  const paragraph = document.createElement("p");
  lines.forEach((line, index) => {
    if (index > 0) {
      paragraph.append(document.createTextNode(" "));
    }
    appendInlineMarkdown(paragraph, line.trim());
  });
  container.append(paragraph);
}

function isBlockStart(line) {
  return /^(```|#{1,6}\s+|>\s?|[-*_](?:\s*[-*_]){2,}\s*$|(?:[-+*]|\d+\.)\s+)/.test(
      line);
}

function tableCells(line) {
  return line.trim().replace(/^\||\|$/g, "").split("|").map(
      (cell) => cell.trim());
}

export function renderMarkdown(text) {
  const root = document.createElement("div");
  root.className = "chat-markdown";
  const lines = String(text || "").replace(/\r\n?/g, "\n").split("\n");
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }

    const fence = line.match(/^```([\w+-]*)\s*$/);
    if (fence) {
      const codeLines = [];
      index += 1;
      while (index < lines.length && !/^```\s*$/.test(lines[index])) {
        codeLines.push(lines[index]);
        index += 1;
      }
      index += index < lines.length ? 1 : 0;
      const pre = document.createElement("pre");
      const code = document.createElement("code");
      if (fence[1]) {
        code.dataset.language = fence[1];
      }
      code.textContent = codeLines.join("\n");
      pre.append(code);
      root.append(pre);
      continue;
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      const element = document.createElement(`h${heading[1].length}`);
      appendInlineMarkdown(element, heading[2].replace(/\s+#+\s*$/, ""));
      root.append(element);
      index += 1;
      continue;
    }

    if (/^[-*_](?:\s*[-*_]){2,}\s*$/.test(line)) {
      root.append(document.createElement("hr"));
      index += 1;
      continue;
    }

    if (/^>\s?/.test(line)) {
      const quote = document.createElement("blockquote");
      const quoteLines = [];
      while (index < lines.length && /^>\s?/.test(lines[index])) {
        quoteLines.push(lines[index].replace(/^>\s?/, ""));
        index += 1;
      }
      appendParagraph(quote, quoteLines);
      root.append(quote);
      continue;
    }

    if (index + 1 < lines.length && line.includes("|")
        && /^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(
            lines[index + 1])) {
      const headers = tableCells(line);
      const table = document.createElement("table");
      const headRow = document.createElement("tr");
      headers.forEach((header) => {
        const cell = document.createElement("th");
        appendInlineMarkdown(cell, header);
        headRow.append(cell);
      });
      const head = document.createElement("thead");
      head.append(headRow);
      table.append(head);
      const body = document.createElement("tbody");
      index += 2;
      while (index < lines.length && lines[index].includes("|")
          && lines[index].trim()) {
        const row = document.createElement("tr");
        tableCells(lines[index]).forEach((value) => {
          const cell = document.createElement("td");
          appendInlineMarkdown(cell, value);
          row.append(cell);
        });
        body.append(row);
        index += 1;
      }
      table.append(body);
      root.append(table);
      continue;
    }

    const listItem = line.match(/^(\s*)([-+*]|\d+\.)\s+(.+)$/);
    if (listItem) {
      const ordered = /\d+\./.test(listItem[2]);
      const list = document.createElement(ordered ? "ol" : "ul");
      while (index < lines.length) {
        const itemMatch = lines[index].match(/^(\s*)([-+*]|\d+\.)\s+(.+)$/);
        if (!itemMatch || /\d+\./.test(itemMatch[2]) !== ordered) {
          break;
        }
        const item = document.createElement("li");
        appendInlineMarkdown(item, itemMatch[3]);
        list.append(item);
        index += 1;
      }
      root.append(list);
      continue;
    }

    const paragraphLines = [line];
    index += 1;
    while (index < lines.length && lines[index].trim()
        && !isBlockStart(lines[index])) {
      paragraphLines.push(lines[index]);
      index += 1;
    }
    appendParagraph(root, paragraphLines);
  }

  return root;
}
