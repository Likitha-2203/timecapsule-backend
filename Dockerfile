# ==========================================
# Build Stage: Maven with Java 17
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy POM and download dependencies to optimize caching
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build the executable jar (skipping unit tests during packaging)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Runtime Stage: Lightweight Temurin JRE 17
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy executable jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Render assigns a dynamic port via the PORT environment variable
ENV PORT=8080
EXPOSE 8080

# JVM container options:
# - UseContainerSupport & MaxRAMPercentage=75.0 ensure the JVM stays within Render's 512MB RAM limit
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
