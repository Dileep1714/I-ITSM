# I-ITSM as ONE service (for Render's free plan): Spring Boot serves the API, the WebSocket and the React app.
# Build context: the repository root.

# 1. Web app
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Backend, with the web app bundled as static files
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

# 3. Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 iitsm
COPY --from=backend /src/app.jar app.jar
COPY render/start.sh start.sh
USER iitsm
# Tuned for a 512 MB free instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=180m"
EXPOSE 10000
ENTRYPOINT ["sh", "start.sh"]
