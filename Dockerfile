FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY --chmod=0755 mvnw ./
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
COPY pom.xml ./
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN useradd --system --user-group --no-create-home --home-dir /app --shell /usr/sbin/nologin learnflow
COPY --from=build /workspace/target/*.jar app.jar
USER learnflow:learnflow

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
