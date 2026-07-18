# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une:
- motor FPS do `jogo-fps` (jogar em primeira pessoa);
- ferramentas de planta/construção do `editor3d` (desenhar o mapa).
Ciclo: desenhar espaço → posicionar prefabs prontos → Testar → jogar → voltar.
Planos em `PLANO.md`, `ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`.
Contrato específico do gerador Livre em `docs/IA-LIVRE.md`.

## Estado atual — 2026-07-18
- **Polimento P2 — botão Testar valida e compila UMA vez.**
  - `EditorHost.test()` agora cria o snapshot profundo primeiro, compila o
    nível dele UMA vez (`LevelCompiler.compile` com falha silenciada — o
    validador explica) e valida com a sobrecarga da v0.26.2
    (`MapValidator.validate(doc, catalog, compiled)`). Erros/avisos
    exibidos são os mesmos; a regra "falha de compilação não duplica
    ruído" continua no validador.
  - `Listener.onTest(snapshot, compiled)` leva o nível pronto;
    `MainActivity.onTest` só recompila no caso raro de `compiled == null`
    (mantém o diálogo antigo). Fim da dupla validação+compilação no toque.
  - PENDÊNCIA DE APARELHO: conferir o botão Testar manualmente (validar →
    jogar → voltar; mapa com erro deve mostrar o mesmo diálogo).
  - Suíte verde: 775 verificações (o editor não tem teste JVM; prova é
    compilação). Build Android OK, APK copiado.
- **Polimento P1 — deduplicações internas (refactor puro, sem versão nova).**
  - `map/TextLimits.java` é a fonte única dos limites de texto de NPC
    (name 48, role 80, greeting 240, background 600, combatLine* 120,
    demais restritos 128); `AiFreeMapScript` e `MapValidator` consultam a
    mesma classe — nenhum valor mudou.
  - As duas espirais de `nudgeFree` do `AiFreeMapScript` viraram uma só
    (`spiralNudge` + interface `FreeCheck`): mesma varredura de 16
    direções até 16 m, com a checagem (colliders compilados ou blocos do
    documento) injetada. Avisos idênticos aos anteriores.
  - Suíte idêntica e verde: 775 verificações. Build Android OK, APK
    366.018 bytes copiado para `/sdcard/TermIa/apks/construa-jogue.apk`.
- **Plano novo agendável: Polimento 2026-07 (`PLANO-POLIMENTO-2026-07.md`).**
  - Teste da Automação de 2026-07-17 23h35 funcionou (tarefa id 100 chegou
    ao claude, leu o projeto e a suíte passou com 775 verificações).
  - Análise de melhorias virou plano com nome próprio, 6 fases: P1 dedup
    (`nudgeFree` em dobro no AiFreeMapScript + tabela de limites de texto
    parser/validador), P2 Testar do editor compilando uma vez (sobrecarga
    da v0.26.2), P3 cache por quadro do raycast inimigo↔aliado, P4
    modularizar AiFeatureController (1094 linhas), P5 modularizar
    AiFreeMapScript (983; PRÉ-CONDIÇÃO: F3–F5 dos macros concluídas — os
    dois planos tocam o arquivo), P6 limpeza pedida pelo usuário: mover
    todo `PLANO*.md` 100% marcado para `docs/historico/` junto do TSV.
  - Receita da Automação em `scripts/automacao-polimento.tsv` (ids 11–13,
    10h/16h/22h — sem colisão com 8h/14h/20h dos macros nem com a id 100).
    Falta o usuário anexar ao `/host/automation.tsv` e abrir o TermIa para
    armar os alarmes. Nenhum código mudou nesta entrada — só documentação.

## Estado anterior — 2026-07-17
- **v0.27.1 (versionCode 68) — F2: o Livre aprende a usar macros.**
  - `buildFreeMapRequest` agora documenta `definir <nome> … fim` e
    `usar <nome> <x> <z> [rot] [tom]`, explica as opções válidas de rotação
    e tom e inclui uma casa mínima completa carimbada duas vezes.
  - A orientação de variedade ficou objetiva: construção repetida 3 ou mais
    vezes deve virar macro, variando `rot` e/ou `tom` entre vizinhos. Isso só
    afeta novas gerações; nenhum mapa salvo é refeito ou alterado.
  - `AiFreeMapTest` verifica a presença da gramática e da regra no request e
    executa o mesmo contrato do exemplo pelo parser, validador e compilador
    (duas casas, 10 estruturas, sem erro).
  - Suíte verde: 775 verificações (`AiFreeMapTest` 108→112). Build Android
    concluído, APK de 366.018 bytes copiado para
    `/sdcard/TermIa/apks/construa-jogue.apk` e `/sdcard/apks/`.
  - Geração real não executada nesta sessão: não havia chamada autenticada ou
    aparelho conectado disponível. Validar no aparelho com mapa NOVO na F7,
    registrando modelo, pedido e resultado; mapas antigos não servem para
    avaliar mudança de instrução.
- **v0.27.0 (versionCode 67) — F1 do plano de macros: `definir`/`usar` no parser.**
  - `definir <nome>` grava linhas até `fim`; `usar <nome> x z [rot] [tom]`
    reexecuta com deslocamento pela âncora, rotação 0/90/180/270 e tom
    (`claro|escuro|r g b`). Novo `ai/AiFreeMacros` (arquivo próprio) faz a
    gravação e a expansão; `AiFreeMapScript` ganhou `execute(...)+Cursor`
    (dispatch extraído, comportamento sem macros intacto) e `LimitReached`.
  - Regras cumpridas do plano: estado de última parede/peça CONFINADO por
    `usar` (Cursor próprio — vao/texto de dentro não vazam, os de fora
    continuam valendo depois); limites 500/400 valem APÓS a expansão e o
    `usar` que estoura é interrompido com aviso "macro-bomba"; aninhado,
    nome desconhecido, macro vazio, `fim` solto e redefinição viram aviso;
    comandos de documento (nome/ceu/objetivo/inicio/saida/usar) não entram
    em macro.
  - Rotação segue a convenção do motor (`rotateBox`: 90° ⇒ (x,z)→(−z,x),
    troca hx↔hz): endpoints de parede giram como pontos, o SINAL do offset
    do `vao` acompanha o eixo da parede original (+90 em parede-x preserva,
    parede-z inverte etc.), yaw de peça soma a rotação, `patrulha` gira, e
    porta girada 90/270 nasce com `halfX↔halfZ` trocados (collider é AABB
    de mundo) — `prop halfX` dentro do macro também é renomeado.
  - `normalizeDoors` 1:1 por instância comprovado: 2 `usar` de casa com
    terminal+portão ligam cada portão ao terminal da própria casa.
  - Suíte: 771 verificações (AiFreeMapTest 83→108). Build OK: `aapt2`
    confirmou 67/0.27.0, APK 366.018 bytes em
    `/sdcard/TermIa/apks/construa-jogue.apk`. Instrução da IA ainda NÃO
    ensina os macros — é a F2; gerar mapa no aparelho continua igual.
- **PLANO substituído por decisão do usuário: macros de construção.**
  - O usuário mandou apagar o plano antigo (endurecimento da IA Livre, já
    cumprido no essencial) e criar um plano completo para MACROS: a IA define
    uma construção uma vez (`definir casaPadrao … fim`) e carimba N vezes
    (`usar casaPadrao x z rot tom`); depois o app ganha serializador
    doc→roteiro com prova de ida-e-volta e a revisão passa a enviar roteiro
    compacto em vez do JSON inteiro (corta o custo de tokens da melhoria).
  - `PLANO.md` novo tem 7 fases com caixas, gates por fase, a receita pronta
    da tarefa 🤖 da Automação do TermIa (uma fase por execução, verificação
    via test-core.sh) e seção de armadilhas (macro-bomba, estado de última
    parede vazando do `usar`, rotação só 90°, perda silenciosa no
    serializador = pior bug possível). Inegociáveis de segurança foram
    preservados fora do plano: funil parser→resgate→validador→compilador,
    saída da revisão sempre roteiro completo, mapa salvo intocado.
  - `INICIAR.md` aponta para o plano novo; pendências de aparelho herdadas
    (revisão, cancelamento, aliado) viraram parte da fase manual F7.
    Nenhum código mudou nesta entrada — só documentação.
  - A seção 3 do PLANO.md ganhou os fatos do código da Automação do TermIa
    (formato do `/host/automation.tsv`, flag de permissões do `claude -p`,
    alarme que só arma ao abrir o app, teto de 10 min por execução) e o
    pedido da tarefa agora manda terminar trabalho não commitado antes de
    abrir fase nova.
