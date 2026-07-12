#!/usr/bin/env python3
"""Re-parent legacy flat PIM XMI deployables under ServerlessService val containment."""

from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

OWNERSHIP_ATTRS = {
    "ownsFunctions": "functions",
    "ownsApis": "apis",
    "ownsChannels": "channels",
    "ownsStores": "stores",
    "ownsWorkflows": "workflows",
    "ownsAdapters": "adapters",
}

LEGACY_ROOT_TAGS = {
    "functions",
    "apis",
    "channels",
    "stores",
    "workflows",
    "adapters",
    "schedules",
    "dataStores",
    "objectStores",
    "triggers",
}


def local_tag(element: ET.Element) -> str:
    tag = element.tag
    if "}" in tag:
        return tag.rsplit("}", 1)[-1]
    return tag


def split_ids(value: str | None) -> list[str]:
    if not value:
        return []
    return [part for part in value.split() if part]


def index_children(parent: ET.Element) -> dict[str, ET.Element]:
    indexed: dict[str, ET.Element] = {}
    for child in list(parent):
        child_id = child.get("id")
        if child_id:
            indexed[child_id] = child
    return indexed


def ensure_child_bucket(service: ET.Element, bucket_name: str) -> ET.Element | None:
    for child in service:
        if local_tag(child) == bucket_name:
            return child
    return None


XSI_TYPE = "{http://www.w3.org/2001/XMLSchema-instance}type"
DATA_STORE_XSI = "data:DataStore"
OBJECT_STORE_XSI = "data:ObjectStore"


def ensure_data_namespace(root_open: str) -> str:
    if "xmlns:data=" in root_open:
        return root_open
    return root_open[:-1] + ' xmlns:data="https://varka.org/pim/data/1.0">'


def rename_element_tag(element: ET.Element, new_local_name: str) -> None:
    if "}" in element.tag:
        namespace, _ = element.tag.rsplit("}", 1)
        element.tag = f"{namespace}}}{new_local_name}"
    else:
        element.tag = new_local_name


def migrate_store_element(element: ET.Element) -> bool:
    tag = local_tag(element)
    if tag == "dataStores":
        rename_element_tag(element, "stores")
        element.set(XSI_TYPE, DATA_STORE_XSI)
        return True
    if tag == "objectStores":
        rename_element_tag(element, "stores")
        element.set(XSI_TYPE, OBJECT_STORE_XSI)
        return True
    if tag == "stores" and XSI_TYPE not in element.attrib:
        element.set(XSI_TYPE, DATA_STORE_XSI)
        return True
    return False


def append_under_service(service: ET.Element, bucket_name: str, element: ET.Element) -> None:
    if bucket_name == "stores":
        migrate_store_element(element)
    legacy_tag = local_tag(element)
    target_tag = LEGACY_SERVICE_CHILD_TAGS.get(legacy_tag)
    if target_tag is not None:
        rename_element_tag(element, target_tag)
    service.append(element)


LEGACY_SERVICE_CHILD_TAGS = {
    "externalAdapters": "adapters",
}


def normalize_contained_tags(root: ET.Element) -> int:
    changed = 0
    for service in root.iter():
        if local_tag(service) != "services":
            continue
        for child in list(service):
            legacy_tag = local_tag(child)
            target_tag = LEGACY_SERVICE_CHILD_TAGS.get(legacy_tag)
            if target_tag is not None:
                rename_element_tag(child, target_tag)
                changed += 1
                continue
            if migrate_store_element(child):
                changed += 1
    return changed


