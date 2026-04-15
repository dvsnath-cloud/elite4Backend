# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom first for dependency resolution
COPY pom.xml .
COPY registration-services/pom.xml registration-services/
COPY payment-services/pom.xml payment-services/
COPY notification-services/pom.xml notification-services/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl notification-services -am -q || true

# Copy source code
COPY notification-services/src notification-services/src

# Build the JAR
RUN mvn clean package -pl notification-services -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/notification-services/target/notification-services-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8084

ENV JAVA_OPTS=""
ENV SERVER_PORT=8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
