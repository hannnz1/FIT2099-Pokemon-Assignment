FROM node:24.18.0-bookworm-slim AS frontend
WORKDIR /build
COPY web-client/package*.json web-client/
RUN cd web-client && npm ci --no-audit --no-fund
COPY web-client web-client
COPY maps maps
COPY tools tools
RUN node tools/export-map.mjs && cd web-client && npm test && npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml .
COPY src src
COPY test test
COPY resources resources
COPY --from=frontend /build/resources resources
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/target/fit2099-pokemon-assignment-1.0.0.jar /app/game.jar
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/game.jar"]
