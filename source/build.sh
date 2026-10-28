#!/bin/bash
set -e
cd "$(dirname "$0")"
ENV=/home/claude/env
JDK=$ENV/jdk-25.0.5+8/bin
CP=$ENV/bundle/mc/client.jar:$(ls $ENV/bundle/libs/*.jar | tr '\n' ':')$(ls $ENV/fapi/META-INF/jars/*.jar | tr '\n' ':')
rm -rf build/classes && mkdir -p build/classes build/libs
find src/main/java -name '*.java' > build/sources.txt
env -u JAVA_TOOL_OPTIONS $JDK/javac --release 25 -encoding UTF-8 -proc:none -Xlint:-options -nowarn -cp "$CP" -d build/classes @build/sources.txt
cp -r src/main/resources/* build/classes/
VERSION=$(python3 -c "import json;print(json.load(open('src/main/resources/fabric.mod.json'))['version'])")
rm -f build/libs/*.jar
(cd build/classes && env -u JAVA_TOOL_OPTIONS $JDK/jar --create --file ../libs/worldlearns-$VERSION.jar .)
echo "built build/libs/worldlearns-$VERSION.jar ($(find build/classes -name '*.class' | wc -l) classes)"
