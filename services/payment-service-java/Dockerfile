FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and proto files
COPY services/payment-service-java/pom.xml .
COPY proto /app/proto

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY services/payment-service-java/src ./src

# Build the application
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose gRPC port
EXPOSE 9091

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
