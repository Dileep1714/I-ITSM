# I-ITSM backend: build with the Maven wrapper, run on a slim Java 21 runtime.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 iitsm
COPY --from=build /src/app.jar app.jar
USER iitsm
EXPOSE 8080
# Leave headroom for a small cloud VM; tune with JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
