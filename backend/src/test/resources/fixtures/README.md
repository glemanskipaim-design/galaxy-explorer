# Fixtures — respostas REAIS das fontes

Nada aqui foi digitado à mão. Cada arquivo é a resposta exata de uma requisição
ao vivo, capturada em **2026-10-05, entre 22:16 e 22:20 UTC** (cabeçalhos `Date`
das respostas). Os testes validam o **nosso processamento** dessas respostas, não
o estado atual do SIMBAD — os valores podem mudar lá. Recapturar é uma ação
explícita (rodar os comandos abaixo de novo), nunca "consertar o JSON".

Única alteração feita sobre um corpo recebido: em `simbad/error_bad_adql.xml` o
IP do usuário (que o TAP ecoa em `<INFO name="USER">`) foi trocado por `0.0.0.0`.

## simbad/ — SIMBAD TAP

Endpoint: `https://simbad.cds.unistra.fr/simbad/sim-tap/sync`. Pares
`<nome>.adql` (consulta exata) + `<nome>.json` (resposta). Uma requisição por
vez, com 1 s de pausa (limite do CDS: 6 req/s por IP). Comando (Git Bash, a
partir desta pasta):

```
curl -s -X POST https://simbad.cds.unistra.fr/simbad/sim-tap/sync \
  --data-urlencode "REQUEST=doQuery" --data-urlencode "LANG=ADQL" \
  --data-urlencode "FORMAT=json" --data-urlencode "MAXREC=100000" \
  --data-urlencode "QUERY@<nome>.adql" -o <nome>.json
```

| arquivo | conteúdo |
|---|---|
| `otypedef_all` | todos os otypes (226) — base da V3/V5; todo otype mapeado tem que existir aqui. Reconferido ao vivo em 2026-10-06: idêntico |
| `tap_schema` | `TAP_SCHEMA.columns` de `basic`, `ident`, `flux`, `mesDistance` |
| `messier_types` | oid, main_id, otype, morph_type dos 110 Messier (via `ident.id` com padding `M%4d`) |
| `sample_basic`, `sample_ident`, `sample_flux`, `sample_mesdistance` | as 4 consultas de `fetch` para os 11 oids da amostra |
| `cz_basic`, `cz_ident`, `cz_flux`, `cz_mesdistance` | as 4 consultas de `fetch` para M51, M81, M94 (os 3 Messier com `rvz_type='c'`), capturadas em 2026-10-06 |
| `vega_*`, `trappist1_*` (basic, ident, flux, mesdistance) | as 4 consultas de `fetch` para Vega (oid 2900336, otype `dS*`) e TRAPPIST-1 (oid 1353525, `LM*`), capturadas em 2026-10-06. Usadas no import ponta a ponta, em lotes de 1 nome e no truncamento real: com MAXREC=20 o `ident` de Vega (65 linhas) trunca e o de TRAPPIST-1 (16) não |
| `error_bad_adql.adql` + `.xml` | ADQL inválida de propósito. Resposta: **HTTP 400**, `Content-Type: application/xml;charset=UTF-8`, corpo VOTable com `QUERY_STATUS=ERROR` |

## sesame/ — resolvedor de nomes do CDS

Endpoint: `https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S?<nome1>&<nome2>...`
(cada nome percent-encoded, espaço como `%20`). Pares `<nome>.names` (um nome por
linha, na ordem enviada) + `<nome>.xml` (corpo da resposta). Ex.:

```
curl -s "https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S?M31&M1&M42&M45&M87&M13&M57&M82&Betelgeuse&Sgr%20A*&M54&XyzzyNotAnObject123" -o sample_resolve.xml
```

`cz_resolve` (M51, M81, M94), `vega` e `trappist1` foram capturados em 2026-10-06.

Testes de falha **não** usam respostas inventadas: os clientes de fixture só
lançam exceção na N-ésima chamada ou alteram **em memória** um corpo real
(ex. trocar `"unit":"mas"` por `"arcsec"`), e o teste declara o que alterou.

## Amostra

M31 (AGN + SA(s)b, várias mesDistance), M1 (SNR), M42 (HII), M45 (OpC, `main_id`
= `Cl Melotte   22`, com paralaxe), M87 (AGN + E-E/S0), M13 (GlC), M57 (PN),
M82 (AGN + `S`, rvz_type `z`), Betelgeuse (`s*r`, paralaxe 6.55 ± 0.83 mas, S/N
7,9), Sgr A* (`X`), **M54** (GlC, paralaxe 0.053 ± 0.011 mas, **S/N 4,82** — o
caso real de S/N < 5; nenhum dos 10 originais tinha S/N < 5: M13 tem 0.127 ±
0.010, S/N 12,7) e `XyzzyNotAnObject123` (nome inexistente).

