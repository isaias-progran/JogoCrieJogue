# Plano — Macros de construção e revisão barata

Decisão do usuário em **2026-07-17**: este plano SUBSTITUI o anterior.
O plano antigo (endurecimento da IA Livre) cumpriu o que precisava — o estado
alcançado está descrito em `DIARIO.md` (v0.26.x) e `docs/IA-LIVRE.md` — e foi
apagado por ordem explícita. O que sobrevive dele não é plano, é regra de
segurança, listada abaixo.

## 0. Inegociáveis (não são plano, não se apagam com ele)

1. Saída da IA NUNCA é executada como código/tool/URL: sempre dados que
   atravessam `AiFreeMapScript` → resgate → `MapValidator` → `LevelCompiler`.
2. A revisão devolve sempre um ROTEIRO COMPLETO que passa pelo funil inteiro;
   a ENTRADA pode mudar de formato (é o objetivo deste plano), a saída não.
3. Mapa salvo nunca é sobrescrito nem regenerado sozinho; revisão aprovada
   salva cópia com ID novo.
4. Cada iteração fecha com: `sh scripts/test-core.sh` verde, APK compilado,
   `DIARIO.md` atualizado, caixa `[x]` marcada aqui e commit+push.

## 1. Objetivo

Hoje uma casa de 10 cômodos custa ~30 linhas de roteiro e a revisão envia o
JSON inteiro do mapa (caro e infiel: o modelo traduz JSON→roteiro de cabeça e
"redesenha" o que devia preservar). O plano cria **macros**:

- a IA define uma construção uma vez (`definir casaPadrao … fim`) e carimba
  N vezes (`usar casaPadrao x z [rot] [tom]`) — 20 casas por ~20 linhas;
- o app aprende a escrever o mapa DE VOLTA em roteiro (serializador com prova
  de ida-e-volta) e a revisão passa a enviar roteiro compacto em vez de JSON;
- grupos repetidos são comprimidos automaticamente em `definir`/`usar`.

Resultado esperado: geração mais densa (interiores em toda casa) e revisão
várias vezes mais barata, sem tocar no modelo de segurança.

## 2. Fases (uma por execução da Automação; marcar `[x]` ao concluir)

### [ ] F1 — comandos `definir` e `usar` no parser

- `definir <nome>` abre um bloco; linhas seguintes são GRAVADAS (não
  executadas) até `fim`. `<nome>`: [a-zA-Z0-9_]{1,24}. Sem `definir` aninhado
  (linha `definir` dentro de bloco = aviso e o bloco atual continua).
- `usar <nome> <x> <z> [rot] [tom]` reexecuta as linhas gravadas com:
  - deslocamento: coordenadas do bloco são RELATIVAS à âncora `<x> <z>`;
  - `rot` só 0|90|180|270 (mundo é AABB; rotação livre quebraria colliders) —
    90/270 trocam x↔z e meias-medidas correspondentes;
  - `tom` opcional (`claro|escuro|<r> <g> <b>`) multiplica/troca as cores do
    bloco para variar vizinhos.
- Estado interno (última parede p/ `vao`, última peça p/ `texto|patrulha|prop`)
  fica CONFINADO à instância: salvar/restaurar os ponteiros ao entrar/sair do
  `usar`, para um `vao` do macro nunca recortar parede de fora.
- Limites APÓS expansão: os mesmos 500 estruturas/400 prefabs; `usar` que
  estourar o teto é interrompido com aviso ("macro-bomba"). `usar` de nome
  não definido = aviso. Macro vazio = aviso.
- Portões/terminais dentro do macro: a ordem sequencial da expansão preserva
  o pareamento 1:1 do `normalizeDoors` POR instância — cada casa liga sua
  porta ao seu terminal. Cobrir com teste.
- Testes (`AiFreeMapTest`): expansão simples com deslocamento; rotação 90;
  tom; vao/texto confinados; macro-bomba interrompida; aninhado vira aviso;
  nome desconhecido vira aviso; 2 `usar` com terminal+portão internos geram
  ligações independentes.
- Gate: suíte verde; roteiro sem macros continua byte-idêntico ao resultado
  atual (nenhuma mudança de comportamento sem `definir`).

### [ ] F2 — instrução do Livre ensina os macros

- `buildFreeMapRequest`: acrescentar a gramática `definir/usar` com exemplo
  curto (casa mínima) e a orientação: repetiu a mesma construção 3+ vezes →
  use macro; SEMPRE varie `rot`/`tom` entre vizinhos (a crítica histórica do
  aparelho é repetição visual).
- Só muda GERAÇÕES NOVAS; mapa salvo continua igual (regra de sempre).
- Testes: request contém a gramática nova; contrato de exemplo compila.
- Gate: suíte verde + geração real no aparelho quando possível (anotar no
  DIARIO modelo/pedido/resultado).

### [ ] F3 — serializador doc→roteiro com prova de ida-e-volta

- Novo `ai/MapScriptWriter` (pequeno, classe própria): percorre `MapDocument`
  e emite roteiro (`nome`, `ceu`, `som`, `ambiente`, `neblina`, `objetivo`,
  `piso/teto/parede/bloco`, `vao`, `peca`, `prop`, `texto`, `patrulha`,
  `inicio`, `saida`).