- **v0.26.2 (versionCode 66) — correções da revisão geral (8 ângulos de análise).**
  - Uma revisão multiagente do diff v0.26.x achou 10 defeitos confirmados;
    todos os de comportamento foram corrigidos nesta versão:
    (1) `normalizeDoors` voltou à distribuição 1:1 — cada portão sem
    `controllerId` liga a um terminal DISTINTO na ordem do roteiro (antes
    todos iam para o primeiro terminal e um único terminal abria o mapa
    inteiro); sem terminal livre, vira porta automática com aviso.
    (2) `sseDelta` trata `response.incomplete`: roteiro cortado no teto de
    tokens vira erro claro em vez de sucesso parcial silencioso — a revisão
    de mapa grande não perde mais a cauda sem avisar.
    (3) Início dentro de peça/porta/polígono virou AVISO no validador (bloco
    desenhado continua erro, como sempre); mapa salvo antes da v0.26 não
    pode mais ficar injogável retroativamente. A checagem compilada roda
    mesmo com outros erros na lista (sem mascarar), e falha de compilação
    não duplica ruído quando já há erros específicos.
    (4) Diálogo de NPC no editor só grava `combatLine1..3` com o checkbox
    marcado; desmarcado remove tudo — NPC pacífico não é mais mutado por
    abrir propriedades e dar OK.
    (5) `gate.acquire()` da melhoria só roda APÓS a validação local; mapa
    >180 KiB recusado não queima mais vaga das 40 da sessão.
    (6) `Cancellation.cancel()` desconecta numa thread própria — nada de
    I/O de rede na thread principal ao tocar Cancelar.
    (7) Compilação única: `MapValidator.validate` ganhou sobrecarga que
    aceita `RuntimeLevel` já compilado; `LazyLevelProvider`,
    `AssetLevelProvider` e `MainActivity.compile` compilam UMA vez (fim do
    engasgo dobrado na troca de setor); o funil Livre não recompila no fim.
    (8) `MapReachability` sem falso alarme: obstáculo não é mais inflado
    pelo raio (vão de ~1 m não fecha na grade de 0,6 m) e célula inicial
    ruim é inconclusiva (sem aviso), nunca acusação.
    (9)+(10) Consertos automáticos do parse (`faltou inicio/saida`,
    marcador movido, portão religado) agora levam prefixo `resgate:` e caem
    em "Corrigidos pelo jogo" na prévia, não em "Pontos de atenção".
  - NOVO (relato do aparelho: "inimigo voando"): `settleEnemies` no funil
    Livre — drone/kamikaze/chefe pairam no Y que a IA mandou e torreta é
    fixa; agora voador fora da faixa [apoio+0,9, apoio+3,4] desce para
    apoio+1,8 e torreta flutuando desce para apoio+0,55 (tabela do
    `PrefabPlacementTool.defaultY`; apoio = topo de collider sob a coluna).
    Mutante tem gravidade e se corrige sozinho. Aviso "altura irreal".
  - Pendências conscientes (limpeza, sem bug): AiFeatureController (~1100
    linhas) e AiFreeMapScript (~950) acima da regra de tamanho — usuário
    dispensou por ora; espiral `nudgeFree` e tabela de limites de texto
    duplicadas; raycast inimigo↔aliado 2x/quadro por par; EditorHost.test
    ainda valida+compila em separado no botão Testar.
  - Suíte: 746 verificações (83 no `AiFreeMapTest`, 6 novas: incomplete,
    portões 1:1, altura de inimigos). Build OK: `aapt2` confirmou 66/0.26.2,
    APK 361.921 bytes copiado para `/sdcard/TermIa/apks/construa-jogue.apk`.
    Validar no aparelho: mapa com 2 terminais deve exigir os dois; gerar
    livre e conferir que não há mais drone perdido no céu.
- **v0.26.1 (versionCode 65) — editar e melhorar mapas com IA sem sobrescrever.**
  - `MELHORAR COM IA` aparece na prévia de geração, no menu `⋮` de cada mapa
    da biblioteca e no painel `☰` do editor. A prévia inicial também troca o
    texto ambíguo por `SALVAR E EDITAR`.
  - A revisão envia pedido + JSON congelado do mapa como dados não confiáveis,
    exige um roteiro completo e preserva explicitamente tudo que não foi
    pedido. O transporte continua Responses/SSE, sem tools e com cancelamento.
  - Geração e revisão convergem no mesmo `AiFreeMapScript` → resgate →
    `MapValidator` → `LevelCompiler`. Falha, cancelamento ou descarte não
    grava nada; `SALVAR CÓPIA E EDITAR` cria um ID novo e mantém o original.
  - A tela informa que a chamada adicional pode consumir créditos e que o mapa
    completo inclui nomes/textos de NPC. Entrada acima de 180 KiB é recusada
    sem truncamento silencioso.
  - Verificação do núcleo: 740 verificações numeradas, incluindo 77 no
    `AiFreeMapTest`, além dos exemplos e da equivalência da arena.
  - Build Android concluído; `aapt2` confirmou versionCode 65/versionName
    0.26.1, e `apksigner` confirmou assinaturas v2/v3. APK copiado para
    `/sdcard/TermIa/apks/construa-jogue.apk`. Sem `adb` neste ambiente, ainda
    falta testar uma chamada real e o fluxo visual no aparelho.
- **v0.26.0 (versionCode 64) — IA Livre protegida e aliado combatente local.**
  - O Livre vira contrato permanente do produto: pedido direto, coordenadas da
    IA, streaming, resgate, validação e confirmação continuam intactos. O novo
    `PLANO.md` registra fases e portões antirregressão; `docs/IA-LIVRE.md`
    documenta a linguagem e o estado real.
  - `MapValidator` agora confere o spawn contra todos os colliders compilados,
    incluindo móveis, portas e polígonos. Uma busca local conservadora avisa
    quando não confirma rota horizontal até a saída. O resgate também move
    marcadores presos em prefabs e converte portão órfão em porta automática.
  - Cancelar fecha a `HttpsURLConnection` ativa; `AtomicReference` protege a
    fase de progresso entre rede/UI. A prévia separa “Corrigidos pelo jogo” de
    “Pontos de atenção” e mostra a quantidade de aliados combatentes.
  - O Livre aceita `texto combate sim|nao` e `combatLine1..3`. Ausência do dado
    mantém NPC antigo pacífico. Combatente segue as regras locais: alcance 14 m,
    dano 1, intervalo 1,20 s, inimigo visível mais próximo e retorno após 1,5 s.
  - Inimigos podem mirar no aliado, favorecendo proximidade e tiro recente. O
    aliado tem 60 de vida, desmaia em vez de morrer e recupera metade depois de
    8 s sem perigo. Som, traçador, cor/pose e fala curta dão feedback; nenhuma
    chamada de IA acontece durante o combate.
  - Verificação: `sh scripts/test-core.sh` passou com 728 verificações
    numeradas (65 em `AiFreeMapTest` e 59 em `GameplayRulesTest`), além dos
    exemplos e da equivalência bit a bit da arena. Build Android concluído;
    `aapt2` confirmou versionCode 64/versionName 0.26.0 e o APK foi copiado para
    `/sdcard/TermIa/apks/construa-jogue.apk`. Teste em aparelho continua
    necessário para cancelamento real, GLES/áudio e balanceamento.