## Fatos tirados do dado (e decisões)

- **Grafia do `ident`**: padding fixo — `M   1`, `M  45`, `M 110`. `IN ('M 1','M1')`
  não casa. Por isso a resolução de nomes é feita pelo **Sesame** (decisão do usuário).
- **Sesame**: responde `Content-Type: text/plain` (não XML) e **HTTP 200 mesmo
  sem resultado**; nome desconhecido = `<Target>` sem `<Resolver>`, só
  `<INFO> *** NNNothing found *** </INFO>`. Aceita vários nomes por requisição:
  um `<Target>` por nome, na mesma ordem, com `<name>` ecoando o pedido. O
  `<oid>` do Sesame é o mesmo `basic.oid` do TAP (verificado: M1=795871,
  M31=1575544, Sgr A*=2419046).
- **Unidades** (`metadata.unit` / TAP_SCHEMA): `ra`/`dec` = `deg`;
  `plx_value`/`plx_err` = `mas`; `rvz_radvel`/`rvz_err` = `km.s-1`;
  `galdim_majaxis`/`galdim_minaxis` = `arcmin`; `flux.flux` sem unidade
  (magnitude); `mesDistance.dist` sem unidade no metadata — a unidade vem na
  coluna `unit`, com padding: `'pc  '`, `'kpc '`, `'Mpc '`.
- **`rvz_type`**: `v` (velocidade medida), `z` (redshift medido) ou `c` (cz
  medido; só M51, M81 e M94 entre os Messier). Quando é `z` ou `c`, `rvz_err` é
  erro de **redshift**, apesar do metadata dizer `km.s-1` (M82: `9.0E-5`; M87:
  `1.2E-4`; M51: `6.671282E-6`).
- **`rvz_type='c'` = cz** (decisão do usuário, conferida nos números): o SIMBAD
  guarda z = cz/c em `rvz_redshift` e a velocidade pela conversão **relativística**
  de z, não cz. M51: z = 0.0017445402, c·z = 523.0 km/s, mas
  c((1+z)²−1)/((1+z)²+1) = 522.5438 = `rvz_radvel`. Então o redshift entra como
  `observed` (com `rvz_err`) e a velocidade como `derived_by_source`, method
  `relativistic conversion from z by SIMBAD`. A mesma conversão vale para `z`
  (M82: z = 7.3E-4 → 218.7686 = `rvz_radvel`).
- **`mesDistance.minus_err` vem negativo** (`-30` / `+30`); `method` vem com
  padding (`'T-RGB   '`) ou nulo. A mesma referência pode trazer duas distâncias
  pelo mesmo método (M31, `2015MNRAS.451..724W`, Cep: 731 e 797 kpc).
- **ADQL**: `ORDER BY` com nome qualificado (`b.oid`) é recusado com HTTP 400.
- **`morph_type`** real dos Messier inclui grafias sem estágio e com sublinhado:
  `S`, `SA`, `SB`, `SAB`, `S_AB`, `SA_B`, `SA_B_x`, `SB_a`, além de `E-E/S0`,
  `Sc(dSc)` e `1` (M104).

---

# Bloco 3 — imagens (capturadas em 2026-10-07, 22:23–22:29 UTC)

Mesma regra: respostas exatas de requisições ao vivo, com
`User-Agent: GalaxyExplorer/0.1 (personal research project)` e ≥ 1 s entre
requisições (`curl.exe -s -A "<UA>" "<url>" -o <arquivo>`). Nenhum byte alterado.

## hips2fits/ — CDS hips2fits + HiPS `properties`

| arquivo | URL | resposta |
|---|---|---|
| `dss2_color.properties` | `https://alasky.cds.unistra.fr/DSS/DSSColor/properties` | 200, `text/plain; charset=utf-8` |
| `panstarrs_color.properties` | `https://alasky.cds.unistra.fr/Pan-STARRS/DR1/color-z-zg-g/properties` | 200, `text/plain; charset=utf-8` |
| `m51_panstarrs_160x120.jpg` (+ `.url` com a URL exata) | `…/hips2fits?hips=CDS%2FP%2FPanSTARRS%2FDR1%2Fcolor-z-zg-g&ra=202.469575&dec=47.195258&fov=0.25&width=160&height=120&projection=TAN&format=jpg` | 200, `image/jpeg`, JPEG baseline 160×120, 3 componentes |
| `error_bad_hips.txt` | mesma URL com `hips=CDS%2FP%2FNoSuchSurvey` | **HTTP 400**, `application/json`: `{"title": "Unknown HiPS", …}` |
| `m31_dss2_800x600.jpg` (+ `.url` com a URL exata) | `…/hips2fits?hips=CDS%2FP%2FDSS2%2Fcolor&ra=10.684708&dec=41.26875&fov=4.156875&width=800&height=600&projection=TAN&format=jpg` — capturado 2026-10-08 22:57 UTC | 200, `image/jpeg`, 38595 bytes, JPEG baseline 800×600, 3 componentes |

