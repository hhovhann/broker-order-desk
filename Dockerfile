# 1) build the React UI   2) build the jar with the UI inside   3) run on a small JRE as a non-root user
FROM node:22-alpine AS web
WORKDIR /w
COPY web/package.json web/package-lock.json web/
RUN cd web && npm ci
COPY web web
RUN cd web && npm run build            # writes to /w/src/main/resources/static

FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src src
COPY --from=web /w/src/main/resources/static src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
USER 10001
WORKDIR /app
COPY --from=build /app/target/broker-order-desk-0.1.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+UseCompactObjectHeaders -XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
