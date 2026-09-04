# ==============================================================================
# FUTFATEC - DOCKERFILE MULTI-STAGE OTIMIZADO
# ==============================================================================

# Estágio 1: Build da Aplicação com Maven e Java 17
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Compila o projeto e copia todas as dependências de runtime para target/lib
RUN mvn clean package -DskipTests --no-transfer-progress

# ------------------------------------------------------------------------------
# Estágio 2: Imagem Final de Execução (Leve, Rápida e Segura)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Instala curl para healthchecks básicos
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Diretórios persistentes para banco relacional H2, uploads de logos e classes
RUN mkdir -p /app/data /app/uploads /app/target/classes /app/lib

# Copia as classes compiladas e bibliotecas de runtime isoladas (sem poluir com cache do Maven)
COPY --from=builder /build/target/classes /app/target/classes
COPY --from=builder /build/target/lib /app/lib

# Copia os arquivos estáticos do frontend sempre atualizados a cada deploy
COPY index.html style.css script.js /app/

# Portas suportadas (padrão local 8085 e padrão nuvem 10000)
EXPOSE 8085 10000

# Variáveis de Ambiente padrão
ENV PORT=8085
ENV ADMIN_USER=adminfatec
ENV ADMIN_PASSWORD=adminfatec2026

# Volumes persistentes (para não perder os times nem as logos ao reiniciar o container)
VOLUME ["/app/data", "/app/uploads"]

# Script de entrada: classpath limpo e universal via target/classes:lib/*
CMD ["java", "-cp", "target/classes:lib/*", "br.com.fatec.futefatec.ServidorFuteFatec"]