Fatos verificados:
- `properties`: linhas `chave = valor` com padding; `#` inicia comentário
  (há chaves comentadas, ex. `#hips_service_url`); nenhuma chave repetida nas duas.
  `creator_did` existe e termina com o id do HiPS (`ivo://CDS/P/DSS2/color`,
  `ivo://CDS/P/PanSTARRS/DR1/color-z-zg-g`). `hips_license = ODbL-1.0` nos dois.
  `obs_copyright_url` é `http:` nos dois. O PanSTARRS tem `\n` literal (barra + n)
  dentro de `obs_description`.
- Documentação do serviço (GET na URL base, 2026-10-07): `projection` é
  "Compulsory if wcs is not provided" (o serviço aceita sem, mas não dependemos
  de default não documentado: usamos `TAN`); `fov` é "the size of the largest
  dimension of the image". Acknowledgment pedido: `This research made use of
  hips2fits,\footnote{https://alasky.cds.unistra.fr/hips-image-services/hips2fits}
  a service provided by CDS.` (gravado sem a marcação LaTeX do `\footnote`, cujo
  conteúdo é a própria `homepage_url` da fonte).
- `m31_dss2_800x600`: parâmetros **exatamente** os que o `CutoutPlanner` gera para
  M31 com a configuração de `application.yml` (`fov-factor` 1,25 × `galdim_majaxis`
  199,53′ do `sample_basic.json` = 4,156875° → passa do `max-fov-deg` 1,0 do PanSTARRS,
  cai no DSS2; posição `ra`/`dec` do `sample_basic.json` com 6 casas; 800×600).
  O `FixtureHips2FitsClient` só devolve esse JPG se a URL gerada pelo plano for igual
  à do `.url`. Usado no teste "objeto grande → recorte é hero, ESA na galeria".
- `hips_copyright = CNRS/Unistra` nas duas `properties`: é o titular da licença
  `hips_license` do HiPS (diferente de `obs_copyright`, o crédito dos dados). Vai
  para `hips_survey_snapshot.hips_copyright` e para o `rights_text` do recorte
  (`HiPS © CNRS/Unistra — ODbL-1.0`).
- `HEAD` na URL base do hips2fits (e do espelho `alaskybis.cds.unistra.fr`)
  responde 405; `GET` funciona. O espelho existe.

## esa/ — ESA/Hubble e ESA/Webb

AVM: `https://<host>/images/<id>/api/json/` → 200, **`Content-Type: text/plain`**,
corpo JSON. Busca: `https://<host>/images/archive/search/?adv=&subject_name=<termo>`
→ 200, `text/html`.

| arquivo | origem |
|---|---|
| `hubble_avm_heic0506a.json` | M51, Observation, HST/ACS — caso aceito |
| `webb_avm_weic2216b.json` | Pilares da Criação (M16), Observation, JWST/NIRCam |
| `hubble_avm_heic0506b.json` | M16 (Eagle Nebula); é o 3º resultado da busca `Messier 1` — caso "não é o objeto" |
| `hubble_avm_heic0506c.json` | M51, Observation, **Facility = Digitized Sky Survey 2** — imagem de contexto de solo |
| `hubble_avm_heic2014a.json` | Betelgeuse, **Type = Artwork**, `Facility` null, `Spatial.*` listas vazias |
| `hubble_avm_opo9604a.json` | Betelgeuse, **Type = Collage** |
| `hubble_avm_opo9604b.json` | Betelgeuse, Observation HST, quadro de 100×100 px (0,34″) |
| `hubble_avm_heic1502a.json` | M31 (PHAT), quadro 58,0′ × 18,5′ |
| `hubble_avm_heic0601a.json` | M42/M43, quadro 30,0′ × 30,0′ |
| `hubble_search_messier_51.html`, `webb_search_messier_16.html`, `hubble_search_messier_1.html` | buscas por `Messier+N` |
| `hubble_search_messier_31.html` | `https://esahubble.org/images/archive/search/?adv=&subject_name=Messier+31`, capturado 2026-10-08 22:56 UTC: 200 `text/html; charset=utf-8`, 53765 bytes; `heic1502a` em 1º (depois `heic2501a`, `heic1112a`, `opo1203d`, …) |
| `hubble_search_m_51.html`, `webb_search_m_16_noresults.html` | buscas por `M+51` / `M+16` (ver abaixo) |
| `hubble_screen_heic0506a.jpg` | `https://cdn.esahubble.org/archives/images/screen/heic0506a.jpg`, capturado 2026-10-08 00:26 UTC: 200 `image/jpeg`, 221167 bytes, JPEG 1280×888 (a AVM declara 1280×889 para o "Small") — usado nos testes de integração |
| `hubble_screen_heic1502a.jpg` | `https://cdn.esahubble.org/archives/images/screen/heic1502a.jpg`, capturado 2026-10-08 22:56 UTC: 200 `image/jpeg`, 142195 bytes, JPEG 1280×409 (a AVM declara 1280×410 para o "Small") — M31 nos testes de hero/curadoria |

Fatos verificados (mudam o plano):
- **A busca é "contém" sobre `Subject.Name`, e a AVM grava `Messier 51`**:
  `subject_name=M+51` devolve 11 imagens `opo*` e **não** `heic0506a`;
  `subject_name=Messier+51` devolve 17–20 (com `heic0506a` em 1º). No Webb,
  `M+16` devolve "No images were found." e `Messier+16` devolve 3. Por isso o
  termo de busca é `Messier N`. "Contém" também significa que `Messier 1` traz
  M10–M19 e M100–M110 (113 resultados) — o casamento de nome filtra.
- O HTML de busca **não** tem `href="/images/<id>/"` nos resultados: eles vêm num
  bloco `<script>var images = [ { id: '<id>', title: …, url: '/images/<id>/' }, … ];`.
  Sem resultado: `<div …>No images were found.</div>`.
- Campos AVM de texto vêm como repr de bytes Python: `"b'…'"` ou `b"…"` (quando o
  texto tem apóstrofo, ex. `Description` de heic0506a), com escapes `\xNN` de UTF-8
  (`\xe2\x80\x99`, `\xc2\xa0`). Listas (`Subject.Name`, `Facility`, `Instrument`,
  `Spectral.Band`, `Spatial.*`) vêm como **arrays JSON reais** — a forma "lista como
  string" prevista no plano não apareceu. Campos simples (`Type`, `Date`, `ID`,
  `Spatial.CoordinateFrame`) vêm como string normal.
