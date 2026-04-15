# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom first for dependency resolution
COPY pom.xml .
COPY registration-services/pom.xml registration-services/
COPY payment-services/pom.xml payment-services/
COPY notification-services/pom.xml notification-services/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl registration-services -am -q || true

# Copy source code
COPY registration-services/src registration-services/src

# Build the JAR
RUN mvn clean package -pl registration-services -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create directory for file storage
RUN mkdir -p /app/colive-files

COPY --from=build /app/registration-services/target/registration-services-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8082

ENV JAVA_OPTS=""
ENV SERVER_PORT=8082
ENV MONGODB_URI=mongodb://mongo:27017/admin
ENV MONGODB_DATABASE=admin
ENV NOTIFICATION_SERVICE_URL=http://notification-services:8084
ENV PAYMENT_SERVICE_URL=http://payment-services:8083
ENV FILE_STORAGE_TYPE=LOCAL
ENV FILE_STORAGE_LOCAL_PATH=/app/colive-files/

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
