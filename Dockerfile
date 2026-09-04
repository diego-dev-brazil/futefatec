# ==============================================================================
# FUTFATEC - DOCKERFILE MULTI-STAGE
# ==============================================================================

# Estágio 1: Build da Aplicação com Maven e Java 17
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Compila o projeto limpo do zero e empacota
RUN mvn clean package -DskipTests --no-transfer-progress

# ------------------------------------------------------------------------------
# Estágio 2: Imagem Final de Execução (Leve e Segura)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Instala curl para healthchecks básicos
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Diretórios persistentes para banco relacional H2 e uploads de logos
RUN mkdir -p /app/data /app/uploads /app/target/classes

# Copia as classes compiladas e dependências do Maven
COPY --from=builder /build/target/classes /app/target/classes
COPY --from=builder /root/.m2/repository /root/.m2/repository

# Invalidação explícita de cache: garante que o Render sempre copie a versão mais recente dos arquivos estáticos
ARG CACHEBUST=20260904
COPY index.html /app/index.html
COPY style.css /app/style.css
COPY script.js /app/script.js

# Variáveis de Ambiente padrão
ENV ADMIN_PASSWORD=fatec2026

# Volumes persistentes (para não perder os times nem as logos ao reiniciar o container)
VOLUME ["/app/data", "/app/uploads"]

# Script de entrada para compor o classpath e iniciar o Tomcat Embarcado
CMD java -cp "target/classes:/root/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.43/tomcat-embed-core-10.1.43.jar:/root/.m2/repository/jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar:/root/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar" br.com.fatec.futefatec.ServidorFuteFatec

