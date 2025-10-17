FROM gradle:8.7-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle build

FROM openjdk:17-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/bandoriscription-0.0.1-all.jar .
EXPOSE 8080
CMD ["java", "-jar", "bandoriscription-0.0.1-all.jar"]
