# Reaproveitamento dos apps existentes

## `jogo-fps` — base do novo app

Manter quase inteiros: `Shader`, `Mesh`, `Boxes`, `FpsCamera`, `Player`,
`Weapon`, `Enemy`, `Drone`, `Mutant`, `Sounds`, `TouchControls`,
`ControlOverlay`, `PauseMenu`, `Hud`, malhas e sons.

Adaptar:

- `Level` → `RuntimeLevel` + `LegacyLevelLoader`;
- `Collision` → `CollisionWorld` com várias formas;
- `Raycast` → AABB + paredes/portas;
- `GameState` → entidades do mapa compilado;
- `GameRenderer` → runtime selecionado, sem caminhos fixos;
- `MainActivity` → Biblioteca/Construir/Jogar.

## `editor3d` — importar seletivamente

Reaproveitar: transform e papéis de `SceneObject`, `WallOpening`, `Extrude`,
`DrawPlanView`, `RaycastMesh`, padrão atômico de `SceneStore`, histórico do
`Scene` e seleção/trava do renderer/view.

Não copiar diretamente: `MainActivity`, renderer inteiro, schema atual, limite
24×24 m ou `Scene` misturando documento e estado de execução.

## Descobertas importantes

- FPS usa caminhos fixos de dois níveis: deve receber mapa da biblioteca.
- cenário atual vira um VBO estático e porta fica separada: preservar.
- FPS espera uma porta/terminal/saída: novo modelo usa listas e UUIDs.
- AABB não resolve paredes diagonais do editor: usar collider de segmento.
- malha do editor não possui cor por vértice: compilador anexará cor.
- `GameMeshes` já oferece sementes para o catálogo de prefabs prontos.

