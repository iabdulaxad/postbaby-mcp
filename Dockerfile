FROM openjdk:21-ea-1-jdk-slim
WORKDIR /app
COPY target/function-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
