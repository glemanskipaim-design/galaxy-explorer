# Galaxy Explorer — backend (Spring Boot 4 / Java 17).
#
# Multi-stage: o JDK e o Maven so existem no estagio de build; o runtime e um JRE.
# Tags fixas, conferidas no Docker Hub em 2026-10-08. Ao atualizar, troque a tag
# inteira (nao use "latest" nem tag flutuante tipo "17-jre").

# ── build ──────────────────────────────────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /src

# Dependencias primeiro: a camada so e refeita quando o pom.xml muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Testes NAO rodam aqui: a suite de integracao usa Testcontainers (precisa do daemon
# Docker, que nao existe dentro do build). Eles rodam no qa-gate antes de cada commit.
RUN mvn -B -q -DskipTests package \
    && cp target/galaxy-explorer-backend-*.jar /src/app.jar

# ── runtime ────────────────────────────────────────────────────────────────────
# Alpine e a variante "slim" oficial do Temurin (nao existe tag -slim para o JRE).
FROM eclipse-temurin:17.0.20.1_1-jre-alpine-3.24

# Usuario sem privilegio, com UID fixo (o volume de imagens fica com este dono).
RUN addgroup -S -g 10001 galaxy && adduser -S -D -H -u 10001 -G galaxy galaxy

WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
COPY --chmod=0755 docker/seed.sh /app/seed.sh

# Diretorio das imagens hospedadas (application.yml: ./data/images relativo a /app).
# Criado com o dono certo ANTES do volume ser montado: o Docker copia o dono para um
# volume nomeado vazio na primeira montagem.
RUN mkdir -p /app/data/images && chown -R galaxy:galaxy /app/data

USER galaxy

# Dentro do container escuta em todas as interfaces; quem limita o acesso e o compose
# (a porta do backend NAO e publicada no host — so o nginx do frontend fala com ela).
ENV GALAXY_BIND_ADDRESS=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# wget vem do busybox da imagem Alpine. /actuator/health inclui o check do banco.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD wget -qO /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
