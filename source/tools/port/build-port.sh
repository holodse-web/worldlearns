#!/bin/bash
set -e
V=$1
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PORT=${PORT_HOME:-/home/claude/port}
case $V in
  1.21.4) E=$PORT/env1214; REL=21;;
  1.21.1) E=$PORT/old/1.21.1; REL=21;;
  1.20.1) E=$PORT/old/1.20.1; REL=17;;
  *) echo "usage: build-port.sh 1.21.4|1.21.1|1.20.1"; exit 1;;
esac
T=$PORT/env1214/tool
J=${JDK:-/home/claude/env/jdk-25.0.5+8}/bin
W=$PORT/work-$V
if [ -z "$NOPIPE" ]; then
"$HERE/pipeline.sh" $V "$ROOT" "$W"
(cd "$W" && patch -s -p1 < "$HERE/patches/patch-$V.diff")
fi
rm -rf "$W/build"
FAPI=$E/fapi-named
CP="$E/mc/mc-named.jar:$(ls $E/libs/*.jar | tr '\n' ':')$(ls $FAPI/*.jar | tr '\n' ':')$T/fabric-loader-0.16.9.jar:$T/sponge-mixin-0.15.4+mixin.0.8.7.jar"
mkdir -p "$W/build/classes" "$W/build/libs"
find "$W/src/main/java" -name '*.java' > "$W/build/sources.txt"
env -u JAVA_TOOL_OPTIONS $J/javac --release $REL -encoding UTF-8 -proc:none -nowarn -Xmaxerrs 2000 -cp "$CP" -d "$W/build/classes" @"$W/build/sources.txt"
cp -r "$W/src/main/resources/"* "$W/build/classes/"
(cd "$W/build/classes" && zip -qr ../libs/named.jar .)
OUTJAR="$W/build/libs/worldlearns-1.0.0-beta+$V.jar"
env -u JAVA_TOOL_OPTIONS $J/java -cp "$PORT/tools:$T/*" MapTool remap "$W/build/libs/named.jar" "$OUTJAR" $E/mc/merged.tiny named intermediary mixin $E/mc/mc-named.jar $(ls $E/libs/*.jar $FAPI/*.jar | tr '\n' ' ')
echo "built $OUTJAR"
