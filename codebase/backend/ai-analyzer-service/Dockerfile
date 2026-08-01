FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app
COPY backend/ai-analyzer-service/pom.xml .
RUN mvn dependency:go-offline -B

COPY contracts/openapi.yml .
COPY backend/ai-analyzer-service/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre
##Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

##Create user to avoid permission issues
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
