import { GNode, IView, RenderingContext, svg } from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";

@injectable()
export class ModlessNodeView implements IView {
  render(node: Readonly<GNode>, context: RenderingContext): VNode {
    const args = (node as unknown as { args?: Record<string, unknown> }).args || {};
    const color = String(args.color || "var(--node-accent, #475569)");
    const tag = String(args.tag || "NODE");
    const width = node.size?.width || 228;
    const height = node.size?.height || 112;
    const labels = (node.children || []).filter((child) => child.type === "label");
    const title = labels[0]?.text || node.id;
    const lines = labels[1]?.text || "";

    return svg`
      <g class="modless-node glsp-node" data-id="${node.id}" data-element-id="${node.id}">
        <rect
          x="0"
          y="0"
          width="${width}"
          height="${height}"
          rx="8"
          ry="8"
          class="modless-node-body glsp-node-body"
          style="fill: var(--node-bg, #1e293b); stroke: ${color}; stroke-width: 1.5;"
        />
        <rect x="0" y="0" width="${width}" height="28" rx="8" class="modless-node-header" style="fill: ${color}; opacity: 0.18;" />
        <text x="12" y="18" class="modless-node-tag" style="fill: ${color}; font-size: 10px; font-weight: 700;">${tag}</text>
        <text x="12" y="44" class="modless-node-title" style="fill: var(--text-primary, #f8fafc); font-size: 13px; font-weight: 600;">${title}</text>
        <text x="12" y="64" class="modless-node-lines" style="fill: var(--text-secondary, #94a3b8); font-size: 11px;">${lines}</text>
      </g>
    `;
  }
}
