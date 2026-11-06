# VEditor

Editor foto & video (Kotlin, Media3). UI gelap, kotak (tanpa bentuk bulat).

## Build lewat GitHub Actions (Termux)
    bash push.sh
Script itu: git init -> commit -> `gh repo create` -> push -> tunggu build -> download APK.
Atau manual: push ke branch `main`, lalu ambil artifact **VEditor-debug** di tab Actions.

## Fitur
Foto: 14 filter + intensitas, brightness/contrast/saturasi/warmth/fade, blur, pixelate, vignette, grain, glitch RGB,
crop rasio, putar, flip, teks (geser), stiker emoji, gambar bebas, export JPG.
Video: trim, 11 filter, brightness/contrast/saturasi/hue, kecepatan 0.25x-4x, crop rasio, putar, flip,
teks overlay, mute, export MP4 (H.264).
