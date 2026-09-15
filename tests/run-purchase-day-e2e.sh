#!/usr/bin/env bash
# 采购录入 E2E 验收跑批包装（含目标库测试数据清理）
# 用法：bash tests/run-purchase-day-e2e.sh [采购日期，默认 2026-09-15]
#
# 说明：
#   - 前后各清理一次该采购日期的 purchase_modify_log / purchase_item / purchase_order，
#     保证脚本从干净状态开始、跑完不留数据（入库单无法通过接口删除，必须走 SQL）。
#   - DB 连接从 lin-entry/src/main/resources/application-local.yml 解析 master 数据源。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATE="${1:-${E2E_PURCHASE_DATE:-2026-09-15}}"
YML="$ROOT/lin-entry/src/main/resources/application-local.yml"

URL=$(grep -m1 -A2 '^ *master:' "$YML" | grep -m1 'url:' | sed 's/.*url: *//' | tr -d '\r')
USER=$(grep -m1 -A3 '^ *master:' "$YML" | grep -m1 'username:' | sed 's/.*username: *//' | tr -d '\r')
PASS=$(grep -m1 -A4 '^ *master:' "$YML" | grep -m1 'password:' | sed 's/.*password: *//' | tr -d '\r')
HOSTPORT=$(echo "$URL" | sed -E 's#jdbc:mysql://([^/]+)/.*#\1#')
DBNAME=$(echo "$URL" | sed -E 's#jdbc:mysql://[^/]+/([^?]+).*#\1#')
HOST="${HOSTPORT%%:*}"
PORT="${HOSTPORT##*:}"

echo "[e2e] 目标库 ${USER}@${HOST}:${PORT}/${DBNAME}，采购日期 ${DATE}"

cleanup() {
  mysql -h"$HOST" -P"$PORT" -u"$USER" -p"$PASS" "$DBNAME" -e "
    DELETE pl FROM purchase_modify_log pl
      JOIN purchase_order po ON po.id = pl.purchase_id WHERE po.order_date = '$DATE';
    DELETE pi FROM purchase_item pi
      JOIN purchase_order po ON po.id = pi.purchase_id WHERE po.order_date = '$DATE';
    DELETE FROM purchase_order WHERE order_date = '$DATE';" 2>/dev/null
  echo "[e2e] 已清理 ${DATE} 的采购数据"
}

echo "[e2e] 跑前清理..."
cleanup
echo "[e2e] 开始验收..."
( cd "$ROOT" && E2E_PURCHASE_DATE="$DATE" node tests/e2e-purchase-day.mjs )
RC=$?
echo "[e2e] 跑后清理..."
cleanup
exit $RC
