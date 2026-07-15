# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une:
- motor FPS do `jogo-fps` (jogar em primeira pessoa);
- ferramentas de planta/construção do `editor3d` (desenhar o mapa).
Ciclo: desenhar espaço → posicionar prefabs prontos → Testar → jogar → voltar.
Planos em `PLANO.md`, `ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`.

## Estado atual — 2026-07-15
- **v0.9.3 (versionCode 17) — pintura POR FACE (canto resolvido de vez).**
  - A v0.9.2 NÃO resolveu no aparelho. Causa raiz dupla: (1) pintar o
    lado NEGATIVO gravava na cor BASE, que também pinta as PONTAS →
    faixa da cor interna vista de fora; (2) a tampa da ponta fica no
    MESMO plano da face da parede perpendicular → briga de z (listra).
  - Modelo novo: `color` = base (pontas/topo, nunca muda ao pintar
    lado), `color2` = face positiva, `color3` = face negativa
    (persistidos; `Boxes.emitBoundsPainted` com 3 cores).
  - Compilador: trecho de canto sai só na base E a ponta pintada RECUA
    1 cm (CORNER_INSET) para dentro da outra parede — some o coplanar.
  - Editor: pintar grava na face tocada; meio = parede toda (base,
    limpa faces); planta mostra as duas metades com as cores das faces.
  - Mapas pintados na v0.6.0–0.9.2 usam a semântica velha: REPINTAR.
- **v0.9.2 (versionCode 16) — CANTOS…** (superada; diagnóstico errado —
  só cobria metade do problema).
  - Problema real do aparelho: a face pintada ia até a ponta e vazava
    pelo canto (faixa da cor interna vista de fora).
  - `LevelCompiler.wallStubPlanes` detecta parede perpendicular
    encostada numa PONTA (a até 0,6m dela; junção em T no meio não
    conta) e `addPainted` corta a pintura na face interna da outra
    parede: o trecho do canto sai na cor base (acabamento). Só afeta
    o visual — colliders intactos. Corte diagonal 45° de verdade não
    dá: colisão é AABB.
  - Funciona também com vãos (cada pedaço do recorte passa pelos
    mesmos planos de canto). Testes do canto em WallOpeningTest.
- **v0.9.1 (versionCode 15) — ESCADAS E RAMPAS (subir andares).**
  - 4 peças de circulação geradas por código (`PrefabMeshFactory.stairs`):
    escada pequena 1m (4 degraus de 0,25), escada de andar 3m (12
    degraus), rampa curta 1m e rampa de andar 3m (espelhos de 0,10).
    Sobem da FRENTE (-Z, seta da planta) para trás; degraus em colunas
    cheias; CADA degrau é um collider — subir = andar (STEP do Player
    é 0,35). Rampa é escada de espelho baixo (colisão só tem AABB).
  - `StairsTest`: caminha com a colisão REAL (moveHorizontal/moveVertical,
    raio 0,35/altura 1,75) e confere que chega a 1m/3m nas 4 peças.
  - ANDAR DE CIMA: usar TETO (block; é pisável) ou BLOCO alto como laje
    e encostar a escada de andar; GIRAR aponta a subida.
- **v0.9.0 (versionCode 14) — SKYBOX com sol, lua e estrelas.**
  - `engine/Sky`: cubo desenhado por dentro, shader próprio — gradiente
    horizonte→zênite (horizonte = cor da neblina do mapa, transição
    suave), disco do sol + brilho (direção IGUAL à luz difusa do
    cenário no dia), lua com halo à noite e ESTRELAS procedurais (hash
    da direção, sem textura; brilho variado, some perto do horizonte).
  - Desenhado antes do cenário com depth/cull off e view SEM translação
    (gl_Position .xyww). Recriado em onSurfaceCreated como as malhas.
  - `MapDocument.sky` ("none/day/dusk/night") persistido no environment;
    `RuntimeLevel.skyMode()`; presets do CÉU… gravam o modo. Mapas
    legados (.txt) e "Instalação" ficam SKY_NONE = comportamento antigo.
  - Entardecer: sol baixo alaranjado + estrelas fracas (0.35).
- **v0.8.2 (versionCode 13) — CÉU…** presets de ambient/fog (superado
  pela v0.9.0, que adicionou o skybox de verdade).
- **v0.8.1 (versionCode 12) — SETA DE FRENTE + 6 peças novas.**
  - Convenção nova: a FRENTE de todo móvel (porta do armário, torneira,
    assento, prateleiras abertas) aponta para -Z em yaw 0; a planta
    desenha uma SETA amarela nesse lado, girando com GIRAR — dá para
    ver qual lado encosta na parede antes de testar. (Encosto da
    cadeira foi virado para +Z para obedecer a convenção.)
  - Peças novas (todas static): pia de cozinha (balcão+cuba+torneira),
    pia de banheiro (coluna+bacia), vaso sanitário, guarda-roupa
    (1,2×2,0m com frisos/puxadores), planta pequena e planta alta
    (vaso terracota + folhagem). Catálogo com 24 peças; o teste de
    integridade cobre malha/collider/pegada automaticamente.
- **v0.8.0 (versionCode 11) — PATRULHA DOS INIMIGOS na planta.**
  - Botão ROTA (ativo com inimigo selecionado): o próximo toque na
    planta vira o 2º ponto de patrulha (patrolX/patrolZ); tocar no
    próprio inimigo REMOVE a rota (fica de guarda). Rota desenhada
    tracejada vermelha com anel no destino (branca na seleção).
  - Rota é ancorada no mapa (mover o inimigo não move o destino).
  - O motor já fazia a patrulha (formato {x,y,z,x2,z2}) — só faltava a
    edição. Drone patrulha voando na própria altura; mutante no chão.
