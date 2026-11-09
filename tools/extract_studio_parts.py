#!/usr/bin/env python3
"""Extract custom LDraw meshes from the user-selected BrickLink Studio .io.

Usage: python tools/extract_studio_parts.py MODEL.io [OUTPUT.zip]
Needs no external dependencies. Extracts *only* parts referenced as bl_*.dat
and defined by model2.ldr. Embedded PNG decals are saved for future UV support;
they are not yet mapped onto Minecraft geometry.
"""
from pathlib import Path
import base64
import re
import sys
import zipfile

REFERENCE = re.compile(r"(?im)^1\s+[^\r\n]*?\s+(bl_[a-z0-9_]+)\.dat\s*$")
SECTION = re.compile(r"(?im)^0 FILE\s+([^\r\n]+)\s*$")
SAFE_ID = re.compile(r"^[a-z0-9_]+$")

def extract(src: Path, dest: Path):
    if src.stat().st_size > 96 * 1024 * 1024:
        raise ValueError("Fichier Studio .io trop volumineux")
    with zipfile.ZipFile(src) as f:
        main = f.read("model.ldr").decode("utf-8-sig")
        details = f.read("model2.ldr").decode("utf-8-sig")
    if len(main) > 32_000_000 or len(details) > 32_000_000:
        raise ValueError("Modele trop volumineux")
    needed = set(REFERENCE.findall(main))
    if not needed:
        raise ValueError("Aucune reference bl_* personnalisee dans ce modele")
    chunks = {}
    section_matches = list(SECTION.finditer(details))
    for i, match in enumerate(section_matches):
        name = match.group(1).strip().lower()
        if not name.endswith(".dat"):
            continue
        name = name[:-4]
        if not SAFE_ID.fullmatch(name):
            continue
        # Exact LDraw part data, not the metadata of the enclosing MPD model.
        until = section_matches[i + 1].start() if i + 1 < len(section_matches) else len(details)
        chunks[name] = details[match.start():until]
    items = {}
    missing = []
    for src_id in sorted(needed):
        id_ = src_id.removeprefix("bl_")
        block = chunks.get(id_)
        if not block:
            missing.append(src_id)
            continue
        output = []
        faces = 0
        for line in block.splitlines():
            line = line.strip()
            if line.startswith("0 PE_TEX_INFO "):
                binary = base64.b64decode(line.split(" ", 2)[2], validate=True)
                if binary.startswith(b"\x89PNG\r\n\x1a\n") and len(binary) < 4_000_000:
                    items["textures/" + src_id + ".png"] = binary
                continue
            if line.startswith("0 FILE "):
                line = "0 FILE " + src_id + ".dat"
            elif line.startswith("0 Name: "):
                line = "0 Name: " + src_id
            if line.startswith(("3 ", "4 ")):
                faces += 1
            output.append(line)
        if not faces:
            missing.append(src_id)
            continue
        items["parts/" + src_id + ".dat"] = ("\n".join(output) + "\n").encode("utf8")
    if not items:
        raise ValueError("Aucune geometrie exportable trouvee dans model2.ldr")
    dest.parent.mkdir(parents=True, exist_ok=True)
    # Preserve custom shapes from previously installed projects.
    merged = {}
    if dest.is_file():
        with zipfile.ZipFile(dest) as old:
            for info in old.infolist():
                if info.filename.startswith(("parts/bl_", "textures/bl_")) and info.file_size < 4_000_000:
                    merged[info.filename] = old.read(info)
    merged.update(items)
    temporary = dest.with_suffix(".working.zip")
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output:
            for key, value in sorted(merged.items()):
                output.writestr(key, value)
        temporary.replace(dest)
    finally:
        if temporary.exists():
            temporary.unlink()
    ids = [key for key in items if key.startswith("parts/")]
    print(f"{len(ids)} references extraites vers {dest}")
    if missing:
        print("Non resolues dans model2.ldr : " + ", ".join(missing))
    print("Redemarre Minecraft pour que la bibliotheque recharge le supplement.")

if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit("Usage: python tools/extract_studio_parts.py MODELE.io [studio_parts.zip]")
    extract(Path(sys.argv[1]), Path(sys.argv[2]) if len(sys.argv) == 3
            else Path("run/config/brickcraft/studio_parts.zip"))
