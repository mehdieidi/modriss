"""Build uploadable archives from this example, retaining relative script paths."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent
for kind in ("transformation", "generation", "validation"):
    with ZipFile(root / f"{kind}.zip", "w", ZIP_DEFLATED) as archive:
        for source in sorted((root / kind).rglob("*")):
            if source.is_file():
                archive.write(source, source.relative_to(root / kind).as_posix())
    print(root / f"{kind}.zip")
