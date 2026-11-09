#!/usr/bin/env python3
"""Conservative source audit for Codex handoff. Static checks, not game certification."""
import csv,json,re,collections
from pathlib import Path
base=Path(__file__).resolve().parent.parent
java=base/'src/main/java'
langs=base/'src/main/resources/assets/brickcraft/lang'
files=sorted(java.rglob('*.java'))
translations={p.stem:json.loads(p.read_text(encoding='utf-8')) for p in langs.glob('*.json')}
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
  # Ignore escaped literal %% and only compare conversion verbs (not translation grammar).
  p=lambda s:sorted(re.findall(r'(?<!%)%(?:\d+\$)?(?:[0-9.]*)([sdfoxX])',s.replace('%%',''),re.I))
  if p(en[k])!=p(table[k]):errors.append(f'{locale}/{k}: placeholders do not match English')
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
  for kind,pattern in patterns:
   if pattern.search(line):
    rows.append((str(f.relative_to(base)),n,kind,line.strip()[:220]))
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
