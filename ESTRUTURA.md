# Estrutura prevista

```text
construa-jogue/
├── README.md
├── PLANO.md
├── ARQUITETURA.md
├── ORIGENS.md
├── ESTRUTURA.md
├── INICIAR.md
├── DIARIO.md
├── docs/
│   ├── FORMATO-MAPA.md
│   └── PREFABS.md
├── scripts/
│   ├── test-core.sh
│   └── test-maps.py
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── assets/
    │   │   ├── maps/
    │   │   ├── prefabs/catalog.json
    │   │   └── textures/
    │   ├── res/{drawable,mipmap-*,raw,values}/
    │   └── java/br/com/termia/construajogue/
    │       ├── MainActivity.java
    │       ├── core/
    │       │   ├── AppMode.java
    │       │   └── AppModeController.java
    │       ├── map/
    │       │   ├── MapDocument.java
    │       │   ├── StructureObject.java
    │       │   ├── PrefabInstance.java
    │       │   ├── LogicMarker.java
    │       │   ├── Transform.java
    │       │   ├── MaterialDef.java
    │       │   └── WallOpening.java
    │       ├── prefab/
    │       │   ├── PrefabCatalog.java
    │       │   ├── PrefabDefinition.java
    │       │   ├── PrefabCategory.java
    │       │   └── PrefabMeshFactory.java
    │       ├── editor/
    │       │   ├── EditorHost.java
    │       │   ├── EditorScene.java
    │       │   ├── EditorRenderer.java
    │       │   ├── EditorGlView.java
    │       │   ├── DrawPlanView.java
    │       │   ├── SelectionController.java
    │       │   ├── UndoHistory.java
    │       │   └── tools/
    │       │       ├── BuildTool.java
    │       │       ├── PrefabPlacementTool.java
    │       │       ├── MarkerTool.java
    │       │       └── MeasureTool.java
    │       ├── geometry/
    │       │   ├── Extrude.java
    │       │   ├── Triangulator.java
    │       │   ├── WallBuilder.java
    │       │   └── RaycastMesh.java
    │       ├── compiler/
    │       │   ├── MapValidator.java
    │       │   ├── ValidationIssue.java
    │       │   └── LevelCompiler.java
    │       ├── runtime/
    │       │   ├── RuntimeLevel.java
    │       │   ├── RuntimeEntity.java
    │       │   └── LegacyLevelLoader.java
    │       ├── physics/
    │       │   ├── CollisionWorld.java
    │       │   ├── Collider.java
    │       │   ├── AabbCollider.java
    │       │   ├── WallSegmentCollider.java
    │       │   ├── FloorRegion.java
    │       │   └── Raycast.java
    │       ├── render/
    │       │   ├── Shader.java
    │       │   ├── Mesh.java
    │       │   ├── Boxes.java
    │       │   ├── GameMeshes.java
    │       │   └── PrefabMeshes.java
    │       ├── game/
    │       │   ├── GameHost.java
    │       │   ├── GameView.java
    │       │   ├── GameRenderer.java
    │       │   ├── GameState.java
    │       │   ├── FpsCamera.java
    │       │   ├── Player.java
    │       │   ├── Weapon.java
    │       │   ├── Enemy.java
    │       │   ├── Drone.java
    │       │   ├── Mutant.java
    │       │   └── Sounds.java
    │       ├── input/{TouchControls,ControlOverlay,PauseMenu}.java
    │       ├── persistence/{MapStore,MapJson,MapMigration}.java
    │       ├── ui/{MapLibraryView,Hud,PrefabCatalogView,ValidationDialog}.java
    │       └── util/{Ids,Numbers}.java
    └── test/java/br/com/termia/construajogue/
        ├── MapJsonTest.java
        ├── PrefabCatalogTest.java
        ├── MapValidatorTest.java
        ├── LevelCompilerTest.java
        └── CollisionWorldTest.java
```

As classes são direção arquitetural. Cada fase cria apenas o necessário para
seu portão, mantendo o APK compilável.

