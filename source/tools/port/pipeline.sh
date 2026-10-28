#!/bin/bash
set -e
V=$1; SRC=$2; OUT=$3
rm -rf "$OUT" && mkdir -p "$OUT" && cp -r "$SRC/src" "$OUT/"
C="$(cd "$(dirname "$0")" && pwd)/codemod"
python3 $C/to1214.py "$OUT/src/main/java"
python3 $C/to1214b.py "$OUT/src/main/java"
if [ "$V" = "1.21.1" ] || [ "$V" = "1.20.1" ]; then python3 $C/to1211.py "$OUT/src/main/java"; fi
if [ "$V" = "1.20.1" ]; then python3 $C/to1201.py "$OUT/src/main/java"; python3 $C/to1201b.py "$OUT/src/main/java"; python3 $C/to1201data.py "$OUT/src/main/resources"; fi
python3 - "$OUT/src/main/resources/fabric.mod.json" "$V" <<'PY'
import json, sys, collections
p, v = sys.argv[1], sys.argv[2]
deps = {"1.21.4": (">=0.16.9", "~1.21.4", ">=21", ">=0.115.0"), "1.21.1": (">=0.16.0", "~1.21.1", ">=21", ">=0.100.0"), "1.20.1": (">=0.14.21", "~1.20.1", ">=17", ">=0.87.0")}[v]
d = json.load(open(p), object_pairs_hook=collections.OrderedDict)
d["version"] = "1.0.0-beta+" + v
d["depends"] = collections.OrderedDict([("fabricloader", deps[0]), ("minecraft", deps[1]), ("java", deps[2]), ("fabric-api", deps[3])])
open(p, "w").write(json.dumps(d, indent=2, ensure_ascii=False) + "\n")
PY
