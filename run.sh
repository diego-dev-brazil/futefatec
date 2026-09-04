#!/bin/bash
# ==============================================================================
# Script de Execução Rápida do FutFatec (Tomcat Embutido + Jakarta Servlets)
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "================================================================"
echo "    [FUTFATEC] Compilando e Inicializando Servidor Java...     "
echo "================================================================"

mkdir -p target/classes uploads data

CP="$HOME/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.43/tomcat-embed-core-10.1.43.jar:$HOME/.m2/repository/jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar:$HOME/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar"

# 1. Compilação
echo ">> Compilando código-fonte Java..."
javac -cp "$CP" -d target/classes $(find src/main/java -name "*.java")

# 2. Execução
echo ">> Iniciando servidor Apache Tomcat Embutido..."
java -cp "target/classes:$CP" br.com.fatec.futefatec.ServidorFuteFatec

