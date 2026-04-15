# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom first for dependency resolution
COPY pom.xml .
COPY registration-services/pom.xml registration-services/
COPY payment-services/pom.xml payment-services/
COPY notification-services/pom.xml notification-services/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl payment-services -am -q || true

# Copy source code
COPY payment-services/src payment-services/src

# Build the JAR
RUN mvn clean package -pl payment-services -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/payment-services/target/payment-services-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083

ENV JAVA_OPTS=""
ENV SERVER_PORT=8083
ENV MONGODB_URI=mongodb://mongo:27017/admin
ENV MONGODB_DATABASE=payments
ENV NOTIFICATION_SERVICE_URL=http://notification-services:8084
ENV RAZORPAY_KEY_ID=rzp_test_SYwvJpnDcJEIvZ
ENV RAZORPAY_KEY_SECRET=acXTG6sTD8L4eAgoKbxwHmzF

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
