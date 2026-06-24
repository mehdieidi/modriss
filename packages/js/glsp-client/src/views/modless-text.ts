export function truncate(text: string, max = 44): string {
  const value = String(text || "");
  return value.length > max ? `${value.slice(0, Math.max(1, max - 1))}…` : value;
}

export function lineBreak(text: string, maxLine = 23, maxLines = 2): string {
  const value = String(text || "").trim();
  if (!value) {
    return "";
  }
  const words = value.split(/\s+/);
  const lines: string[] = [];
  let line = "";
  for (const word of words) {
    const next = line ? `${line} ${word}` : word;
    if (next.length > maxLine && line) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  }
  if (line) {
    lines.push(line);
  }
  return lines
    .slice(0, maxLines)
    .map((item, index) => {
      if (index === maxLines - 1 && lines.length > maxLines) {
        return truncate(item, maxLine - 1);
      }
      return truncate(item, maxLine);
    })
    .join("\n");
}

export function lineCount(text: string): number {
  const value = String(text || "");
  return value ? value.split("\n").length : 0;
}

export function boundedText(text: string, maxLine: number, maxLines: number): string {
  return lineBreak(text, maxLine, maxLines).toUpperCase();
}
