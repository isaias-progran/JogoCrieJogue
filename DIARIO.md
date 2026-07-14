# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une:
- motor FPS do `jogo-fps` (jogar em primeira pessoa);
- ferramentas de planta/construção do `editor3d` (desenhar o mapa).
Ciclo: desenhar espaço → posicionar prefabs prontos → Testar → jogar → voltar.
Planos em `PLANO.md`, `ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`.

## Estado atual — 2026-07-14
- **Fase 1 (documento/prefabs/compilador) COMPILADA — v0.2.0 (versionCode
  2)** em `/sdcard/TermIa/apks/construa-jogue.apk`. O setor 1 da campanha
  agora carrega `maps/arena.json` (validado + compilado); o setor 2 segue
  no formato legado `levels/labirinto.txt`.
- Novo núcleo puro-Java (testável no JVM): `util/Json` (JSON próprio com
  token de número preservado — bit-exato), `map/*` (MapDocument,
  StructureObject `block`, PrefabInstance, LogicMarker, Transform),
  `persistence/MapJson` (schema 1), `prefab/PrefabCatalog` +
  `assets/prefabs/catalog.json` (7 peças de jogo), `compiler/MapValidator`
  (erros bloqueiam; avisos não) e `compiler/LevelCompiler`.
- Refatoração: `game/Level` virou `runtime/RuntimeLevel` (dados imutáveis)
  + `runtime/LegacyLevelLoader` (.txt via InputStream); GameState/Player/
  GameRenderer usam RuntimeLevel; renderer decide .json × .txt pela
  extensão.
- **PORTÃO FASE 1 PASSOU (JVM)**: `sh scripts/test-core.sh` — 5 testes,
  127 verificações; `LevelCompilerTest` prova arena JSON == arena texto
  BIT A BIT (colliders, malhas, porta, spawns, itens, neblina). O script
  também trava se `arena.json` divergir do `LegacyTxtConverter`.
- Fase 0 validada implicitamente se a campanha v0.2.0 rodar igual.

## Estado anterior
- **Fase 0 (clone seguro)** — v0.1.0 (versionCode 1) compilada e instalada.
- Fonte copiado do `jogo-fps` v0.5.0 (campanha arena → labirinto, 120 FPS
  validado lá). Package renomeado para `br.com.termia.construajogue`,
  nome "Construa & Jogue", ícone novo (letra C, gerado pelo icone.py;
  só mipmap-xxhdpi — densidades antigas removidas).
- Estrutura de pacotes: java copiado mantém `engine/game/input/ui`; as pastas
  vazias previstas na ESTRUTURA (`map/`, `prefab/`, `compiler/`, …) serão
  preenchidas fase a fase.
- `scripts/test-levels.py` passa (arena 25 caixas, labirinto 36 caixas +
  7 mutantes, rotas OK).

## Portão no aparelho (pendente — usuário)
Validar a campanha completa na v0.2.0: setor 1 (arena, agora vinda do
JSON) idêntico ao jogo-fps, setor 2 (labirinto) igual, ~120 FPS, pausa,
os dois finais. Se a arena estiver diferente do jogo-fps, o compilador
tem bug (mas o teste bit a bit torna isso improvável).

## Decisões e armadilhas
- **Testes JVM**: sem Gradle/JUnit aqui, e `org.json` do android.jar é stub
  fora do Android → runner `main()` via `scripts/test-core.sh` (compila SÓ
  os pacotes puros: util/map/persistence/prefab/compiler/runtime +
  engine/{Boxes,Collision}) e parser JSON próprio (`util/Json`). Testes de
  nível .txt seguem em Python (`scripts/test-levels.py`).
- `util/Json` preserva o TOKEN do número (`Json.Num`): float lido com
  `Float.parseFloat` direto. Não trocar por double→float (arredondamento
  duplo quebra a igualdade bit a bit com os níveis legados).
- `assets/maps/arena.json` é GERADO (`LegacyTxtConverter`, no src/test).
  Não editar à mão: o test-core.sh compara com o conversor e falha.
- Pés exatamente sobre o topo de um bloco NÃO colidem (comparação estrita
  do Collision) — validador de "spawn dentro de collider" respeita isso.
- ESTRUTURA prevê `Shader/Mesh/Boxes` em `render/`; mantidos em `engine/`
  (menos diff com o jogo-fps). Reorganizar só se doer.
- Ao importar o editor3d na Fase 2: NÃO copiar o MainActivity de 916 linhas;
  quebrar em `EditorHost` + tools (regra das ~400 linhas).
- Renderer do editor será `WHEN_DIRTY`; o do jogo, contínuo. Um renderer não
  atende os dois modos (ver ARQUITETURA §7).

## Próximos passos
1. Usuário valida a campanha v0.2.0 no aparelho (portões Fase 0 + 1).
2. Fase 2: construir o espaço — planta 2D, snap, paredes/pisos/tetos do
   editor3d, seleção 3D, undo/redo, início/saída, salvamento (MapStore)
   e os modos Biblioteca/Construir/Jogar na Activity.
