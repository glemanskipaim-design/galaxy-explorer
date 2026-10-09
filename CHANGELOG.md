# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [0.1.0-beta] — 2026-10-08

Primeira versão beta: `docker compose up -d --build` sobe banco, API e interface, e
carrega os dados reais na primeira subida.

### Empacotamento (Beta-2)
- `backend/Dockerfile` multi-stage (Maven + Temurin 17 → JRE 17 Alpine), usuário sem
  privilégio, healthcheck em `/actuator/health`.
- `frontend/Dockerfile` multi-stage (Node 24 → nginx 1.30) com SPA fallback, proxy
  `/api/` para o backend (mesma origem) e headers de segurança (CSP de produção,
  `nosniff`, `Referrer-Policy`).
- `docker-compose.yml`: backend sem porta no host, frontend só em `127.0.0.1:8088`,
  volumes nomeados para banco e imagens, `depends_on` por healthcheck. O modo
  desenvolvimento (`docker compose up -d postgres`) continua igual.
- Serviço `seed`: carga inicial idempotente e retomável (110 Messier + imagens), com
  o estado lido do banco; novas flags `--galaxy.seed.only-missing`,
  `--galaxy.images.run.strict` e `--galaxy.images.run.skip-if-complete`.
- `README.md` na raiz com execução, créditos e licenças das fontes.

### Imagens na interface (Beta-1)
- Página do objeto com imagem de destaque, crédito literal sempre visível, licença,
  provedor, missão/instrumento e link para a página original; "Imagem não disponível"
  com o motivo.
- Miniaturas na busca e nas listas de categoria; imagens só da própria API
  (CSP `img-src 'self'`), sem layout shift.

### Backend de imagens (Bloco 3a)
- Recortes hips2fits (PanSTARRS ou DSS2) e fotos ESA/Hubble e ESA/Webb selecionadas por
  metadados AVM (observação real, licença CC BY 4.0 exata, objeto dentro do quadro);
  curadoria manual em `images/curation.csv`.
- Arquivos baixados com allowlist de hosts e validação, endereçados por SHA-256 e
  servidos em `/api/v1/images/files/{sha}.jpg`. Migração V8.

### Frontend (Bloco 2)
- React 19 + TypeScript strict + Vite 8: busca com importação sob demanda, página do
  objeto com valor, incerteza, fonte e referência por medição, ausências com motivo e
  selo "Fontes divergem" (regra 3σ).
- API de categorias (`/api/v1/categories`), ordenação natural (V7).

### Fundação (Bloco 1)
- Spring Boot 4.1.1 / Java 17, PostgreSQL 17, JdbcClient + Flyway (V1–V6) com
  proveniência completa por medição e payload cru auditável.
- Conectores CDS Sesame + SIMBAD TAP com limite de taxa, retry e circuit breaker;
  seed real dos 110 objetos Messier; API `/api/v1/search`, `/objects/{slug}`,
  `/imports`.
