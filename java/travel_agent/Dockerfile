# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn compile dependency:copy-dependencies -DincludeScope=runtime -B

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/classes /app/classes
COPY --from=build /app/target/dependency /app/dependency

EXPOSE 8080

CMD ["java", "-cp", "/app/classes:/app/dependency/*", "com.example.travel.TravelAgent"]
