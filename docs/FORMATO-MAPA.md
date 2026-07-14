# Formato do mapa

## Princípios

- JSON legível e versionado;
- metros e Y para cima;
- IDs UUID;
- estruturas, prefabs e marcadores separados;
- documento guarda intenção; malhas/colliders são derivados.

## Esboço do schema 1

```json
{
  "schema": 1,
  "id": "uuid-mapa",
  "name": "Minha arena",
  "environment": {
    "ambient": 0.35,
    "fog": [0.04, 0.05, 0.07],
    "fogFar": 30
  },
  "structures": [
    {
      "id": "uuid-piso",
      "kind": "floor",
      "transform": {"x": 0, "y": -0.15, "z": 0, "yaw": 0},
      "height": 0.15,
      "polygon": [-8, -8, 8, -8, 8, 8, -8, 8]
    },
    {
      "id": "uuid-parede",
      "kind": "wall",
      "transform": {"x": 0, "y": 0, "z": 0, "yaw": 0},
      "height": 3,
      "thickness": 0.15,
      "path": [-8, -8, 8, -8, 8, 8],
      "openings": []
    }
  ],
  "prefabs": [
    {
      "id": "uuid-escada",
      "prefabId": "stairs.straight.small",
      "transform": {"x": -3, "y": 0, "z": 1, "yaw": 90},
      "scale": 1
    },
    {
      "id": "uuid-drone",
      "prefabId": "enemy.drone",
      "transform": {"x": 2, "y": 1.7, "z": 0, "yaw": 0},
      "properties": {"dormant": false, "patrolX": -2, "patrolZ": 0}
    }
  ],
  "markers": [
    {
      "id": "uuid-spawn",
      "type": "player_spawn",
      "x": 0, "y": 0, "z": 6, "yaw": 180
    },
    {
      "id": "uuid-exit",
      "type": "exit",
      "x": 0, "y": 0, "z": -6, "radius": 1.2
    }
  ]
}
```

Estruturas desenháveis: `floor`, `wall`, `ceiling`, `opening` (fases 2+)
e `block` (fase 1, já implementado).

## Implementado na Fase 1 (schema 1 atual)

- Estrutura `block`: paralelepípedo alinhado aos eixos. `transform` é o
  CENTRO, `"half": [hx, hy, hz]` são meias dimensões e `"color": [r, g, b]`
  a cor 0..1. Meias dimensões (e não dimensões totais) para reproduzir
  bit a bit os níveis legados sem arredondamento de float.
- Inimigos (`enemy.drone`, `enemy.drone.wave`, `enemy.mutant`):
  propriedades `patrolX`/`patrolZ` = segundo ponto da patrulha
  (padrão: parado na posição inicial).
- `door.gate`: `transform` no centro, propriedades `halfX/halfY/halfZ`
  (meias dimensões) e `controllerId` = id da instância do terminal.
- Restrições da fase: exatamente 1 `player_spawn` e 1 `exit`; no máximo
  1 terminal e 1 porta (limite atual do RuntimeLevel).
- Números no JSON preservam o token (classe `Json.Num`) — a leitura usa
  `Float.parseFloat` direto, sem passar por double.
- `assets/maps/arena.json` é gerado por `LegacyTxtConverter` a partir de
  `assets/levels/arena.txt`; `scripts/test-core.sh` falha se divergirem.

Famílias de prefab: `stairs.*`, `ramp.*`, `platform.*`, `obstacle.*`,
`furniture.*`, `door.*`, `terminal.*`, `enemy.*`, `pickup.*`.

Marcadores invisíveis: `player_spawn`, `exit`, `patrol_point`.

Porta controlada referencia a instância do terminal por `controllerId`.
O mapa salva `prefabId`, mas não copia a definição interna da peça.

Não salvar triângulos, normais, VBO, AABB derivada, seleção, cache de raycast
ou estado temporário da partida.