- `Facility` do Webb = `James Webb Space Telescope`; do Hubble = `Hubble Space Telescope`.
- `Spatial.ReferenceDimension` **existe** (dimensões do original, em px, como
  string `"11477.0"`), junto com `ReferencePixel`, `Scale` (graus/px; x negativo),
  `Rotation`, `CoordinateFrame = ICRS`. Em Artwork/Collage os `Spatial.*` vêm `[]`.
- `Credit` sem marcação HTML em todas as fixtures.
- `HEAD https://cdn.esahubble.org/archives/images/screen/heic0506a.jpg` → 200
  `image/jpeg`, 221167 bytes; `HEAD https://cdn.esawebb.org/archives/images/screen/weic2216b.jpg`
  → 200 `image/jpeg`, 1088429 bytes (template `screen` confirmado nos dois; o JPG
  não entra no repositório). A AVM traz o mesmo endereço em `formats_url.screen`.
- Termos e licenças (200 em 2026-10-07): `https://esahubble.org/copyright/`,
  `https://esawebb.org/copyright/`, `https://cds.unistra.fr/legals/`,
  `https://creativecommons.org/licenses/by/4.0/` (título "Attribution 4.0
  International"), `https://opendatacommons.org/licenses/odbl/1-0/` (título "Open
  Data Commons Open Database License (ODbL) v1.0").

## simbad/m16_* + sesame/m16.* (2026-10-07, 22:25 UTC)

M16 resolvido pelo Sesame (oid 2585552, `OpC`, `M  16`) e as 4 consultas de `fetch`
no formato exato de `SimbadQueries`. `galdim_majaxis = 80` arcmin. O centro de
`weic2216b` fica a ~4,3′ do centro do objeto — fora do quadro de 4,2′ × 7,3′ da
imagem, mas dentro da extensão do objeto.
