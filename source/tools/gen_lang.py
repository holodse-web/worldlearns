import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from lang_ru import RU
from lang_en import EN
from lang_add1 import RU_ADD, EN_ADD
from lang_wiki import RU_WIKI, EN_WIKI
from lang_pvp import RU_PVP, EN_PVP
from lang_v13 import RU_V13, EN_V13
from lang_v14 import RU_V14, EN_V14
from lang_v15 import RU_V15, EN_V15

ru, en = {}, {}
for d in (RU, RU_ADD, RU_WIKI, RU_PVP, RU_V13, RU_V14, RU_V15): ru.update(d)
for d in (EN, EN_ADD, EN_WIKI, EN_PVP, EN_V13, EN_V14, EN_V15): en.update(d)
root = os.path.join(os.path.dirname(__file__), '..')
out = os.path.join(root, 'src/main/resources/assets/mobslearn/lang')
if '--check-old' in sys.argv:
    old = json.load(open(os.path.join(out, 'ru_ru.json'), encoding='utf-8'))
    diff = [k for k in old if old[k] != ru.get(k)]
    print('changed vs old:', len(diff), diff[:40])
    sys.exit()
miss_en = sorted(set(ru) - set(en)); miss_ru = sorted(set(en) - set(ru))
used = set()
for dp, _, fs in os.walk(os.path.join(root, 'src/main/java')):
    for f in fs:
        if f.endswith('.java'):
            used |= set(re.findall(r'translatable\("(mobslearn\.[a-z0-9_.]+)"', open(os.path.join(dp, f), encoding='utf-8').read()))
missing_code = sorted(k for k in used if k not in ru and not k.endswith('.'))
print('keys:', len(ru), len(en), 'missing en:', miss_en, 'missing ru:', miss_ru, 'missing for code:', missing_code)
json.dump(ru, open(os.path.join(out, 'ru_ru.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
json.dump(en, open(os.path.join(out, 'en_us.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
