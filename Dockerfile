# JARs are built once, sequentially, by the Compose builder before image creation.
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG SERVICE
COPY build/lite-jars/${SERVICE}.jar app.jar
COPY scripts/healthcheck.sh healthcheck.sh
RUN sed -i 's/\r$//' healthcheck.sh
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-jar","app.jar"]
