#!/bin/bash
set -e
V=$1
D=/home/claude/port/old/$V
T=/home/claude/port/env1214/tool
J=/home/claude/env/jdk-25.0.5+8/bin
cd $D
unzip -p mc/intermediary.jar mappings/mappings.tiny > mc/intermediary.tiny
JA="env -u JAVA_TOOL_OPTIONS $J/java -Xmx4g -cp /home/claude/port/tools:$T/*"
$JA MapTool merge mc/intermediary.tiny mc/client.txt mc/merged.tiny
LIBS=$(ls libs/*.jar | tr '\n' ' ')
$JA MapTool remap mc/client.jar mc/mc-named.jar mc/merged.tiny official named x $LIBS
$JA MapTool remap mc/client.jar mc/mc-inter.jar mc/merged.tiny official intermediary x $LIBS
rm -rf fapi fapi-named && mkdir -p fapi fapi-named
(cd fapi && unzip -q -o ../fabric-api.jar 'META-INF/jars/*' && mv META-INF/jars/*.jar . && rm -rf META-INF)
$JA MapTool batch mc/merged.tiny intermediary named fapi-named $(ls fapi/*.jar | tr '\n' ' ') -- mc/mc-inter.jar $LIBS
ls fapi-named | wc -l
