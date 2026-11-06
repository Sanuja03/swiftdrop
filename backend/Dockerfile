# Build the Spring Boot application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven Wrapper and project files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy application source code
COPY src/ src/

# Build the application
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the packaged JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
