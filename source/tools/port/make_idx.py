"""Builds /home/claude/port/idx1214.json (class simple name -> FQNs) from the named 1.21.4 Minecraft jar.
Needed by codemod/to1214.py. Usage: python3 make_idx.py <mc-named-1.21.4.jar> [out.json]"""
import zipfile, json, collections, sys
z = zipfile.ZipFile(sys.argv[1])
idx = collections.defaultdict(list)
for n in z.namelist():
    if not n.endswith('.class') or '$' in n:
        continue
    fq = n[:-6].replace('/', '.')
    if fq.startswith('net.minecraft.') or fq.startswith('com.mojang.'):
        idx[fq.rsplit('.', 1)[1]].append(fq)
json.dump(idx, open(sys.argv[2] if len(sys.argv) > 2 else '/home/claude/port/idx1214.json', 'w'))
print(len(idx), 'names')
