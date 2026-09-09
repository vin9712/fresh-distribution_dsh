#!/usr/bin/env bash
# ============================================================
# dev.sh — 一键启动/关闭前后端（macOS / Linux）
#   后端: lin-entry        (Spring Boot, profile=local, 端口 8090)
#   前端: RuoYi-Vue3       (vite, 端口 1025, 代理 /dev-api、/jmreport → 8090)
#
# 用法:
#   ./dev.sh start                          启动前后端（后端先起，再起前端）
#   ./dev.sh stop                            停止前后端
#   ./dev.sh restart                         重启
#   ./dev.sh status                          查看运行状态
#   ./dev.sh logs [backend|frontend]         跟踪日志（不带参数=两个一起）
#   ./dev.sh build                           全量构建后端依赖（mvn install）
#
# 说明:
#   - 后端首次启动会自动 mvn install 兄弟模块（之后跳过，直接 spring-boot:run）。
#   - 停止按端口杀进程（devtools 下 spring-boot:run 会 fork，端口最可靠）。
#   - MySQL(3306)/Redis(6379) 需自行保证运行；启动前会做非致命预检告警。
# ============================================================
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"

# ---- 配置（按需修改）----
BACKEND_PORT=8090
FRONTEND_PORT=1025
BACKEND_DIR="lin-entry"
FRONTEND_DIR="RuoYi-Vue3"
BACKEND_MAIN="com.lin.FreshDistributionApplication"
LOG_DIR="$ROOT/logs"
RUN_DIR="$ROOT/.run"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
BACKEND_PID="$RUN_DIR/backend.pid"
FRONTEND_PID="$RUN_DIR/frontend.pid"
START_TIMEOUT_BE=120   # 后端就绪等待秒数
START_TIMEOUT_FE=60     # 前端就绪等待秒数

# ---- 颜色 ----
if [ -t 1 ]; then
  C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_YELLOW=$'\033[33m'; C_CYAN=$'\033[36m'; C_OFF=$'\033[0m'
else
  C_GREEN=""; C_RED=""; C_YELLOW=""; C_CYAN=""; C_OFF=""
fi

# ---- 工具函数 ----
listen_pid() { lsof -ti tcp:"$1" -sTCP:LISTEN 2>/dev/null; }      # 返回监听该端口的 PID
is_listening() { [ -n "$(listen_pid "$1")" ]; }
pid_alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }
ensure_dirs() { mkdir -p "$LOG_DIR" "$RUN_DIR"; }

# 项目要求 Java 17；本机默认 JDK 可能是 8/11，自动切到 JDK 17
ensure_jdk17() {
  local need=true
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17' && need=false
  fi
  if $need; then
    local j17
    j17=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
    if [ -n "$j17" ]; then
      export JAVA_HOME="$j17"; export PATH="$JAVA_HOME/bin:$PATH"
      echo "${C_CYAN}已切换 JDK 17：$j17${C_OFF}"
    else
      echo "${C_RED}未找到 JDK 17（/usr/libexec/java_home -v 17 无结果），后端编译将失败${C_OFF}"
    fi
  fi
}

prereq() {
  local miss=0 c
  for c in mvn npm lsof; do
    command -v "$c" >/dev/null 2>&1 || { echo "${C_RED}缺少命令: $c${C_OFF}"; miss=1; }
  done
  [ $miss -eq 0 ] || { echo "请先安装上述命令并加入 PATH"; exit 1; }
  ensure_jdk17
}

warn_deps() {
  is_listening 3306 || echo "  ${C_YELLOW}⚠ MySQL(3306) 未监听，后端将无法连接数据库${C_OFF}"
  is_listening 6379 || echo "  ${C_YELLOW}⚠ Redis(6379) 未监听，RuoYi 的 token/认证依赖 Redis${C_OFF}"
}

# 等待端口就绪：$1=port $2=log $3=timeout $4=label
wait_for_port() {
  local port="$1" log="$2" to="$3" label="$4" i=0
  printf "%s 等待端口 %s 就绪" "$label" "$port"
  while [ $i -lt "$to" ]; do
    if is_listening "$port"; then printf " %s✓%s\n" "$C_GREEN" "$C_OFF"; return 0; fi
    printf "."; sleep 2; i=$((i+2))
  done
  printf " %s✗%s\n" "$C_RED" "$C_OFF"
  echo "$label 未在 ${to}s 内就绪，最近日志："
  tail -n 30 "$log" 2>/dev/null || true
  return 1
}

# 通用停止：$1=port $2=pidfile
stop_service() {
  local port="$1" pidfile="$2" i=0 ppids mpids=""
  if ! is_listening "$port" && [ ! -f "$pidfile" ]; then echo "未运行 (端口 $port)"; return 0; fi
  ppids=$(listen_pid "$port"); [ -n "$ppids" ] && kill -TERM $ppids 2>/dev/null
  [ -f "$pidfile" ] && { mpids=$(cat "$pidfile" 2>/dev/null || true); [ -n "$mpids" ] && kill -TERM "$mpids" 2>/dev/null; }
  while [ $i -lt 30 ] && { is_listening "$port" || { [ -n "$mpids" ] && pid_alive "$mpids"; }; }; do
    sleep 1; i=$((i+1))
  done
  ppids=$(listen_pid "$port"); [ -n "$ppids" ] && kill -9 $ppids 2>/dev/null
  [ -n "$mpids" ] && pid_alive "$mpids" && kill -9 "$mpids" 2>/dev/null
  rm -f "$pidfile"
  echo "已停止 (端口 $port)"
}

