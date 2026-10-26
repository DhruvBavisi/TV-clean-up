#!/usr/bin/env bash
# Push favourites lineup assets (lineup.json, character PNGs, scenes/*.jpg) to the launcher on the TV.
#
# Usage: TV_HOST=192.168.1.50 tools/push_favourites.sh [tv-assets/export]
set -euo pipefail
ASSETS_ARG="${1:-tv-assets/export}"
case "$ASSETS_ARG" in
  /*) ASSETS_ARG="$(cd "$ASSETS_ARG" 2>/dev/null && { pwd -W 2>/dev/null || pwd; } || printf '%s' "$ASSETS_ARG")" ;;
  *) ASSETS_ARG="$(pwd -W 2>/dev/null || pwd)/$ASSETS_ARG" ;;
esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-fav
tv() { "$PY" -I tv.py "$@"; }

stage() { echo "$TMP-${1//\//-}"; }
put() { echo "mkdir -p \$(dirname $DIR/$1) 2>/dev/null; rm -f $DIR/$1 2>/dev/null; { o=\$(content write --uri $URI/$1 < $(stage "$1") 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $(stage "$1") $DIR/$1 2>/dev/null; rm -f $(stage "$1")"; }

echo "Creating remote directories..."
tv "mkdir -p $DIR/favourites/scenes $DIR/favourites/posters /data/local/tmp 2>/dev/null" >/dev/null

if [ -f "$ASSETS_ARG/lineup.json" ]; then
    echo "Pushing lineup.json..."
    tv --push "$ASSETS_ARG/lineup.json" "$(stage favourites/lineup.json)" >/dev/null
    tv "$(put favourites/lineup.json)" >/dev/null
else
    echo "no lineup.json found in $ASSETS_ARG" >&2
    exit 1
fi

if [ -f "$ASSETS_ARG/titles.json" ]; then
    echo "Pushing titles.json..."
    tv --push "$ASSETS_ARG/titles.json" "$(stage favourites/titles.json)" >/dev/null
    tv "$(put favourites/titles.json)" >/dev/null
fi

for f in "$ASSETS_ARG"/*.png; do
    [ -f "$f" ] || continue
    fname="$(basename "$f")"
    echo "Pushing character $fname..."
    tv --push "$f" "$(stage "favourites/$fname")" >/dev/null
    tv "$(put "favourites/$fname")" >/dev/null
done

if [ -d "$ASSETS_ARG/scenes" ]; then
    for f in "$ASSETS_ARG/scenes"/*.jpg; do
        [ -f "$f" ] || continue
        fname="$(basename "$f")"
        echo "Pushing scene $fname..."
        tv --push "$f" "$(stage "favourites/scenes/$fname")" >/dev/null
        tv "$(put "favourites/scenes/$fname")" >/dev/null
    done
fi

if [ -d "$REPO_ROOT/tv-assets/posters" ]; then
    for f in "$REPO_ROOT/tv-assets/posters"/*.jpg; do
        [ -f "$f" ] || continue
        fname="$(basename "$f")"
        echo "Pushing poster $fname..."
        tv --push "$f" "$(stage "favourites/posters/$fname")" >/dev/null
        tv "$(put "favourites/posters/$fname")" >/dev/null
    done
fi

if [ -f "$REPO_ROOT/tv-assets/my-favourites.psd" ]; then
    echo "Pushing my-favourites.psd..."
    tv --push "$REPO_ROOT/tv-assets/my-favourites.psd" "$(stage "favourites/my-favourites.psd")" >/dev/null
    tv "$(put "favourites/my-favourites.psd")" >/dev/null
fi

echo "All favourites assets pushed successfully to $DIR/favourites/"
