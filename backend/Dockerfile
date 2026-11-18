# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
# Tesseract (OCR de fotos e PDFs digitalizados) com idioma português
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por curl \
 && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 1001 app && mkdir -p /data/storage && chown app /data/storage
WORKDIR /app
COPY --from=build /app/target/comissao-api-*.jar app.jar
USER app
ENV STORAGE_DIR=/data/storage \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
