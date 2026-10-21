# ============================================================
#  多阶段构建（Multi-stage build）
#
#  阶段 1  build  ：用带 Maven + JDK 的镜像编译、打包
#  阶段 2  runtime：只保留 JRE + 一个 jar，构建工具全部丢掉
#
#  为什么必须分两个阶段？
#    如果直接拿 maven 镜像当运行环境，最终镜像里会带着 Maven 本体、
#    整个 ~/.m2 依赖缓存、甚至源码，体积 700MB 以上，
#    而且把构建工具暴露在生产容器里（攻击面变大）。
#    分阶段之后，第二阶段只 COPY 一个 jar 过去，
#    CI 实测最终镜像 352MB。
#
#  ★ 注意：Dockerfile 里【每一行注释都必须以 # 开头】。
#    注释换行时忘了写 #，Docker 会把后半行当成指令，报
#    "dockerfile parse error ... unknown instruction"。
#    （这次就是被 CI 的 docker-image 任务抓到的。）
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

# HEALTHCHECK 需要一个 HTTP 客户端，而这个基础镜像里没有 curl。
# 装完立刻清掉 apt 列表，否则会在镜像里留下几十 MB 的缓存。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# --chown 让 appuser 拥有 jar；不加的话文件属主是 root，虽然只读也能跑，
# 但保持"运行用户拥有自己的文件"是更清晰的做法
COPY --from=build --chown=appuser:appuser /build/target/*.jar app.jar

USER appuser
EXPOSE 8080

# ★ 关键：查的是 readiness 而不是端口存活。
#   -f           HTTP 4xx/5xx 视为失败（503 时 curl 会返回非 0）
#   --start-period  启动阶段给足时间（应用启动 + 建表），期间的失败不计入 retries
#   --interval/--retries  连续 3 次失败才判定 unhealthy，避免偶发抖动
#   于是 docker compose ps 里会显示 (healthy)，而不是永远 (running)。
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

# ★ 用 exec 形式（JSON 数组），不要用 shell 形式。
#   shell 形式会变成 "sh -c java -jar ..."，java 不再是 1 号进程，
#   收不到 docker stop 发来的 SIGTERM，只能等 10 秒后被 SIGKILL 强杀，
#   Spring 的优雅停机（关闭连接池、处理完在途请求）就失效了。
#
#   需要调 JVM 参数时不要改这里，用环境变量 JAVA_TOOL_OPTIONS（JVM 会自动读取），
#   这样同一个镜像能在不同环境跑不同参数 —— 也就是"配置外置"。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