def migrate_membership_orphans(root: ET.Element, root_index: dict[str, ET.Element]) -> int:
    moved = 0
    service_by_element: dict[str, tuple[ET.Element, str]] = {}
    bucket_by_tag = {
        "functions": "functions",
        "apis": "apis",
        "channels": "channels",
        "stores": "stores",
        "workflows": "workflows",
        "adapters": "adapters",
        "schedules": "schedules",
        "dataStores": "stores",
        "objectStores": "stores",
        "externalAdapters": "adapters",
    }
    for membership in [child for child in root if local_tag(child) == "serviceMemberships"]:
        service_id = membership.get("service")
        element_id = membership.get("element")
        if not service_id or not element_id:
            continue
        service = root_index.get(service_id)
        if service is not None and local_tag(service) == "services":
            service_by_element[element_id] = (service, "")

    for tag_name, bucket_name in bucket_by_tag.items():
        for element in [child for child in list(root) if local_tag(child) == tag_name]:
            element_id = element.get("id")
            if not element_id:
                continue
            target = service_by_element.get(element_id)
            if target is None:
                continue
            service, _ = target
            root.remove(element)
            append_under_service(service, bucket_name, element)
            moved += 1
    return moved


def normalize_trigger_references(root: ET.Element) -> int:
    changed = 0
    for trigger in root.iter():
        if local_tag(trigger) != "triggers":
            continue
        if "invokesFunction" in trigger.attrib:
            del trigger.attrib["invokesFunction"]
            changed += 1
    return changed


def migrate_triggers(root: ET.Element) -> int:
    moved = 0
    function_index: dict[str, ET.Element] = {}

    def collect_functions(node: ET.Element) -> None:
        for child in node:
            tag = local_tag(child)
            if tag == "functions":
                function_id = child.get("id")
                if function_id:
                    function_index[function_id] = child
            if list(child):
                collect_functions(child)

    collect_functions(root)

    for trigger in [child for child in list(root) if local_tag(child) == "triggers"]:
        function_id = trigger.get("invokesFunction")
        if not function_id:
            continue
        function = function_index.get(function_id)
        if function is None:
            continue
        root.remove(trigger)
        function.append(trigger)
        moved += 1
    return moved


def migrate_service_ownership(root: ET.Element, root_index: dict[str, ET.Element]) -> int:
    moved = 0
    for service in [child for child in root if local_tag(child) == "services"]:
        for legacy_attr, bucket_name in OWNERSHIP_ATTRS.items():
            for element_id in split_ids(service.get(legacy_attr)):
                element = root_index.pop(element_id, None)
                if element is None:
                    continue
                root.remove(element)
                append_under_service(service, bucket_name, element)
                moved += 1
            if legacy_attr in service.attrib:
                del service.attrib[legacy_attr]
    return moved


def migrate_schedules_from_memberships(root: ET.Element, root_index: dict[str, ET.Element]) -> int:
    moved = 0
    service_by_element: dict[str, ET.Element] = {}
    for membership in [child for child in root if local_tag(child) == "serviceMemberships"]:
        service_id = membership.get("service")
        element_id = membership.get("element")
        if not service_id or not element_id:
            continue
        service = root_index.get(service_id)
        if service is not None and local_tag(service) == "services":
            service_by_element[element_id] = service
    for schedule in [child for child in list(root) if local_tag(child) == "schedules"]:
        schedule_id = schedule.get("id")
        if not schedule_id:
            continue
        service = service_by_element.get(schedule_id)
        if service is None:
            continue
        root.remove(schedule)
        append_under_service(service, "schedules", schedule)
        moved += 1
    return moved


def migrate_remaining_orphans(root: ET.Element) -> int:
    services = [child for child in root if local_tag(child) == "services"]
    if not services:
        return 0
    moved = 0
    bucket_by_tag = {
        "functions": "functions",
        "apis": "apis",
        "channels": "channels",
        "stores": "stores",
        "workflows": "workflows",
        "adapters": "adapters",
        "schedules": "schedules",
        "dataStores": "stores",
        "objectStores": "stores",
        "externalAdapters": "adapters",
    }
    for tag_name, bucket_name in bucket_by_tag.items():
        for element in [child for child in list(root) if local_tag(child) == tag_name]:
            service = services[0] if len(services) == 1 else services[moved % len(services)]
            root.remove(element)
            append_under_service(service, bucket_name, element)
            moved += 1
    return moved


