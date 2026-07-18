# PLANO-POLIMENTO-2026-07 — dívidas técnicas e limpeza de planos

Nome oficial: **Polimento 2026-07**. Executável pela Automação do TermIa,
uma fase por execução. Convive com o plano de macros (`PLANO.md`): tarefas
em horários diferentes; a única sobreposição de arquivo (AiFreeMapScript)
está protegida por pré-condição na P5.

## 1. Inegociáveis (valem para TODAS as fases)

- Fase completa = implementação + testes + build do APK
  (`sh /root/toolchain/build.sh /host/home/apps/construa-jogue`, conferir
  cópia em `/sdcard/TermIa/apks/construa-jogue.apk`) + `DIARIO.md`
  atualizado + caixa `[x]` marcada aqui + commit e push.
- `sh scripts/test-core.sh` verde SEMPRE (hoje 775 verificações). Fase de
  refactor puro (P1, P4, P5) = suíte IDÊNTICA, nenhum comportamento novo.
- Arquivo novo ≤ 400 linhas. Mapa salvo nunca é alterado. Contratos do
  `INICIAR.md` (arena bit a bit, IA Livre permanente etc.) intocados.
- Teto da Automação é 10 min: se o tempo apertar, commit parcial coeso com
  a caixa AINDA desmarcada — a próxima execução termina (o pedido manda).

## 2. Fases (uma por execução; marcar `[x]` ao concluir)

### [x] P1 — deduplicações internas (refactor puro)

- `AiFreeMapScript.java` tem DUAS implementações de `nudgeFree`
  (linhas ~567 e ~588), cada uma com a própria espiral de busca de espaço
  livre: unificar a espiral num único método/base comum.
- Tabela de limites de texto (nome/falas/combatLine1..3) duplicada entre o
  parser Livre e o `MapValidator`: extrair para uma classe única de
  constantes (ex.: `map/TextLimits.java`) usada pelos dois.
- Gate: suíte idêntica e verde; nenhum limite muda de valor.

### [x] P2 — botão Testar valida e compila UMA vez

- `EditorHost.test()` (linha ~902) roda `MapValidator.validate(doc,...)` e
  depois `listener.onTest(...)` compila o mapa de novo. Usar a sobrecarga
  de `validate` que aceita `RuntimeLevel` compilado (existe desde v0.26.2)
  e aproveitar a compilação única no caminho do Testar.
- Gate: suíte verde; erros/avisos exibidos continuam os mesmos; anotar no
  DIARIO que o toque no botão precisa de conferência manual no aparelho.

### [ ] P3 — linha de visada inimigo↔aliado 1x por par/quadro

- Pendência do DIARIO (v0.26.2): o raycast inimigo↔aliado roda 2x por
  quadro por par. Cachear o resultado por par dentro do mesmo quadro em
  `GameState` (mira do inimigo e escolha de alvo usam o mesmo cache).
- Gate: `GameplayRulesTest` inalterado ou estendido (mesmas decisões de
  alvo em cenário fixo); suíte verde.

### [ ] P4 — modularizar AiFeatureController (refactor puro)

- 1094 linhas hoje. Extrair blocos coesos (ex.: fluxo de geração, fluxo de
  revisão/melhoria, diálogo de progresso/prévia) para arquivos próprios em
  `ai/`, cada um ≤ 400 linhas, no padrão das extrações anteriores
  (EditorForms/EditorPickers/PlanRenderer: mover mecânico, qualificar).
- Gate: suíte idêntica e verde; compilação Android OK.

### [ ] P5 — modularizar AiFreeMapScript (refactor puro)

- PRÉ-CONDIÇÃO: F3, F4 e F5 do `PLANO.md` (macros) marcadas `[x]`. Se
  ainda não estiverem, PULE esta fase (não marque) e execute a próxima
  desmarcada, avisando na resposta — os dois planos editam este arquivo.
- 983 linhas hoje. Extrair o resgate (`salvage`, `nudgeFree` unificado,
  `settleEnemies`) para `ai/AiFreeRescue.java` (≤ 400 linhas).
- Gate: suíte idêntica e verde (`AiFreeMapTest` completo).

### [ ] P6 — limpeza: planos executados vão para o histórico

- Criar `docs/historico/` (se não existir). Para CADA arquivo `PLANO*.md`
  da raiz com TODAS as caixas marcadas `[x]`: mover para
  `docs/historico/<nome>-concluido-<data>.md` junto com o TSV de receita
  correspondente em `scripts/` (se houver).
- Plano com fase pendente — inclusive manual, como a F7 do PLANO.md — NÃO
  move; citar na resposta o que ficou e por quê.
- Atualizar `INICIAR.md` (apontar só para planos vivos) e `DIARIO.md`.
- Se nenhum plano estiver totalmente executado, apenas marcar esta caixa e
  relatar "nada a arquivar".

## 3. Como agendar na Automação do TermIa

Receita pronta em `scripts/automacao-polimento.tsv` (ids 11–13, horários
10h/16h/22h — não colidem com os 8h/14h/20h do plano de macros nem com a
tarefa de teste id 100). Anexar as linhas ao `/host/automation.tsv` e
**abrir o TermIa uma vez** para armar os alarmes (ou usar o ⚡ da tarefa).

- **Pedido** (o mesmo nas 3 tarefas): leia DIARIO.md e este arquivo,
  termine trabalho não commitado antes de abrir fase nova, execute a
  PRIMEIRA fase desmarcada, respeite pré-condições (pular quando mandado),
  uma fase por execução.
- **🔎 Verificação**: `cd /host/home/apps/construa-jogue && sh
  scripts/test-core.sh >/dev/null 2>&1`.
- Histórico da Automação em `/host/automation-log.txt`; respostas em
  `/sdcard/TermIa/ia/`.

## 4. Armadilhas

- Refactor puro que "aproveita para melhorar" um comportamento = regressão
  disfarçada. Mover mecânico; melhoria vira fase própria ou fica de fora.
- P2: validador tem DOIS caminhos (com e sem compilado). O do Testar deve
  continuar mostrando os mesmos erros específicos sem duplicar ruído de
  compilação (regra da v0.26.2, item 3).
- P3: o cache é POR QUADRO — segurar linha de visada entre quadros muda a
  mira e quebra o contrato "inimigo favorece proximidade e tiro recente".
- P6: mover plano vivo para o histórico quebra a Automação do outro plano
  (o prompt aponta para o arquivo). Só mover com TODAS as caixas `[x]`.
