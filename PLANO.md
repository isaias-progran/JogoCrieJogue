# Plano — Construa & Jogue

## 1. Conceito

O ciclo principal será:

1. desenhar o espaço;
2. escolher objetos prontos;
3. posicionar elementos de jogo;
4. tocar em **Testar**;
5. jogar em primeira pessoa;
6. voltar, corrigir e testar novamente.

Não será necessário modelar uma escada, cadeira ou drone. O jogador desenha
somente a arquitetura e monta o interior com peças prontas.

## 2. O que foi confirmado nos projetos existentes

### Base `jogo-fps`

O jogo já tem OpenGL ES 3.0 contínuo, câmera FPS, jogador com gravidade, pulo,
degraus e colisão AABB, tiro hitscan, arma, HUD, áudio, pausa, controles
multitoque, terminal, porta, itens, drones, mutantes e níveis em texto. A base
foi medida a 120 FPS no aparelho.

### Origem `editor3d`

O editor já tem desenho cotado, snap, formas exatas, retas e curvas, pisos,
paredes, tetos, portas/janelas vazadas, seleção por malha, medidas, trava,
transformações, undo/redo e JSON atômico.

### Decisão

Criar app novo a partir do FPS. Incorporar seletivamente os algoritmos do
editor. Não unir diretamente os dois `MainActivity`, renderers ou formatos.

## 3. Modos

### Biblioteca

- criar, renomear, duplicar e excluir mapas;
- abrir para construir ou jogar;
- mapas de exemplo somente leitura.

### Construir

- desenhar piso, paredes, teto e aberturas;
- escolher objetos no catálogo;
- pré-visualizar, posicionar, girar, duplicar e remover;
- colocar início, saída e rotas;
- desfazer/refazer, salvar e validar;
- testar sem perder o documento.

### Jogar/Testar

- controles e combate do FPS atual;
- colisão com arquitetura e prefabs;
- inimigos, itens, porta, terminal e objetivo;
- botão **Voltar ao editor**.

## 4. Catálogo de objetos prontos

### Circulação

- escada reta pequena e média;
- rampa curta;
- plataforma.

### Obstáculos

- caixas pequenas e grandes;
- barril;
- barricada;
- cobertura baixa;
- pilar.

### Móveis

- mesa;
- cadeira;
- estante;
- armário;
- cama;
- bancada.

### Jogo

- porta e portão;
- terminal;
- drone ativo e drone dormente;
- mutante;
- kit médico e munição.

Cada peça já contém aparência, collider, ponto de apoio, dimensões padrão,
limites de escala e comportamento. O usuário apenas escolhe e posiciona.

## 5. MVP

- biblioteca com pelo menos três mapas;
- piso por contorno;
- paredes retas e curvas segmentadas;
- teto opcional;
- aberturas;
- catálogo mínimo de circulação, obstáculos, móveis e conteúdo de jogo;
- um início e uma saída obrigatórios;
- porta associada a terminal;
- inimigos, itens e patrulha de dois pontos;
- salvar/carregar;
- Construir → Testar → Construir;
- mapa de exemplo convertido da arena atual.

Pronto quando uma pessoa cria uma sala, escolhe uma escada, móvel, obstáculo,
início, saída e inimigo, testa, vence, volta, move uma peça e testa novamente.

## 6. Fora do MVP

- multiplayer e servidor;
- publicação pública de mapas;
- modelos 3D importados pelo usuário;
- terreno orgânico;
- vários andares conectados;
- navmesh completo;
- materiais/texturas livres;
- lógica visual por nós;
- scripts arbitrários.

## 7. Validação antes de jogar

Bloquear teste quando faltar piso, início ou saída; houver mais de um início;
spawn estiver dentro de collider; porta referenciar terminal inexistente;
contorno cruzar a si mesmo; ou existir número/dimensão inválida.

Avisar, sem bloquear, quando inimigo/item parecer inacessível, teto estiver
baixo, mapa exceder limites recomendados ou saída não tiver rota evidente.

## 8. Fases

### Fase 0 — clone seguro

Copiar `jogo-fps` sem `.git`/`build`, trocar identidade/package, compilar,
rodar testes de níveis e confirmar a campanha atual no aparelho.

Portão: APK novo mantém o jogo e o desempenho existentes.

### Fase 1 — documento, prefabs e compilador

Criar `MapDocument`, `PrefabCatalog`, JSON versionado, `MapValidator`,
`LevelCompiler`, `RuntimeLevel` e testes JVM. Converter a arena para JSON.

Portão: arena JSON/prefabs reproduz a arena em texto e continua jogável.

### Fase 2 — construir o espaço

Incorporar planta, snap, formas exatas, paredes/pisos/tetos, seleção 3D,
medidas, undo/redo, início/saída e salvamento.

Portão: uma sala criada no aparelho pode ser navegada em primeira pessoa.

### Fase 3 — catálogo pronto

Adicionar navegador por categorias, peça fantasma, encaixe no piso/parede,
escadas, móveis, obstáculos, portas, terminal, inimigos e itens.

Portão: ciclo completo do MVP.

### Fase 4 — biblioteca e acabamento

Miniaturas, vários mapas, duplicação, backup, tutorial, limites de desempenho,
mapas exemplo e validação no aparelho.

## 9. Limites iniciais

- área recomendada de 64 × 64 m;
- até 80 estruturas desenhadas;
- até 200 instâncias de prefabs;
- até 1.500 segmentos após aproximar curvas;
- até 24 inimigos;
- até 64 itens/marcadores;
- JSON recomendado até 2 MB;
- meta mínima de 60 FPS.

## 10. Qualidade obrigatória

- nenhuma triangulação/JSON no quadro principal;
- nenhuma chamada GLES fora da thread GL;
- documento nunca alterado pelo teste;
- mapa compilado uma vez ao entrar no jogo;
- salvamento atômico com backup;
- IDs estáveis e relações por UUID;
- testes de schema, catálogo, compilador e colisão;
- APK jogável ao fim de cada fase.

