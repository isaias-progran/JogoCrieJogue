# IA pessoal e modelo de segurança

Esta integração foi desenhada para um jogo pessoal, instalado diretamente no
aparelho e sem servidor próprio. Ela oferece geração **Guiada** por plano
estruturado e geração **Livre** por uma linguagem fechada de comandos, além de
conversa com pessoas amigáveis durante o jogo. Todo mapa é gerado antes da
partida. Na criação, a IA Livre pode marcar um NPC como combatente e escrever
falas curtas, mas não há geração por quadro nem decisão de combate pela rede.
Isso evita custo imprevisível e mutação da física enquanto o jogador anda.

## Por que não há Alpine

Alpine colocaria no APK um sistema Linux, shell, gerenciador de pacotes e uma
superfície de ataque que o jogo não precisa. O modelo também não roda dentro
do celular: GPT-5.4 mini é acessado pela API. Portanto, a opção menor e mais
segura é Java → HTTPS → endpoint fixo da OpenAI.

O APK não inclui shell, WebView, servidor HTTP, serviço em segundo plano,
carregamento de DEX, biblioteca nativa de IA, download de executável nem
permissão ampla de armazenamento. A única permissão nova é `INTERNET`.

## Fluxo comum permitido

1. Na geração, o jogador escreve uma ideia de até 1.000 caracteres. Na revisão
   opcional, escreve a mudança e confirma o envio do mapa atual completo.
2. O app chama apenas `POST https://api.openai.com/v1/responses`, com
   `store:false` e sem campo `tools`. Para cenário, o jogador escolhe somente
   entre `gpt-5.6-terra`, `gpt-5.6-sol`, `gpt-5.6-luna` e
   `gpt-5.4-mini-2026-03-17`; conversa de NPC continua fixa no último.
3. A saída sempre vira dados locais de `MapDocument`. Ela não é executada como
   Java, shell, URL, tool, nome de classe ou chamada Android.
4. `MapValidator` e `LevelCompiler` precisam aceitar o resultado. O jogador vê
   uma prévia e decide se quer salvar; nenhum dos dois modos salva sozinho.

### Modo Guiado

Structured Outputs restringe a resposta a temas, tamanhos, layouts, rotas,
padrões de cômodos, coberturas, zonas, recursos, inimigos e textos conhecidos.
`AiScenarioPlan` repete a validação no aparelho e `AiScenarioBuilder` decide as
coordenadas usando receitas locais. O perfil de desempenho pode dividir um
pedido enorme em até quatro mapas ligados; o provedor preguiçoso mantém apenas
o setor atravessado ativo.

### Modo Livre

O Livre deliberadamente **não usa o JSON Schema do Guiado**. A IA escolhe
coordenadas e escreve, por streaming SSE, um roteiro com comandos conhecidos de
arquitetura, ambiente, peças, lógica e marcadores. `AiFreeMapScript` interpreta
linha por linha; texto desconhecido vira aviso, não execução.

Coordenadas, medidas e cores são limitadas; peças e propriedades precisam
existir no catálogo; o parser aceita no máximo 500 estruturas e 400 prefabs.
Redes locais completam início/saída, ajustam posições e vãos e ligam
portão/terminal. Se o primeiro `MapValidator` recusar um problema conhecido,
`salvage()` remove ou corrige apenas o dado inválido, registra o conserto e
valida novamente. Persistindo um erro, ou falhando a compilação, o mapa não é
oferecido para salvar.

O botão Cancelar usa um token local que desconecta a `HttpsURLConnection`
ativa, além de interromper a tarefa. Contadores de streaming e fase usam estado
atômico entre a thread de rede e a interface; cancelamento nunca salva roteiro
parcial silenciosamente.

Assim, a IA tem liberdade para decidir **o desenho**, mas não autoridade para
decidir **como executar dados no aparelho**. Comandos, limites e problemas já
conhecidos estão descritos em `IA-LIVRE.md`.

### Melhorar com IA

Esta ação é deliberadamente explícita. O app envia o JSON completo do mapa
atual, inclusive nome, identidade, saudação, contexto e falas de NPC que
estiverem gravados nele. A tela informa esse escopo e o possível consumo de
créditos antes da chamada. Nenhum arquivo externo, chave, histórico de conversa
em RAM ou dado do aparelho acompanha o mapa.

O pedido de melhoria e o JSON ficam no `input`, rotulados como dados não
confiáveis, separados das instruções da aplicação. A resposta precisa repetir
um roteiro completo, atravessa o mesmo parser fechado e os mesmos limites do
Livre e é descartada se continuar inválida. O original nunca é sobrescrito:
somente a confirmação `SALVAR CÓPIA E EDITAR` grava um documento com ID novo.
Cancelar, falhar ou recusar a prévia não muda o mapa salvo.

