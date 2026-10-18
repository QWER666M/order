# ============================================================
#  多阶段构建（Multi-stage build）
#
#  阶段 1  build  ：用带 Maven + JDK 的镜像编译、打包
#  阶段 2  runtime：只保留 JRE + 一个 jar，构建工具全部丢掉
#
#  为什么必须分两个阶段？
#    如果直接拿 maven 镜像当运行环境，最终镜像里会带着
#    Maven 本体、整个 ~/.m2 依赖缓存、甚至源码，
#    体积 600MB+，而且把构建工具暴露在生产容器里（攻击面变大）。
#    分阶段之后，第二阶段只 COPY 一个 jar 过去，能得到 ~200MB 的干净镜像。
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# ★ 先只复制 pom.xml 并预下载依赖，再复制源码。
#   Docker 每个指令是一层，某一层的输入变了，它【以及之后所有层】都失效重建。
#   pom.xml 很少改、src 天天改，所以这个顺序能把"下载依赖"这一步缓存住；
#   反过来（先 COPY src 再跑 mvn）会导致改一行代码就重新下载全部依赖。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# ★ 不用 root 跑应用：容器被突破时，root 的危害远大于普通用户。
#   这条几乎是容器安全基线的必查项。
RUN useradd --system --uid 1001 --create-home appuser

# --chown 让 appuser 拥有 jar；不加的话文件属主是 root，虽然只读也能跑，
# 但保持"运行用户拥有自己的文件"是更清晰的做法
COPY --from=build --chown=appuser:appuser /build/target/*.jar app.jar

USER appuser
EXPOSE 8080

# ★ 用 exec 形式（JSON 数组），不要用 shell 形式。
#   shell 形式会变成 "sh -c java -jar ..."，java 不再是 1 号进程，
#   收不到 docker stop 发来的 SIGTERM，只能等 10 秒后被 SIGKILL 强杀，
#   Spring 的优雅停机（关闭连接池、处理完在途请求）就失效了。
#
#   需要调 JVM 参数时不要改这里，用环境变量 JAVA_TOOL_OPTIONS（JVM 会自动读取），
#   这样同一个镜像能在不同环境跑不同参数 —— 也就是"配置外置"。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
