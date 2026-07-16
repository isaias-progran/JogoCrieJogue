# Próxima sessão

O código está em v0.21.1. Antes de alterar:

1. Leia `DIARIO.md` (entrada v0.21.0), `docs/FORMATO-MAPA.md` e
   `docs/IA-SEGURA.md`.
2. Rode `sh scripts/test-core.sh`.
3. Compile com
   `sh /root/toolchain/build.sh /host/home/apps/construa-jogue`.
4. Quando houver `adb`, rode
   `ADB=/caminho/adb sh scripts/test-device.sh` para validar GLES, áudio e
   gestos no aparelho.

Preserve a igualdade bit a bit entre `arena.json` e o conversor legado. Toda
mudança incompatível de JSON precisa de um novo passo em `MapMigration`.
Regras do editor devem continuar saindo de `PlanEditorView` para
`editor/tools/`; pavimentos são derivados por `StoryLevels` das coordenadas Y,
sem campo novo no JSON. Render do editor é `WHEN_DIRTY` e o jogo é contínuo.

Os exemplos `Complexo Ômega — nove núcleos` e `Cidade Aurora — apagão` são
mapas-vitrine; alterações neles devem continuar passando em
`ElaborateMapTest` e `CityMapTest`. A cidade também é o contrato do material
`asphalt`, de `prop.lamp.street` e da missão que sobe à prefeitura.
O principal trabalho restante é validação visual/tátil no aparelho e ajustes
finos que ela revelar. A suíte pura-Java tem 587 verificações. A integração
de rede foi validada por contrato, mas uma chamada real exige uma chave
pessoal e deve ser conferida no aparelho sem registrar o segredo.
