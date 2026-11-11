#!/usr/bin/env python3
"""Create a clean HOMElauncher.zip without local build artifacts or secrets."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT.parent / "HOMElauncher.zip"
EXCLUDED_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__"}
EXCLUDED_FILES = {"local.properties"}
SECRET_SUFFIXES = {".jks", ".keystore", ".p12"}

with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as archive:
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if any(part in EXCLUDED_DIRS for part in relative.parts):
            continue
        if path.name in EXCLUDED_FILES or path.suffix.lower() in SECRET_SUFFIXES or path.suffix.lower() == ".pyc":
            continue
        archive.write(path, Path("HOMElauncher") / relative)
print(f"Created: {OUTPUT}")
