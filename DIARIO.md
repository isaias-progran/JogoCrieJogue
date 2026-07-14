# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une:
- motor FPS do `jogo-fps` (jogar em primeira pessoa);
- ferramentas de planta/construção do `editor3d` (desenhar o mapa).
Ciclo: desenhar espaço → posicionar prefabs prontos → Testar → jogar → voltar.
Planos em `PLANO.md`, `ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`.

## Estado atual — 2026-07-14
- **v0.3.1 (versionCode 4)** — feedback do usuário na v0.3.0 ("construir
  parede ficou difícil no dedo"):
  - PAREDE agora GRUDA nas pontas: tocar perto da extremidade de uma
    parede existente (círculos amarelos na tela) continua dela ou fecha
    canto exato; vale para o início E o fim do arrasto (`nearWallEnd`,
    raio de captura 36px/escala, mínimo 0.4m);
  - botão ALTURA: digitar a medida real da estrutura selecionada —
    parede/bloco = altura (base fixa), piso = espessura (topo fixo),
    teto = elevação da base. Decisão: diálogo só sob demanda na seleção,
    NÃO após cada criação (criar 4 paredes abriria 4 telas);
  - ferramenta TETO: placa de 0.3m com base na altura da parede (3m),
    desenhada translúcida por cima da planta; na seleção o teto é o
    ÚLTIMO candidato (não rouba o toque de quem está embaixo);
  - `StructureObject.role` (floor/wall/block/ceiling) persistido no JSON
    (campo opcional — mapas convertidos caem em heurística por
    dimensões em `StructureRoles`).
- **Fase 2 (construir o espaço) — v0.3.0 (versionCode 3)** em
  `/sdcard/TermIa/apks/construa-jogue.apk`. O app abre na BIBLIOTECA:
  novo mapa, construir (planta 2D), jogar mapa, campanha original,
  duplicar/excluir. Ciclo Construir → Testar → Construir funciona.
- Editor (Fase 2 mínima, sem editor3d 3D ainda): `PlanEditorView` =
  planta vista de topo editando o MapDocument direto — ferramentas PISO/
  PAREDE/BLOCO/INÍCIO/SAÍDA/MOVER + EXCLUIR, snap 0.25m, área ±32m,
  pan/zoom com 2 dedos. Tudo vira `block` AABB (paredes retas alinhadas
  aos eixos, 3m; piso 0.3m com topo em y=0) — diagonais/curvas ficam p/
  quando importar Extrude/segmentos do editor3d.
- `EditorHost`: undo/redo global por snapshot JSON (50), salvamento
  automático (sair/testar), validação (erros = diálogo, avisos = toast),
  Testar passa snapshot profundo — documento intocado pela partida.
- `MapStore`: filesDir/maps/<id>.json com .tmp/.bak atômicos (padrão
  editor3d). `MainActivity` reescrito: modos LIBRARY/EDIT/PLAY, partida
  monta e descarta GameView/HUD/controles a cada entrada; Voltar
  contextual (jogo→pausa→editor/biblioteca; editor salva e volta).
- `GameRenderer` não conhece mais caminhos: recebe `LevelProvider`
  (`AssetLevelProvider` = campanha arena.json+labirinto.txt;
  `SingleLevelProvider` = mapa do usuário compilado).
- `GameState`: mapa sem porta nasce com saída liberada; objetivo sem
  terminal = "CHEGUE À SAÍDA"; vitória genérica p/ 1 setor.

## Fase 1 (v0.2.0) — resumo
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

## Portões no aparelho (pendentes — usuário, v0.3.0)
1. **Fases 0+1**: CAMPANHA na biblioteca → arena (JSON) idêntica ao
   jogo-fps, labirinto igual, ~120 FPS, pausa, os dois finais.
2. **Fase 2**: criar mapa novo → desenhar piso, 4 paredes, início e
   saída → TESTAR → andar na sala em primeira pessoa → voltar (botão
   Voltar) → mover uma parede → testar de novo. Undo/redo, salvar
   (sair e reabrir mantém o mapa), excluir/duplicar na biblioteca.

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
1. Usuário valida os dois portões da v0.3.0 no aparelho (acima).
2. Fase 3: catálogo no editor — navegador por categorias, peça
   fantasma, encaixe, inimigos/itens/terminal/porta posicionáveis na
   planta (o compilador já entende todos), escadas/móveis/obstáculos
   (exigem PrefabMeshFactory + instâncias no renderer).
3. Fase 2.5 (quando quiser diagonais/curvas): importar Extrude/
   triangulação do editor3d + WallSegmentCollider (ARQUITETURA §6).
