# china-workday 构建镜像（多阶段 + 阿里云镜像加速 + CFCA 证书）
# 执行：在 deploy 目录下运行 ../deploy/build.sh
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY deploy/cert/cfca-ev-root.der .
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/central</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
WORKDIR /app
COPY --from=build /build/target/china-workday-*.jar app.jar
COPY --from=build /build/cfca-ev-root.der /tmp/cfca-ev-root.der
RUN keytool -importcert -noprompt -cacerts -alias cfca-ev-root -file /tmp/cfca-ev-root.der -storepass changeit && \
    rm -f /tmp/cfca-ev-root.der
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
