#!/usr/bin/env bash
# Parcours « client » sur une télé Android virtuelle : vidéo de l'écran + capture après chaque touche.
set -u
mkdir -p shots
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
n=0
shot() { n=$((n+1)); f=$(printf "shots/%s-%02d-%s.png" "$1" "$n" "$2"); adb exec-out screencap -p > "$f"; echo "capture $f"; }
key() { adb shell input keyevent "$1"; sleep "${2:-1.6}"; }
rec_start() { adb shell "screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/$1.mp4" & REC=$!; sleep 1; }
rec_stop() { adb shell pkill -INT screenrecord || true; wait $REC 2>/dev/null || true; sleep 2; adb pull "/sdcard/$1.mp4" "shots/$1.mp4" || true; }
open() {
  adb shell am force-stop app.tvo.tv
  # la clé TMDB n'est passée que si elle existe (un paramètre vide fait échouer le lancement)
  if [ -n "${TMDB:-}" ]; then adb shell am start -n app.tvo.tv/.MainActivity --es tvo_test_type "$1" --es tvo_test_id "$2" --es tvo_test_tmdb "$TMDB"
  else adb shell am start -n app.tvo.tv/.MainActivity --es tvo_test_type "$1" --es tvo_test_id "$2"; fi
  sleep 18
}
UP=19; DOWN=20; LEFT=21; RIGHT=22; OK=23; BACK=4

# 0) Écran « Qui regarde ? » : profils visibles, on monte et on se déplace entre eux
open profiles x; n=0
shot profils "ouverture"
key $UP; shot profils "haut"
key $RIGHT; shot profils "droite"
key $DOWN; shot profils "bas-gerer"
key $UP; shot profils "remonte"

# 1) Accueil : on parcourt les rangées comme un client, puis on ouvre un titre
open home x; n=0; rec_start 1-accueil
shot accueil "ouverture"
key $DOWN; shot accueil "bas-1"
key $RIGHT; key $RIGHT; shot accueil "droite-2"
key $DOWN; shot accueil "bas-2"
key $DOWN; shot accueil "bas-3"
key $UP; shot accueil "haut-1"
key $OK 6; shot accueil "fiche-ouverte"
key $DOWN; shot accueil "fiche-bas"
key $BACK 3; shot accueil "retour"
rec_stop 1-accueil

# 2) Fiche film puis fiche série : boutons, onglets, rangées, remontée
fiche() { # $1 = nom, $2 = type, $3 = id
  open "$2" "$3"; n=0; rec_start "$1"
  shot "$1" "ouverture"
  key $RIGHT; shot "$1" "droite-bouton"
  key $LEFT;  shot "$1" "gauche-bouton"
  key $DOWN;  shot "$1" "bas-1"
  key $RIGHT; shot "$1" "onglet-2"
  key $RIGHT; shot "$1" "onglet-3"
  key $LEFT;  key $LEFT; shot "$1" "onglet-1"
  key $DOWN;  shot "$1" "bas-2"
  key $RIGHT; key $RIGHT; shot "$1" "droite-2"
  key $DOWN;  shot "$1" "bas-3"
  key $DOWN;  shot "$1" "bas-4"
  key $UP;    shot "$1" "haut-1"
  key $UP;    shot "$1" "haut-2"
  key $UP;    shot "$1" "haut-3"
  key $UP;    shot "$1" "haut-4"
  key $UP;    shot "$1" "haut-5"
  rec_stop "$1"
}
fiche 2-film movie "${MOVIE:-tt0099785}"
fiche 3-serie series "${SERIES:-tt0903747}"
adb logcat -d -t 400 '*:E' > shots/erreurs.txt || true
ls -la shots