- **v0.25.4 (versionCode 63) — resgate: erro do validador não perde geração.**
  - Print real (v0.25.3): "Torreta fixa: propriedade 'patrolX' não
    permitida" derrubou o mapa inteiro. Duas camadas novas:
    (1) PREVENÇÃO — prop/texto/patrulha agora consultam o
    `allowsProperty` do catálogo (mesma regra do validador) e recusam na
    linha, virando aviso; patrulha em peça fixa idem.
    (2) RESGATE — `AiFreeMapScript.salvage()`: se o validador recusar o
    mapa, remove propriedades proibidas/inválidas, vãos impossíveis e
    estruturas sem volume, ajusta objetivo (collect sem fichas →
    reach_exit; alvo > fichas → alvo = fichas; survive sem duração) com
    saída automática, re-empurra o spawn, e valida DE NOVO — só desiste
    se nem assim passar ("recusado mesmo após o resgate"). Cada conserto
    vira aviso "resgate: …" na prévia. Estado atual confirmado com 689
    verificações numeradas, além dos contratos de exemplos e conversão.
- **v0.25.3 (versionCode 62) — instrução do livre afinada pelo aparelho.**
  - VALIDADO NO APARELHO (v0.25.2): o modo livre construiu bem e o
    usuário achou "até melhor que os mapas pré-programados". Falhas
    relatadas: cômodos sem porta, poucos inimigos/lógica rala e paredes
    com textura repetida. As três viraram blocos explícitos na
    instrução: PORTAS (recortar a entrada logo após fechar cada sala),
    JOGABILIDADE (12-24 inimigos misturados guardando itens/saída,
    progressão leve→disputado→defendido) e VARIEDADE VISUAL (material e
    tom por prédio, piso interno ≠ rua). Suíte com 688 verificações.
  - Instrução só muda GERAÇÕES NOVAS; mapa salvo continua igual.
- **v0.25.2 (versionCode 61) — modo livre não perde a geração por erro bobo.**
  - Teste real (print): streaming OK, mas o validador recusou "o início
    está dentro de uma estrutura" — a IA gastou a geração inteira e o
    mapa foi jogado fora por 1 coordenada. Redes novas no parser:
    início/saída dentro de bloco são EMPURRADOS para o espaço livre mais
    próximo (espiral 16 direções até 16 m, mesma checagem
    Collision.overlaps do validador, com aviso na prévia); vão é preso
    ao comprimento/altura reais da parede e vão sobreposto vira aviso em
    vez de erro fatal. Suíte com 685 verificações (11 novas, incluindo a
    reprodução exata do erro do aparelho).
  - Ainda pode falhar por: >1 'inicio' não (marker substitui), ids ok…
    se aparecer recusa nova do validador, capturar o print e criar rede
    equivalente.
- **v0.25.1 (versionCode 60) — streaming: dá para VER a IA trabalhando.**
  - Teste real do usuário: modo livre estourou o timeout (120 s fixos do
    cliente para uma geração de minutos). Timeouts agora por rota:
    livre 10 min, guiado 5 min, NPC 2 min; estouro vira mensagem clara
    ("tente Luna/mini") em vez de erro seco.
  - Modo livre pede `stream:true` e lê SSE (`postStream`/`sseDelta`):
    o diálogo mostra fase + cronômetro + "N comandos (M caracteres)
    recebidos", atualizado a cada segundo por Handler; recusa/falha no
    meio do stream vira erro com mensagem segura. Guiado ganhou
    cronômetro simples. Suíte com 674 verificações (6 novas de SSE).
  - Validar no aparelho: gerar cidade no modo livre e ver o contador
    subir; cancelar no meio deve abortar sem travar.
- **v0.25.0 (versionCode 59) — modo LIVRE: a IA desenha o mapa inteiro.**
  - Pedido do usuário após diagnóstico da cidade vazia: mesmo com o plano
    perfeito, o modo guiado cobre ~9% do chão (teto de 8 prédios no
    schema, receitas ancoradas no centro) e confina inimigos/itens na
    faixa x=±2,1 m. Em vez de expandir o schema, o usuário decidiu
    liberar: "IA trabalhando livre, se ela errar removemos".
  - Novo `ai/AiFreeMapScript`: roteiro de comandos, um por linha (nome,
    ceu, som, ambiente, neblina, objetivo, piso, teto, parede [diagonal
    vira poly], vao na última parede, bloco, peca do catálogo, prop,
    texto de NPC, patrulha, inicio, saida). Linha inválida vira AVISO
    (mostrado na prévia), nunca código; coordenadas/cores/medidas presas
    à grade ±48 e 0..1; redes de segurança: spawn/saída automáticos com
    aviso, portas completam halfX/Y/Z, portão liga sozinho ao terminal.
  - `AiOpenAiClient.buildFreeMapRequest`: instrução SÓ técnica (formato +
    catálogo + regras mínimas + "preencha a área toda"), pedido do
    jogador passa direto, sem schema; saída maior (16k/20k/10k/6k tokens
    por modelo). MapValidator + LevelCompiler continuam sendo o portão
    antes de salvar — mapa quebrado é recusado com a mensagem do
    validador.
  - UI: spinner "Modo de criação" no diálogo GERAR — Guiado (padrão) ou
    Livre (experimental); prévia própria com contagens e avisos. Modo
    livre gera 1 mapa único (sem setores). Suíte com 668 verificações
    (35 novas em AiFreeMapTest). Validar no aparelho: gerar "cidade
    cheia" no Livre com Terra e comparar com o Guiado; se falhar muito,
    remover a opção é só tirar o spinner.
- **v0.24.0 (versionCode 58) — arsenal: metralhadora, escopeta e rifle.**
  - `WeaponSpec` (pistola 12/0,32s/dano 1; metralhadora 30/0,11s/1;
    escopeta 6/0,85s/3 — derruba drone num tiro; rifle 5/1,05s/4 —
    derruba mutante num tiro). `Weapon.equip()` troca a arma com pente e
    reserva próprios; a bala especial agora soma +2 ao dano da arma
    (pistola especial continua 3, como antes).
  - Armas entram no mapa como pickups do catálogo (42 peças):
    `pickup.weapon.smg/shotgun/rifle` — novo behavior no compilador,
    validador, runtime (ITEM_WEAPON_*), pegar troca a arma na hora com
    aviso no HUD; visual reutiliza a malha de munição com tinta dourada.
    Também posicionáveis no editor (PEÇA…) e nos mapas trocados.
  - IA: campo `weapons` no schema estrito (lista fechada de 0-3 entre
    smg/shotgun/rifle — livre escolha em lista, sem abrir a segurança);
    a instrução manda combinar com o clima do pedido; o construtor
    espalha os pickups pelo caminho. Arma fora da lista é recusada antes
    da rede. Suíte com 633 verificações (9 novas). Validar no aparelho:
    gerar mapa pedindo "escopeta escondida", pegar e sentir cadência.
- **v0.23.2 (versionCode 57) — versão visível na biblioteca.**
  - Usuário instalou e "continua a mesma coisa": não havia como saber
    qual versão estava rodando (o app não exibia versão em lugar
    nenhum). A biblioteca agora mostra "v0.23.2" sob o título, via
    PackageManager — prova definitiva do APK instalado.
  - Badging confirmou que o APK do 📦 era o certo (56/0.23.1 com
    AiCityBlocks no dex). Causa provável do "mesma coisa": jogar mapa
    SALVO — mapas já gerados nunca mudam; toda melhoria de receita vale
    só para GERAÇÕES NOVAS. Regra de teste: instalar → conferir a
    versão na biblioteca → gerar mapa novo.
