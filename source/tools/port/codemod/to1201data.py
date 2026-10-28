import json, glob, os, sys
res = sys.argv[1]
a = os.path.join(res, 'data/mobslearn/advancement')
b = os.path.join(res, 'data/mobslearn/advancements')
if os.path.isdir(a): os.rename(a, b)
for p in glob.glob(b + '/*.json'):
    j = json.load(open(p))
    ic = j.get('display', {}).get('icon')
    if ic and 'id' in ic: ic['item'] = ic.pop('id')
    json.dump(j, open(p, 'w'), indent=2, ensure_ascii=False)
