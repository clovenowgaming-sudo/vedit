#!/data/data/com.termux/files/usr/bin/bash
# Jalankan di folder VEditor: bash push.sh
set -e
git init
git add .
git commit -m "VEditor: editor foto & video"
git branch -M main
gh repo create vedit --public --source=. --remote=origin --push
echo "Menunggu build..."
sleep 8
gh run watch --exit-status
mkdir -p ~/storage/downloads 2>/dev/null || true
gh run download -n VEditor-debug -D ~/storage/downloads/VEditor 2>/dev/null || gh run download -n VEditor-debug -D .
echo "APK ada di Download/VEditor (atau folder ini)."
