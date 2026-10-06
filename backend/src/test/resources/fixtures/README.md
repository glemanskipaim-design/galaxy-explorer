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
