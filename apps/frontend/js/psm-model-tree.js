import { enhanceSelects, closeThemedSelect } from "./themed-select.js";

const XSI = "http://www.w3.org/2001/XMLSchema-instance";
const XMLNS = "http://www.w3.org/2000/xmlns/";

function qualified(type) {
  return `${type.namespace}#${type.name}`;
}

export function modelElementType(element, schema) {
  const explicit = element.getAttributeNS(XSI, "type");
  if (explicit) {
    const [prefix, local] = explicit.includes(":") ? explicit.split(":") : [null, explicit];
    const namespace = prefix ? element.lookupNamespaceURI(prefix) : element.namespaceURI;
    return schema.find((type) => type.name === local && type.namespace === namespace);
  }
  if (element.parentElement) {
    const parentType = modelElementType(element.parentElement, schema);
    const feature = parentType?.features.find(
      (feature) => feature.containment && feature.name === element.localName,
    );
    if (feature)
      return schema.find(
        (type) => type.name === feature.type && type.namespace === feature.typeNamespace,
      );
  }
  return schema.find(
    (type) => type.name === element.localName && type.namespace === element.namespaceURI,
  );
}

function candidates(feature, schema) {
  const base = `${feature.typeNamespace}#${feature.type}`;
  return schema.filter(
    (type) => !type.abstract && (qualified(type) === base || type.supertypes?.includes(base)),
  );
}

function namespacePrefix(xml, namespace) {
  const existing = xml.documentElement.lookupPrefix(namespace);
  if (existing) return existing;
  let index = 1;
  while (xml.documentElement.lookupNamespaceURI(`psm${index}`)) index++;
  const prefix = `psm${index}`;
  xml.documentElement.setAttributeNS(XMLNS, `xmlns:${prefix}`, namespace);
  return prefix;
}

/** A metamodel-derived editor: attributes, enums, references and typed containment creation. */
export function renderPsmModelTree({ xml, container, schema, onChange }) {
  closeThemedSelect();
  const doc = container.ownerDocument;
  const elements = Array.from(xml.getElementsByTagName("*"));
  container.replaceChildren();
  const changed = (refresh = false) => {
    onChange(xml);
    if (refresh) renderPsmModelTree({ xml, container, schema, onChange });
  };
  for (const [index, element] of elements.slice(0, 250).entries()) {
    const type = modelElementType(element, schema);
    const row = doc.createElement("details");
    row.open = index === 0;
    const summary = doc.createElement("summary");
    summary.textContent = `${type?.name || element.getAttributeNS(XSI, "type") || element.localName}${element.getAttribute("name") ? ` · ${element.getAttribute("name")}` : ""}`;
    row.append(summary);
    const features = new Map((type?.features || []).map((feature) => [feature.name, feature]));
    const fields = new Set([
      ...Array.from(element.attributes).map((attribute) => attribute.name),
      ...(type?.features || [])
        .filter((feature) => !feature.containment && !feature.derived)
        .map((feature) => feature.name),
    ]);
    for (const name of fields) {
      if (name.startsWith("xmlns") || name === "xmi:version" || name === "xsi:type") continue;
      const feature = features.get(name);
      if (feature?.containment || feature?.derived) continue;
      const label = doc.createElement("label");
      label.textContent = `${name}${feature?.required ? " *" : ""}`;
      const options =
        feature?.literals || (feature?.type === "EBoolean" ? ["true", "false"] : null);
      const input = doc.createElement(options ? "select" : "input");
      if (options) {
        for (const value of ["", ...options]) {
          const option = doc.createElement("option");
          option.value = value;
          option.textContent = value || "Not set";
          input.append(option);
        }
      } else if (
        feature &&
        /^(EInt|ELong|EShort|EByte|EFloat|EDouble|EBigDecimal|EBigInteger)$/.test(feature.type)
      ) {
        input.type = "number";
        input.step = /Float|Double|Decimal/.test(feature.type) ? "any" : "1";
      }
      input.value = element.getAttribute(name) ?? feature?.defaultValue ?? "";
      input.dataset.modelAttribute = name;
      input.setAttribute("aria-label", name);
      input.disabled = feature?.changeable === false;
      input.title = feature ? `${feature.type}${feature.many ? " · multiple values" : ""}` : name;
      if (feature?.kind === "reference") input.placeholder = "XMI reference";
      input.addEventListener("change", () => {
        const attribute = element.getAttributeNode(name);
        if (!input.value) element.removeAttribute(name);
        else if (attribute) attribute.value = input.value;
        else element.setAttribute(name, input.value);
        changed();
      });
      label.append(input);
      row.append(label);
    }
    for (const feature of type?.features || []) {
      if (!feature.containment || feature.derived || feature.changeable === false) continue;
      const count = Array.from(element.children).filter(
        (child) => child.localName === feature.name,
      ).length;
      const allowed = candidates(feature, schema);
      if (!allowed.length) continue;
      const group = doc.createElement("div");
      group.className = "psm-containment-action";
      const label = doc.createElement("label");
      label.textContent = `${feature.name} · ${count}${feature.upperBound > -1 ? ` / ${feature.upperBound}` : ""}`;
      const select = doc.createElement("select");
      select.setAttribute("aria-label", `New ${feature.name} type`);
      for (const childType of allowed) {
        const option = doc.createElement("option");
        option.value = qualified(childType);
        option.textContent = childType.name;
        select.append(option);
      }
      label.append(select);
      group.append(label);
      const add = doc.createElement("button");
      add.className = "btn btn-secondary btn-sm";
      add.type = "button";
      add.textContent = `Add ${feature.name}`;
      add.disabled = feature.upperBound > -1 && count >= feature.upperBound;
      add.addEventListener("click", () => {
        const childType = allowed.find((candidate) => qualified(candidate) === select.value);
        const child = xml.createElement(feature.name);
        child.setAttributeNS(
          XSI,
          "xsi:type",
          `${namespacePrefix(xml, childType.namespace)}:${childType.name}`,
        );
        for (const attr of childType.features) {
          if (attr.kind === "attribute" && attr.defaultValue != null && !attr.derived)
            child.setAttribute(attr.name, attr.defaultValue);
        }
        element.append(child);
        changed(true);
        container.querySelectorAll("details").forEach((details) => {
          details.open = true;
        });
      });
      group.append(add);
      row.append(group);
    }
    if (element !== xml.documentElement && element.parentElement) {
      const remove = doc.createElement("button");
      remove.type = "button";
      remove.className = "btn btn-secondary btn-sm psm-remove-element";
      remove.textContent = "Remove element";
      remove.addEventListener("click", () => {
        element.remove();
        changed(true);
      });
      row.append(remove);
    }
    container.append(row);
  }
  if (elements.length > 250) {
    const notice = doc.createElement("p");
    notice.textContent =
      "Showing the first 250 elements. Use XMI source to edit the complete model.";
    container.append(notice);
  }
  enhanceSelects(container);
}