- **v0.23.1 (versionCode 56) — quarteirões com arquétipos, rumo à Aurora.**
  - Feedback do aparelho: setores variam, mas a cidade gerada ainda não
    chega ao exemplo embarcado. Novo `ai/AiCityBlocks`: rodízio
    determinístico de arquétipos por quarteirão — loja (addRoom),
    garagem de portão largo com caixotes, SOBRADO com escada externa até
    sala no telhado (mesmo padrão laje+escada do deck validado pelo
    StairsTest) e mercado de dois portais com corredores de estantes.
    Vale para as três malhas (avenida, gêmeas, cruz).
  - Malha viária completa no estilo Aurora: faixas de pedestres nas
    QUATRO bocas do cruzamento, linha central também na avenida
    perpendicular (maze) e anel viário marcado no asfalto (half>=28).
  - Suíte com 624 verificações (3 novas: escada externa presente, sala
    no telhado acima de 4 m e 12+ faixas). Conferir no aparelho: cidade
    maze deve ter garagem/mercado/sobrado distintos e subida jogável ao
    telhado.
- **v0.23.0 (versionCode 55) — o kind da zone comanda a planta do setor.**
  - Feedback real do aparelho (v0.22.9): 4 setores com arquitetura
    idêntica, mudando só cores. Causa: a instrução nova faz o modelo
    mandar 1 zone POR setor, então a rotação de planta (que só agia
    quando as zones acabavam) nunca disparava — todos os setores usavam
    o layout global, e a zone só trocava material/cor/mobília.
  - Agora, do 2º setor em diante, o kind da zone escolhe planta e tema:
    house/apartment/tower → prédio focal (com os ANDARES da própria
    zone, mesmo em campanha térrea), courtyard/park → pátio, plaza →
    praça, warehouse → rua industrial, laboratory/fortress/ruins → seus
    temas, shop/station → rua urbana. Zone repetindo o desenho do 1º
    setor rotaciona como antes. O 1º setor segue o layout global.
  - `buildThemedStreet` ganhou o parâmetro de tema (o setor de galpões
    ergue indústria mesmo com setting city). Suíte com 621 verificações
    (12 novas: 4 distritos válidos, 6 pares de arquiteturas distintas e
    torre subindo). Conferir no aparelho com o mesmo prompt de mundo
    enorme: loja/galpões/praça/torre devem sair visivelmente diferentes.

### Resumo do loop noturno (v0.22.1 → v0.22.9, 9 iterações autônomas)
Todas as oito receitas de planta respondem à rota (cidade, praça, pátio,
indústria, fortaleza, ruínas, campus, linear); paredes diagonais poly
estrearam em praças e bastiões; a mobília segue a finalidade da zone.
AiScenarioBuilder foi modularizado (1607→531 linhas + AiGeometry/City/
Theme/Place/FocalRecipes) e EditorHost enxugado (1391→891 + EditorForms/
EditorPickers). Suíte de 601→609 verificações, tudo commitado e pushado
por versão. Pendências para o aparelho: diálogos e seletores do editor
(refactor sem teste JVM) e a variedade nova de mapas gerados por IA.

- **v0.22.9 (versionCode 54) — campus e linear pela rota.**
  - `campus`: `loop` arruma os prédios em anel ao redor de uma praça
    verde; `branching` cria correntes diagonais que se afastam do eixo;
    `scattered` continua aleatório. `linear`: `loop` força serpentina de
    borda a borda; `branching` abre uma segunda porta em cada câmara —
    caminhos paralelos de verdade.
  - Com isso as OITO receitas de planta respondem à rota. Suíte com 609
    verificações (2 novas). Iteração 9 e última do loop noturno.
- **v0.22.8 (versionCode 53) — seletores do editor em EditorPickers.**
  - Segunda leva: paleta de pintura (com a tabela PALETTE), cor livre,
    contorno, vão, peça, lista de objetos e MEDIDAS viraram
    `editor/EditorPickers` (338 linhas). EditorHost caiu para 891 linhas
    (era 1391 no início da noite); nele ficam barra, painel, undo/redo,
    salvamento, teste, prévia 3D e configuração de lógica.
  - Prova por compilação + suíte intacta (607); os seletores pedem a
    mesma conferência visual dos formulários. Iteração 8 do loop
    noturno; próxima: contratos de teste extras ou pequenos acabamentos
    — os alvos grandes da fila acabaram.
- **v0.22.7 (versionCode 52) — formulários do editor em EditorForms.**
  - Primeira leva da quebra do EditorHost (1391→1168 linhas): andar
    ativo, objetivo, material, céu (com a tabela SKIES) e os campos
    genéricos de diálogo (field/textField/parse/setFieldVisible) viraram
    `editor/EditorForms` (287 linhas), lendo o host pelo pacote e
    mutando o documento só pelos ganchos beforeChange/afterChange.
  - Movimentação mecânica; prova por compilação + suíte intacta com 607
    verificações (o editor Android não tem teste JVM — conferir os
    quatro diálogos no aparelho). Iteração 7 do loop noturno; próxima
    leva: paleta de pintura, cor livre, contorno, vão, peça e medidas.
- **v0.22.6 (versionCode 51) — modularização do construtor concluída.**
  - Passos finais: `ai/AiPlaceRecipes` (198 linhas — pátio, campus,
    praça, labirinto, linear, prédio isolado, chanfros) e
    `ai/AiFocalRecipes` (343 — casa de 1-3 pavimentos com cascas,
    divisões, telhado, mobília, luzes; túnel; dimensionamento
    houseHalfX/Z/effectiveFloors). AiScenarioBuilder ficou com 531
    linhas: dispatch por layout, ambiente, perigos, spawn/objetivo,
    inimigos e suprimentos. Era 1607 no início da noite.
  - Refactor puro: suíte idêntica com 607 verificações nas quatro
    extrações. test-core.sh compila os arquivos novos. Iteração 6 do
    loop noturno; próxima: extrair formulários do EditorHost (1393
    linhas) ou mais contratos de teste.
- **v0.22.5 (versionCode 50) — receitas urbanas e temáticas em arquivos próprios.**
  - Segundo passo da modularização: `ai/AiCityRecipes` (283 linhas —
    cidade, avenida, avenidas gêmeas, cruz com quadras, faixas, skyline,
    addRoom/roomPurpose) e `ai/AiThemeRecipes` (194 linhas — indústria,
    laboratório, fortaleza, ruínas). AiScenarioBuilder caiu de 1439 para
    1018 linhas; o dispatch por layout continua nele. test-core.sh
    compila os dois arquivos novos.
  - Refactor puro: suíte idêntica com 607 verificações. Iteração 5 do
    loop noturno; próxima: extrair praça/pátio/campus/labirinto/linear
    (AiPlaceRecipes) e depois casa/túnel (AiFocalRecipes).
- **v0.22.4 (versionCode 49) — AiGeometry: tijolos separados das receitas.**
  - Primeiro passo da modularização do AiScenarioBuilder (1607→1439
    linhas): os 16 utilitários de estrutura/vão/peça/material/cor
    (block, wall, opening, windowOpening, diagonalWall, prefab, hazard,
    rowPosition, isUnderground e as paletas ground/wall/building) e as
    constantes CONCRETE/DARK/LIGHT viraram `ai/AiGeometry` (202 linhas,
    sem decisão de planta). Movimentação mecânica com chamadas
    qualificadas; `test-core.sh` ganhou o arquivo novo na lista.
  - Refactor puro: suíte idêntica com 607 verificações. Iteração 4 do
    loop noturno; próximas: extrair receitas de cidade/temas e depois as
    de praça/pátio/campus para arquivos próprios.
