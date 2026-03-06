FROM eclipse-temurin:21-jre-alpine

WORKDIR /usr/src/app
COPY *.jar ./app.jar

RUN chown -R root:root ./app.jar

USER root
ENTRYPOINT ["java", "-jar", "./app.jar", "--port=8080"]