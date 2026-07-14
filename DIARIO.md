# DIARIO — Construa & Jogue

## O que é
App Android (builder TermIa, Java puro, sem Gradle/androidx) que une:
- motor FPS do `jogo-fps` (jogar em primeira pessoa);
- ferramentas de planta/construção do `editor3d` (desenhar o mapa).
Ciclo: desenhar espaço → posicionar prefabs prontos → Testar → jogar → voltar.
Planos em `PLANO.md`, `ARQUITETURA.md`, `ESTRUTURA.md`, `ORIGENS.md`.

## Estado atual — 2026-07-14
- **Fase 0 (clone seguro) COMPILADA — aguardando validação no aparelho.**
- v0.1.0 (versionCode 1). APK em `/sdcard/TermIa/apks/construa-jogue.apk`.
- Fonte copiado do `jogo-fps` v0.5.0 (campanha arena → labirinto, 120 FPS
  validado lá). Package renomeado para `br.com.termia.construajogue`,
  nome "Construa & Jogue", ícone novo (letra C, gerado pelo icone.py;
  só mipmap-xxhdpi — densidades antigas removidas).
- Estrutura de pacotes: java copiado mantém `engine/game/input/ui`; as pastas
  vazias previstas na ESTRUTURA (`map/`, `prefab/`, `compiler/`, …) serão
  preenchidas fase a fase.
- `scripts/test-levels.py` passa (arena 25 caixas, labirinto 36 caixas +
  7 mutantes, rotas OK).

## Portão da Fase 0 (pendente — usuário)
Validar no aparelho: campanha completa igual ao jogo-fps (andar, atirar,
pausa, dois setores) e desempenho equivalente (~120 FPS).

## Decisões e armadilhas
- **Fase 1 seguinte** (só após o portão): `MapDocument`, `PrefabCatalog`,
  JSON versionado, `MapValidator`, `LevelCompiler`, arena convertida p/ JSON.
- **Testes JVM**: sem Gradle/JUnit aqui, e `org.json` do android.jar é stub
  fora do Android → usar runner `main()` via `scripts/test-core.sh` e parser
  JSON próprio mínimo (ou testes em Python como o test-levels.py).
- ESTRUTURA prevê `Shader/Mesh/Boxes` em `render/`; mantidos em `engine/`
  (menos diff com o jogo-fps). Reorganizar só se doer.
- Ao importar o editor3d na Fase 2: NÃO copiar o MainActivity de 916 linhas;
  quebrar em `EditorHost` + tools (regra das ~400 linhas).
- Renderer do editor será `WHEN_DIRTY`; o do jogo, contínuo. Um renderer não
  atende os dois modos (ver ARQUITETURA §7).

## Próximos passos
1. Usuário valida a campanha no aparelho (portão Fase 0).
2. Fase 1: documento/prefabs/compilador + arena JSON jogável.
