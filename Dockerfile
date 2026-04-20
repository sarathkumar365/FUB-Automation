# syntax=docker/dockerfile:1

FROM node:20-alpine AS ui-build
WORKDIR /workspace/ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS app-build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src ./src
COPY --from=ui-build /workspace/ui/dist/ ./src/main/resources/static/
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=app-build /workspace/target/*.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
