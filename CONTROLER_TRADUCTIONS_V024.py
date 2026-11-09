#!/usr/bin/env python3
"""Static, reproducible locale and user-facing-string audit (no network)."""
import argparse,csv,json,re
from pathlib import Path

ROOT=Path(__file__).resolve().parent
LANG=ROOT/'src/main/resources/assets/brickcraft/lang'
JAVA=ROOT/'src/main/java/fr/brickcraft'

def key(s):
    n=0
    # Java String.hashCode on UTF-16 code units, rather than Python Unicode code points
    data=s.encode('utf-16-be')
    for i in range(0,len(data),2):n=(31*n+int.from_bytes(data[i:i+2],'big'))&0xffffffff
    return 'brickcraft.ui.u%08x'%n

def audit():
    en=json.loads((LANG/'en_us.json').read_text(encoding='utf-8'))
    fr=json.loads((LANG/'fr_fr.json').read_text(encoding='utf-8'))
    locales={f.stem:json.loads(f.read_text(encoding='utf-8')) for f in LANG.glob('*.json')}
    result={}
    for locale,d in sorted(locales.items()):
        missing=sorted(set(en)-set(d))
        extra=sorted(set(d)-set(en))
        english_fallback=sum(1 for k in en if d.get(k)==en.get(k))
        result[locale]={'total_keys':len(d),'missing_keys':len(missing),
                        'extra_keys':len(extra),'same_as_english':english_fallback,
                        'identical_fraction':round(english_fallback/max(1,len(en)),3)}
    rows=[]
    # UI-relevant Java files and dialog/server feedback sources. Report dynamic patterns separately.
    literal=re.compile(r'"((?:\\.|[^"\\])*)"')
    files=list(JAVA.rglob('*.java'))
    for file in files:
        for no,line in enumerate(file.read_text(encoding='utf-8').splitlines(),1):
            if not any(t in line for t in ('BrickI18n.tr(', 'Text.literal(', 'sendMessage(', 'status=',
                    'message=', 'drawText(', 'addButton(', 'button(', 'new String[]{', 'String[] lines=',
                    'new BrickConfirmScreen(', 'sendError(')):continue
            for raw in literal.findall(line):
                if len(raw)<3 or re.match(r'^[a-z0-9._/-]+$',raw):continue
                if '\\' in raw or raw.startswith('brickcraft.') or raw.startswith('key.') or raw.startswith('command.'):
                    continue
                k=key(raw)
                rows.append({'file':str(file.relative_to(ROOT)),'line':no,'literal':raw,'key':k,
                    'en_value':en.get(k,''),'fr_value':fr.get(k,''),
                    'en_missing':int(k not in en),'fr_missing':int(k not in fr),
                    'dynamic_concat':int('+' in line),
                    'english_is_original':int(en.get(k)==raw)})
    # Preserve duplicates by location; each occurrence is actionable
    out=ROOT/'AUDIT_TEXTES_V024.csv'
    with out.open('w',newline='',encoding='utf-8-sig') as f:
        writer=csv.DictWriter(f,fieldnames=rows[0].keys());writer.writeheader();writer.writerows(rows)
    summary={'java_files':len(files),'occurrences':len(rows),'unique_literals':len({x['literal'] for x in rows}),
             'missing_en_occurrences':sum(x['en_missing'] for x in rows),
             'missing_fr_occurrences':sum(x['fr_missing'] for x in rows),
             'dynamic_candidates':sum(x['dynamic_concat'] for x in rows),
             'locales':result,'scope':'Static heuristics only; cannot certify every runtime-composed string.'}
    (ROOT/'AUDIT_TRADUCTIONS_V024.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False,indent=2))
    return summary

if __name__=='__main__':audit()
