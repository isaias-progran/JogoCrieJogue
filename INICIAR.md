# Próxima sessão

O código está em **v0.26.1**. Antes de alterar:

1. Leia `DIARIO.md`, `PLANO.md`, `docs/IA-LIVRE.md`,
   `docs/IA-SEGURA.md` e `docs/FORMATO-MAPA.md`.
2. Rode `sh scripts/test-core.sh`.
3. Compile com
   `sh /root/toolchain/build.sh /host/home/apps/construa-jogue`.
4. Quando houver `adb`, rode
   `ADB=/caminho/adb sh scripts/test-device.sh` para validar GLES, áudio e
   gestos no aparelho.

## Contratos que não podem regredir

- Preserve a igualdade bit a bit entre `arena.json` e o conversor legado.
- Toda mudança incompatível de JSON exige um novo passo em `MapMigration`.
- Pavimentos continuam derivados por `StoryLevels` das coordenadas Y, sem campo
  novo no JSON.
- Render do editor é `WHEN_DIRTY`; render do jogo é contínuo.
- `Complexo Ômega — nove núcleos` e `Cidade Aurora — apagão` continuam passando
  em `ElaborateMapTest` e `CityMapTest`.
- Mapas salvos nunca são regenerados automaticamente nem sobrescritos por
  mudanças na IA; a revisão explícita sempre salva outro ID.

## Regra da IA Livre

A IA Livre foi aprovada no aparelho e é uma função permanente. Não a converta
em Guiado, não substitua suas coordenadas por receitas e não remova o modo por
causa de uma geração ruim. Toda melhoria deve preservar pedido direto,
streaming, comandos atuais, avisos, resgate, validação, compilação e confirmação
antes de salvar.

`MELHORAR COM IA` precisa continuar sendo uma nova chamada explícita. O mapa
atual entra como dado, a saída é um roteiro completo que passa pelo mesmo funil
Livre e a confirmação salva uma cópia com ID novo. Não transforme a função em
patch executável, autosave, sobrescrita ou reparo remoto silencioso.

Mudanças no prompt/parser exigem `AiFreeMapTest` e comparação em aparelho com
**mapas novos**. Melhorias no prompt não alteram mapas já salvos.

## Próxima ordem técnica

1. validar no aparelho a revisão da v0.26.1 e confirmar original/cópia;
2. validar o cancelamento real e o aliado combatente;
3. ampliar alcançabilidade horizontal para escadas, terminais, fichas e
   interiores obrigatórios;
4. medir balanceamento do aliado com vários inimigos/NPCs;
5. evoluir análise local, prévia 3D e desempenho sem reduzir a IA Livre.

A integração de rede tem testes de contrato, mas uma chamada real exige chave
pessoal e deve ser conferida no aparelho sem registrar o segredo.
