FROM eclipse-temurin:21-jre
WORKDIR /app
COPY game.jar /app/game.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-Xms64m","-Xmx320m","-jar","/app/game.jar"]
