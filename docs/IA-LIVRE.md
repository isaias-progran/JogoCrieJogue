# IA Livre — contrato atual e evolução protegida

Estado de referência: **v0.26.1, em 2026-07-17**.

Este documento descreve o modo Livre que existe no aplicativo. O roteiro de
melhorias e os portões de não regressão ficam em `../PLANO.md`; segurança da
integração, chave e conversa de NPC ficam em `IA-SEGURA.md`.

## Decisão de produto

A IA Livre é uma capacidade permanente do Construa & Jogue. Ela foi criada
para deixar o modelo desenhar de verdade, depois que o modo Guiado mostrou
limitações de densidade e variedade. O resultado foi validado no aparelho e
considerado melhor que os mapas pré-programados.

Melhorar o Livre significa conservar sua criatividade e aumentar a chance de o
mapa sair jogável. Não significa transformá-lo novamente numa coleção de
receitas.

## Diferença entre Guiado e Livre

### Guiado

- a IA devolve um JSON Schema estrito com tema, layout, rota, zonas, objetivo,
  recursos, inimigos e NPC;
- `AiScenarioBuilder` escolhe as coordenadas e monta o mapa com receitas
  locais;
- pode dividir pedidos grandes em setores carregados sob demanda;
- tende a ser mais previsível e usar menos saída.

### Livre

- o pedido do jogador é enviado junto das regras técnicas e do catálogo;
- a IA escreve um roteiro textual, um comando por linha, e escolhe as
  coordenadas de toda a arquitetura;
- não usa o JSON Schema de `AiScenarioPlan` e não passa pelas receitas de
  `AiScenarioBuilder`;
- gera um mapa único, com streaming e saída maior;
- o aplicativo interpreta, repara, valida, compila e mostra a prévia antes de
  permitir o salvamento.

O modo Guiado é uma alternativa. Ele não é fallback silencioso do Livre.

## Fluxo atual completo

1. O jogador escolhe Livre, modelo e descreve o mapa em até 1.000 caracteres.
2. O cliente envia `POST https://api.openai.com/v1/responses` com
   `stream:true`, `store:false`, sem `tools` e com um modelo da allowlist.
3. A resposta chega por SSE. Eventos `response.output_text.delta` alimentam o
   roteiro e atualizam a quantidade de caracteres e comandos na tela.
4. `AiFreeMapScript` lê cada linha e chama somente operações conhecidas de
   construção de `MapDocument`.
5. Linha desconhecida, peça inválida ou propriedade incompatível vira aviso;
   as linhas válidas continuam sendo aproveitadas.
6. Redes de segurança completam ou ajustam problemas simples.
7. `MapValidator` verifica o documento. Se houver erro conhecido e reparável,
   `salvage()` corrige e o validador roda novamente.
8. `LevelCompiler` compila o documento como prova final de que seus tipos e
   comportamentos são conhecidos.
9. A prévia mostra nome, modelo, contagens, aliados combatentes e separa
   correções automáticas de pontos de atenção. O jogador decide entre
   **SALVAR E EDITAR**, **MELHORAR COM IA** e **Descartar**.

Nada é salvo antes da confirmação.

## Melhorar um mapa com IA

A v0.26.1 oferece `MELHORAR COM IA` em três lugares: na prévia de uma geração,
no menu `⋮` de um mapa salvo e no painel `☰` do editor. Essa função não é um
autosave, não altera o documento aberto e não regenera mapas em segundo plano.

Fluxo da revisão:

1. o jogador descreve em até 1.000 caracteres somente o que deseja mudar e
   escolhe um modelo da mesma allowlist;
2. o app congela o mapa atual em JSON e informa que a nova chamada pode
   consumir créditos e envia também nomes e textos narrativos do mapa;
3. pedido e JSON entram no `input` como dados não confiáveis. As instruções
   exigem ignorar tentativas de comando dentro deles, preservar o que não foi
   pedido e devolver um **roteiro completo**, nunca patch ou diff;
