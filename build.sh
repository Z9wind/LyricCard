#!/bin/bash
set -e
ROOT=$(cd "$(dirname "$0")" && pwd)
SDK=/root/workspace/lyric-project/work/sdk
BT=$SDK/android-14
AJ=$SDK/android-34-ext12/android.jar
AAPT2=/usr/bin/aapt2
OUT=$ROOT/build

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/stubclasses"

echo "[1/8] compile libxposed stubs"
javac -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -d "$OUT/stubclasses" \
  $(find "$ROOT/stubsrc" -name '*.java') 2>/dev/null
jar cf "$OUT/libxposed-stub.jar" -C "$OUT/stubclasses" .

echo "[2/8] aapt2 compile"
"$AAPT2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

VER_FILE="$ROOT/.build_num"
VER_CODE=$(cat "$VER_FILE" 2>/dev/null || echo 3)
VER_CODE=$((VER_CODE + 1))
echo "$VER_CODE" > "$VER_FILE"
NAME_FILE="$ROOT/.version"
if [ -f "$NAME_FILE" ]; then
  VER_NAME=$(tr -d ' \r\n' < "$NAME_FILE")
else
  VER_NAME="2.$VER_CODE"
fi

echo "[3/8] aapt2 link"
"$AAPT2" link -o "$OUT/base.apk" -I "$AJ" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 33 \
  --version-code $VER_CODE --version-name $VER_NAME \
  "$OUT/res.zip"

echo "[4/8] javac"
find "$ROOT/src" -name '*.java' > "$OUT/sources.txt"
find "$OUT/gen" -name '*.java' >> "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -cp "$OUT/libxposed-stub.jar" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[5/8] d8 (app classes only)"
find "$OUT/classes" -name '*.class' -path '*/com/dsh/*' > "$OUT/d8in.txt"
java -Xmx1g -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --min-api 26 --lib "$AJ" --classpath "$OUT/libxposed-stub.jar" \
  --output "$OUT/dex" @"$OUT/d8in.txt"

echo "[6/8] package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
(cd "$ROOT/xposed-meta" && zip -q -r "$OUT/unsigned.apk" META-INF/xposed)

echo "[7/8] sign"
# 固定签名：keystore 放在 build/ 之外，清空 build 不会删掉它，保证所有版本签名一致。
KS=$ROOT/keystore/debug.keystore
mkdir -p "$ROOT/keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias lg \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android -dname "CN=LyricCard,OU=DSH,O=DSH,C=CN" >/dev/null 2>&1
fi
rm -f "$ROOT/LyricCard.apk"
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/LyricCard.apk" "$OUT/unsigned.apk"

echo "[8/8] verify"
java -jar "$BT/lib/apksigner.jar" verify "$ROOT/LyricCard.apk" >/dev/null 2>&1 && echo "signature ok"
unzip -l "$ROOT/LyricCard.apk" | grep -E "META-INF/xposed|classes.dex" 
echo "OK -> $ROOT/LyricCard.apk"
ls -lh "$ROOT/LyricCard.apk"
