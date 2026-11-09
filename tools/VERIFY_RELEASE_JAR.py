"""Verify the remapped mod JAR contains the declared metadata and source assets."""
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
properties = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
    if "=" in line and not line.lstrip().startswith("#")
)
version = properties["mod_version"]
jar = root / "build" / "libs" / f'{properties["archives_base_name"]}-{version}.jar'
resources = root / "src" / "main" / "resources"
locales = "ar_sa de_de en_us es_es fr_fr hi_in ja_jp pt_br ru_ru zh_cn zh_tw".split()

with ZipFile(jar) as archive:
    corrupt = archive.testzip()
    if corrupt is not None:
        raise SystemExit(f"Corrupt JAR entry: {corrupt}")
    metadata = json.loads(archive.read("fabric.mod.json"))
    if metadata.get("id") != "brickcraft" or metadata.get("version") != version:
        raise SystemExit("Unexpected Fabric mod ID or version")
    for group in metadata["entrypoints"].values():
        for entrypoint in group:
            archive.getinfo(entrypoint.replace(".", "/") + ".class")
    for locale in locales:
        table = json.loads(archive.read(f"assets/brickcraft/lang/{locale}.json"))
        if not table:
            raise SystemExit(f"Empty language table: {locale}")
    for resource in (resources / "assets").rglob("*"):
        if resource.is_file():
            name = resource.relative_to(resources).as_posix()
            if archive.read(name) != resource.read_bytes():
                raise SystemExit(f"Packaged asset differs from source: {name}")

print(f"{jar.name}: Fabric metadata, entrypoints and assets verified ({len(locales)} languages)")
