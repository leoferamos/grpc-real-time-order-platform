FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and proto files
COPY pom.xml .
COPY ../proto /app/proto

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install OpenSSL for cert generation
RUN apk add --no-cache openssl

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Copy certificate generation script
COPY ../infra/certs/generate-certs.sh /generate-certs.sh

# Generate certificates at build time
RUN mkdir -p /certs && chmod +x /generate-certs.sh && /generate-certs.sh
ENV CERTS_DIR=/certs

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/orders/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
