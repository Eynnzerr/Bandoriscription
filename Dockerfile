FROM gradle:9.1-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle build -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk --no-cache add curl

COPY --from=build /home/gradle/project/build/libs/*-all.jar app.jar
EXPOSE 18080
HEALTHCHECK --interval=60s --timeout=5s --start-period=5s --retries=3 CMD curl -f http://localhost:18080/health || exit 1
CMD ["java", "-jar", "app.jar"]
