# Construa & Jogue

App Android offline em Java/OpenGL ES 3.0: desenhe um mapa em planta, coloque
móveis, inimigos e lógica, veja a prévia 3D e teste imediatamente em primeira
pessoa.

Principais recursos atuais:

- editor 2D com andares sobre lajes/tetos, undo/redo, autosave, contornos
  diagonais, vãos, pintura por lado, materiais, seleção retangular,
  duplicação, travas e prévia orbital;
- objetivos de saída, combate, coleta e sobrevivência, com tempo-limite,
  recordes e estrelas;
- múltiplos terminais/portas, porta automática, seis comportamentos de
  inimigo/item, água/lava e munição especial;
- iluminação pontual, materiais procedurais, sombras blob, céus, passos e
  ambientes sonoros procedurais externos, industriais e subterrâneos;
- IA pessoal opcional para gerar cenários completos no modo Guiado ou Livre,
  melhorar um mapa existente sem sobrescrever o original, conversar por voz
  com NPCs e criar aliados combatentes; a memória de conversa fica apenas em
  RAM e todo mapa é validado/compilado localmente antes de salvar;
- importação/exportação JSON, código `CJ2:`, QR e campanhas do usuário.

Entre os exemplos embarcados está **Complexo Ômega — nove núcleos**, uma
arena grande que reúne sequência de terminais, sete portas, exploração
vertical, todos os inimigos e peças, iluminação colorida, água e lava.
Também há **Cidade Aurora — apagão**, uma área de 88×80 m com núcleo urbano,
anel viário, bairros externos, 32 postes, garagem, mercado, estação alagada
e uma prefeitura cujo primeiro andar faz parte obrigatória da missão. A
engenheira Lia fica perto do início para demonstrar a conversa mesmo sem IA.
Esses são os dois únicos exemplos embarcados; os antigos exemplos pequenos
foram removidos. Mapas próprios ou cópias de teste têm botão `EXCLUIR`
visível na biblioteca, com confirmação antes de apagar.

## Construir vários andares

No editor, desenhe o teto do pavimento atual e selecione-o. Abra `☰` →
`Andar ativo` → `Novo andar sobre o teto selecionado`. A planta passa a
mostrar somente esse pavimento; paredes, blocos, móveis, inimigos, início e
saída novos recebem automaticamente a elevação correta. O topo do teto já é
a laje do novo andar, portanto não é necessário sobrepor outro piso.

Use `stairs.floor` ou `ramp.floor` para vencer os 3 m de altura. Deixe uma
abertura entre placas de teto para uma escada interna, ou coloque a circulação
por fora. O seletor `Andar ativo` permite voltar ao térreo e aos demais níveis.
A planta aceita construções entre -48 e +48 m em X/Z, uma área útil de
96×96 m; ampliar apenas o terreno é barato, mas inimigos e colliders devem
respeitar os avisos de desempenho do rodapé.

## IA pessoal, sem Alpine

Na biblioteca, `CONFIGURAR IA` recebe uma chave pessoal e `GERAR CENÁRIO COM
IA` oferece duas formas de criação:

- **Guiado:** a IA escolhe tema, objetivo, NPC, layout, rota, divisões,
  cobertura, prédios, cômodos, andares e zonas num JSON Schema estrito; o jogo
  decide as coordenadas com receitas locais.
- **Livre:** a IA desenha arquitetura, materiais, peças, inimigos, itens e
  marcadores com coordenadas próprias numa linguagem fechada de comandos. O
  resultado chega por streaming, linhas inválidas viram avisos e problemas
  conhecidos passam por resgate antes da validação final.

`MELHORAR COM IA` está disponível na prévia de um mapa gerado, no menu `⋮` de
cada mapa da biblioteca e em `☰` dentro do editor. O jogador descreve somente
o que deseja mudar e escolhe o modelo. A nova chamada recebe o JSON completo do
mapa como dado não confiável e deve devolver o roteiro completo revisado,
preservando tudo que não foi pedido. O resultado passa pelo mesmo parser,
resgate, validador e compilador do Livre e volta a uma prévia. Nada é
sobrescrito: somente `SALVAR CÓPIA E EDITAR` cria outro mapa; cancelar conserva
o original. Como há uma nova chamada e todo o mapa, inclusive textos de NPC, é
enviado à API, a tela avisa sobre créditos e privacidade antes de gerar.

