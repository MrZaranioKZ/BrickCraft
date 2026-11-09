#!/usr/bin/env python3
"""Offline smoke checks for V0.25.1: 11 locales, placeholders, widgets, formatting.
Not a substitute for a Fabric compile or in-game screenshots.
"""
from pathlib import Path
import json,subprocess,re,sys
ROOT=Path(__file__).resolve().parent.parent
translations=ROOT/'src/main/resources/assets/brickcraft/lang'
lang={f.stem:json.loads(f.read_text(encoding='utf-8')) for f in translations.glob('*.json')}
assert len(lang)==11
keys=set(lang['en_us'])
assert all(keys==set(x) for x in lang.values())
newkeys={k for k in keys if k.startswith('brickcraft.v251.')}
assert len(newkeys)>=20
for k in newkeys:
    expected=lang['en_us'][k]
    placeholders=lambda s:re.findall(r'%(?:\d+\$)?[sdif]|%%',s)
    for code,value in lang.items():
        assert placeholders(expected)==placeholders(value[k]),(code,k,expected,value[k])
    assert 'Format error' not in expected

root=ROOT/'src/main/java/fr/brickcraft/client'
assert 'Language.getInstance().get(translationKey)' in (root/'BrickI18n.java').read_text()
assert 'private boolean embeddedPreview=false' in (root/'ProjectImportScreen.java').read_text()
assert 'if(!embeddedPreview)' in (root/'ProjectImportScreen.java').read_text()
assert '_free_v099' not in (root/'PreciseFreeScreen.java').read_text()
# Add, Duplicate, Delete must be separated from the fixed footer by at least 10 GUI pixels.
for height in range(280,1001):
    last=315 if height>=439 else 289
    factor=max(.45,min(1,(height-102-32)/(last-32)))
    edit_y=32+round((last-32)*factor)
    assert (height-72)-(edit_y+19)>=10,(height,edit_y)

stubs=ROOT/'tools/translation_test_stubs'
files=list(stubs.rglob('*.java'))
files += [ROOT/'src/main/java/fr/brickcraft/client/BrickI18n.java', ROOT/'tools/BrickI18nFormatV0251Test.java']
out=ROOT/'build/test_i18n_v0251';out.mkdir(parents=True,exist_ok=True)
r=subprocess.run(['javac','-encoding','UTF-8','-d',str(out),*map(str,files)],capture_output=True,text=True)
if r.returncode:print(r.stderr);sys.exit(r.returncode)
r=subprocess.run(['java','-cp',str(out),'BrickI18nFormatV0251Test'],capture_output=True,text=True)
if r.returncode:print(r.stderr);sys.exit(r.returncode)
print(r.stdout.strip())
print(f"OK: {len(lang)} langues, {len(newkeys)} clés V0.25.1, hauteur de l'interface de 280 à 1000")