- **v0.22.3 (versionCode 48) — mobília pela finalidade da zone.**
  - `furnishStory` diferencia mais kinds: loja (estantes, balcão e
    armário), parque/praça/pátio (plantas e cadeiras), apartamento/torre
    (sofá+TV na sala; cama, guarda-roupa, espelho e pia no andar de cima).
    Antes shop/park/apartment caíam todos na mobília genérica de casa.
  - `addRoom` (cômodos da cidade/avenidas) agora mobilia pela zone via
    `roomPurpose`: loja tem estante e balcão, galpão tem bancada e barril,
    parque tem plantas, resto ganha mesa+cadeira. Só com o feature
    `furniture` ligado, como nas casas.
  - Suíte com 607 verificações (3 novas). Iteração 3 do loop noturno;
    próxima: modularizar AiScenarioBuilder (~1700 linhas) em receitas.
- **v0.22.2 (versionCode 47) — indústria, fortaleza e ruínas pela rota.**
  - `industrial`: `loop` ergue uma espinha central de galpões criando dois
    corredores paralelos (caixotes centrais saem para não colidir);
    `branching` deixa uma travessa livre no meio do galpão.
  - `fortress`: `loop` põe duas portas afastadas em cada muralha
    transversal — dá para rondar em anel; `branching` ergue bastiões
    chanfrados nos quatro cantos em paredes diagonais (KIND_POLY).
  - `ruins`: `loop` arruma os destroços em elipse ao redor de um vazio
    central, no lugar das duas fileiras.
  - Todas as seis receitas temáticas agora respondem à rota. Suíte com
    604 verificações (3 novas). Iteração 2 do loop noturno.
- **v0.22.1 (versionCode 46) — praça e pátio também obedecem à rota.**
  - `hub`: rota `direct` = quatro alas cardeais com cantos da praça
    chanfrados em paredes diagonais reais (KIND_POLY, primeira vez que o
    construtor da IA usa poly fora dos marcos); `branching` = seis alas;
    `loop` = anel de alas na diagonal (sem chanfros para não bloquear).
  - `courtyard`: `branching` desalinha as alas alternadamente; `loop`
    cerca o jardim central com as diagonais chanfradas.
  - Suíte com 601 verificações (3 novas). Trabalho do loop noturno
    autônomo autorizado pelo usuário; próximas iterações seguem as
    prioridades: demais receitas (industrial/fortaleza/ruínas/campus),
    mobiliar por finalidade da zone, modularizar AiScenarioBuilder.
- **v0.22.0 (versionCode 45) — setores distintos e malha viária pela rota.**
  - Campanhas multi-setor saíam clones: o modelo não sabia da divisão e
    mandava 1–2 zones, `zoneAt` repetia por floorMod e o construtor aplicava
    os mesmos features em todo setor (16 usos de random em 1326 linhas =
    jitter cosmético). Agora a instrução exige 4–6 zones DISTINTAS para
    cidade/mundo enorme; setor além das zones descritas gira a planta
    (`rotatedLayout`: street→courtyard→campus→hub→maze→linear) e
    água/lava alternam por paridade de setor.
  - A rota passou a comandar a malha viária da cidade (antes era só faixa
    decorativa): `direct` = avenida, `branching` = avenida com transversais
    desobstruídas, `loop` = avenidas gêmeas com quadra central (circulável),
    `maze` = avenida em cruz com quadras nos quatro quadrantes — o desenho
    da Cidade Aurora, usada como referência de complexidade. Cidade ganha
    faixa central tracejada, faixas de pedestres no cruzamento (maze) e
    volumes de skyline nos cantos dos setores huge.
  - Segurança intacta: a IA continua só escolhendo valores fechados do
    schema; toda geometria nova nasce no construtor local e passa pelo
    mesmo validador. Suíte com 598 verificações (8 novas: setores não
    clonados, perigos alternados, três malhas distintas, faixa central e
    instrução); APK assinado com 329.153 bytes nesta build. Falta validar
    no aparelho com uma chave real.
- **v0.21.3 (versionCode 44) — desenho da planta extraído para PlanRenderer.**
  - `PlanEditorView` caiu de 2264 para 1695 linhas: o bloco de desenho
    inteiro (grade, estruturas, vãos, peças, rotas, contorno, alças, cotas)
    virou `editor/PlanRenderer` (599 linhas), que lê o estado da view pelo
    mesmo pacote e nunca muta documento nem seleção. Movimentação mecânica:
    código idêntico, só com os membros qualificados (`v.`) e os paints
    migrados. Sem mudança de comportamento pretendida.
  - A view segue dona de toque/gestos/seleção/undo — essa metade é
    entrelaçada e NÃO é extração rápida; fica para quando doer. Suíte com
    590 verificações (não cobre o editor Android: a prova aqui é
    compilação + conferência visual no aparelho); APK assinado com
    329.152 bytes nesta build.
- **v0.21.2 (versionCode 43) — estrelas do survive medem a vida restante.**
  - No objetivo sobreviver as metas `twoStarSeconds`/`threeStarSeconds` eram
    constantes (o tempo decorrido é sempre a própria duração): todo mapa
    survive dava sempre a mesma quantidade de estrelas, e os gerados por IA
    ficavam eternamente em 1 porque o construtor já pulava as metas de tempo.
    Agora `GameResult` dá 2 estrelas ao terminar com vida 40+ e 3 com 80+;
    os demais objetivos seguem medindo tempo, sem mudança.
  - O formulário de objetivo esconde as metas de tempo no survive (e as zera
    ao aplicar), mostrando a regra por vida no lugar. `docs/FORMATO-MAPA.md`
    documenta a semântica. Sem mudança de schema: os campos continuam os
    mesmos e mapas antigos não precisam de migração.
  - Removidas as pastas vazias `core/`, `physics/` e `render/` (resíduo da
    ESTRUTURA planejada; `Shader/Mesh/Boxes` moram em `engine/` de
    propósito). Suíte com 590 verificações (3 novas de estrela do survive);
    APK assinado v2/v3 com 329.152 bytes nesta build.
  - NOTA DE HISTÓRICO: o trabalho v0.14.0→v0.21.1 entrou no git num único
    commit (938784f) porque os estados intermediários não existiam mais em
    lugar nenhum; o detalhe por versão está nas entradas abaixo. Remoto:
    github.com/isaias-progran/JogoCrieJogue.
- **v0.21.1 (versionCode 42) — NPC brasileiro casual e voz por personalidade.**
  - A conversa agora separa identidade, personalidade, regras e exemplos no
    prompt. O NPC responde em uma ou duas frases de português brasileiro
    cotidiano, aceita contrações e até duas gírias leves quando couber, evita
    fórmulas de atendimento como “Certamente” e não mistura regionalismos ao
    acaso. A saída caiu de 220 para 140 tokens para manter fala curta.
  - Seis personalidades locais e determinísticas — parceiro, prático, calmo,
    firme, animado e reservado — são escolhidas pelo papel/contexto ou pela
    identidade do NPC. A mesma personalidade orienta o texto e altera
    levemente ritmo/tom do TTS; portanto até uma única voz instalada ganha
    variação consistente, sem rede ou cobrança adicional.
  - O gerador passou a pedir temperamento e saudação casual; Lia agora avisa
    “Deu ruim na cidade” e chama o jogador com “bora”. O schema dos mapas e as
    barreiras de segurança não mudaram. São 587 verificações JVM; o APK Android
    compilou com 329.153 bytes e assinatura v2/v3. Falta conferir no aparelho
    se os seis ajustes de ritmo/tom agradam na voz Google instalada.
