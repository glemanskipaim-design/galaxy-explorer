#!/bin/sh
# Carga inicial de dados reais (servico "seed" do docker-compose).
#
# O estado "carga concluida" vem do BANCO, nunca de arquivo: banco novo (ex. depois
# de "docker compose down -v") = carga refeita, mesmo com as imagens num bind mount.
#
# 1. profile "seed" com only-missing: ingere do SIMBAD so os Messier que ainda nao
#    existem no banco (banco vazio = os 110; banco completo = nenhuma requisicao).
#    Qualquer consulta que falhe da exit 1.
# 2. profile "images" com skip-if-complete: se o run manual mais recente de cada fonte
#    (CUTOUT, ESA_HUBBLE, ESA_WEBB) terminou ok, nada a fazer; senao recortes hips2fits
#    + ESA/Hubble + ESA/Webb, pulando o que ja tem imagem (refresh=false). strict: qualquer
#    objeto que falhe da exit 1 (o run fica partial e a proxima subida tenta de novo).
#
# Se a rede falhar, basta subir de novo (ou "docker compose run --rm seed"): so o que
# faltou e consultado. GALAXY_SEED_FORCE=1 refaz a passada de imagens mesmo com runs ok
# (continua incremental). GALAXY_SEED_QUERIES (ex. "M51,M31") limita o passo 1 a esses
# nomes — so para teste rapido.
set -eu

SKIP=true
if [ "${GALAXY_SEED_FORCE:-0}" = "1" ]; then
    SKIP=false
fi

echo "seed: passo 1/2 — objetos Messier (SIMBAD/Sesame)"
java -jar /app/app.jar --spring.profiles.active=seed --galaxy.seed.only-missing=true \
    --galaxy.seed.queries="${GALAXY_SEED_QUERIES:-}"

echo "seed: passo 2/2 — imagens (CUTOUT, ESA_HUBBLE, ESA_WEBB)"
java -jar /app/app.jar --spring.profiles.active=images \
    --galaxy.images.run.sources=CUTOUT,ESA_HUBBLE,ESA_WEBB --galaxy.images.run.objects=all \
    --galaxy.images.run.strict=true --galaxy.images.run.skip-if-complete="$SKIP"

echo "seed: carga inicial concluida."
