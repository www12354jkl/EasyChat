# 多阶段构建：在 backend 目录执行
# docker build --build-arg MODULE=easychat-user -t easychat-user .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY easychat-common easychat-common
COPY easychat-gateway easychat-gateway
COPY easychat-user easychat-user
COPY easychat-chat easychat-chat
ARG MODULE=easychat-user
RUN mvn -pl ${MODULE} -am package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE=easychat-user
COPY --from=build /build/${MODULE}/target/${MODULE}-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
