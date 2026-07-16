#!/bin/sh
# Smoke test real: instala, abre GLES, injeta gestos e procura crash/erro GL.
# Uso: ADB=/caminho/adb sh scripts/test-device.sh
set -eu
cd "$(dirname "$0")/.."

ADB=${ADB:-adb}
if ! command -v "$ADB" >/dev/null 2>&1; then
    echo "ERRO: adb não encontrado (defina ADB=/caminho/adb)"
    exit 2
fi

PACKAGE=br.com.termia.construajogue
APK=build/construa-jogue.apk
sh /root/toolchain/build.sh "$(pwd)"
"$ADB" install -r "$APK" >/dev/null
"$ADB" logcat -c
"$ADB" shell am start -S -n "$PACKAGE/.MainActivity" \
    --ez smoke_test true >/dev/null

READY=
i=0
while [ "$i" -lt 24 ]; do
    READY=$("$ADB" logcat -d -s CJ_SMOKE:I 2>/dev/null \
        | grep 'GL_READY' || true)
    [ -n "$READY" ] && break
    sleep 0.5
    i=$((i + 1))
done
if [ -z "$READY" ]; then
    "$ADB" logcat -d -t 200
    echo "ERRO: GL não ficou pronto em 12 s"
    exit 1
fi

SIZE=$("$ADB" shell wm size | sed -n 's/.*: \([0-9]*x[0-9]*\).*/\1/p' \
    | tail -n 1 | tr -d '\r')
W=${SIZE%x*}
H=${SIZE#*x}
if [ -n "$W" ] && [ -n "$H" ]; then
    if [ "$H" -gt "$W" ]; then
        OLD_W=$W
        W=$H
        H=$OLD_W
    fi
    # Analógico esquerdo, giro na metade direita e toque de tiro.
    "$ADB" shell input swipe $((W / 6)) $((H * 4 / 5)) \
        $((W / 4)) $((H * 3 / 5)) 650
    "$ADB" shell input swipe $((W * 3 / 4)) $((H / 2)) \
        $((W * 4 / 5)) $((H * 2 / 5)) 350
    "$ADB" shell input tap $((W * 4 / 5)) $((H / 2))
fi
sleep 2

LOG=$("$ADB" logcat -d -t 500)
if echo "$LOG" | grep -E 'CJ_SMOKE.*GL_ERROR|AndroidRuntime.*FATAL EXCEPTION' \
        >/dev/null; then
    echo "$LOG"
    echo "ERRO: falha no teste do aparelho"
    exit 1
fi
"$ADB" shell dumpsys gfxinfo "$PACKAGE" framestats >/dev/null
"$ADB" shell am force-stop "$PACKAGE"
echo "teste no aparelho OK — GL abriu, gestos foram injetados, sem crash"
