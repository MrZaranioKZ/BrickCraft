#!/usr/bin/env python3
"""Conservative source audit for Codex handoff. Static checks, not game certification."""
import csv,json,re,collections
from pathlib import Path
base=Path(__file__).resolve().parent.parent
java=base/'src/main/java'
langs=base/'src/main/resources/assets/brickcraft/lang'
files=sorted(java.rglob('*.java'))
def unique_entries(pairs):
    entries = {}
    for key, value in pairs:
        if key in entries:
            raise ValueError(f"Duplicate translation key: {key}")
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"Empty or invalid translation: {key}")
        entries[key] = value
    return entries

translations = {
    p.stem: json.loads(p.read_text(encoding='utf-8'), object_pairs_hook=unique_entries)
    for p in sorted(langs.glob('*.json'))
}
expected_locales = set('ar_sa de_de en_us es_es fr_fr hi_in ja_jp pt_br ru_ru zh_cn zh_tw'.split())
if translations.keys() != expected_locales:
    raise SystemExit(f"Unexpected locale inventory: {sorted(translations.keys() ^ expected_locales)}")

# Compare argument identity and conversion, allowing explicit argument reordering.
placeholder = re.compile(r'%(%|n|(?:(?P<index>[1-9][0-9]*)\$)?(?P<flags>[-#+0,(<]*)(?:[0-9]+)?(?:\.[0-9]+)?(?P<verb>[bBhHsScCdoxXeEfgGaA]))')

def signature(template, formatted):
    arguments = collections.Counter()
    cursor = ordinary = 0
    last = None
    while (pos := template.find('%', cursor)) != -1:
        token = placeholder.match(template, pos)
        if token is None:
            if formatted:
                raise ValueError(f"Invalid format token at character {pos + 1}")
            cursor = pos + 1
            continue
        cursor = token.end()
        verb = token['verb']
        if verb is None:
            arguments[(0, token[1])] += 1
            continue
        if '<' in token['flags']:
            if last is None or token['index']:
                raise ValueError("Invalid previous-argument reference")
            index = last
        elif token['index']:
            index = int(token['index'])
        else:
            ordinary += 1
            index = ordinary
        arguments[(index, verb.lower())] += 1
        last = index
    return arguments

fr=translations['fr_fr'];en=translations['en_us']
errors=[];warnings=[]
keys=sorted(set(en)|set(fr))
for locale,table in translations.items():
 missing=set(keys)-set(table)
 extra=set(table)-set(keys)
 if missing:errors.append(f'{locale}: {len(missing)} missing keys: '+','.join(sorted(missing)[:8]))
 if extra:warnings.append(f'{locale}: {len(extra)} additional keys')
 for k in keys:
  if k not in table:continue
  try:
   formatted = any(match['verb'] for match in placeholder.finditer(en.get(k, '')))
   if signature(en.get(k, ''), formatted) != signature(table[k], formatted):
    errors.append(f'{locale}/{k}: placeholders do not match English')
   if 'Format error:' in table[k]:
    errors.append(f'{locale}/{k}: internal formatting error in translation')
  except ValueError as exc:
   errors.append(f'{locale}/{k}: {exc}')
# Catch keys absent from all locales as well as keys missing in one locale.
key_call = re.compile(r'\b(?:Text\.translatable|BrickI18n\.fmt)\s*\(\s*"((?:brickcraft|block\.brickcraft|item\.brickcraft)\.[^"]+)"')

# Source texts to review: dynamic strings rendered via legacy fragment fallback.
rows=[]
patterns=[
 ('dynamic_status',re.compile(r'\bstatus\s*=.*\+.*(?:"|\bString\b)')),
 ('literal_gui',re.compile(r'\b(?:drawText|text|drawCenteredText|drawTextWithShadow)\s*\([^;]*"[^"\n]{3,}"')),
 ('server_literal',re.compile(r'\bsendMessage\s*\(\s*Text\.literal\s*\(')),
 ('legacy_translate',re.compile(r'\bBrickI18n\.tr\s*\(.*\+.*\)')),
]
for f in files:
 for n,line in enumerate(f.read_text(encoding='utf-8').splitlines(),1):
  for key in key_call.findall(line):
   if key not in en:
    errors.append(f'{f.relative_to(base).as_posix()}:{n}: missing source key {key}')
  for kind,pattern in patterns:
   if pattern.search(line):
    rows.append((f.relative_to(base).as_posix(),n,kind,line.strip()[:220]))
with (base/'AUDIT_I18N_RESTANT_V0252.csv').open('w',newline='',encoding='utf-8-sig') as out:
 w=csv.writer(out);w.writerow(('source','line','type','excerpt'));w.writerows(rows)
result={
 'version':'0.25.2-rc3',
 'type':'static audit (NOT full client/server runtime testing)',
 'java_sources':len(files),
 'languages':len(translations),
 'en_us_keys':len(en),
 'fr_fr_keys':len(fr),
 'named_v252_keys':sum(k.startswith('brickcraft.v252.') for k in keys),
 'other_languages_english_identical':{code:sum(1 for k,v in en.items() if table.get(k)==v) for code,table in translations.items() if code not in ('en_us','fr_fr')},
 'remaining_text_candidates':len(rows),
 'candidate_types':dict(collections.Counter(kind for _,_,kind,_ in rows)),
 'blocking_key_errors':errors[:150],
 'notes':['Identical strings can be product names or untranslated UI; do not equate key count with translation coverage.',
          'All GUI states and network chat messages require testing inside Minecraft with FR and EN.',
          'Report marks review candidates; not all matches are real defects.'],
}
(base/'AUDIT_I18N_RESTANT_V0252.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2,ensure_ascii=False))
if errors:raise SystemExit(1)
