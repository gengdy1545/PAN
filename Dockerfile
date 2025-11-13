FROM eclipse-temurin:17-jre

WORKDIR /app
ENV PAN_HOME=/app

RUN mkdir -p /app/lib /app/etc /app/log

COPY target/*.jar /app/lib/app.jar
COPY src/main/resources/pan.properties /app/etc/pan.properties

ENTRYPOINT ["java", \
            "-Dpan.home=/app", \
            "-jar", "/app/lib/app.jar", \
            "--pan.mode=oneshot"]