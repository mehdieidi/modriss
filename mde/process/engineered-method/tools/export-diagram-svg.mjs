import fs from 'node:fs';
import path from 'node:path';

const [, , sourceArg, outputArg] = process.argv;
if (!sourceArg) throw new Error('Usage: node export-diagram-svg.mjs <source.html> [output.svg]');

const source = path.resolve(sourceArg);
const output = path.resolve(outputArg ?? source.replace(/\.html$/i, '.svg'));
const html = fs.readFileSync(source, 'utf8');
const match = html.match(/<svg\b[\s\S]*?<\/svg>/i);
if (!match) throw new Error(`No SVG element found in ${source}`);

let svg = match[0];
if (!/^<svg\b[^>]*\bxmlns=/i.test(svg)) {
  svg = svg.replace(/^<svg\b/i, '<svg xmlns="http://www.w3.org/2000/svg"');
}
if (!/^<svg\b[^>]*\bviewBox=/i.test(svg)) throw new Error('SVG has no viewBox');

const fontImport = "<style>@import url('https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&amp;family=Geist:wght@400;500;600&amp;family=Geist+Mono:wght@400;500;600&amp;family=Noto+Serif:ital@0;1&amp;display=swap');</style>";
if (/<defs>/i.test(svg)) {
  svg = svg.replace(/<defs>/i, `<defs>\n    ${fontImport}`);
} else {
  const descEnd = svg.indexOf('</desc>');
  if (descEnd < 0) throw new Error('SVG has no accessible description');
  const insertAt = descEnd + '</desc>'.length;
  svg = `${svg.slice(0, insertAt)}\n  <defs>${fontImport}</defs>${svg.slice(insertAt)}`;
}

svg = svg.replace(
  /(fill|stroke)="rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d*\.?\d+)\s*\)"/g,
  (_, property, red, green, blue, alpha) => {
    const hex = [red, green, blue]
      .map(channel => Number(channel).toString(16).padStart(2, '0'))
      .join('');
    return `${property}="#${hex}" ${property}-opacity="${alpha}"`;
  },
);
svg = svg.replace(/(fill|stroke)="transparent"/g, '$1="none"');

fs.writeFileSync(output, `<?xml version="1.0" encoding="UTF-8"?>\n${svg}\n`);
console.log(path.relative(process.cwd(), output).replaceAll('\\', '/'));