4. a resposta chega pelo mesmo SSE e atravessa o mesmo `AiFreeMapScript`,
   `salvage()`, `MapValidator` e `LevelCompiler` da geração Livre;
5. a revisão aparece em outra prévia. Cancelar ou voltar recupera a prévia
   anterior quando ela ainda não havia sido salva;
6. `SALVAR CÓPIA E EDITAR` cria um ID novo e abre o editor. O arquivo e o ID do
   mapa original permanecem intocados.

O corpo do mapa não é truncado silenciosamente: revisões acima de 180 KiB são
recusadas antes da rede. Como o modelo precisa repetir tudo que deve continuar
existindo, mapas densos podem usar bastante entrada e saída. A função é uma
segunda chamada explícita, nunca uma tentativa automática escondida.

## Linguagem de comandos atual

O roteiro aceita comentários com `#` e ignora cercas Markdown. Os comandos
atuais são:

```text
nome <título>
ceu day|dusk|night|none
som outdoor|tunnel|industrial|auto
ambiente <0.05-1>
neblina <r> <g> <b> <alcance>
objetivo reach_exit [tempo]
objetivo collect <fichas> [tempo]
objetivo eliminate_all [tempo]
objetivo survive <segundos>

piso <x> <z> <hx> <hz> <material> <r> <g> <b> [ytopo]
teto <x> <z> <hx> <hz> <ybase> <material> <r> <g> <b>
parede <x1> <z1> <x2> <z2> <altura> <material> <r> <g> <b> [ybase]
vao porta|janela|portal [offset] [largura] [altura] [peitoril]
bloco <x> <ycentro> <z> <hx> <hy> <hz> <material> <r> <g> <b>

peca <id-do-catálogo> <x> <y> <z> [yaw]
prop <chave-numérica> <valor>
texto name|role|greeting|background <valor>
texto combate sim|nao
texto combatLine1|combatLine2|combatLine3 <fala curta>
patrulha <x> <z>

inicio <x> <z> [y] [yaw]
saida <x> <z> [y]
```

`vao`, `prop`, `texto` e `patrulha` atuam sobre a última parede ou peça
compatível. A peça continua decidindo, pelo catálogo, quais propriedades
aceita. Parede diagonal vira polígono e atualmente não aceita vão.

Materiais aceitos: `plain`, `brick`, `wood`, `checker`, `metal`, `water`,
`lava` e `asphalt`.

## Limites atuais

- coordenadas X/Z são presas à grade de -48 a +48 m; a instrução pede uso
  preferencial de -44 a +44 para deixar margem;
- altura é presa entre -6 e +30 m;
- cores são presas a 0..1 e medidas têm mínimos seguros;
- no máximo 500 estruturas e 400 prefabs entram pelo parser;
- o corpo de resposta aceito tem no máximo 256 KiB;
- o JSON de um mapa enviado para melhoria tem no máximo 180 KiB;
- o Livre pode reservar 16 mil, 20 mil, 10 mil ou 6 mil tokens de saída,
  conforme Terra, Sol, Luna ou mini;
- timeout de leitura: dez minutos;
- o mapa Livre atual é único; não usa a campanha de setores do Guiado.

Esses são limites de proteção, não metas de preenchimento. O validador pode
avisar antes deles quando o mapa excede os orçamentos recomendados do editor.

## Redes de segurança atuais

- cria início em `0 0` quando ele falta;
- cria saída no ponto estrutural mais distante quando o objetivo exige saída;
- move início/saída presos em blocos, prefabs, portas ou paredes poligonais
  para um ponto livre próximo;
- prende vãos às medidas reais da parede e recusa sobreposição;
- completa meias-medidas ausentes de porta automática e portão;
- liga portões sem controlador a um terminal; sem terminal, converte o portão
  em porta automática para preservar uma passagem utilizável;
- rejeita na própria linha propriedade ou patrulha não permitida pela peça;
- remove, no resgate, peça desconhecida, propriedade inválida, estrutura sem
  volume e vão impossível;
- ajusta objetivos incoerentes: coleta sem fichas, alvo maior que a quantidade
  existente, sobrevivência sem duração ou saída ausente.
