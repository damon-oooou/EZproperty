# v0.6 阶段 C:后端容器化(Railway 用 Dockerfile 部署)
# 多阶段:maven 构建 -> 纯 JRE 运行,镜像小、无构建工具残留。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 单独拉依赖,充分利用 Docker layer 缓存(源码改动不重拉依赖)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Railway 注入 PORT,application-prod.yml 里 server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