No Guiado, “casa de dois andares” produz uma casa percorrível com laje vazada,
escada, cômodos e missão no piso superior. No Livre, o pedido não é reduzido a
uma lista de plantas: a IA preenche o mapa e o aplicativo conserva somente os
comandos que consegue interpretar com segurança. Perto de uma `Pessoa
amigável`, ela
cumprimenta por TTS, passa a seguir o jogador e o botão `FALAR` oferece teclado
ou microfone. A resposta chega por voz sem tela modal de espera; sem chave, a
fala estática do mapa continua funcionando. No modo Livre, a IA também pode
marcar a pessoa como combatente e gravar três falas curtas. Mira, dano,
cadência, desmaio e recuperação são regras locais, sem rede durante a partida.

Cada geração permite escolher **GPT-5.6 Terra** (recomendado), **GPT-5.6 Sol**
(maior qualidade, latência/custo potencialmente maiores), **GPT-5.6 Luna**
(econômico) ou o **GPT-5.4 mini** antigo para compatibilidade. A escolha afeta
somente o arquiteto do mapa; conversas curtas dos NPCs permanecem no mini.

O modo Guiado oferece perfis Automático, Econômico, Equilibrado, Grande e
S23/forte. O último produz quatro setores de 88×88 m ligados por portas; ao
atravessar, o setor seguinte é lido e compilado e o anterior deixa de ficar
ativo. Isso cria uma cidade extensa sem somar toda a geometria na RAM/GPU.
Pedidos de túnel/metrô/mina usam um tema subterrâneo com teto contínuo em toda
a área. O tamanho automático considera RAM, heap do app e núcleos, mas o
perfil manual continua disponível para reduzir ou ampliar no próprio aparelho.

O sintetizador escolhe a melhor voz pt-BR anunciada pelo mecanismo Android,
favorecendo versões naturais online. O resultado depende das vozes instaladas
no sistema. Mesmo quando há apenas uma, o jogo varia levemente ritmo e tom de
acordo com seis personalidades estáveis. As respostas usam português
brasileiro cotidiano, contrações e gírias leves sem forçar; a conversa guarda
apenas três pares de pergunta/resposta durante a sessão para continuar o
assunto sem o NPC se apresentar de novo.

O app não instala Alpine nem qualquer shell. Cada pedido faz uma conexão HTTPS
com a Responses API, sem ferramentas, downloads ou execução dinâmica. No
Guiado, a saída usa JSON Schema estrito; no Livre, um parser fechado aceita
somente comandos, peças e propriedades conhecidas. Nos dois casos o mapa passa
pelo validador e pelo compilador antes de ser oferecido ao editor. A chave fica
só na memória por padrão; persistência é opcional e usa Android Keystore.

A IA Livre foi validada no aparelho e é uma capacidade protegida do produto:
melhorias não podem convertê-la silenciosamente em Guiado nem reduzir mapas já
salvos. A v0.26.1 acrescenta a revisão opcional com IA, sempre como nova cópia;
a base v0.26.0 mantém cancelamento real da conexão, colisão compilada no spawn,
aviso de rota horizontal, prévia agrupada e NPCs pacíficos ou combatentes.
O combate do aliado é local e determinístico; a IA define somente a escolha
narrativa, personalidade e falas gravadas no mapa.

Uma chave dentro de qualquer app móvel não tem a mesma proteção de um backend.
Como este é um app pessoal sem servidor, use uma chave exclusiva e restrita,
com limite baixo, e não marque `Lembrar` se não for necessário. Detalhes e
ameaças restantes: `docs/IA-SEGURA.md`.

## Construir e testar

```sh
sh scripts/test-core.sh
sh /root/toolchain/build.sh /host/home/apps/construa-jogue
```

O APK sai em `build/construa-jogue.apk`. Com `adb` e um aparelho conectado:

```sh
ADB=/caminho/adb sh scripts/test-device.sh
```

O smoke test instala o APK, abre GLES, injeta gestos e falha em crash/erro GL.

Formato e catálogo: `docs/FORMATO-MAPA.md` e `docs/PREFABS.md`. Contrato da IA
Livre: `docs/IA-LIVRE.md`. Segurança da IA: `docs/IA-SEGURA.md`. Histórico e
decisões: `DIARIO.md`, `ARQUITETURA.md` e `PLANO.md`.