Na conversa, o app envia apenas nome do mapa, nome/papel/contexto do NPC, a
pergunta e no máximo três turnos recentes mantidos em RAM. A instrução de
aplicação pede resposta direta, brasileira e cotidiana, permite poucas gírias
leves e proíbe reapresentar nome/biografia quando a identidade não foi
perguntada. Identidade, regras, exemplos e dados narrativos ficam separados no
prompt. A resposta é texto curto falado pelo
sintetizador do Android e mostrado brevemente como legenda; se a voz falhar,
aparece em diálogo. Ela não
é analisada como comando e não altera NPCs, mapa, arquivos ou controles.
Seguimento, mira, alvo, dano, desmaio e recuperação do companheiro são regras
locais e determinísticas, sem novas chamadas à IA.

O sintetizador seleciona a voz pt-BR de maior qualidade que o mecanismo
instalado anuncia e favorece uma variante natural online; se ela não existir,
usa a melhor alternativa local. Se houver uma só voz, seis perfis locais e
determinísticos ainda variam levemente ritmo e tom conforme papel, contexto e
identidade do NPC. Essa variação também vale para a saudação e não faz chamada
de rede adicional. O botão de microfone abre o reconhecedor de
fala instalado no Android (em geral o Google). O jogo recebe somente o texto
reconhecido: não pede `RECORD_AUDIO`,
não captura PCM e não salva áudio. O sintetizador/reconhecedor escolhido pelo
aparelho pode usar a rede conforme a voz e as configurações do usuário.

## Chave API

A chave nunca fica no código, assets, logs ou mapas exportados. O campo usa
modo de senha, bloqueio de captura de tela e desativa aprendizado do teclado e
autofill quando a versão do Android permite.

Por padrão, a chave fica apenas na memória do processo e some ao fechar o app.
Se `Lembrar neste aparelho` for marcado, o texto é cifrado com AES-GCM; a chave
de cifra é não exportável e criada no Android Keystore. O backup do aplicativo
está desativado.

Para este uso pessoal:

1. Crie um Project separado somente para o jogo.
2. Crie uma chave exclusiva e com permissão restrita ao necessário para
   Responses; não reutilize a chave principal da conta.
3. Configure limite/alerta de gasto baixo e acompanhe a página de uso.
4. Prefira não marcar `Lembrar`. Apague e rotacione a chave se o aparelho for
   perdido, tiver root ou mostrar consumo inesperado.

A recomendação oficial para software móvel continua sendo manter a chave em um
backend. Android Keystore protege o valor em repouso, mas não torna um bearer
token impossível de extrair enquanto um aparelho comprometido executa o app.
Sem servidor, esta é uma redução de risco consciente, não uma garantia absoluta.

## Barreiras contra uso indevido

- HTTPS obrigatório e tráfego claro desativado no manifesto;
- hostname e caminho fixos, validação TLS do Android e redirects bloqueados;
- modelos em allowlist compilada, sem ID livre vindo do mapa ou do texto;
- nenhum tool, function call, shell, URL ou código vindo do modelo é executado;
- corpo de entrada limitado, saída máxima de 256 KiB e timeouts de conexão;
- revisão recusa mapas acima de 180 KiB sem truncar ou enviar parcialmente;
- no máximo 40 solicitações por abertura do app e intervalo mínimo de 2,5 s;
- mapa sujeito aos avisos/orçamentos do editor e aos limites duros do parser
  Livre;
- texto de mapas importados é tratado como dado não confiável;
- `allowBackup=false` e nenhum componente de rede exportado.

Essas barreiras significam que uma instrução hostil pode, no pior caso,
produzir um plano recusado, um roteiro com comandos permitidos ou texto que o
parser ignora. Ela não ganha uma ferramenta para virar código ou acessar o
sistema. Uma pessoa que modifique e reassine o APK estará criando outro
programa; nenhum app pode impedir que alguém com controle total do aparelho
instale software diferente.

Referências oficiais:

- Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create
- Structured Outputs: https://developers.openai.com/api/docs/guides/structured-outputs
- Família GPT-5.6: https://developers.openai.com/api/docs/guides/latest-model
- GPT-5.6 Sol: https://developers.openai.com/api/docs/models/gpt-5.6-sol
- GPT-5.6 Terra: https://developers.openai.com/api/docs/models/gpt-5.6-terra
- GPT-5.6 Luna: https://developers.openai.com/api/docs/models/gpt-5.6-luna
- GPT-5.4 mini: https://developers.openai.com/api/docs/models/gpt-5.4-mini
- Segurança de chaves: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety
