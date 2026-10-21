# Solo el runtime de Java 21: el .jar ya viene compilado por Maven
FROM eclipse-temurin:21-jre

# curl: lo usa el health check de ECS (curl -f .../actuator/health)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Usuario sin privilegios: si alguien compromete la app, no es root
RUN useradd --system --uid 1001 app

COPY target/reservas-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]