# Formato do mapa — schema 2

O documento é JSON legível, usa metros (Y para cima) e guarda intenção de
edição. Malhas, normais, VBOs, colliders e estado da partida são derivados e
nunca são salvos.

```json
{
  "schema": 2,
  "id": "uuid-mapa",
  "name": "Minha arena",
  "objective": {
    "type": "collect",
    "target": 3,
    "timeLimitSeconds": 90,
    "twoStarSeconds": 60,
    "threeStarSeconds": 35
  },
  "environment": {
    "ambient": 0.35,
    "fog": [0.04, 0.05, 0.07],
    "fogFar": 30,
    "sky": "night",
    "soundscape": "tunnel"
  },
  "structures": [
    {
      "id": "uuid-piso",
      "kind": "block",
      "role": "floor",
      "material": "checker",
      "locked": true,
      "transform": {"x": 0, "y": -0.15, "z": 0},
      "half": [8, 0.15, 8],
      "color": [0.3, 0.33, 0.38]
    },
    {
      "id": "uuid-parede",
      "kind": "block",
      "role": "wall",
      "transform": {"x": 0, "y": 1.5, "z": -8},
      "half": [8, 1.5, 0.15],
      "color": [0.46, 0.48, 0.55],
      "color2": [0.75, 0.25, 0.2],
      "openings": [{
        "id": "uuid-vao", "type": "door", "offset": 0,
        "width": 1, "height": 2.1
      }]
    }
  ],
  "prefabs": [{
    "id": "uuid-portao",
    "prefabId": "door.gate",
    "transform": {"x": 0, "y": 1.4, "z": 0},
    "properties": {
      "halfX": 1.5, "halfY": 1.4, "halfZ": 0.4,
      "controllerId": "uuid-terminal"
    }
  }],
  "markers": [
    {"id": "uuid-spawn", "type": "player_spawn", "x": 0, "y": 0,
      "z": 6, "yaw": 180},
    {"id": "uuid-exit", "type": "exit", "x": 0, "y": 0,
      "z": -6, "radius": 1.2}
  ]
}
```

## Campos

- `objective.type`: `reach_exit`, `eliminate_all`, `collect` ou `survive`.
  `target` vale para fichas; `durationSeconds` para sobreviver;
  `timeLimitSeconds` é opcional para qualquer tipo. Metas de estrelas são
  opcionais. `twoStarSeconds`/`threeStarSeconds` valem para tempo; em
  `survive` o tempo decorrido é sempre a própria duração, então as estrelas
  vêm da vida restante (40+ vale 2, 80+ vale 3) e as metas de tempo são
  ignoradas.
- `environment.sky`: `none`, `day`, `dusk` ou `night`.
- `environment.soundscape`: campo opcional `auto`, `outdoor`, `tunnel` ou
  `industrial`. Ausente/`auto` preserva o ambiente externo dos mapas antigos.
- Estruturas: `kind` é `block` ou `poly`; `role` é `floor`, `wall`,
  `ceiling` ou `block`. Em `poly`, `polygon` contém pares X,Z absolutos.
- Materiais: `plain`, `brick`, `wood`, `checker`, `metal`, `asphalt`, `water`
  e `lava`. Asfalto recebe granulação procedural; água reduz a velocidade e
  lava causa dano. `color2`/`color3` pintam os dois lados de paredes retas ou
  diagonais.
- Vãos: `door`, `portal` ou `window`; `offset` percorre o eixo da parede,
  inclusive diagonal. `sill` é o peitoril.
- `locked` pode existir em estrutura, prefab, marcador ou vão e afeta só o
  editor.
- Prefabs guardam `prefabId`, transformação, escala e apenas propriedades
  permitidas pelo catálogo. Patrulha usa `patrolX`/`patrolZ`; luminárias
  podem deslocar a posição da luz em Y com `lightOffsetY`.
- `npc.human` é uma pessoa amigável. Suas propriedades textuais são `name`
  (48 caracteres), `role` (80), `greeting` (240), `background` (600) e
  `combatLine1..3` (120 cada). O booleano opcional `combatant` ativa as regras
  locais de aliado; ausente significa pacífico. Esses valores são dados
  narrativos/configuração e nunca viram código, comando ou nome de classe. O NPC
  funciona com fala local mesmo quando a integração de IA está desligada. Papel
  e contexto também derivam localmente uma personalidade estável para conversa
  e pequenas variações de ritmo/tom no TTS.
- Há exatamente um `player_spawn`. Uma `exit` é obrigatória somente para
  `reach_exit`. Portas e terminais podem ser múltiplos; `controllerId` liga
  um portão a um terminal e `order` cria sequência de terminais.

## Andares e coordenada Y

Andares não exigem um campo novo: todas as transformações já usam coordenadas
absolutas em metros. O editor deriva cada pavimento da geometria e trabalha
com uma `baseY` ativa:

- piso: o topo fica em `baseY`;
- parede/bloco: a base fica em `baseY`;
- teto padrão: a face inferior fica em `baseY + 3,00` e o topo da laje em
  `baseY + 3,30`;
- peças e marcadores somam sua altura local à `baseY`.

Assim, um teto térreo com `transform.y = 3.15` e `half[1] = 0.15` sustenta um
novo pavimento em `Y = 3.30`. Uma parede criada sobre ele terá centro em
`Y = 4.80`. A própria laje aparece tanto no andar que ela cobre quanto no
andar apoiado sobre ela.

Início, saída, água/lava, terminais e portas respeitam Y no runtime. Uma saída
ou porta automática não é acionada por um jogador alinhado em X/Z, mas em
outro pavimento. Mapas antigos com saída em Y zero mantêm o formato runtime
legado e o mesmo comportamento.

## Compatibilidade e troca

`MapMigration` converte schema 1 para schema 2 ao ler; o próximo salvamento
grava o formato atual. A importação sempre dá um novo ID ao mapa, evitando
sobrescrever conteúdo local. O mesmo JSON pode ser exportado como arquivo ou
compactado no código `CJ2:`/QR.

`assets/maps/arena.json` é gerado por `LegacyTxtConverter`; não deve ser
editado à mão. `scripts/test-core.sh` compara o arquivo gerado bit a bit com
o nível legado.
