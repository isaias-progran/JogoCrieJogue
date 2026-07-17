# Arquitetura

## 1. Três representações

1. `MapDocument`: fonte editável e persistente.
2. `EditorScene`: seleção, ferramentas e histórico temporário.
3. `RuntimeLevel`: resultado compilado e otimizado para jogar.

Fluxo:

`MapDocument → MapValidator → LevelCompiler → RuntimeLevel → GameState`

Ao sair do teste, o runtime é descartado e o documento permanece intacto.

## 2. Modelo do mapa

`MapDocument` contém:

- metadados e ambiente;
- `StructureObject`: piso, parede, teto e abertura desenhados;
- `PrefabInstance`: objeto pronto escolhido no catálogo;
- `LogicMarker`: início, saída e ponto de patrulha invisíveis.

`PrefabInstance` salva id, `prefabId`, transform, variante e apenas propriedades
permitidas. Não duplica malha, collider ou comportamento no mapa.

Unidades: metros, Y para cima, posição Y na base, yaw em Y e planta em X/Z.

## 3. Catálogo

`PrefabCatalog` carrega `assets/prefabs/catalog.json`. Cada definição informa:

- id/nome/categoria/ícone;
- gerador ou malha visual;
- collider e ponto de apoio;
- dimensões e limites de escala;
- rotações permitidas;
- encaixe em piso ou parede;
- comportamento opcional;
- propriedades editáveis.

Os prefabs iniciais serão low-poly procedurais. Escadas e móveis combinam
caixas; drones, mutantes, terminal e itens reaproveitam `GameMeshes` do FPS.

## 4. Por que o `Level` atual precisa evoluir

O FPS converte sólidos para AABBs, suficiente para mapas escritos à mão. O
editor aceita paredes diagonais, curvas segmentadas, contornos e IDs. O formato
`.txt` continuará temporariamente via `LegacyLevelLoader`; novos mapas usarão
JSON e serão compilados antes da partida.

## 5. LevelCompiler

Recebe snapshot validado e produz:

- lotes estáticos posição+normal+cor;
- malhas dinâmicas separadas para portas;
- instâncias de prefabs;
- `CollisionWorld`;
- spawn, saída, objetivos, inimigos, itens e patrulhas;
- ambiente, névoa e índice por UUID.

## 6. Colisão

Preservar AABB para móveis, caixas, degraus, escadas e plataformas. Acrescentar:

- `WallSegmentCollider` para paredes diagonais/curvas;
- `FloorRegion` para contornos de piso;
- collider dinâmico de porta;
- raycast contra AABBs, segmentos, portas e inimigos.

O jogador continua como cápsula/círculo no plano. Parede usa ponto mais próximo
do segmento expandido pelo raio, evitando AABB exagerada em diagonais.

## 7. Renderização e modos

Manter do FPS o renderer contínuo, shader, neblina, VBO, arma e HUD. Adaptar do
editor extrusão, paredes/vãos, triangulação, seleção e câmera de construção.

Construir usa renderer próprio `WHEN_DIRTY`. Jogar usa renderer contínuo. A
Activity troca a pilha de views; um renderer não tenta executar os dois modos.

## 8. Threads

- editor/documento na UI;
- snapshot profundo ao testar;
- compilação antes do loop;
- runtime pertencente à thread GL;
- controles por `volatile`/`synchronized` como no FPS;
- retorno limpa inputs, pausa áudio e libera VBOs do teste.

## 9. Persistência

`MapStore` herda do editor JSON com schema, validação, arquivo temporário,
troca atômica, backup e migrações. Mapas ficam privados; exemplos em assets.

## 10. Compatibilidade incremental

Arena/labirinto `.txt` continuam funcionando até a arena JSON reproduzir o
mesmo resultado. `Player`, `Weapon`, `Enemy`, `Drone`, `Mutant`, `Sounds`, HUD e
controles devem sofrer o mínimo. Assim todo marco mantém um APK jogável.

## 11. Geração de mapas por IA

Os dois modos compartilham transporte, allowlist de modelos, validação,
compilação e confirmação antes de salvar, mas têm arquiteturas diferentes:

```text
Guiado:
ideia → Responses/JSON Schema → AiScenarioPlan → AiScenarioBuilder
      → MapValidator → LevelCompiler → prévia → salvar

Livre:
ideia → Responses/SSE → roteiro de comandos → AiFreeMapScript
      → MapValidator → salvage → MapValidator → LevelCompiler
      → prévia com avisos → salvar

Melhorar com IA:
MapDocument atual → JSON como dado + mudança pedida → Responses/SSE
      → roteiro completo → mesmo funil Livre → prévia
      → salvar cópia com ID novo
```

O Guiado devolve intenções enumeradas e o builder local escolhe coordenadas. O
Livre deixa a IA escolher coordenadas e composição, mas o roteiro não é código:
o parser possui uma allowlist de comandos, catálogo e propriedades. Uma linha
inválida vira aviso; um mapa que continue inválido após o resgate é recusado.

A revisão é uma segunda chamada iniciada pelo jogador, não um reparo remoto
automático. Ela envia o documento atual como dado não confiável, exige uma
substituição completa para reutilizar o funil de confiança e nunca escreve no
ID original. Biblioteca, editor e prévia convergem para o mesmo controlador.

O fluxo Livre é uma decisão permanente de produto. Seus contratos atuais,
limites e redes de segurança estão em `docs/IA-LIVRE.md`; o roteiro de evolução
sem regressão está em `PLANO.md`.

## 12. Aliado combatente local

`npc.human` permanece pacífico quando a propriedade booleana `combatant` está
ausente. No Livre, `texto combate sim|nao` define esse dado e as propriedades
`combatLine1..3` guardam falas opcionais criadas junto com o mapa.

Durante a partida, `NpcCompanion` escolhe o inimigo visível mais próximo,
controla alcance, cadência, dano, desmaio, recuperação e retorno ao seguimento.
`GameState` integra objetivo, som e seleção de alvo pelos inimigos; o renderer
mostra estado, disparo e traçador. Não há chamada de IA no loop de combate.
Essa separação mantém o runtime determinístico e o balanceamento testável sem
depender da rede.
