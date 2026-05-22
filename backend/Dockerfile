# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY backend/pom.xml backend/pom.xml
COPY judge-worker/pom.xml judge-worker/pom.xml

COPY common common
COPY backend backend
RUN --mount=type=cache,id=local-oj-maven,target=/root/.m2,sharing=locked \
    set -eux; \
    for attempt in 1 2 3 4 5; do \
        if mvn -B -U -s .mvn/settings-cn.xml -pl backend -am package -Dmaven.test.skip=true; then \
            break; \
        fi; \
        find /root/.m2/repository -name "*.lastUpdated" -delete; \
        find /root/.m2/repository -name "*.part" -delete; \
        if [ "$attempt" = "5" ]; then \
            exit 1; \
        fi; \
        sleep 5; \
    done

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/backend/target/backend-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