# ---- 后端 ----
# 兄弟模块源码比已安装产物新（或无产物）→ 需要重装：
# -pl lin-entry spring-boot:run 只编译入口模块，依赖模块从 ~/.m2 解析，
# 若只看 target/classes 是否存在会误跳过 install，导致后端跑旧包（OA 链路曾因此不生效）
needs_build() {
  local mod jar newest
  for mod in $(cd "$ROOT" && ls -d lin-* 2>/dev/null); do
    [ -d "$ROOT/$mod/src" ] || continue
    jar=$(ls -t "$ROOT/$mod/target/$mod-"*.jar 2>/dev/null | grep -v sources | head -1)
    [ -n "$jar" ] || return 0
    newest=$(find "$ROOT/$mod/src" -type f -newer "$jar" -print -quit 2>/dev/null)
    [ -n "$newest" ] && return 0
  done
  return 1
}

start_backend() {
  if is_listening "$BACKEND_PORT"; then echo "后端已在运行 (端口 $BACKEND_PORT)"; return 0; fi
  if needs_build; then
    echo "${C_CYAN}检测到模块源码有更新，重建后端依赖（mvn install -DskipTests）...${C_OFF}"
    ( cd "$ROOT" && mvn -T 1C install -DskipTests ) || { echo "${C_RED}后端构建失败${C_OFF}"; return 1; }
  fi
  warn_deps
  echo "${C_CYAN}启动后端：$BACKEND_DIR (端口 $BACKEND_PORT)${C_OFF}"
  ( cd "$ROOT" && nohup mvn -pl "$BACKEND_DIR" spring-boot:run \
       -Dspring-boot.run.main-class="$BACKEND_MAIN" \
       >"$BACKEND_LOG" 2>&1 & echo $! >"$BACKEND_PID" )
  wait_for_port "$BACKEND_PORT" "$BACKEND_LOG" "$START_TIMEOUT_BE" "后端"
}
stop_backend() { echo "${C_CYAN}停止后端...${C_OFF}"; stop_service "$BACKEND_PORT" "$BACKEND_PID"; }

# ---- 前端 ----
start_frontend() {
  if is_listening "$FRONTEND_PORT"; then echo "前端已在运行 (端口 $FRONTEND_PORT)"; return 0; fi
  if [ ! -d "$ROOT/$FRONTEND_DIR/node_modules" ]; then
    echo "${C_CYAN}安装前端依赖（npm install）...${C_OFF}"
    ( cd "$ROOT/$FRONTEND_DIR" && npm install ) || { echo "${C_RED}前端依赖安装失败${C_OFF}"; return 1; }
  fi
  echo "${C_CYAN}启动前端：$FRONTEND_DIR (端口 $FRONTEND_PORT)${C_OFF}"
  ( cd "$ROOT/$FRONTEND_DIR" && nohup npm run dev >"$FRONTEND_LOG" 2>&1 & echo $! >"$FRONTEND_PID" )
  wait_for_port "$FRONTEND_PORT" "$FRONTEND_LOG" "$START_TIMEOUT_FE" "前端"
}
stop_frontend() { echo "${C_CYAN}停止前端...${C_OFF}"; stop_service "$FRONTEND_PORT" "$FRONTEND_PID"; }

# ---- 组合命令 ----
start() { ensure_dirs; prereq; start_backend; start_frontend
  echo "完成：前端 http://localhost:$FRONTEND_PORT  →  后端 http://localhost:$BACKEND_PORT"; }
stop() { stop_frontend; stop_backend; }
restart() { stop; start; }

status() {
  if is_listening "$BACKEND_PORT"; then
    echo "后端: ${C_GREEN}运行中${C_OFF} (端口 $BACKEND_PORT, pid $(listen_pid "$BACKEND_PORT"))"
  else echo "后端: ${C_RED}未运行${C_OFF}"; fi
  if is_listening "$FRONTEND_PORT"; then
    echo "前端: ${C_GREEN}运行中${C_OFF} (端口 $FRONTEND_PORT, pid $(listen_pid "$FRONTEND_PORT"))"
  else echo "前端: ${C_RED}未运行${C_OFF}"; fi
}

logs() {
  case "${1:-}" in
    backend|be)  tail -f "$BACKEND_LOG" ;;
    frontend|fe) tail -f "$FRONTEND_LOG" ;;
    "")          tail -f "$BACKEND_LOG" "$FRONTEND_LOG" ;;
    *) echo "用法: $0 logs [backend|frontend]"; return 1 ;;
  esac
}

build() { prereq; echo "${C_CYAN}全量构建后端依赖（mvn install -DskipTests）...${C_OFF}"; ( cd "$ROOT" && mvn -T 1C install -DskipTests ); }

usage() {
  cat <<EOF
${C_CYAN}dev.sh${C_OFF} — 一键启动/关闭前后端
  后端 lin-entry: 端口 $BACKEND_PORT   前端 RuoYi-Vue3: 端口 $FRONTEND_PORT

用法:
  ./dev.sh start                          启动前后端（后端先起，再起前端）
  ./dev.sh stop                           停止前后端
  ./dev.sh restart                        重启
  ./dev.sh status                         查看运行状态
  ./dev.sh logs [backend|frontend]        跟踪日志（不带参数=两个一起）
  ./dev.sh build                          全量构建后端依赖（mvn install）
EOF
}

# ---- 入口 ----
case "${1:-}" in
  start)   start ;;
  stop)    stop ;;
  restart) restart ;;
  status)  status ;;
  logs)    logs "${2:-}" ;;
  build)   build ;;
  "")      usage ;;
  *)       echo "${C_RED}未知命令: $1${C_OFF}"; usage; exit 1 ;;
esac
