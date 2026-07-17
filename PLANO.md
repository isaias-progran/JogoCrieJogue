# Plano de evolução — IA Livre sem regressão

Estado de referência: **v0.26.1, em 2026-07-17**.

## 1. Decisão de produto

A **IA Livre está aprovada como parte permanente do Construa & Jogue**. O
resultado atual foi validado no aparelho e considerado melhor que os mapas
pré-programados em criatividade e ocupação do espaço. A partir desta decisão,
o objetivo não é restringir ou substituir esse modo: é torná-lo mais confiável,
mais observável e mais fácil de corrigir sem perder sua liberdade.

O modo Guiado continua existindo como alternativa previsível e econômica. Ele
não é substituto automático do Livre e não deve impor suas receitas, limites de
layout ou JSON Schema ao desenho livre.

## 2. Base que deve ser preservada

No modo Livre atual:

1. o jogador descreve o cenário em até 1.000 caracteres e escolhe o modelo;
2. a IA recebe as regras técnicas, o catálogo atual e o pedido do jogador;
3. a resposta chega em streaming como um roteiro de comandos de mapa;
4. a IA escolhe coordenadas, arquitetura, materiais, peças, inimigos, itens,
   objetivo e ambientação, sem depender das receitas do modo Guiado;
5. `AiFreeMapScript` interpreta somente comandos conhecidos e produz um
   `MapDocument`; nunca executa a resposta como Java, shell ou ferramenta;
6. linha inválida vira aviso e o restante do mapa continua sendo aproveitado;
7. redes locais completam início/saída ausentes, ajustam posições presas,
   dimensões de portas, ligações de portão e problemas reparáveis;
8. `MapValidator` valida, o resgate tenta corrigir rejeições conhecidas, o
   validador roda novamente e `LevelCompiler` prova que o mapa compila;
9. o jogador vê contagens, avisos e consertos antes de escolher entre salvar
   ou descartar. Nada é salvo automaticamente.

O formato e os limites atuais estão documentados em `docs/IA-LIVRE.md`.

## 3. Contrato obrigatório de não regressão

Toda alteração futura na IA Livre deve manter estes comportamentos:

- o pedido do jogador continua chegando ao modelo sem ser convertido numa
  lista curta de temas ou layouts;
- a IA continua escolhendo coordenadas reais e preenchendo o mapa inteiro;
- os comandos existentes mantêm sua semântica, salvo migração explicitamente
  versionada e coberta por teste;
- o streaming continua mostrando que a geração está avançando;
- um erro localizado continua virando aviso ou reparo localizado, não perda
  automática da geração inteira;
- segurança é aplicada durante e depois da interpretação, sem trocar o desenho
  por uma receita genérica;
- o mapa só é salvo por escolha do jogador e mapas já salvos nunca são
  reescritos por uma atualização do gerador;
- o modo Guiado continua disponível, mas nunca substitui silenciosamente uma
  geração solicitada no Livre;
- nenhuma saída da IA ganha acesso a código, arquivos, URLs, tools ou APIs do
  jogo além da linguagem fechada de comandos;
- densidade, variedade visual, interiores acessíveis, progressão de combate e
  fidelidade ao pedido permanecem critérios de qualidade.

Se uma otimização só funciona reduzindo a criatividade, ela deve ser opcional
e comparada no aparelho; não pode virar o comportamento padrão sem evidência.

## 4. Portão para qualquer mudança

Antes de aceitar uma mudança no prompt, parser, resgate, validador, compilador
ou cliente de rede:

1. rodar `sh scripts/test-core.sh` sem regressão;
2. manter e ampliar `AiFreeMapTest`, cobrindo comandos, streaming, limites,
   avisos, resgate, validação e compilação;
3. gerar **mapas novos** no aparelho com a mesma versão e o mesmo modelo para
   comparar a base e a proposta;
4. testar pelo menos cidade grande, edifício vertical, subterrâneo, mapa de
   coleta e arena de sobrevivência;
5. jogar do início ao objetivo, entrando nos interiores e usando portas,
   escadas, itens, inimigos e saída;
6. registrar no `DIARIO.md` modelo, prompt, duração aproximada, consertos,
   falhas e percepção visual;
7. rejeitar a mudança se ela piorar de forma recorrente a fidelidade ao
   pedido, a ocupação do espaço, a variedade ou a jogabilidade.

Alterar somente a instrução afeta gerações novas. Um mapa salvo deve continuar
idêntico e editável.

## 5. Plano por fases

Progresso na v0.26.1:

- Fase 0 concluída;
- Fase 1 parcial: spawn usa todos os colliders compilados, o resgate move
  marcadores presos em peças e uma busca horizontal avisa sobre saída sem rota;
