from pathlib import Path
import json,subprocess,sys
r=Path(__file__).resolve().parent.parent
lang=r/'src/main/resources/assets/brickcraft/lang'
fr=json.loads((lang/'fr_fr.json').read_text(encoding='utf-8'))
en=json.loads((lang/'en_us.json').read_text(encoding='utf-8'))
required=['studio.restored','studio.color_chosen','studio.grid_changed','import.gpu_preparing','import.fast_points','missing.description','missing.remove_detail','replace.detail','replace.title','replace.suggest','projections.remove.detail','projections.physics_on.detail','projections.item','physics.progress','physics.complete_on']
for suffix in required:
 key='brickcraft.v252.'+suffix
 assert key in fr and key in en,key
 assert not fr[key].startswith('Format error:'),key
 assert fr[key]!=key and en[key]!=key
for code in ['fr_fr','en_us','es_es','de_de','pt_br','ru_ru','zh_cn','zh_tw','ja_jp','ar_sa','hi_in']:
 assert (lang/(code+'.json')).exists(),code
studio=(r/'src/main/java/fr/brickcraft/client/BrickStudioScreen.java').read_text(encoding='utf-8')
missing=(r/'src/main/java/fr/brickcraft/client/BrickMissingPartsScreen.java').read_text(encoding='utf-8')
physics=(r/'src/main/java/fr/brickcraft/world/PrecisePhysicsManager.java').read_text(encoding='utf-8')
confirm=(r/'src/main/java/fr/brickcraft/client/BrickConfirmScreen.java').read_text(encoding='utf-8')
for stale in ['status="Dernier atelier restaure : "','status="Couleur choisie : "','status="Plateau "+size']:
 assert stale not in studio,stale
assert 'textRenderer.wrapLines' in confirm
assert 'BrickI18n.fmt("brickcraft.v252.missing.description")' in missing
assert 'Text.translatable(key,args)' in physics
assert 'sendMessage(Text.translatable("brickcraft.v252.physics.progress"' in physics
print('OK: localization hot paths, physics actionbar, dialogs, 11 language files')
