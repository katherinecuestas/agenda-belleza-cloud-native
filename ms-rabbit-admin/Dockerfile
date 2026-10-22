# ms-rabbit-admin: imagen multi-stage. La máquina que la construye solo necesita Docker
# (ni JDK ni Maven): el jar se compila dentro de la etapa "build".

# ── Etapa 1: compilar ──
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build

# Primero solo el pom: si el código cambia pero las dependencias no, Docker reutiliza esta capa
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests corren en local y en CI; aquí solo se empaqueta
RUN mvn -B -q package -DskipTests

# ── Etapa 2: ejecutar (solo el JRE, sin Maven ni código fuente) ──
FROM eclipse-temurin:21-jre

# curl: lo usa el healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Usuario sin privilegios: si alguien compromete la app, no es root
RUN useradd --system --uid 1001 app

COPY --from=build /build/target/ms-rabbit-admin-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8082/actuator/health || exit 1

# El heap se ajusta al límite de memoria del contenedor
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
