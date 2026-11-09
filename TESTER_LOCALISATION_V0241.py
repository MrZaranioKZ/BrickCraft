#!/usr/bin/env python3
"""Static regression guard for the translation and HUD corrections.
    Does not replace a Fabric client launch or real visual testing."""
import json,re
from pathlib import Path
root=Path(__file__).resolve().parent
l=root/'src/main/resources/assets/brickcraft/lang'
java=root/'src/main/java/fr/brickcraft/client'
locales={p.stem:json.loads(p.read_text(encoding='utf8')) for p in l.glob('*.json')}
assert len(locales)>=11, 'Missing a supported language'
en,fr=locales['en_us'],locales['fr_fr']
def key(source):
    b=source.encode('utf-16-be');v=0
    for i in range(0,len(b),2):v=(v*31+int.from_bytes(b[i:i+2],'big'))&0xffffffff
    return f'brickcraft.ui.u{v:08x}'
examples={
    'Maillages LDraw : formes reelles / LOD':('LDraw meshes','Maillages LDraw'),
    "QUALITE D'APERCU":('PREVIEW QUALITY','QUALITÉ'),
    'Refaire':('Redo','Rétablir'),
    'Vue max':('Maximize','agrandie'),
    'REGLAGES':('SETTINGS','RÉGLAGES'),
    'BrickCraft : rotation reinitialisee':('rotation reset','rotation réinitialisée'),
    'Finition : ':('Finish:','Finition :'),
}
for source,(english,french) in examples.items():
    k=key(source)
    assert english in en.get(k,''),f'English wrong: {source}: {en.get(k)}'
    assert french in fr.get(k,''),f'French wrong: {source}: {fr.get(k)}'
files=list(java.glob('*.java'))
uses=set(re.findall(r'BrickI18n\.fmt\("([^"]+)"','\n'.join(x.read_text(encoding='utf8') for x in files)))
for name,data in locales.items():
    assert set(uses).issubset(data),f'{name}: missing dynamic-format keys: {sorted(uses-set(data))}'
    for k in uses:
        assert data[k].count('%s')==en[k].count('%s'),f'{name}: placeholder mismatch {k}'
# Source-oriented regression checks: all UI clipping should occur after translation.
checks={
'ProjectImportScreen.java':['String[] lines={BrickI18n.fmt','BrickI18n.fmt("brickcraft.v241.import.engine"','ctx.drawTextWithShadow(textRenderer,s,x,y,color)'],
'BrickStudioScreen.java':['brickcraft.v241.studio.parts.classic','ctx.drawTextWithShadow(textRenderer,str,x,y,rgb)'],
'BrickQuickScreen.java':['brickcraft.v241.quick.help','textRenderer.trimToWidth(selected,width-24)'],
'PreciseWorldEditor.java':['brickcraft.v241.hud.title','client.textRenderer.trimToWidth(lines[i],maxWidth)'],
'BrickGuideScreen.java':['brickcraft.v241.guide.project','brickcraft.v241.guide.materials'],
'BrickCapacityScreen.java':['brickcraft.v241.capacity.parts','brickcraft.v241.capacity.cells'],
'PreciseFreeScreen.java':['message=BrickI18n.tr(message)','textRenderer.trimToWidth(message,'],
'BrickCraftClient.java':['brickcraft.v241.notice.rotation','brickcraft.v241.notice.pick'],
}
for file,patterns in checks.items():
    s=(java/file).read_text(encoding='utf8')
    for pattern in patterns:assert pattern in s,f'{file}: {pattern}'
print(f'OK: {len(examples)} screenshot cases, {len(uses)} dynamic keys, {len(locales)} locale files, {len(checks)} UI modules.')
print('LIMIT: This is static checking, not a Minecraft screenshot or a full Fabric build.')
