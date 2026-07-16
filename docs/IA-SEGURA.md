# IA pessoal e modelo de segurança

Esta integração foi desenhada para um jogo pessoal, instalado diretamente no
aparelho e sem servidor próprio. Ela gera um mapa completo antes da partida e
permite conversar com pessoas amigáveis durante o jogo. Não há geração
infinita por quadro: isso evita custo imprevisível, travamentos e mutação da
física enquanto o jogador anda.

## Por que não há Alpine

Alpine colocaria no APK um sistema Linux, shell, gerenciador de pacotes e uma
superfície de ataque que o jogo não precisa. O modelo também não roda dentro
do celular: GPT-5.4 mini é acessado pela API. Portanto, a opção menor e mais
segura é Java → HTTPS → endpoint fixo da OpenAI.

O APK não inclui shell, WebView, servidor HTTP, serviço em segundo plano,
carregamento de DEX, biblioteca nativa de IA, download de executável nem
permissão ampla de armazenamento. A única permissão nova é `INTERNET`.

## Fluxo permitido

1. O jogador escreve uma ideia de até 1.000 caracteres.
2. O app chama apenas `POST https://api.openai.com/v1/responses`, com
   `store:false` e sem campo `tools`. Para cenário, o jogador escolhe somente
   entre `gpt-5.6-terra`, `gpt-5.6-sol`, `gpt-5.6-luna` e
   `gpt-5.4-mini-2026-03-17`; conversa de NPC continua fixa no último.
3. Structured Outputs restringe a resposta a seis temas (incluindo túnel),
   quatro tamanhos, dez layouts, cinco rotas, cinco padrões de cômodos, quatro
   coberturas, contagens locais limitadas, até seis zonas tipadas, 16 recursos
   conhecidos, cinco famílias de inimigos e quatro textos do NPC.
4. `AiScenarioPlan` repete a validação no aparelho e recusa tipos, tamanhos,
   itens duplicados ou valores fora da lista.
5. `AiScenarioBuilder` decide todas as coordenadas e usa apenas APIs normais do
   mapa. Layout, rota, prédios, cômodos e pavimentos são intenções enumeradas,
   não geometria livre. O perfil de desempenho pode reduzir o setor ou dividir
   um pedido enorme em até quatro mapas ligados; nenhum dado arbitrário vira
   objeto.
6. `MapValidator` e `LevelCompiler` precisam aceitar cada setor. O jogador vê
   uma prévia textual; só então pode salvar. Campanhas setorizadas usam um
   provedor preguiçoso que compila apenas o setor atravessado.

Na conversa, o app envia apenas nome do mapa, nome/papel/contexto do NPC, a
pergunta e no máximo três turnos recentes mantidos em RAM. A instrução de
aplicação pede resposta direta, brasileira e cotidiana, permite poucas gírias
leves e proíbe reapresentar nome/biografia quando a identidade não foi
perguntada. Identidade, regras, exemplos e dados narrativos ficam separados no
prompt. A resposta é texto curto falado pelo
sintetizador do Android e mostrado brevemente como legenda; se a voz falhar,
aparece em diálogo. Ela não
é analisada como comando e não altera NPCs, mapa, arquivos ou controles. O
seguimento do companheiro é uma regra local de movimento e colisão, sem novas
chamadas à IA.

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
- no máximo 40 solicitações por abertura do app e intervalo mínimo de 2,5 s;
- mapa limitado pelos mesmos avisos/orçamentos do editor;
- texto de mapas importados é tratado como dado não confiável;
- `allowBackup=false` e nenhum componente de rede exportado.

Essas barreiras significam que prompt injection pode, no pior caso, produzir
um plano recusado ou um texto estranho. Ela não ganha uma ferramenta para
virar comando. Uma pessoa que modifique e reassine o APK estará criando outro
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
- Segurança de chaves: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safet
