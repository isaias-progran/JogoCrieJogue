# Arquitetura

## 1. Três representações

1. `MapDocument`: fonte editável e persistente.
2. `EditorScene`: seleção, ferramentas e histórico temporário.
3. `RuntimeLevel`: resultado imutável e otimizado para jogar.

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

