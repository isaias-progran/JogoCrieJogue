# Catálogo de peças

O mapa salva só `prefabId` e propriedades de instância. Malha, collider,
nome, categoria e comportamento vêm de `assets/prefabs/catalog.json`.

## Jogo

- Inimigos: `enemy.drone`, `enemy.drone.wave`, `enemy.mutant`,
  `enemy.turret`, `enemy.kamikaze`, `enemy.boss`.
- Itens: `pickup.health`, `pickup.ammo`, `pickup.token`, `pickup.special`.
- Lógica: `terminal.wall`, `door.gate`, `door.auto`.
- Pessoa amigável: `npc.human`, com `name`, `role`, `greeting`, `background`,
  `combatant` e `combatLine1..3`.

Inimigos móveis aceitam `patrolX`/`patrolZ`. O terminal aceita `order`.
`door.gate` aceita meias dimensões e `controllerId`; `door.auto` aceita as
dimensões e abre por proximidade. `npc.human` aparece como uma pessoa low-poly,
segue sem virar obstáculo estático e oferece `FALAR`. Sem `combatant`, permanece
pacífico; com o booleano ativo, combate por regras determinísticas locais. A IA
opcional só define texto e dados na criação, nunca controla o objeto na partida.

## Cenário

- Móveis: mesa, cadeira, prateleira, armário, cama, bancada, pias, vaso,
  guarda-roupa e sofá (`furniture.*`).
- Objetos: caixas, barril, plantas, TV, espelho, luminárias internas e o
  poste urbano `prop.lamp.street`.
- Circulação: `stairs.small`, `stairs.floor`, `ramp.short`, `ramp.floor`.

As luminárias aceitam `lightR`, `lightG`, `lightB` e `lightRadius`; o runtime
seleciona até quatro luzes pontuais próximas da câmera. O poste também usa
`lightOffsetY` (3,35 m por padrão) para posicionar a luz na cabeça, embora a
origem e o collider continuem no chão. Peças estáticas usam malha low-poly
procedural e collider simplificado quando necessário.

## Convenções

- Frente de móveis em yaw 0 aponta para `-Z`; a planta desenha uma seta.
- Rotação é em múltiplos de 90° para manter colliders AABB.
- Alturas iniciais são definidas por `PrefabPlacementTool` e podem ser
  alteradas em **MEDIDAS**.
- Novos IDs exigem definição no catálogo, malha/collider quando estáticos,
  suporte no compilador e cobertura em `PrefabCatalogTest`.
