FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
COPY build.sh .
RUN sh build.sh

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
EXPOSE 8080
CMD ["java", "-cp", "out", "rl.Server", "8080", "1000", "2000"]
