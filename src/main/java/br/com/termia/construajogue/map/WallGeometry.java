package br.com.termia.construajogue.map;

/** Geometria comum de paredes retas em bloco e diagonais poligonais. */
public final class WallGeometry {

    private WallGeometry() {
    }

    public static boolean diagonal(StructureObject wall) {
        return wall != null && wall.polygon != null
                && wall.polygon.length == 8
                && StructureObject.ROLE_WALL.equals(wall.role);
    }

    /** Centro da primeira ponta e da segunda: {ax,az,bx,bz}. */
    public static float[] centerLine(StructureObject wall) {
        float[] p = wall.polygon;
        return new float[]{(p[0] + p[6]) * 0.5f,
                (p[1] + p[7]) * 0.5f,
                (p[2] + p[4]) * 0.5f,
                (p[3] + p[5]) * 0.5f};
    }

    public static float halfLength(StructureObject wall) {
        if (!diagonal(wall)) {
            return Math.max(wall.half[0], wall.half[2]);
        }
        float[] line = centerLine(wall);
        return (float) Math.hypot(line[2] - line[0],
                line[3] - line[1]) * 0.5f;
    }

    /** Projeção do ponto no eixo da parede, relativa ao centro. */
    public static float offsetAt(StructureObject wall, float x, float z) {
        if (!diagonal(wall)) {
            return wall.half[0] >= wall.half[2]
                    ? x - wall.transform.x : z - wall.transform.z;
        }
        float[] line = centerLine(wall);
        float dx = line[2] - line[0];
        float dz = line[3] - line[1];
        float length = Math.max(0.0001f, (float) Math.hypot(dx, dz));
        float cx = (line[0] + line[2]) * 0.5f;
        float cz = (line[1] + line[3]) * 0.5f;
        return (x - cx) * dx / length + (z - cz) * dz / length;
    }

    /** Ponto no eixo a partir de um offset relativo ao centro. */
    public static float[] pointAt(StructureObject wall, float offset) {
        if (!diagonal(wall)) {
            boolean x = wall.half[0] >= wall.half[2];
            return new float[]{wall.transform.x + (x ? offset : 0f),
                    wall.transform.z + (x ? 0f : offset)};
        }
        float[] line = centerLine(wall);
        float dx = line[2] - line[0];
        float dz = line[3] - line[1];
        float length = Math.max(0.0001f, (float) Math.hypot(dx, dz));
        return new float[]{(line[0] + line[2]) * 0.5f + dx / length * offset,
                (line[1] + line[3]) * 0.5f + dz / length * offset};
    }

    /** Direção unitária ao longo do eixo. */
    public static float[] direction(StructureObject wall) {
        if (!diagonal(wall)) {
            return wall.half[0] >= wall.half[2]
                    ? new float[]{1f, 0f} : new float[]{0f, 1f};
        }
        float[] line = centerLine(wall);
        float dx = line[2] - line[0];
        float dz = line[3] - line[1];
        float length = Math.max(0.0001f, (float) Math.hypot(dx, dz));
        return new float[]{dx / length, dz / length};
    }

    /** Normal positiva: lado dos primeiros dois vértices do retângulo. */
    public static float[] positiveNormal(StructureObject wall) {
        if (!diagonal(wall)) {
            return wall.half[0] < wall.half[2]
                    ? new float[]{1f, 0f} : new float[]{0f, 1f};
        }
        float[] line = centerLine(wall);
        float nx = wall.polygon[0] - line[0];
        float nz = wall.polygon[1] - line[1];
        float length = Math.max(0.0001f, (float) Math.hypot(nx, nz));
        return new float[]{nx / length, nz / length};
    }

    public static float thickness(StructureObject wall) {
        if (!diagonal(wall)) {
            return Math.min(wall.half[0], wall.half[2]) * 2f;
        }
        float[] line = centerLine(wall);
        return (float) Math.hypot(wall.polygon[0] - line[0],
                wall.polygon[1] - line[1]) * 2f;
    }

    /**
     * Redimensiona comprimento/espessura preservando centro e direção.
     * Também mantém os vãos dentro do novo comprimento.
     */
    public static void resize(StructureObject wall, float length,
                              float thickness) {
        float safeLength = Math.max(0.05f, length);
        float safeThickness = Math.max(0.01f, thickness);
        if (!diagonal(wall)) {
            boolean alongX = wall.half[0] >= wall.half[2];
            wall.half[alongX ? 0 : 2] = safeLength * 0.5f;
            wall.half[alongX ? 2 : 0] = safeThickness * 0.5f;
            fitOpenings(wall, safeLength * 0.5f);
            return;
        }
        float[] line = centerLine(wall);
        float cx = (line[0] + line[2]) * 0.5f;
        float cz = (line[1] + line[3]) * 0.5f;
        float[] along = direction(wall);
        float[] normal = positiveNormal(wall);
        float hl = safeLength * 0.5f;
        float ht = safeThickness * 0.5f;
        float ax = cx - along[0] * hl;
        float az = cz - along[1] * hl;
        float bx = cx + along[0] * hl;
        float bz = cz + along[1] * hl;
        wall.polygon = new float[]{
                ax + normal[0] * ht, az + normal[1] * ht,
                bx + normal[0] * ht, bz + normal[1] * ht,
                bx - normal[0] * ht, bz - normal[1] * ht,
                ax - normal[0] * ht, az - normal[1] * ht};
        wall.syncPolyBounds();
        fitOpenings(wall, hl);
    }

    private static void fitOpenings(StructureObject wall, float halfLength) {
        for (WallOpening opening : wall.openings) {
            opening.width = Math.min(opening.width, halfLength * 2f);
            float room = Math.max(0f, halfLength - opening.width * 0.5f);
            opening.offset = Math.max(-room, Math.min(room, opening.offset));
        }
    }

    /** Distância XZ do ponto ao segmento central limitado. */
    public static float distanceTo(StructureObject wall, float x, float z) {
        if (!diagonal(wall)) {
            float dx = Math.max(0f, Math.abs(x - wall.transform.x)
                    - wall.half[0]);
            float dz = Math.max(0f, Math.abs(z - wall.transform.z)
                    - wall.half[2]);
            return (float) Math.hypot(dx, dz);
        }
        float[] line = centerLine(wall);
        float dx = line[2] - line[0];
        float dz = line[3] - line[1];
        float length2 = dx * dx + dz * dz;
        float t = length2 < 1e-6f ? 0f
                : ((x - line[0]) * dx + (z - line[1]) * dz) / length2;
        t = Math.max(0f, Math.min(1f, t));
        return (float) Math.hypot(x - (line[0] + dx * t),
                z - (line[1] + dz * t));
    }
}