def extract_root_open_tag(content: str) -> str:
    start = content.find("<pim:PIMModel")
    if start < 0:
        raise ValueError("Expected <pim:PIMModel ...> root open tag")
    in_quote = False
    for index in range(start, len(content)):
        character = content[index]
        if character == '"':
            in_quote = not in_quote
        elif character == ">" and not in_quote:
            return content[start : index + 1]
    raise ValueError("Unterminated <pim:PIMModel root open tag")


def replace_root_open_tag(content: str, original_root_open: str) -> str:
    start = content.find("<")
    if start < 0:
        raise ValueError("Expected XML root element")
    if content.startswith("<?xml"):
        decl_end = content.find("?>")
        if decl_end < 0:
            raise ValueError("Malformed XML declaration")
        start = content.find("<", decl_end + 2)
    in_quote = False
    for index in range(start, len(content)):
        character = content[index]
        if character == '"':
            in_quote = not in_quote
        elif character == ">" and not in_quote:
            return content[:start] + original_root_open + content[index + 1 :]
    raise ValueError("Unterminated root open tag in migrated XML")


def write_preserving_root(path: Path, tree: ET.ElementTree, original_root_open: str) -> None:
    ET.indent(tree, space="  ")
    buffer = io.BytesIO()
    tree.write(buffer, encoding="utf-8", xml_declaration=True)
    content = buffer.getvalue().decode("utf-8")
    content = re.sub(
        r"<\?xml version='1\.0' encoding='utf-8'\?>",
        '<?xml version="1.0" encoding="ASCII"?>',
        content,
        count=1,
    )
    content = replace_root_open_tag(content, ensure_data_namespace(original_root_open))
    content = content.replace("</ns0:PIMModel>", "</pim:PIMModel>")
    path.write_text(content, encoding="utf-8", newline="\n")


def migrate_pim_xmi(path: Path, dry_run: bool = False) -> tuple[int, int, int, int, int]:
    original = path.read_text(encoding="utf-8")
    original_root_open = extract_root_open_tag(original)
    tree = ET.parse(path)
    root = tree.getroot()
    if local_tag(root) != "PIMModel":
        raise ValueError(f"{path} root is {local_tag(root)}, expected PIMModel")

    root_index = index_children(root)
    service_moves = migrate_service_ownership(root, root_index)
    membership_moves = migrate_membership_orphans(root, root_index)
    orphan_moves = migrate_remaining_orphans(root)
    trigger_moves = migrate_triggers(root)
    trigger_attr_fixes = normalize_trigger_references(root)
    tag_fixes = normalize_contained_tags(root)
    schedule_moves = migrate_schedules_from_memberships(root, root_index)

    if not dry_run and (
        service_moves
        or membership_moves
        or orphan_moves
        or trigger_moves
        or trigger_attr_fixes
        or tag_fixes
        or schedule_moves
    ):
        write_preserving_root(path, tree, original_root_open)

    return service_moves, membership_moves, orphan_moves, trigger_moves, schedule_moves


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path, help="PIM .xmi files to migrate in place")
    parser.add_argument("--dry-run", action="store_true", help="Report moves without writing files")
    args = parser.parse_args(argv)

    total = 0
    for path in args.paths:
        if not path.is_file():
            print(f"skip missing file: {path}", file=sys.stderr)
            continue
        service_moves, membership_moves, orphan_moves, trigger_moves, schedule_moves = migrate_pim_xmi(
            path, dry_run=args.dry_run
        )
        moved = service_moves + membership_moves + orphan_moves + trigger_moves + schedule_moves
        total += moved
        action = "would move" if args.dry_run else "moved"
        print(
            f"{path}: {action} {service_moves} owns* deployables, "
            f"{membership_moves} membership orphans, {orphan_moves} remaining orphans, "
            f"{trigger_moves} triggers, {schedule_moves} schedules"
        )

    return 0 if total >= 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