- Fase 2 com o núcleo concluído: Cancelar desconecta o HTTPS e o progresso usa
  estado seguro entre threads; roteiro parcial ainda não é oferecido;
- Fase 3 inicial: a segunda chamada opcional de melhoria recebe o mapa atual,
  preserva o original, usa o mesmo funil Livre e salva somente uma cópia;
- Fase 4 inicial implementada e coberta por testes do núcleo; falta validar o
  balanceamento e a troca de alvo em aparelho;
- Fase 5 parcial: a prévia separa correções de pontos de atenção, conta aliados
  combatentes e permite editar ou pedir outra melhoria sem salvar primeiro.

### Fase 0 — contrato e documentação

- descrever fielmente Guiado e Livre em documentos separados;
- registrar a IA Livre como decisão permanente de produto;
- estabelecer a matriz de teste e os critérios antirregressão;
- remover das orientações a ideia de que o Livre pode ser eliminado ao primeiro
  erro de modelo.

Portão: documentação, implementação e histórico descrevem o mesmo fluxo.

### Fase 1 — jogabilidade comprovada sem limitar a criação

- validar início e saída contra todos os colliders compilados, incluindo
  prefabs, portas e paredes poligonais;
- verificar rotas até saída, terminais, fichas e regiões obrigatórias;
- detectar cômodos fechados e escadas/pavimentos sem conexão;
- ampliar o resgate para mover, abrir ou completar somente o elemento
  problemático, preservando o restante do desenho;
- adicionar casos reais encontrados no aparelho como testes de regressão.

Portão: nenhum mapa é aceito com objetivo comprovadamente inalcançável; mapas
reparáveis continuam aproveitados e mantêm sua identidade visual.

### Fase 2 — cancelamento e recuperação de rede

- fazer o botão Cancelar fechar a conexão ativa, não apenas interromper a
  `Future`;
- tornar fase, contadores e estado da requisição seguros entre threads;
- impedir que uma geração cancelada ocupe a única fila por vários minutos;
- diferenciar timeout, cancelamento, recusa, limite e roteiro inválido;
- oferecer o roteiro parcial somente quando ele já formar um mapa validável e
  o jogador aceitar explicitamente os avisos.

Portão: cancelar e iniciar outra geração funciona imediatamente no aparelho,
sem travamento, salvamento parcial oculto ou mensagem enganosa.

### Fase 3 — análise de qualidade local

- medir ocupação por regiões do mapa, quantidade de interiores, diversidade de
  materiais, distribuição de inimigos/itens e progressão até o objetivo;
- mostrar essas informações na prévia, separadas de erros técnicos;
- corrigir localmente apenas fatos objetivos, como marcador preso ou porta
  ausente; não redesenhar bairros nem substituir a intenção da IA;
- manter a segunda chamada opcional implementada na v0.26.1 para reparar
  problemas específicos, sempre com mapa original preservado, custo adicional
  claro, roteiro completo validado e confirmação antes da cópia;
- criar um conjunto fixo de prompts de avaliação, sem transformar suas saídas
  em receitas rígidas.

Portão: a taxa de mapas jogáveis aumenta sem queda perceptível de variedade e
sem esconder consertos do usuário.

### Fase 4 — aliado combatente local

A IA pode decidir, durante a criação, se cada `npc.human` é **pacífico** ou
**combatente**. Essa escolha vira somente um dado booleano do mapa. A IA define
o papel e o estilo narrativo; mira, dano, movimento, vida e recuperação são
regras determinísticas do jogo e não fazem chamadas de rede durante a partida.

Contrato implementado na v0.26.0:

- nova forma Livre `texto combate sim|nao`, aplicada ao último `npc.human`,
  conforme a linguagem já usada para configurar a pessoa; o parser normaliza
  `sim|nao` para uma propriedade booleana/enum, nunca para texto executável;
- NPC antigo ou sem a propriedade continua pacífico, preservando todos os
  mapas existentes;
- alcance inicial de 14 m, tiro hitscan de dano 1 e intervalo de 1,20 s: mais
  lento que todas as armas atuais do jogador e sem munição especial, explosão
  ou prêmio de precisão;
- escolher o inimigo vivo mais próximo que esteja no mesmo pavimento, dentro do
  alcance e visível por raycast; paredes e portas bloqueiam o tiro;
- não atirar através do jogador, de outro aliado ou de geometria; sem dano
  amigo;
- interromper momentaneamente o seguimento para mirar e voltar a seguir quando
  não houver alvo visível por 1,5 s;