- **v0.7.1 (versionCode 10) — botão GIRAR (90° por toque).**
  - Peça estática: acumula yaw em `transform.yaw` (persistido);
    compilador gira centro das caixas e troca meias-dimensões
    (`quarterTurns`/`rotateBox` — AABBs só giram em passos de 90°).
  - Porta (portão): girar troca halfX↔halfZ. Estrutura selecionada:
    troca largura × profundidade no lugar. Pegada na planta acompanha.
- **v0.7.0 (versionCode 9) — MÓVEIS, OBSTÁCULOS E LUMINÁRIAS.**
  - `PrefabMeshFactory`: 11 peças estáticas procedurais em caixas —
    mesa, cadeira, estante, armário, cama, bancada; caixa pequena/
    grande, barril; luminária de pé e lâmpada de teto (pendura da
    origem p/ baixo; nasce em y=3.0). Cúpulas com cor >1 = "acesas"
    (emissivo fake — NÃO há luz dinâmica; anotar se pedirem sombra).
  - Comportamento `static` no catálogo: malha visual detalhada +
    collider SIMPLIFICADO separado (mesa = 1 bloco; lâmpada de teto
    sem collider). Compilador refatorado: listas `visuals` × `solids`
    independentes (arena legada segue bit a bit — teste prova).
  - Planta desenha a PEGADA real do móvel (retângulo translúcido;
    contorno amarelado nas luminárias). Validador exige malha
    registrada p/ toda peça static.
- **v0.6.0 (versionCode 8) — PINTAR: individual, por lado e balde.**
  - Botão PINTAR… abre paleta de 13 cores + caixa "Balde".
  - Toque pinta a estrutura; em PAREDE pinta o LADO voltado para o dedo
    (terço central = parede inteira). Balde: varre as paredes LIGADAS
    (encostadas em XZ) e pinta em cada uma a face voltada para o ponto
    tocado → tocar dentro do cômodo pinta todo o interior; fora, o
    exterior. Usa coordenada SEM snap (rawX/rawZ) p/ decidir o lado.
  - Modelo: `StructureObject.color2` = cor da face larga do lado
    POSITIVO do eixo fino (null = cor única). `Boxes.emitBoundsSided`
    emite as duas faces largas em cores distintas; compilador usa
    quando color2 != null (mapas legados intactos — caminho antigo).
    Planta desenha a parede em duas metades quando pintada por lado.
  - Persistido como `color2` no JSON; validado; testes atualizados.
- **v0.5.1 (versionCode 7) — COTAS NA PLANTA + diálogo MEDIDAS.**
  - Cota ao vivo enquanto desenha: retângulo mostra "L × P" (medida do
    cômodo) e parede mostra o comprimento, acompanhando o dedo.
  - Cotas permanentes: comprimento no meio de cada parede e L×P das
    demais estruturas quando o zoom dá espaço (sempre na selecionada);
    vão selecionado mostra largura × altura.
  - ALTURA virou **MEDIDAS**: formulário com campos reais por tipo —
    parede: comprimento/altura/espessura; piso/bloco/teto: largura/
    profundidade/altura(sentido do papel); vão: largura/altura/peitoril
    (janela); peça: distância do chão. Aceita vírgula decimal.
  - `PlanEditorView.mutateSelected(Runnable)` centraliza mutação com
    undo/status/redraw.
- **v0.5.0 (versionCode 6) — VÃOS NAS PAREDES + seleção melhorada.**
  - Botão VÃO… (porta 1,0×2,1 / portal livre 1,6×altura da parede /
    janela 1,2×1,2 peitoril 0,9): tocar numa parede RECORTA o vão de
    verdade — `LevelCompiler.cutOpenings` fatia a parede em trechos
    cheios + verga + peitoril; passagem/visão realmente livres (a porta
    hoje é um vão com marco visual na planta; folha que abre/fecha fica
    para depois — o portão+terminal continua sendo a porta animada).
  - `WallOpening` no modelo (offset relativo ao centro da parede — anda
    junto quando a parede move), persistido no JSON, validado
    (dentro da parede, sem sobrepor, altura <= parede).
  - SELECIONAR (ex-MOVER): seleciona vão (arrasta ao longo da própria
    parede), peça, marcador, estrutura; EXCLUIR e ALTURA valem p/ vão
    (ALTURA = altura do vão).
  - Correção: com PEÇA armada, tocar numa peça existente agora a
    SELECIONA em vez de empilhar outra em cima (era a causa do "não
    consigo selecionar/excluir").
  - `WallOpeningTest` (13 verificações) no test-core.sh.
- **v0.4.0 (versionCode 5) — Fase 3 inicial: PEÇAS DE JOGO NA PLANTA.**
  - Botão PEÇA… abre o catálogo (7 peças: drone ativo/dormente, mutante,
    kit, munição, terminal, portão); tocar na planta solta a peça com
    altura típica da campanha (drone 1.8m, mutante 0.85m, terminal/porta
    1.4m, itens 0.5m). MOVER seleciona/arrasta peças (prioridade sobre
    estruturas), EXCLUIR remove, ALTURA vira "distância do chão".
  - Ícones na planta: D vermelho (drone), Z cinza (dormente), M roxo
    (mutante), + verde (kit), A amarelo (munição), T laranja (terminal);
    portão = retângulo com halfX/halfZ reais.
  - Porta↔terminal se LIGAM SOZINHOS ao colocar (um de cada no mapa →
    `controllerId` preenchido). Patrulha ainda não editável (inimigo
    fica parado: patrolX/Z default = posição) — próximo passo natural.
  - Parede agora também gruda no MEIO de outra (junção em T):
    `nearWallBody` projeta o toque na linha central; prioridade
    ponta > corpo > grade.
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