- valida o início contra todos os colliders compilados e avisa quando uma busca
  horizontal conservadora não confirma caminho até a saída.

Todo conserto relevante aparece como aviso na prévia. O resgate não autoriza
salvar um mapa que continue falhando no validador ou no compilador.

## Segurança sem retirar liberdade

O roteiro é uma linguagem de dados fechada, não um script executável. Palavras
fora dos comandos não chamam métodos, não abrem URLs e não têm acesso a shell,
arquivos, Android, rede ou tools. IDs de peças e propriedades precisam existir
no catálogo local.

A liberdade está em **qual mapa desenhar com os elementos conhecidos**. A
barreira está em **como esse desenho entra no jogo**. Essa separação permite
que a IA escolha arquitetura e coordenadas sem receber autoridade de execução.

## Limitações conhecidas que o plano deve corrigir

- a busca de rota atual cobre somente saída no mesmo pavimento e gera aviso;
  ainda não comprova escadas, terminais, fichas e todos os interiores;
- o cancelamento desconecta o cliente imediatamente, mas ainda precisa de teste
  instrumental com uma chamada real e de uma política para roteiro parcial;
- uma geração longa pode consumir tempo e tokens antes de o servidor perceber
  a desconexão, mesmo se a qualidade final não for suficiente;
- prévia 3D antes de salvar e métricas locais de qualidade continuam futuras.

Essas limitações justificam validação, reparo e melhor cancelamento. Não
justificam voltar ao desenho por receitas.

## NPC combatente implementado

A IA Livre pode marcar o último `npc.human` como combatente ou pacífico pela
forma fechada `texto combate sim|nao`. O parser converte esse valor para o
booleano `combatant`; ele não é texto executável. Ausência da propriedade
significa pacífico, garantindo compatibilidade com mapas antigos.

`combatLine1`, `combatLine2` e `combatLine3` podem ser criadas durante a geração
do mapa. A IA não controla o combate durante a partida. Alcance, cadência,
escolha de alvo, raycast, dano, vida, desmaio, recuperação e retorno ao
seguimento são regras locais e determinísticas.

Base de balanceamento da v0.26.0:

- alcance de 14 m, dano 1 e intervalo de 1,20 s;
- inimigo vivo mais próximo, no mesmo pavimento e visível;
- paredes, portas, jogador e aliados bloqueiam o tiro;
- sem fogo amigo, munição especial, pickups ou bônus de abate;
- retorno ao seguimento após 1,5 s sem alvo visível;
- inimigos preferem o alvo mais próximo e dão mais atenção ao aliado que
  acabou de atirar;
- o aliado tem 60 de vida; ao zerar, desmaia e recupera metade após 8 s sem
  inimigo próximo, em vez de morrer definitivamente ou ser invencível;
- som, traçador e frase curta ocasional confirmam a ação. Falas geradas pela IA
  são gravadas no mapa e não exigem rede durante o combate.

O aliado não soma tiros/acertos às estatísticas do jogador. Abates ainda
avançam `eliminate_all` uma vez. Os números precisam de validação no aparelho;
o critério permanece: o aliado ajuda, mas não joga no lugar da pessoa.

## Testes de não regressão

Mudanças no Livre precisam manter:

- `AiFreeMapTest` e toda a suíte do núcleo verdes;
- pedido direto, ausência de JSON Schema e streaming no corpo da requisição;
- comandos existentes, avisos, resgate, validação e compilação;
- revisão como chamada explícita, roteiro completo, prévia e cópia com ID novo;
- mapas salvos intocados;
- comparação em aparelho com mapas novos e prompts representativos.

O histórico detalhado de cada falha real e respectiva rede de proteção fica no
`../DIARIO.md`.

## Referências externas do transporte

- Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create
- Streaming por SSE: https://developers.openai.com/api/docs/guides/streaming-responses
- Structured Outputs usado pelo Guiado: https://developers.openai.com/api/docs/guides/structured-outputs
- Família GPT-5.6: https://developers.openai.com/api/docs/guides/latest-model