- inimigos podem escolher o aliado como alvo quando ele estiver mais próximo
  ou tiver acabado de atirar, sem abandonar definitivamente o jogador;
- aliado tem vida própria, mas não morre de vez: ao zerar, desmaia, deixa de
  lutar e de bloquear passagem; recupera-se depois do combate, com tempo claro
  e possibilidade futura de o jogador acelerar a recuperação;
- sons de tiro, traçador e fala curta dão feedback. As falas de combate ficam
  salvas no mapa: podem ser fixas ou geradas uma vez com o cenário, nunca por
  chamada de IA a cada disparo;
- aliado não soma tiros/acertos aos números do jogador, não consome pickups e
  não deve vencer sozinho uma arena enquanto o jogador permanece parado. A
  morte do inimigo ainda avança o objetivo uma única vez.

Antes de tornar os números definitivos, comparar no aparelho 14 m/1,20 s com
uma alternativa mais conservadora. Balancear por cadência, alcance e prioridade
de alvo, não por comportamento imprevisível.

Testes obrigatórios:

- pacífico nunca atira e mapas antigos permanecem iguais;
- combatente seleciona o inimigo visível mais próximo e respeita oclusão;
- limpa a área e volta a seguir;
- inimigos alternam o alvo sem esquecer o jogador;
- desmaio, recuperação, reinício e troca de setor restauram estado correto;
- falas são curtas, limitadas, ocasionais e não abrem diálogo durante combate;
- objetivo `eliminate_all` continua contando cada inimigo exatamente uma vez;
- desempenho é medido com múltiplos inimigos e mais de um NPC.

Portão: o aliado ajuda e cria tensão, mas o jogador continua sendo o agente
principal do combate.

### Fase 5 — experiência do jogador

- apresentar o Livre como modo criativo, e não como função descartável;
- mostrar avisos agrupados em “corrigido”, “atenção” e “bloqueio”;
- oferecer prévia 3D local antes de salvar;
- manter `SALVAR E EDITAR` e `MELHORAR COM IA`; acrescentar variação ou repetição
  do pedido sem redigitação somente com custo e efeito igualmente explícitos;
- exibir tempo e tamanho efetivos da geração quando esses dados estiverem
  disponíveis, sem inventar preço.

Portão: o jogador entende o que a IA criou, o que foi corrigido e por que um
mapa foi recusado antes de gastar tempo tentando jogá-lo.

### Fase 6 — escala e desempenho

- manter os limites duros contra esgotamento de memória e tornar os avisos de
  orçamento proporcionais ao aparelho;
- medir compilação, memória, quantidade de colliders e FPS dos mapas Livres;
- processar miniaturas e tarefas pesadas fora da thread de interface;
- otimizar lotes e consulta espacial antes de reduzir conteúdo;
- oferecer setores como opção explícita para pedidos enormes, preservando o
  mapa único atual para quem o escolher.

Portão: mapas densos mantêm a meta de desempenho definida para o aparelho sem
redução silenciosa do pedido.

### Fase 7 — manutenção

- separar transporte/streaming, coordenação Android, análise e reparo;
- documentar e versionar a linguagem de comandos;
- manter o catálogo como fonte única das peças e propriedades permitidas;
- reduzir classes grandes em mudanças mecânicas, sempre com a suíte intacta e
  validação visual quando envolver UI.

Portão: adicionar um comando ou uma peça exige uma alteração central e testes,
sem regras duplicadas divergindo entre prompt, parser e validador.

## 6. Próxima ordem recomendada

1. testar a v0.26.1 no aparelho: melhorar um mapa real, voltar, salvar a cópia
   e comprovar que o original permaneceu idêntico;
2. validar cancelamento real e jogar com aliado em vários combates;
3. estender alcançabilidade a escadas, terminais, fichas e regiões obrigatórias;
4. medir balanceamento com vários inimigos/NPCs e ajustar somente números;
5. acrescentar análise local, prévia 3D, variações e desempenho sem reduzir o
   desenho Livre.

## 7. Mudanças explicitamente fora do plano

- transformar o Livre em outro formulário do modo Guiado;
- obrigar a saída Livre a obedecer ao JSON Schema de `AiScenarioPlan`;
- substituir coordenadas da IA por receitas centrais;
- apagar o modo porque uma geração específica falhou;
- reduzir mapas antigos ou regenerá-los automaticamente;
- executar scripts, tools, URLs ou código retornado pelo modelo;
- deixar a IA controlar mira, dano ou decisões por chamadas durante o combate;
- tornar NPC antigo combatente por padrão;
- adicionar novas funções antes de fechar os problemas de validação e
  cancelamento já conhecidos.
