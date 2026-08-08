# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une o motor
FPS do `jogo-fps` (jogar em primeira pessoa) com as ferramentas de
planta/construção do `editor3d` (desenhar o mapa). Ciclo: desenhar espaço →
posicionar prefabs → Testar → jogar → voltar. Planos em `PLANO.md`,
`ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`; contrato do gerador Livre em
`docs/IA-LIVRE.md`. Remoto: github.com/isaias-progran/JogoCrieJogue.

## Estado atual
- **v0.27.1 (versionCode 68)** — APK 366.018 bytes em
  `/sdcard/TermIa/apks/construa-jogue.apk`. Suíte verde: 778 verificações
  (`sh scripts/test-core.sh`).
- Plano vivo: **macros de construção** (`PLANO.md`, 7 fases). F1 (parser
  `definir`/`usar`) e F2 (instrução do Livre ensina macros) concluídas;
  F3–F7 pendentes (serializador doc→roteiro com ida-e-volta, revisão
  enviando roteiro compacto, validação manual F7 no aparelho com mapa NOVO).
- Plano **Polimento 2026-07** (`PLANO-POLIMENTO-2026-07.md`): P1–P4 e P6
  feitas; resta P5 (modularizar `AiFreeMapScript`, ~983 linhas) — só depois
  de F3–F5 dos macros (os dois planos editam o mesmo arquivo) — e as
  conferências manuais no aparelho.
- Pendências de aparelho acumuladas: botão Testar (validar→jogar→voltar),
  diálogos/seletores do editor pós-refactor, diálogos de IA (P4), mapa com
  2 terminais exigindo os dois, geração Livre nova (macros, inimigo sem
  "voar"), revisão/cancelamento/aliado combatente.
- Receita da Automação em `scripts/automacao-polimento.tsv` (ids 11–13,
  10h/16h/22h); falta o usuário anexar ao `/host/automation.tsv`.

## Decisões travadas
- Segurança da IA é inegociável: funil parser→resgate→validador→compilador
  sempre; saída da revisão é roteiro COMPLETO; mapa salvo jamais alterado
  por melhoria de instrução; modelos numa allowlist compilada; nenhuma
  tool enviada ao modelo; `store:false`, HTTPS, timeouts, 40 chamadas/sessão.
- Chave de IA só em memória por padrão; `Lembrar` cifra com AES-GCM +
  Android Keystore. Sem WebView, shell, executável baixado ou serviço.
- Falas de NPC ficam no modelo mini (barato); a escolha de modelo vale só
  para gerar cenário. Timeouts por rota: livre 10 min, guiado 5, NPC 2.
- Modo livre gera 1 mapa único (sem setores); linha inválida do roteiro
  vira AVISO na prévia, nunca erro fatal.
- Arquivos ≤ ~400 linhas: extrações no padrão "classe do mesmo pacote lendo
  o host" (EditorForms/EditorPickers/PlanRenderer, ai/AiScenarioFlow etc.).
- Com fase pendente, `PLANO*.md` NÃO vai para `docs/historico/` (regra P6).
- Sem `adb` aqui: prova local é suíte JVM + compilação; visual é do usuário.

## Armadilhas
- **Testes JVM sem Gradle/JUnit**: runner `main()` via `scripts/test-core.sh`
  (compila SÓ os pacotes puros) e parser JSON próprio — o `org.json` do
  android.jar é stub fora do Android.
- `util/Json` preserva o TOKEN do número (`Json.Num`, `Float.parseFloat`
  direto). NÃO trocar por double→float: arredondamento duplo quebra a
  igualdade bit a bit com os níveis legados.
- `assets/maps/arena.json` é GERADO (`LegacyTxtConverter` no src/test). Não
  editar à mão: o test-core.sh compara e falha.
- Pés exatamente sobre o topo de um bloco NÃO colidem (comparação estrita
  do Collision) — o validador de spawn respeita isso.
- `Shader/Mesh/Boxes` ficam em `engine/` de propósito (menos diff com o
  jogo-fps); reorganizar só se doer. Renderer do editor é `WHEN_DIRTY`, o do
  jogo é contínuo — um renderer não atende os dois (ARQUITETURA §7).
- Editor Android NÃO tem teste JVM: prova de refactor é compilação +
  conferência visual no aparelho.
- Colisão é AABB: rotação só em passos de 90° (`quarterTurns`/`rotateBox`,
  90° ⇒ (x,z)→(−z,x) e troca hx↔hz); porta girada 90/270 nasce com
  halfX↔halfZ trocados. Corte diagonal 45° de verdade não dá.
- Macros do Livre: estado de última parede/peça CONFINADO por `usar`
  (Cursor próprio); limites 500/400 valem APÓS a expansão ("macro-bomba"
  interrompe com aviso); o SINAL do offset do `vao` acompanha o eixo da
  parede original ao girar; `prop halfX` dentro do macro é renomeado.
- `AllySight` (visada inimigo↔aliado): cache POR QUADRO — `beginFrame` a
  cada quadro; o resultado nunca pode atravessar quadros.
- `normalizeDoors` é 1:1: cada portão sem `controllerId` liga a um terminal
  DISTINTO na ordem do roteiro (um terminal só já abriu o mapa inteiro).
- `sseDelta` precisa tratar `response.incomplete`: roteiro cortado no teto
  de tokens é ERRO claro, não sucesso parcial silencioso.
- Structured Outputs estrito recusa `uniqueItems`/limites de texto/faixas no
  schema (HTTP 400): enviar só tipos, enums, required, descrições e
  `additionalProperties:false`; limites ficam no `parse()` local.
- Chave mascarada (`...`/`***`), `Bearer`, aspas e `VAR=` são recusados
  antes da rede — chave copiada mascarada dá HTTP 401 confuso.
- Melhoria de receita/instrução só vale para GERAÇÕES NOVAS: mapa salvo
  nunca muda. Teste real = instalar → conferir a versão na biblioteca
  (visível sob o título) → gerar mapa NOVO.
- Mapas pintados na v0.6.0–0.9.2 usam semântica velha de cor: REPINTAR.
- Parede diagonal (poly): visual liso, colisão rasterizada em caixinhas —
  serrilhado só na física; pontos de sondagem de teste não podem cair na
  fronteira exata das faixas; sem vãos e pintura de cor única.
- Inimigo voador do Livre: `settleEnemies` prende o Y à faixa da tabela
  `PrefabPlacementTool.defaultY` (drone fora de [apoio+0,9, apoio+3,4] desce).
- `gate.acquire()` da melhoria só APÓS a validação local (recusa não queima
  vaga); `Cancellation.cancel()` desconecta em thread própria (nada de I/O
  de rede na thread principal).

## Próximos passos
1. F3–F5 dos macros: serializador doc→roteiro com prova de ida-e-volta e
   revisão enviando roteiro compacto (corta tokens da melhoria).
2. Depois delas: P5 do Polimento (modularizar `AiFreeMapScript`).
3. F6–F7 + conferências manuais no aparelho (lista em "Estado atual").
4. Usuário: anexar `scripts/automacao-polimento.tsv` ao `/host/automation.tsv`
   e abrir o TermIa para armar os alarmes.
