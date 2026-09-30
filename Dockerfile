# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
ENV MAVEN_OPTS=-Xmx512m
WORKDIR /app

# 阿里云 Maven 镜像(国内服务器直连 Central 会卡死)
COPY settings.xml .
COPY pom.xml .
RUN mvn -B -q -s settings.xml dependency:go-offline

COPY src ./src
RUN mvn -B -s settings.xml clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