- **v0.21.0 (versionCode 41) — modelo escolhível para gerar mapas.**
  - O formulário oferece GPT-5.6 Terra (padrão equilibrado), GPT-5.6 Sol
    (qualidade máxima), GPT-5.6 Luna (econômico) e o GPT-5.4 mini anterior
    para compatibilidade. Terra usa raciocínio médio, Sol alto e Luna baixo;
    cada perfil reserva saída suficiente para raciocínio e JSON estruturado.
  - A escolha vale somente para criar o cenário e aparece novamente na prévia.
    Falas de NPC continuam no 5.4 mini para não transformar cada conversa em
    uma solicitação cara/lenta. Sol avisa na tela que pode demorar e custar
    mais, sem presumir preço específico.
  - Segurança não foi aberta: os quatro IDs ficam numa allowlist compilada no
    APK, um valor diferente é recusado antes da rede, nenhum modelo recebe
    ferramentas e o resultado continua preso ao mesmo schema/validador. HTTP
    404 orienta escolher outra opção caso o Project não tenha acesso.
  - A suíte pura-Java soma 581 verificações, cobrindo modelo, esforço, limite
    de saída, fallback e separação entre cenário/NPC. APK assinado v2/v3 com
    325.057 bytes nesta build.
- **v0.20.1 (versionCode 40) — prompt grande sem esconder GERAR.**
  - O formulário de cenário passou a rolar dentro do diálogo, enquanto a barra
    `CANCELAR/GERAR` permanece fora da área rolável. O campo aceita até 1.000
    caracteres, mostra no máximo sete linhas e possui rolagem interna; o
    teclado redimensiona o diálogo. A mesma proteção foi aplicada à tela da
    chave para aparelhos menores e orientação paisagem. As 573 verificações
    passaram; APK assinado v2/v3 com 325.058 bytes nesta build.
- **v0.20.0 (versionCode 39) — plantas realmente diferentes e prédios verticais.**
  - O contrato da IA agora inclui implantação, rota, padrão de cômodos,
    cobertura, número de prédios/cômodos/andares e uma lista ordenada de zonas
    com tipo, tamanho, pavimentos e finalidade. Dez layouts, cinco rotas,
    cinco padrões internos e 16 recursos seguros substituem a escolha antiga
    que se limitava quase só a tema, tamanho e acabamento.
  - O construtor local ganhou plantas independentes para edifício único/torre,
    rua, pátio, campus, labirinto, praça com alas, sequência linear e volumes
    espalhados. `buildingCount`, `roomCount`, `route`, `roofStyle`, `zones` e
    `seed` agora alteram a construção; o tema define materiais e atmosfera sem
    forçar sempre a mesma avenida.
  - Uma casa de dois ou três andares é um espaço percorrível: paredes e
    divisões em cada nível, laje em três placas deixando o poço livre,
    escada/rampa, janelas, porta, móveis, luzes, teto ou cobertura acessível.
    Saída, fichas e inimigos podem ficar nos pavimentos superiores, fazendo a
    circulação vertical participar da missão em vez de ser só decoração.
  - A instrução do Structured Output explica a semântica de cada planta e dá
    o caso concreto de casa de dois andares. A segurança permanece igual: a IA
    não envia coordenadas, código, URL ou ferramentas; somente escolhe valores
    fechados e toda geometria nasce/é validada localmente. A suíte pura-Java
    soma 573 verificações, incluindo oito impressões geométricas distintas e o
    contrato completo da casa vertical. APK assinado v2/v3 com 325.057 bytes
    nesta build; falta somente a conferência visual/tátil no aparelho.
- **v0.19.0 (versionCode 38) — mapas adaptáveis, túneis reais e conversa natural.**
  - O gerador deixou de reutilizar uma única avenida: cidade, indústria,
    laboratório, fortaleza, ruínas e túnel têm plantas próprias, com variação
    determinística pelo `seed`. Túnel agora cria uma laje contínua sobre 100%
    do setor, antecâmaras, paredes de serviço, iluminação de teto e som de
    ventilação/gotas; pedidos de metrô, mina e esgoto escolhem esse tema.
  - A tela de geração oferece Automático, Econômico, Equilibrado, Grande e
    S23/forte. O automático considera RAM, heap e núcleos; o perfil forte gera
    quatro setores de 88×88 m. Portas fazem a transição e
    `LazyLevelProvider` lê/valida/compila somente o setor solicitado, sem
    manter toda a campanha em `RuntimeLevel[]`.
  - Áudio ganhou passos alternados, respingos, grilos e paisagens próprias de
    túnel/indústria. O TTS escolhe a voz pt-BR de maior qualidade instalada,
    favorecendo variantes naturais online. NPCs guardam três turnos só em RAM;
    a instrução da Responses API proíbe reapresentação e exige resposta direta
    e variada, salvo quando o jogador realmente pergunta a identidade.
  - `environment.soundscape` é uma extensão opcional e compatível do schema 2.
    A suíte pura-Java soma 560 verificações, incluindo 96 combinações de
    tema/tamanho/objetivo, teto integral e carregamento preguiçoso. APK assinado
    v2/v3 com 316.865 bytes nesta build; validação visual/sonora no S23 ainda
    deve ser feita no aparelho.
- **v0.18.0 (versionCode 37) — NPC com voz, microfone e companhia.**
  - Ao entrar em 3 m, cada NPC cumprimenta uma vez por voz pt-BR e começa a
    seguir o jogador no mesmo pavimento, desviando pelos colliders. O movimento
    é determinístico e local: não há chamada de IA por quadro.
  - `FALAR` agora oferece pergunta digitada ou pelo reconhecedor de voz do
    Android/Google. Após enviar, a partida continua sem tela de “pensando”; a
    resposta chega por TTS e legenda breve. Diálogo fica apenas como fallback.
  - Manifesto declara visibilidade dos serviços de TTS/reconhecimento, a voz é
    encerrada no ciclo de vida e nenhum áudio é gravado pelo jogo. Suíte com
    498 verificações; APK assinado v2/v3 com 304.571 bytes nesta build.
- **v0.17.3 (versionCode 36) — prevenção de chave mascarada no HTTP 401.**
  - A entrada agora recusa antes da rede `Bearer`, aspas, atribuições
    (`OPENAI_API_KEY=...`) e cópias mascaradas com `...` ou `***`. Uma chave
    mascarada copiada da lista pode estar em ALL e ainda ser inválida.
  - O cliente e o cofre Android compartilham a mesma normalização, preservando
    a chave completa byte a byte. O contrato ganhou três verificações. Suíte
    com 491 verificações.
- **v0.17.2 (versionCode 35) — diagnóstico preciso da chave de IA.**
  - A mensagem de rede agora separa autenticação recusada (`HTTP 401`) de
    acesso bloqueado (`HTTP 403`). No primeiro caso, orienta conferir a chave
    inteira, o Project e a permissão de escrita em `/v1/responses`; no segundo,
    região, organização e políticas do Project.
  - Respostas de autenticação não são exibidas, pois podem repetir um trecho
    mascarado da chave. O contrato ganhou verificações específicas para os
    dois estados. Suíte com 488 verificações.
- **v0.17.1 (versionCode 34) — correção do HTTP 400 na geração por IA.**
  - O JSON Schema remoto usava palavras-chave que podem ficar fora do
    subconjunto aceito por Structured Outputs estrito (`uniqueItems`, limites
    de texto/lista e faixas numéricas). A API recusava a requisição antes de
    executar o modelo. O schema enviado agora usa somente tipos, enumerações,
    campos obrigatórios, descrições e `additionalProperties:false`.
  - Os limites não foram removidos da segurança: `AiScenarioPlan.parse()`
    continua recusando textos, números, listas, repetições e campos extras fora
    do contrato. O erro HTTP 400 também passa a mostrar o detalhe seguro
    devolvido pela API. Suíte com 486 verificações.
