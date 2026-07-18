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
    "$SRC"/engine/Raycast.java \
    "$SRC"/util/*.java \
    "$SRC"/geometry/Triangulator.java \
    "$SRC"/map/*.java \
    "$SRC"/editor/StructureRoles.java \
    "$SRC"/editor/tools/GroupSelection.java \
    "$SRC"/editor/tools/OpeningTool.java \
    "$SRC"/editor/tools/PaintTool.java \
    "$SRC"/editor/tools/PrefabPlacementTool.java \
    "$SRC"/editor/tools/StoryLevels.java \
    "$SRC"/persistence/MapJson.java \
    "$SRC"/persistence/MapMigration.java \
    "$SRC"/sharing/Base64Url.java \
    "$SRC"/sharing/MapShareCodec.java \
    "$SRC"/sharing/QrCode.java \
    "$SRC"/game/ObjectiveTracker.java \
    "$SRC"/game/SpatialRules.java \
    "$SRC"/game/GameResult.java \
    "$SRC"/game/NpcGreetingTracker.java \
    "$SRC"/game/NpcCompanion.java \
    "$SRC"/game/AllySight.java \
    "$SRC"/game/Enemy.java \
    "$SRC"/game/Mutant.java \
    "$SRC"/game/Kamikaze.java \
    "$SRC"/game/Weapon.java \
    "$SRC"/game/WeaponSpec.java \
    "$SRC"/ai/AiScenarioPlan.java \
    "$SRC"/ai/AiScenarioProfile.java \
    "$SRC"/ai/AiScenarioBuilder.java \
    "$SRC"/ai/AiGeometry.java \
    "$SRC"/ai/AiCityRecipes.java \
    "$SRC"/ai/AiCityBlocks.java \
    "$SRC"/ai/AiThemeRecipes.java \
    "$SRC"/ai/AiPlaceRecipes.java \
    "$SRC"/ai/AiFocalRecipes.java \
    "$SRC"/ai/AiOpenAiClient.java \
    "$SRC"/ai/AiFreeMapScript.java \
    "$SRC"/ai/AiFreeMacros.java \
    "$SRC"/ai/AiMapRevision.java \
    "$SRC"/ai/NpcPersonality.java \
    "$SRC"/ai/NpcConversationMemory.java \
    "$SRC"/ai/AiRequestGate.java \
    "$SRC"/prefab/*.java \
    "$SRC"/compiler/*.java \
    "$SRC"/runtime/RuntimeLevel.java \
    "$SRC"/runtime/RuntimeDoor.java \
    "$SRC"/runtime/RuntimeTerminal.java \
    "$SRC"/runtime/RuntimeNpc.java \
    "$SRC"/runtime/LegacyLevelLoader.java \
    "$SRC"/runtime/LevelProvider.java \
    "$SRC"/runtime/LazyLevelProvider.java \
    "$SRC"/runtime/SingleLevelProvider.java \
    "$TEST"/*.java

# mapas de exemplo: gerados, validados e compilados (falha quebra aqui)
java -cp "$OUT" br.com.termia.construajogue.ExampleMapsGenerator

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
        LevelCompilerTest WallOpeningTest StairsTest PolygonTest \
        MapShareQrTest EditorToolsTest GameplayRulesTest \
        StoryLevelsTest VerticalEnemyTest ElaborateMapTest CityMapTest \
        AiScenarioTest AiFreeMapTest; do
    java -cp "$OUT" "br.com.termia.construajogue.$TESTE"
done
echo "testes do núcleo OK"
