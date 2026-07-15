#!/bin/sh
# Testes JVM do núcleo (sem Android): compila só os pacotes puros-Java e
# roda cada teste. Rodar da raiz do projeto: sh scripts/test-core.sh
set -e
cd "$(dirname "$0")/.."

OUT=build/test-classes
rm -rf "$OUT"
mkdir -p "$OUT"

SRC=src/main/java/br/com/termia/construajogue
TEST=src/test/java/br/com/termia/construajogue

# Só arquivos puros-Java (MapStore e AssetLevelProvider usam Android)
javac -d "$OUT" -encoding UTF-8 \
    "$SRC"/engine/Boxes.java \
    "$SRC"/engine/Collision.java \
    "$SRC"/util/*.java \
    "$SRC"/map/*.java \
    "$SRC"/persistence/MapJson.java \
    "$SRC"/prefab/*.java \
    "$SRC"/compiler/*.java \
    "$SRC"/runtime/RuntimeLevel.java \
    "$SRC"/runtime/LegacyLevelLoader.java \
    "$SRC"/runtime/LevelProvider.java \
    "$SRC"/runtime/SingleLevelProvider.java \
    "$TEST"/*.java

# arena.json não pode divergir do que o conversor gera da arena.txt
java -cp "$OUT" br.com.termia.construajogue.LegacyTxtConverter \
    src/main/assets/levels/arena.txt build/arena-gerada.json arena
if ! diff -q src/main/assets/maps/arena.json build/arena-gerada.json \
        > /dev/null 2>&1; then
    echo "ERRO: assets/maps/arena.json difere do conversor;"
    echo "      copie build/arena-gerada.json ou ajuste o conversor."
    exit 1
fi

for TESTE in JsonTest MapJsonTest PrefabCatalogTest MapValidatorTest \
        LevelCompilerTest WallOpeningTest StairsTest; do
    java -cp "$OUT" "br.com.termia.construajogue.$TESTE"
done
echo "testes do núcleo OK"