- **v0.17.0 (versionCode 33) — cenários e pessoas com IA opcional segura.**
  - A biblioteca ganhou configuração de chave e geração de cenário. O cliente
    Java usa somente `POST https://api.openai.com/v1/responses`, com snapshot
    fixo `gpt-5.4-mini-2026-03-17`, HTTPS obrigatório, redirects bloqueados,
    `store:false`, resposta limitada, timeouts e no máximo 40 chamadas por
    sessão com intervalo mínimo. Nenhuma ferramenta é enviada ao modelo.
  - A IA não fornece coordenadas nem objetos arbitrários: preenche um JSON
    Schema estrito de tema/tamanho/céu/objetivo/recursos/inimigos/NPC. Um
    construtor determinístico monta somente estruturas e prefabs do catálogo;
    `MapValidator` e `LevelCompiler` precisam aceitar o resultado antes da
    prévia e do salvamento.
  - Novo `npc.human`: modelo humano low-poly, collider, sombra, botão `FALAR`
    por proximidade e campos editáveis de nome, papel, primeira fala e contexto.
    Sem chave ele usa o texto local; com chave conversa em até três frases, sem
    ferramenta e sem capacidade de modificar a partida. A engenheira Lia foi
    incluída em Cidade Aurora para o fluxo funcionar imediatamente sem chave.
  - Alpine foi deliberadamente descartado: não há shell, executável baixado,
    WebView, serviço em segundo plano ou interpretador. A chave fica só em
    memória por padrão; `Lembrar` cifra com AES-GCM e chave do Android Keystore.
    Backup e HTTP claro estão desativados, e a tela da chave bloqueia captura e
    autofill onde o Android oferece suporte.
  - `AiScenarioTest` cobre schema, allowlist, requisição sem ferramentas,
    extração limitada, geração, validação e compilação do NPC, além das 60
    combinações tema/tamanho/objetivo. Suíte com 485 verificações; APK assinado
    com 304.576 bytes nesta build.
- **v0.16.2 (versionCode 32) — biblioteca sem mapas pequenos.**
  - Removidos do APK `casa.json`, `patio.json` e `fortaleza.json`; o gerador
    também apaga arquivos antigos para que eles não reapareçam. Permanecem
    somente Complexo Ômega e Cidade Aurora como exemplos embarcados.
  - Cada mapa próprio agora mostra `EXCLUIR` diretamente na linha, mantendo
    a confirmação e a opção equivalente em `⋮`. Cópias já editadas nunca são
    removidas automaticamente. A suíte soma 396 verificações.
- **v0.16.1 (versionCode 31) — cidade 2,6× maior sem estourar orçamento.**
  - A Cidade Aurora passou de 56×48 m para 88×80 m: avenidas prolongadas,
    anel viário, oito volumes simples de skyline e quatro células novas nas
    extremidades. A missão agora recolhe 12 células em até 720 s.
  - Grade do editor ampliada de ±32 m para ±48 m (96×96 m). O mapa ficou com
    79 estruturas, 103 prefabs, 17 inimigos, 164 colliders e malha estática
    de 131.868 floats (~515 KiB), ainda abaixo dos avisos de 80 estruturas,
    200 peças e 24 inimigos.
  - São 32 postes + cinco luzes internas; o renderer continua calculando
    iluminação apenas para as quatro luzes mais próximas. `CityMapTest`
    cresceu para 49 contratos e a suíte soma 395 verificações.
- **v0.16.0 (versionCode 30) — Cidade Aurora, asfalto e postes.**
  - Novo exemplo `cidade-aurora.json`: quatro quarteirões e uma avenida em
    cruz com asfalto procedural, linhas viárias, quatro faixas de pedestres,
    garagem, mercado, estação alagada e prefeitura de dois andares.
  - Objetivo de recolher oito células em 540 s; a rota usa dois terminais em
    sequência, dois portões, cinco portas automáticas, 13 inimigos e obriga
    subir pela escada externa até o gabinete no primeiro andar.
  - Novo material `asphalt` no formato/editor/shader. Novo prefab procedural
    `prop.lamp.street`: base, poste, braço, foco emissivo, collider estreito e
    luz pontual a 3,35 m via `lightOffsetY`. Dezesseis postes e cinco luzes
    internas iluminam o mapa ao entardecer.
  - `CityMapTest` fixa 32 contratos da cidade; com os contratos do novo
    prefab e de sua inserção no editor, a suíte soma 378 verificações.
- **v0.15.0 (versionCode 29) — ANDARES editáveis sobre tetos.**
  - O painel ganhou `Andar ativo`: alterna entre elevações descobertas, aceita
    Y personalizado e abre um novo nível exatamente no topo do teto
    selecionado. A planta mostra só o pavimento ativo; a laje compartilhada
    aparece nos dois lados.
  - Piso, parede, bloco, teto por pontos/retângulo, prefab e início/saída
    nascem relativos à base do andar. Seleção, lista de objetos, encaixe,
    vãos, balde de pintura, duplicação e enquadramento não misturam objetos
    sobrepostos de pavimentos diferentes. A lista exibe o Y e troca de andar
    automaticamente ao abrir um objeto.
  - O runtime passou a considerar Y em saída, água/lava, interação com
    terminais e proximidade de portas automáticas; preserva os arrays legados
    para mapas no térreo. Ligação automática porta↔terminal também não cruza
    lajes.
  - `StoryLevels` concentra as regras puras sem mudar o schema. Novos testes
    cobrem laje compartilhada, ferramentas sobrepostas, altura de peças,
    vínculo de portas, perigos, escada até a laje e saída vertical; suíte com
    342 verificações.
- **v0.14.1 (versionCode 28) — mapa-vitrine Complexo Ômega.**
  - Novo exemplo grande `complexo-omega.json`: 34 estruturas, 96 prefabs,
    21 vãos, 18 inimigos, 13 luzes e todos os materiais/peças estáticas.
  - Objetivo de coletar nove núcleos em 600 s: hangar, água, reator,
    laboratório, telhado, plataformas, torre e cofre. Três terminais em
    sequência controlam três portões; há mais quatro portas automáticas.
  - Usa as quatro escadas/rampas, paredes diagonais com portais e pintura
    por lado, água/lava, chefe, torretas, kamikazes, ondas dormentes e toda
    a variedade de suprimentos. `ElaborateMapTest` mantém 16 contratos da
    vitrine; a suíte completa agora soma 312 verificações.
- **v0.14.0 (versionCode 27) — pacote de jogabilidade, editor e troca.**
  - Schema 2 com `objective`, materiais e travas; `MapMigration` mantém
    mapas schema 1. Autosave periódico e renomear na biblioteca.
  - Objetivos: saída, eliminar todos, coletar fichas e sobreviver, todos
    com tempo-limite opcional; recordes por mapa, precisão e 1–3 estrelas.
  - Runtime generalizado para listas de terminais/portas com
    `controllerId` e sequência `order`; porta automática deslizante com
    histerese. Novos inimigos: torreta, kamikaze e chefe; pickup de ficha
    e munição especial.
  - Editor: prévia 3D orbital `WHEN_DIRTY`, duplicação, seleção retangular
    para mover cômodo, travas, enquadramento/lista de objetos, RGB livre,
    objetivo/material/lógica editáveis e contador visível de limites.
    Regras foram extraídas para `editor/tools/{GroupSelection,PaintTool,
    OpeningTool,PrefabPlacementTool}`.
  - Render: até quatro luzes pontuais, tijolo/madeira/xadrez/metal/água/
    lava no fragment shader, sombras blob e vãos/pintura por lado também
    em paredes diagonais. Água reduz velocidade; lava causa dano.
  - Troca: JSON em `/sdcard/TermIa/troca` (fallback SAF), importar arquivo
    ou código `CJ2:`, compartilhamento e QR offline validado com ZXing.
    "Minha campanha" persiste playlist de mapas e soma o tempo.
  - Acabamento: tutorial de cinco balões, pássaros diurnos/vento noturno
    gerados em PCM e `scripts/test-device.sh` para smoke test real de
    GLES + gestos. `test-core.sh` cobre também objetivo, arma, ferramentas,
    diagonal, codec e QR.
