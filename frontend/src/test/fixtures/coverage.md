# Cobertura de estados por dados reais

Gerado por `scripts/capture-fixtures.mjs` a partir das fixtures em disco — não editar à mão.
Estado **sem exemplo real** não ganha teste de componente com dado inventado (plano do Bloco 2,
seção 7.4 / risco R3): só a função pura é testada, quando há valor real citável.

## Fluxos (busca, importação, categorias)

| Estado da UI | Fixture |
|---|---|
| Busca com hit EXACT | `search-m31` |
| Busca só com hits FUZZY | `search-fuzzy` |
| Busca sem resultado | `search-empty-betelgeuse` |
| Busca 400 (termo longo demais via ?q=) | `search-400` |
| Categorias raiz com contagem | `categories` |
| Listagem de categoria (página 1) | `category-galaxy-p1` |
| Listagem de categoria (página 2) | `category-galaxy-p2` |
| Página de categoria além do total (200, lista vazia) | `category-galaxy-p3-beyond` |
| Categoria 404 | `category-404` |
| Objeto 404 | `object-404` |
| Import criado (202, queued) | `import-betelgeuse-created` |
| Import em andamento (running) | `import-betelgeuse-running` |
| Import concluído com objeto novo (succeeded, 1 slug) | `import-betelgeuse-succeeded` |
| Import concluído "já existia no catálogo" | `import-vega-succeeded` |
| Import não encontrado (not_found) | `import-not-found-not-found` |
| Import com falha SOURCE_UNAVAILABLE (failed) | `import-failed-failed` |
| Import 400 (charset inválido) | `import-400` |
| Import 404 (job inexistente/expirado) | `import-404` |
| Import 429 (fila cheia, com Retry-After) | `import-429` |
| Import concluído com mais de 1 slug | **sem exemplo real** |
| Import failed "interrompido por reinício do servidor" | **sem exemplo real** |
| Erro 5xx / INTERNAL_ERROR da API | **sem exemplo real** |

## Página do objeto (varredura de todos os objetos do catálogo local)

| Estado da UI | Fixture |
|---|---|
| Natureza epistêmica "observed" | `object-betelgeuse` |
| Natureza epistêmica "derived_by_source" | `object-betelgeuse` |
| Natureza epistêmica "computed_by_us" | `object-betelgeuse` |
| Natureza epistêmica "estimated" | **sem exemplo real** |
| Natureza epistêmica "model" | **sem exemplo real** |
| Ausência com código NOT_PROVIDED_BY_SOURCE | `object-betelgeuse` |
| Ausência com código PARALLAX_SNR_BELOW_5 | **sem exemplo real** |
| Ausência com código PARALLAX_WITHOUT_UNCERTAINTY | **sem exemplo real** |
| Ausência com código PARALLAX_UNCERTAINTY_INVALID | **sem exemplo real** |
| Ausência com código PARALLAX_NON_POSITIVE | **sem exemplo real** |
| limitFlag "<" | **sem exemplo real** |
| limitFlag ">" | **sem exemplo real** |
| limitFlag "~" | **sem exemplo real** |
| Incerteza simétrica (±) | `object-betelgeuse` |
| Incerteza assimétrica (errMinus ≠ errPlus) | `object-betelgeuse` |
| Incerteza só de um lado | **sem exemplo real** |
| Valor numérico sem incerteza | `object-betelgeuse` |
| Valor textual (valueText) | `object-betelgeuse` |
| Propriedade com valores alternativos (alternatives não vazio) | `object-messier-100` |
| Disponível sem valor preferido (só alternativas) | **sem exemplo real** |
| Valor calculado com derivedFrom (aponta a medição de origem) | `object-betelgeuse` |
| derivedFrom em valor que não é computed_by_us | **sem exemplo real** |
| Medição sem referência bibliográfica | `object-messier-103` |
| Propriedade com qualificador (ex.: banda fotométrica) | `object-betelgeuse` |
| Unidade arcmin | `object-messier-100` |
| Distância < 1 kpc (escala em pc) | `object-betelgeuse` |
| Distância ≥ 1 Mpc (escala de milhões de anos-luz) | `object-messier-100` |
| Objeto sem posição (position: null) | **sem exemplo real** |
| Objeto sem categoria (category: null) | **sem exemplo real** |
| existenceStatus diferente de "confirmed" | **sem exemplo real** |
| Fonte sem termsUrl | **sem exemplo real** |
| Fonte sem acknowledgment | **sem exemplo real** |
