# ==========================================
# NEO FHIR SERVER - DOCKERFILE (Apple Silicon Compatible)
# ==========================================

# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copiar archivos de configuración Maven
COPY pom.xml .
COPY src ./src

# Construir el proyecto
RUN mvn clean package -DskipTests

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre

LABEL maintainer="Darío Pérez <dario@hec.cl>"
LABEL description="Neo FHIR Server - Servidor FHIR R4 con perfiles CL-CORE"
LABEL version="1.0.0"

WORKDIR /app

# Copiar JAR desde etapa de build
COPY --from=builder /build/target/neo-fhir-server.jar app.jar

# Crear directorios necesarios
RUN mkdir -p /data/uploads /data/temp /logs

# Variables de entorno por defecto
ENV JAVA_OPTS="-Xms512m -Xmx2048m"
ENV SERVER_PORT=8080

# Exponer puerto
EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
