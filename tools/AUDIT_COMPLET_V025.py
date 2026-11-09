#!/usr/bin/env python3
"""BrickCraft pre-release static audit: assets, locales, UI and safety guards.
This cannot replace compilation with Fabric Loom nor an interactive Minecraft test.
"""
from pathlib import Path
import json,re,csv,struct,collections,sys
ROOT=Path(__file__).resolve().parent.parent
JAVA=ROOT/'src/main/java/fr/brickcraft'
ASSETS=ROOT/'src/main/resources/assets/brickcraft'
SCREENS=sorted((JAVA/'client').glob('*Screen.java'))
LANG=ASSETS/'lang'
JFILES=sorted(JAVA.rglob('*.java'))
UI_TEXT_RE=re.compile(r'(?:addButton\(|button\(|ButtonWidget\.builder\(|Text\.literal\(|drawTextWithShadow\(|drawText\(|status\s*=)')
PLH_RE=re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdif]')
EXCEPTIONS=[]
def insist(ok,msg):
    if not ok:EXCEPTIONS.append(msg)
props={}
for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.strip().startswith('#'):
        k,v=line.split('=',1);props[k.strip()]=v.strip()
manifest=json.loads((ROOT/'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))
insist(manifest.get('version')=='${version}','fabric.mod.json must use the Gradle version placeholder')
insist(props.get('mod_version')=='0.25.1-rc2','Unintended mod_version')
for rel in ['src/main/java/fr/brickcraft/BrickDirectories.java',
            'src/main/java/fr/brickcraft/client/BrickGuideBookScreen.java',
            'src/main/java/fr/brickcraft/client/PreciseWorldProjectsScreen.java']:
    insist((ROOT/rel).is_file(),f'Missing source {rel}')
# All JSON files, including models and mod metadata.
json_paths=sorted((ROOT/'src/main/resources').rglob('*.json'))
parsed={}
for p in json_paths:
    try:parsed[p]=json.loads(p.read_text(encoding='utf-8'))
    except Exception as e:EXCEPTIONS.append(f'Invalid JSON: {p.relative_to(ROOT)}: {e}')
base=parsed.get(LANG/'en_us.json',{})
locales={}
for p in sorted(LANG.glob('*.json')):
    data=parsed.get(p,{})
    missing=sorted(set(base)-set(data))
    extra=sorted(set(data)-set(base))
    placeholders=[]
    for key in base.keys() & data.keys():
        # Placeholders must be the same types; ordering is checked where positional indexes are used.
        original=collections.Counter(PLH_RE.findall(str(base[key])))
        target=collections.Counter(PLH_RE.findall(str(data[key])))
        if original!=target:placeholders.append(key)
    locales[p.stem]={'keys':len(data),'missing_keys':len(missing),'extra_keys':len(extra),
                     'same_as_english':sum(data.get(k)==v for k,v in base.items()),
                     'placeholder_mismatch_count':len(placeholders),
                     'placeholder_mismatch_sample':placeholders[:15]}
    insist(not missing,f'Locale {p.stem} missing {len(missing)} keys')
    insist(not placeholders,f'Locale {p.stem}: {len(placeholders)} placeholder mismatch(es)')

# PNG validation needs no Pillow installation.
pngs=sorted(ASSETS.rglob('*.png'))
invalid_png=[]
for file in pngs:
    raw=file.read_bytes()
    if len(raw)<24 or raw[:8]!=b'\x89PNG\r\n\x1a\n':invalid_png.append(str(file.relative_to(ROOT)))
insist(not invalid_png,f'Invalid PNGs: {invalid_png}')
models=ASSETS/'models/item'; tex=ASSETS/'textures/item'
kind_source=(JAVA/'core/BrickKind.java').read_text(encoding='utf-8')
kinds=re.findall(r'^\s*(BRICK_\w+|PLATE_\w+|TILE_\w+|SLOPE_\w+)\(',kind_source,re.M)
items=kinds+['builder_wand','ldraw_part','guide_book']
missing_models=[x for x in items if not (models/f'{x.lower()}.json').is_file()]
missing_textures=[x for x in items if not (tex/f'{x.lower()}.png').is_file()]
insist(not missing_models,f'Missing models {missing_models}')
insist(not missing_textures,f'Missing textures {missing_textures}')
wrong_item_texture_refs=[]
for item in items:
    model=models/f'{item.lower()}.json'
    if model.is_file():
        data=parsed.get(model,{})
        layer0=data.get('textures',{}).get('layer0','')
        if not layer0.startswith('brickcraft:item/'):
            wrong_item_texture_refs.append(f'{item} -> {layer0}')
insist(not wrong_item_texture_refs,'Non-BrickCraft item textures: '+str(wrong_item_texture_refs))
pbr={}
for name in ['lego_materials','lego_materials_n','lego_materials_s']:
    p=ASSETS/'textures/entity'/f'{name}.png'
    if p.is_file():
        raw=p.read_bytes(); pbr[name]=list(struct.unpack('>II',raw[16:24]))
    else:pbr[name]=None
insist(all(v==[160,16] for v in pbr.values()),'PBR atlas textures missing or not 160x16')
# Minimal static checks for previously broken/dangerous UI flows.
source=lambda rel:(JAVA/rel).read_text(encoding='utf-8')
studio=source('client/BrickStudioScreen.java')
projects=source('client/PreciseWorldProjectsScreen.java')
imports=source('client/ProjectImportScreen.java')
guide=source('client/BrickGuideScreen.java')
client=source('client/BrickCraftClient.java')
checks={
 'no_duplicate_studio_rotate_footer': 'this::rotate, this::applyToWorld' not in studio,
 'studio_undo_controls_dynamic': 'ASSEMBLY.canUndo()' in studio and 'footerActions.get(i).active' in studio,
 'project_actions_disabled_without_selection': '"Physique ON",hasSelection' in projects and '"Physique OFF",hasSelection' in projects,
 'responsive_project_list': 'pageRows()' in projects and 'int listY=68,rows=pageRows()' in projects,
 'project_refresh_keeps_selected_file': 'previouslySelected=loadedFile!=null?loadedFile:selectedFile()' in imports,
 'guide_actions_disabled_without_project': '"Verifier pieces",hasGuide' in guide,
 'old_io_file_cap_removed': '.limit(200).toList()' not in imports,
 'old_corrected_filename_removed': '_corrige_v019' not in imports,
 'grid_import_checks_compatibility': 'gridCompatible' in imports,
 'advanced_GPU_debug_is_optin': 'experimentalDiagnostics' in client and 'gpuViewKey!=null' in client,
 'builder_and_ldraw_items_distinct': 'new ItemStack(BrickCraft.TOOL)' in client and 'BrickPartStacks.ldraw(' in client,
 'workspace_directories_created': 'BrickDirectories.ensure(' in source('BrickCraft.java'),
 'server_rechecks_export_permissions': 'world.canPlayerModifyAt(player,anchor)' in source('world/PreciseExportManager.java'),
 'physics_is_prepared_progressively': 'tickPreparation(' in source('world/PrecisePhysicsManager.java'),
}
for name,value in checks.items():insist(value,f'Regression in {name}')
# Approximate actionable UI control inventory. Does not infer runtime-generated labels.
rows=[]
for file in SCREENS:
    raw=file.read_text(encoding='utf-8')
    for line_no,line in enumerate(raw.splitlines(),1):
        if any(tag in line for tag in ('addButton(', 'button(', 'ButtonWidget.builder(')) and not line.strip().startswith(('*','//')):
            rows.append({'screen':file.name,'line':line_no,'source':line.strip()[:240]})
with (ROOT/'AUDIT_CONTROLES_V025.csv').open('w',encoding='utf-8-sig',newline='') as h:
    writer=csv.DictWriter(h,fieldnames=['screen','line','source']);writer.writeheader();writer.writerows(rows)
text_literals=0;message_lines=0;dynamic_candidates=0
for file in JFILES:
    s=file.read_text(encoding='utf-8')
    text_literals+=s.count('Text.literal(')
    for line in s.splitlines():
        if UI_TEXT_RE.search(line):
            message_lines+=1
            if '+' in line and ('status' in line or 'Text.literal' in line or 'sendMessage' in line):dynamic_candidates+=1
# Caution: static ratios include technical IDs that cannot/should not be translated.
summary={
 'type':'static audit, not a Minecraft runtime or FPS certification',
 'version':props.get('mod_version'),
 'java_sources':len(JFILES),'screen_classes':len(SCREENS),
 'ui_button_source_occurrences':len(rows),'resource_json':len(json_paths),'png_assets':len(pngs),
 'item_models_expected':len(items),'item_models_missing':missing_models,
 'item_textures_missing':missing_textures,'non_custom_item_texture_refs':wrong_item_texture_refs,
 'pbr_material_atlas':pbr,
 'language_files':len(locales),'locales':locales,
 'text_literal_occurrences_java':text_literals,
 'ui_related_lines_estimate':message_lines,
 'dynamic_text_candidates_estimate':dynamic_candidates,
 'checks':checks,'blocking_static_findings':EXCEPTIONS,
 'regression_summary_path':'AUDIT_TESTS_V025.json'
}
(ROOT/'AUDIT_COMPLET_V025.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k not in ('locales','checks')},ensure_ascii=False,indent=2))
if EXCEPTIONS:sys.exit(1)
