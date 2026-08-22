#!/usr/bin/env bash
# ============================================================
# dev_windows.sh — 一键启动/关闭前后端（Windows Git Bash / MinGW / MSYS）
#   后端: lin-entry        (Spring Boot, profile=local, 端口 8090)
#   前端: RuoYi-Vue3       (vite, 端口 1025, 代理 /dev-api、/jmreport → 8090)
#
# 用法:
#   ./dev_windows.sh start [backend|frontend]   启动（默认=两个一起）
#   ./dev_windows.sh stop  [backend|frontend]   停止（默认=两个一起）
#   ./dev_windows.sh restart                     重启
#   ./dev_windows.sh status                      查看运行状态
#   ./dev_windows.sh logs [backend|frontend]     跟踪日志（不带参数=两个一起）
#   ./dev_windows.sh build                       全量构建后端依赖（mvn install）
#
# 说明:
#   - 专为 Windows Git Bash / MinGW 设计，使用 netstat + taskkill 查端口 / 杀进程。
#   - 后端首次启动会自动 mvn install 兄弟模块（之后跳过，直接 spring-boot:run）。
#   - 自动切换 JDK 17（路径写死为 D:/Java/jdk17/azul-17.0.13，可按需修改）。
#   - MySQL(3306)/Redis(6379) 需自行保证运行。
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
JAVA_HOME_REQUIRED="D:/Java/jdk17/azul-17.0.13"

# ---- 颜色 ----
if [ -t 1 ]; then
  C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_YELLOW=$'\033[33m'; C_CYAN=$'\033[36m'; C_OFF=$'\033[0m'
else
  C_GREEN=""; C_RED=""; C_YELLOW=""; C_CYAN=""; C_OFF=""
fi

# ---- 工具函数 ----
ensure_dirs() { mkdir -p "$LOG_DIR" "$RUN_DIR"; }

# 查端口监听的 PID（Windows: netstat -ano）
listen_pid() {
  netstat -ano 2>/dev/null \
    | grep ":$1 " \
    | grep LISTENING \
    | head -1 \
    | awk '{print $5}'
}

is_listening() { [ -n "$(listen_pid "$1")" ]; }

pid_alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }

# 强制杀进程（Windows: taskkill //F）
kill_pid() {
  local pid="$1"
  [ -z "$pid" ] && return
  taskkill //F //PID "$pid" >/dev/null 2>&1
}

# 自动切换 JDK 17
ensure_jdk17() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17' && return 0
  fi
  if [ -x "$JAVA_HOME_REQUIRED/bin/java" ]; then
    export JAVA_HOME="$JAVA_HOME_REQUIRED"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "${C_CYAN}已切换 JDK 17：$JAVA_HOME${C_OFF}"
    return 0
  fi
  echo "${C_RED}未找到 JDK 17（$JAVA_HOME_REQUIRED），后端编译将失败${C_OFF}"
}

prereq() {
  local miss=0
  command -v mvn >/dev/null 2>&1 || { echo "${C_RED}缺少命令: mvn${C_OFF}"; miss=1; }
  command -v npm >/dev/null 2>&1 || { echo "${C_RED}缺少命令: npm${C_OFF}"; miss=1; }
  command -v node >/dev/null 2>&1 || { echo "${C_RED}缺少命令: node${C_OFF}"; miss=1; }
  [ "$miss" -eq 0 ] || { echo "请先安装上述命令并加入 PATH"; exit 1; }
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
  local port="$1" pidfile="$2" i=0

  if ! is_listening "$port" && [ ! -f "$pidfile" ]; then
    echo "未运行 (端口 $port)"
    return 0
  fi

  # 通过端口找监听进程并杀掉
  local ppids=$(listen_pid "$port")
  if [ -n "$ppids" ]; then
    for p in $ppids; do
      kill_pid "$p"
    done
  fi

  # 再用 pidfile 兜底
  if [ -f "$pidfile" ]; then
    local mpids=$(cat "$pidfile" 2>/dev/null || true)
    if [ -n "$mpids" ]; then
      for p in $mpids; do
        kill_pid "$p"
      done
    fi
  fi

  # 最多等 15s
  while [ $i -lt 15 ] && is_listening "$port"; do
    sleep 1; i=$((i+1))
  done

  # 超时再强杀一次
  ppids=$(listen_pid "$port")
  if [ -n "$ppids" ]; then
    for p in $ppids; do
      kill_pid "$p"
    done
  fi

  rm -f "$pidfile"
  echo "已停止 (端口 $port)"
}

# ---- 后端 ----
start_backend() {
  if is_listening "$BACKEND_PORT"; then
    echo "${C_GREEN}后端已在运行${C_OFF} (端口 $BACKEND_PORT, pid $(listen_pid "$BACKEND_PORT"))"
    return 0
  fi

  if [ ! -d "$ROOT/$BACKEND_DIR/target/classes" ]; then
    echo "${C_CYAN}首次构建后端依赖（mvn install -DskipTests，可能较慢）...${C_OFF}"
    ( cd "$ROOT" && mvn -T 1C install -DskipTests ) || {
      echo "${C_RED}后端构建失败${C_OFF}"
      return 1
    }
  fi

  warn_deps

  echo "${C_CYAN}启动后端：$BACKEND_DIR (端口 $BACKEND_PORT)${C_OFF}"
  (
    cd "$ROOT" && \
    nohup mvn -pl "$BACKEND_DIR" spring-boot:run \
         -Dspring-boot.run.main-class="$BACKEND_MAIN" \
         -Dspring.profiles.active=local \
         >"$BACKEND_LOG" 2>&1 &
    echo $! > "$BACKEND_PID"
  )

  wait_for_port "$BACKEND_PORT" "$BACKEND_LOG" "$START_TIMEOUT_BE" "后端"
}