- **v0.13.0 (versionCode 26) — PAREDES POR PONTOS com DIAGONAIS.**
  - "Desenho por pontos" ganhou 3º modo: "Paredes (linha de pontos)".
    Toque marca os pontos; tocar no ÚLTIMO termina a linha aberta,
    tocar no PRIMEIRO fecha o anel. Cada trecho vira parede: reta =
    bloco normal; DIAGONAL = laje poligonal em pé (KIND_POLY role
    wall, faixa de 0,3m de espessura ao longo do trecho).
  - Diagonal: visual liso (polyTriangles); colisão rasterizada em
    caixinhas de 0,25m (polyColliders com passo fino p/ role wall) —
    dá para deslizar encostado; serrilhado só na física.
  - "Piso + paredes" do contorno agora inclui as diagonais (o aviso
    de trecho pulado morreu).
  - Limitações da parede diagonal: sem vãos (wallAt ignora), pintura
    de cor única (sem lados), sem grude de ponta no desenho.
- **v0.12.1 (versionCode 25) — sofá, TV, espelho, janela de banheiro**
  e pintura de piso/teto.**
  - Peças novas (static): Sofá (assento/encosto/braços, collider 1
    bloco), TV de LED de parede (tela emissiva; fixa a 1,4m, sem
    collider), Espelho redondo de parede (círculo em 3 faixas, "vidro"
    azul-claro >1 — reflexo FAKE, não espelha; fixa a 1,5m, sem
    collider). Catálogo com 27 peças.
  - Vão novo: "Janela de banheiro" 0,6×0,6 peitoril 1,5 (preset
    window_bath do editor; SALVA como window normal no JSON).
  - PINTAR agora usa as BOLINHAS: tocar na bolinha pinta aquele
    piso/teto/bloco específico (mesma desambiguação da seleção; antes
    o teto era impintável — structureAt achava o piso primeiro).
- **v0.12.0 (versionCode 24) — MAPAS DE EXEMPLO na biblioteca.**
  - 3 exemplos GERADOS POR CÓDIGO (`ExampleMapsGenerator` em src/test,
    roda no test-core.sh: valida + compila ou quebra o build; IDs
    determinísticos) → `assets/maps/exemplos/*.json`:
    1. Casa com quintal (dia): casa mobiliada c/ porta+janela, teto,
       lâmpadas, drone rondando o quintal;
    2. Pátio noturno: caixas/barris/luminárias, 2 drones + mutante em
       patrulhas cruzadas, coberturas;
    3. Fortaleza do terminal (entardecer): terminal abre o portão do
       muro divisório, escada p/ plataforma com munição, drone
       dormente de guarda.
  - Biblioteca ganhou seção EXEMPLOS: miniatura + JOGAR + EDITAR CÓPIA
    (copia p/ os mapas do usuário com id novo e "(minha cópia)" e abre
    o editor). Originais são somente leitura (assets).
- **v0.11.1 (versionCode 23) — topo em 2 LINHAS FIXAS (sem rolagem).**
  - Linha 1: ← ↶ ↷ ☰ ▶. Linha 2: SELEC. PINTAR GIRAR MEDIDAS EXCLUIR.
    Peso 1 por botão = sempre cabem na largura; sem HorizontalScroll.
  - Medidas/Excluir saíram do painel ☰ (agora no topo; painel ficou:
    Piso, Parede, Teto, Bloco, Desenho por pontos…, Vão…, Peça…,
    Início, Saída, Rota do inimigo, Céu…). Painel desce p/ 104dp.
- **v0.11.0 (versionCode 22) — DESENHO POR PONTOS (contorno livre).**
  - Painel ☰ → "Desenho por pontos…" (Piso ou Teto): toque marca
    pontos (cota do trecho ao vivo; ↶ remove o último; trocar de
    ferramenta cancela); tocar no PRIMEIRO ponto (verde) fecha. Piso
    pergunta "Piso + paredes / Só o piso" — paredes automáticas nos
    trechos RETOS; diagonais ficam sem parede (aviso conta quantos).
  - Novo `kind: poly`: `polygon` (pares x,z ABSOLUTOS) + envolvente
    sincronizado em transform/half (`syncPolyBounds`) p/ seleção/chip/
    miniatura funcionarem sem mudança. Persistido/validado (3+ pontos,
    sem auto-cruzamento — `Triangulator.selfIntersects`, área mínima).
  - `geometry/Triangulator`: ear clipping puro-Java (CCW normalizado).
    `LevelCompiler.polyTriangles`: tampo+fundo triangulados + lados
    verticais (winding CCW visto de fora, culling ok);
    `polyColliders`: faixas de 0,5m rasterizadas (borda diagonal
    serrilhada SÓ na física, invisível).
  - SELECIONAR em laje poly: alças NOS VÉRTICES (puxar remodela, com
    grude em face/grade), arrastar pelo meio move o contorno inteiro,
    GIRAR rotaciona 90° em torno do centro.
  - `PolygonTest` (13 verificações; pegadinha: pontos de sondagem não
    podem cair na fronteira exata das faixas de 0,5m).
- **v0.10.0 (versionCode 21) — UI reorganizada + retrato no editor.**
  - Orientação POR MODO: biblioteca e Construir em RETRATO
    (setRequestedOrientation; manifest sem orientação fixa, e
    configChanges evita recriar a Activity); Testar/Jogar continua
    paisagem.
  - Topo enxuto: ← ↶ ↷ SELEC. PINTAR GIRAR ☰ ▶ (scroll horizontal se
    faltar largura). Barra de baixo REMOVIDA; status ocupa o rodapé.
  - Painel LATERAL recolhível (☰, estilo editor3d, 168dp, linhas de
    40dp): Piso, Parede, Teto, Bloco, Vão…, Peça…, Início, Saída,
    Rota do inimigo, Medidas, Excluir, Céu…. Fecha ao escolher.
  - refreshButtons continua valendo (refs preservadas nos dois lugares).
- **v0.9.6 (versionCode 20) — MINIATURAS na biblioteca (Fase 4).**
  - `ui/MapThumbnail.render(doc, w, h)`: Bitmap com vista de topo
    enquadrada no conteúdo — pisos, blocos/paredes, tetos translúcidos,
    peças como pontos coloridos, início verde/saída azul; FUNDO na cor
    do céu/neblina do mapa (mapa noturno fica escuro na lista).
  - Biblioteca: ImageView 84×56dp por linha (bitmap 2x p/ nitidez),
    gerada síncrona no refresh (mapas têm poucos KB); mapa corrompido
    vira retângulo cinza; tocar na miniatura abre o Construir.
- **v0.9.5 (versionCode 19) — BOLINHA DE SELEÇÃO por objeto.**
  - Problema: o 2D sobrepõe piso/teto/bloco e o toque não sabia qual
    selecionar. Agora todo piso/teto/bloco tem uma BOLINHA colorida
    (teto azul acima do centro, piso cinza abaixo, bloco laranja no
    centro — nunca coincidem) SEMPRE visível; tocar nela seleciona
    aquele objeto com prioridade máxima (`chipPos`/pick).
  - Etiqueta ganhou o NOME: "teto 3,00 × 5,00" (sempre na seleção;
    com zoom quando cabe). Arrastar pela bolinha move o objeto.
- **v0.9.4 (versionCode 18) — ALINHAMENTO DE LAJE + puxar arestas.**
  - Causa do desalinho: as FACES das paredes ficam fora da grade (centro
    na grade ± espessura 0,15) e o snap era só grade.
  - Desenhar PISO/TETO/BLOCO agora gruda cada eixo na face de parede
    mais próxima (laterais E pontas; raio 28px/escala, mín 0,24m);
    longe de faces, cai na grade (`faceOrGrid`).
  - SELECIONAR + estrutura selecionada: 4 ALÇAS brancas no meio das
    arestas; puxar move SÓ aquela aresta, grudando em faces/grade
    (`edgeAt`/`dragEdgeTo`, mínimo 0,1m para não inverter). Serve
    também para esticar comprimento/espessura de parede.
  - Mover (arrastar pelo meio) continua igual; undo compartilhado.
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
