# Construa & Jogue — plano de partida

Novo app Android 3D em que o jogador desenha apenas o espaço e monta a arena
com objetos prontos. Depois testa imediatamente em primeira pessoa e volta ao
editor para ajustar.

## Decisão confirmada após ler os dois apps

O projeto nasce de uma cópia segura do `jogo-fps`, que já entrega câmera FPS,
colisão, pulo, tiro, inimigos, HUD, áudio e 120 FPS medidos no aparelho. Do
`editor3d` entram planta, paredes, pisos, tetos, aberturas, medidas, seleção,
undo/redo, extrusão e JSON atômico.

O usuário desenha piso, paredes, teto e aberturas. Escadas, móveis, obstáculos,
portas, drones, inimigos, itens e terminais vêm de um catálogo criado pelo app.

- Diretório: `/host/home/apps/construa-jogue`
- Nome visível: **Construa & Jogue**
- Package previsto: `br.com.termia.construajogue`
- Java puro, OpenGL ES 3.0, minSdk 24, horizontal e offline

Leia `INICIAR.md` ao abrir este diretório como próximo workspace.

