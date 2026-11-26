#!/usr/bin/env bash
# Parcours télécommande sur la fiche TV : capture d'écran après chaque touche.
set -u
mkdir -p shots
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global window_animation_scale 1
n=0
shot() { n=$((n+1)); f=$(printf "shots/%s-%02d-%s.png" "$1" "$n" "$2"); adb exec-out screencap -p > "$f"; echo "capture $f"; }
key() { adb shell input keyevent "$1"; sleep "${2:-1.6}"; }
open() {
  adb shell am force-stop app.tvo.tv
  adb shell am start -n app.tvo.tv/.MainActivity --es tvo_test_type "$1" --es tvo_test_id "$2" --es tvo_test_tmdb "${TMDB:-}"
  sleep 18
}
UP=19; DOWN=20; LEFT=21; RIGHT=22; OK=23
run() { # $1 = film|serie
  local t=$1; n=0
  shot "$t" "ouverture"
  key $RIGHT; shot "$t" "droite-bouton"
  key $LEFT;  shot "$t" "gauche-bouton"
  key $DOWN;  shot "$t" "bas-1"
  key $DOWN;  shot "$t" "bas-2"
  key $RIGHT; shot "$t" "droite-1"
  key $RIGHT; shot "$t" "droite-2"
  key $DOWN;  shot "$t" "bas-3"
  key $DOWN;  shot "$t" "bas-4"
  key $DOWN;  shot "$t" "bas-5"
  key $UP;    shot "$t" "haut-1"
  key $UP;    shot "$t" "haut-2"
  key $UP;    shot "$t" "haut-3"
  key $UP;    shot "$t" "haut-4"
  key $UP;    shot "$t" "haut-5"
  key $UP;    shot "$t" "haut-6"
}
open movie "${MOVIE:-tt0099785}";  run film
open series "${SERIES:-tt0903747}"; run serie
adb logcat -d -t 400 '*:E' > shots/erreurs.txt || true
ls -la shots
