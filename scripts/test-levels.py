#!/usr/bin/env python3
"""Validação rápida do formato e da rota dos níveis, sem depender do Android."""

from collections import deque
from pathlib import Path
import math
import sys


STEP = 0.25
RADIUS = 0.35
EYE_HEIGHT = 1.75
LIMIT = 24.0


def bounds(values):
    x, y, z, hx, hy, hz = values[:6]
    return x - hx, y - hy, z - hz, x + hx, y + hy, z + hz


def load(path):
    level = {"box": [], "mutant": []}
    arity = {
        "box": 9, "door": 6, "spawn": 4, "terminal": 3,
        "exit": 3, "drone": 5, "wave": 5, "mutant": 5,
        "ambient": 1, "fog": 4,
    }
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        command = parts[0]
        expected = 4 if command == "item" else arity.get(command)
        if expected is None or len(parts) != expected + 1:
            raise ValueError(f"{path}:{number}: comando/quantidade inválida")
        if command == "item":
            values = [parts[1], *map(float, parts[2:])]
        else:
            values = list(map(float, parts[1:]))
        if command in ("box", "mutant"):
            level[command].append(values)
        else:
            level[command] = values
    for required in ("spawn", "terminal", "door", "exit"):
        if required not in level:
            raise ValueError(f"{path}: faltando {required}")
    return level


def route_exists(level, start, target, target_range, include_door):
    colliders = [bounds(box) for box in level["box"]]
    if include_door:
        colliders.append(bounds(level["door"]))
    colliders = [b for b in colliders if b[4] > 0.01 and b[1] < EYE_HEIGHT]

    def blocked(x, z):
        for x0, _, z0, x1, _, z1 in colliders:
            nx = min(max(x, x0), x1)
            nz = min(max(z, z0), z1)
            if (x - nx) ** 2 + (z - nz) ** 2 < RADIUS ** 2:
                return True
        return False

    def cell(x, z):
        return round((x + LIMIT) / STEP), round((z + LIMIT) / STEP)

    def point(ix, iz):
        return ix * STEP - LIMIT, iz * STEP - LIMIT

    first = cell(*start)
    queue = deque([first])
    seen = {first}
    maximum = round(2 * LIMIT / STEP)
    while queue:
        ix, iz = queue.popleft()
        x, z = point(ix, iz)
        if math.hypot(x - target[0], z - target[1]) <= target_range:
            return True, len(seen)
        for nxt in ((ix + 1, iz), (ix - 1, iz),
                    (ix, iz + 1), (ix, iz - 1)):
            if (nxt in seen or nxt[0] < 0 or nxt[1] < 0
                    or nxt[0] > maximum or nxt[1] > maximum):
                continue
            x, z = point(*nxt)
            if not blocked(x, z):
                seen.add(nxt)
                queue.append(nxt)
    xs = [point(ix, iz)[0] for ix, iz in seen]
    zs = [point(ix, iz)[1] for ix, iz in seen]
    edge_z = min(zs)
    edge_x = [point(ix, iz)[0] for ix, iz in seen
              if point(ix, iz)[1] == edge_z]
    return False, len(seen), (min(xs), max(xs), min(zs), max(zs)), \
        (min(edge_x), max(edge_x))


def validate(path):
    level = load(path)
    solid = [bounds(box) for box in level["box"]]
    for mutant in level["mutant"]:
        x, y, z = mutant[:3]
        for x0, y0, z0, x1, y1, z1 in solid:
            if y1 <= y - 0.85 or y0 >= y + 0.85:
                continue
            nx = min(max(x, x0), x1)
            nz = min(max(z, z0), z1)
            if (x - nx) ** 2 + (z - nz) ** 2 < 0.32 ** 2:
                raise ValueError(f"{path}: mutante dentro de caixa em {x},{z}")
    spawn = (level["spawn"][0], level["spawn"][2])
    terminal = (level["terminal"][0], level["terminal"][2])
    exit_point = (level["exit"][0], level["exit"][1])
    to_terminal = route_exists(level, spawn, terminal, 2.2, True)
    # Depois da ativação o jogador continua no mesmo componente do spawn;
    # testar a saída a partir dele evita usar o ponto do terminal dentro da parede.
    to_exit = route_exists(level, spawn, exit_point, level["exit"][2], False)
    if not to_terminal[0] or not to_exit[0]:
        raise ValueError(f"{path}: rota bloqueada {to_terminal} {to_exit}")
    print(f"OK {path.name}: {len(level['box'])} caixas, "
          f"{len(level['mutant'])} mutantes, rotas {to_terminal[1]}/"
          f"{to_exit[1]} células")


base = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/assets/levels")
for source in sorted(base.glob("*.txt")):
    validate(source)
