/** Read ZIP filenames for entry selection. Server-side extraction performs authoritative checks. */
export function zipEntryNames(buffer) {
  const view = new DataView(buffer);
  let end = -1;
  for (
    let offset = buffer.byteLength - 22;
    offset >= Math.max(0, buffer.byteLength - 65557);
    offset--
  ) {
    if (view.getUint32(offset, true) === 0x06054b50) {
      end = offset;
      break;
    }
  }
  if (end < 0) throw new Error("Choose a valid ZIP archive.");
  const count = view.getUint16(end + 10, true);
  let offset = view.getUint32(end + 16, true);
  if (count > 500) throw new Error("A ZIP may contain at most 500 entries.");
  const names = [];
  const decoder = new TextDecoder("utf-8", { fatal: true });
  for (let i = 0; i < count; i++) {
    if (offset + 46 > buffer.byteLength || view.getUint32(offset, true) !== 0x02014b50) {
      throw new Error("ZIP directory is incomplete.");
    }
    const length = view.getUint16(offset + 28, true);
    const name = decoder.decode(new Uint8Array(buffer, offset + 46, length));
    if (!name.endsWith("/")) names.push(name);
    offset += 46 + length + view.getUint16(offset + 30, true) + view.getUint16(offset + 32, true);
  }
  return names.sort();
}

export function preferredEntry(names, extension) {
  const entries = names.filter((name) => name.endsWith(extension));
  return entries.find((name) => name === `main${extension}`) || entries[0] || "";
}

export function branchIsStale(branch, pims) {
  const source = pims.find((pim) => pim.id === branch.sourcePimId);
  return !source || Number(source.revision) !== Number(branch.sourcePimRevision);
}
