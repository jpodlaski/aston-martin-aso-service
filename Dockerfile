# Runs the pre-built Spring Boot JAR (built on the host with ./mvnw package).
# Keeps the image small: no Maven download inside Docker — just JRE + jar.
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
