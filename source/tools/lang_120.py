"""Merges the World Learns 1.2.0 strings into the six lang files: python3 tools/lang_120.py (from source/)."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lang_120_en_ru as a, lang_120_uk as b, lang_120_de_fr_es as c

LANG = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'mobslearn', 'lang')
data = {
    'en_us': (a.EN, a.FIRST['en_us'], a.TITLE['en_us']),
    'ru_ru': (a.RU, a.FIRST['ru_ru'], a.TITLE['ru_ru']),
    'uk_ua': (b.UK, b.FIRST, b.TITLE),
    'de_de': (c.DE, c.FIRST['de_de'], c.TITLE['de_de']),
    'fr_fr': (c.FR, c.FIRST['fr_fr'], c.TITLE['fr_fr']),
    'es_es': (c.ES, c.FIRST['es_es'], c.TITLE['es_es']),
}
ref = set(a.EN)
for lang, (strings, first, title) in data.items():
    missing = ref - set(strings)
    extra = set(strings) - ref
    assert not missing and not extra, (lang, sorted(missing)[:5], sorted(extra)[:5])
    assert len(first) == 20 and len(title) == 20, lang
    path = os.path.join(LANG, lang + '.json')
    d = json.load(open(path, encoding='utf-8'))
    d.update(strings)
    for i in range(20):
        d['mobslearn.nemesis.first.%d' % i] = first[i]
        d['mobslearn.nemesis.title.%d' % i] = title[i]
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(lang, len(d))