- ARMADILHA CENTRAL: o roteiro é SUBCONJUNTO do editor. Sem equivalente:
  pintura por face (`color2/color3`), travas (`locked`), formas poly
  desenhadas por pontos que `parede` diagonal não reproduz exatamente.
  Por isso a prova: `parse(write(doc))` e comparar com o original ignorando
  IDs (mesma ideia da igualdade arena JSON×texto). Diferente = mapa "não
  serializável" e quem chamar usa JSON. NUNCA enviar roteiro com perda.
- Testes: round-trip de mapa gerado pelo Livre (passa); mapa com pintura por
  face/trava (detecta perda e recusa); marcadores e propriedades de NPC
  (inclusive `combatant`/`combatLine*`) sobrevivem.
- Gate: suíte verde; writer não altera documento (função pura).

### [ ] F4 — revisão envia roteiro quando a prova passa

- `promptImproveMap`/`generateImprovedMap`: se `MapScriptWriter` provar
  ida-e-volta, o `input` leva "MAPA ATUAL EM ROTEIRO (dados)" no lugar do
  JSON; senão, JSON como hoje (fallback silencioso é PROIBIDO ser com perda —
  ver F3). Limite de 180 KiB continua valendo para o que for enviado.
- Instrução do modo revisão: "preserve copiando as linhas que não mudam".
- A tela de aviso continua dizendo que nomes/textos de NPC vão junto.
- Testes: request de revisão com roteiro; fallback JSON acionado por mapa
  não serializável; contrato anti-injeção (texto do mapa não entra nas
  instructions) mantido nos dois formatos.
- Gate: suíte verde; comparação real no aparelho (mesmo pedido, mapa médio:
  anotar no DIARIO os caracteres enviados antes×depois).

### [ ] F5 — compressão automática: grupos repetidos viram `definir`/`usar`

- No `MapScriptWriter`: detectar conjuntos de estruturas+peças repetidos
  (mesma geometria relativa, ancorada no centro; tolerância de cor p/ `tom`)
  e emitir um `definir` + N `usar`. Começar simples: assinatura canônica do
  grupo ordenada por tipo/offset; só comprimir 3+ repetições.
- A prova de ida-e-volta da F3 vale igual (expandir tem que devolver o doc).
- Testes: 5 casas idênticas viram 1 definir + 5 usar; 2 repetições NÃO
  comprimem; round-trip da saída comprimida.
- Gate: suíte verde; medir no DIARIO a redução em um mapa real.

### [ ] F6 (opcional) — modelos prontos embutidos

- Comando `casa <x> <z> [andares] [comodos] [material]` expandindo pelas
  receitas locais já testadas (`AiFocalRecipes` — casa 1-3 pavimentos com
  escada/laje/mobília validada pelo StairsTest).
- É COMPLEMENTO do desenho livre, nunca substituto: a instrução oferece, não
  obriga. Testes de expansão + validação.

### [ ] F7 — validação no aparelho (manual, com o usuário)

- Gerar: "vila com 20 casas parecidas mas não idênticas" (macros + variação),
  "cidade com interiores em todos os prédios" (densidade), revisão de mapa
  grande (custo antes×depois).
- Critério: menos tokens, mais interiores, nenhuma regressão de jogabilidade.
- Registrar tudo no DIARIO; falha recorrente de fase anterior reabre a caixa.

## 3. Como rodar pela Automação do TermIa

Tarefa 🤖 de IA (uma execução = uma fase), repetição a gosto do usuário:

- **Pedido**: `No projeto /host/home/apps/construa-jogue: leia DIARIO.md e
  PLANO.md. Execute a PRIMEIRA fase não marcada [ ] do PLANO.md, completa:
  implementação, testes, build do APK, DIARIO atualizado, caixa marcada [x]
  e commit+push. UMA fase por execução. F7 é manual: se for a próxima, pare
  e avise na resposta. Se todas estiverem marcadas, responda "plano
  concluído" sem alterar nada.`
- **🔎 Verificação**: `cd /host/home/apps/construa-jogue && sh
  scripts/test-core.sh >/dev/null 2>&1`
- Com o terminal aberto, `/loop` numa sessão claude faz o mesmo papel com
  cadência de minutos.

## 4. Armadilhas já conhecidas (ler antes de implementar)

- Macro-bomba: limites SEMPRE após a expansão; nunca confiar no tamanho do
  roteiro. Sem `definir` aninhado.
- Estado `última parede/peça` vaza para fora do `usar` se não for
  salvo/restaurado — vao/texto/patrulha no lugar errado.
- Rotação: só múltiplos de 90°; trocar halfX↔halfZ junto; `patrolX/patrolZ`
  e `lightOffsetY` das peças giram junto com a instância.
- `normalizeDoors` é 1:1 na ordem do documento (corrigido na v0.26.2) — a
  expansão sequencial preserva isso; teste com 2 instâncias.
- Serializador: perda silenciosa é o pior bug possível deste plano — a prova
  de ida-e-volta é obrigatória ANTES de qualquer envio de roteiro (F4/F5
  dependem de F3 passar).
- Instrução nova só afeta geração nova; comparação no aparelho exige gerar
  mapas novos na mesma versão (regra aprendida na v0.23.2).
- `settleEnemies`/`nudgeCompiled` rodam DEPOIS da expansão no `finish` — não
  duplicar redes dentro do `usar`.