stop_backend() {
  echo "${C_CYAN}停止后端...${C_OFF}"
  stop_service "$BACKEND_PORT" "$BACKEND_PID"
}

# ---- 前端 ----
start_frontend() {
  if is_listening "$FRONTEND_PORT"; then
    echo "${C_GREEN}前端已在运行${C_OFF} (端口 $FRONTEND_PORT, pid $(listen_pid "$FRONTEND_PORT"))"
    return 0
  fi

  if [ ! -d "$ROOT/$FRONTEND_DIR/node_modules" ]; then
    echo "${C_CYAN}安装前端依赖（npm install）...${C_OFF}"
    ( cd "$ROOT/$FRONTEND_DIR" && npm install ) || {
      echo "${C_RED}前端依赖安装失败${C_OFF}"
      return 1
    }
  fi

  echo "${C_CYAN}启动前端：$FRONTEND_DIR (端口 $FRONTEND_PORT)${C_OFF}"
  (
    cd "$ROOT/$FRONTEND_DIR" && \
    nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
    echo $! > "$FRONTEND_PID"
  )

  wait_for_port "$FRONTEND_PORT" "$FRONTEND_LOG" "$START_TIMEOUT_FE" "前端"
}

stop_frontend() {
  echo "${C_CYAN}停止前端...${C_OFF}"
  stop_service "$FRONTEND_PORT" "$FRONTEND_PID"
}

# ---- 组合命令 ----
start_all() {
  ensure_dirs; prereq
  start_backend
  start_frontend
  echo "${C_GREEN}完成${C_OFF}：前端 http://localhost:$FRONTEND_PORT  →  后端 http://localhost:$BACKEND_PORT"
}

stop_all() { stop_frontend; stop_backend; }
restart_all() { stop_all; start_all; }

status() {
  if is_listening "$BACKEND_PORT"; then
    echo "后端: ${C_GREEN}运行中${C_OFF} (端口 $BACKEND_PORT, pid $(listen_pid "$BACKEND_PORT"))"
  else
    echo "后端: ${C_RED}未运行${C_OFF}"
  fi
  if is_listening "$FRONTEND_PORT"; then
    echo "前端: ${C_GREEN}运行中${C_OFF} (端口 $FRONTEND_PORT, pid $(listen_pid "$FRONTEND_PORT"))"
  else
    echo "前端: ${C_RED}未运行${C_OFF}"
  fi
}

logs() {
  case "${1:-}" in
    backend|be)  tail -f "$BACKEND_LOG" ;;
    frontend|fe) tail -f "$FRONTEND_LOG" ;;
    "")          tail -f "$BACKEND_LOG" "$FRONTEND_LOG" ;;
    *) echo "用法: $0 logs [backend|frontend]"; return 1 ;;
  esac
}

build() {
  prereq
  echo "${C_CYAN}全量构建后端依赖（mvn install -DskipTests）...${C_OFF}"
  ( cd "$ROOT" && mvn -T 1C install -DskipTests )
}

usage() {
  cat <<EOF
${C_CYAN}dev_windows.sh${C_OFF} — 一键启动/关闭前后端（Windows Git Bash）
  后端 lin-entry: 端口 $BACKEND_PORT   前端 RuoYi-Vue3: 端口 $FRONTEND_PORT

用法:
  ./dev_windows.sh start [backend|frontend]   启动（默认=两个一起）
  ./dev_windows.sh stop  [backend|frontend]   停止（默认=两个一起）
  ./dev_windows.sh restart                     重启
  ./dev_windows.sh status                      查看运行状态
  ./dev_windows.sh logs [backend|frontend]     跟踪日志（不带参数=两个一起）
  ./dev_windows.sh build                       全量构建后端依赖（mvn install）
EOF
}

# ---- 入口：支持 start/stop + 可选子命令 ----
CMD="${1:-}"
SUB="${2:-}"

case "$CMD" in
  start)
    case "$SUB" in
      backend|be)  start_backend ;;
      frontend|fe) start_frontend ;;
      all|"")      start_all ;;
      *)           echo "${C_RED}未知子命令: start $SUB${C_OFF}"; usage; exit 1 ;;
    esac
    ;;
  stop)
    case "$SUB" in
      backend|be)  stop_backend ;;
      frontend|fe) stop_frontend ;;
      all|"")      stop_all ;;
      *)           echo "${C_RED}未知子命令: stop $SUB${C_OFF}"; usage; exit 1 ;;
    esac
    ;;
  restart) restart_all ;;
  status)  status ;;
  logs)    logs "${SUB}" ;;
  build)   build ;;
  "")      usage ;;
  *)       echo "${C_RED}未知命令: $CMD${C_OFF}"; usage; exit 1 ;;
esac
