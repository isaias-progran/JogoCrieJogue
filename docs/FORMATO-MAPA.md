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

Estruturas desenháveis: `floor`, `wall`, `ceiling`, `opening`.

Famílias de prefab: `stairs.*`, `ramp.*`, `platform.*`, `obstacle.*`,
`furniture.*`, `door.*`, `terminal.*`, `enemy.*`, `pickup.*`.

Marcadores invisíveis: `player_spawn`, `exit`, `patrol_point`.

Porta controlada referencia a instância do terminal por `controllerId`.
O mapa salva `prefabId`, mas não copia a definição interna da peça.

Não salvar triângulos, normais, VBO, AABB derivada, seleção, cache de raycast
ou estado temporário da partida.

