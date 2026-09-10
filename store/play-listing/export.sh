#!/bin/bash
# Render each slide of deck.html to PNG with headless Edge. Output: out/
cd "$(dirname "$0")"; mkdir -p out
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
URL="file:///$(cygpath -m "$PWD")/deck.html"
for id in 01 02 03 04 05 06 07; do
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1080,1920 --virtual-time-budget=4000 --screenshot="$(cygpath -w "$PWD/out/keysnap_$id.png")" "$URL?slide=$id" 2>/dev/null
done
"$EDGE" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1024,500 --virtual-time-budget=4000 --screenshot="$(cygpath -w "$PWD/out/keysnap_feature_graphic.png")" "$URL?slide=fg" 2>/dev/null
ls -la out; file out/*.png
