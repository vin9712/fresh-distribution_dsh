#!/usr/bin/env bash
# ============================================================
# 生鲜配送系统 数据库备份脚本（DESIGN §11 / DEVELOPMENT §8）
# 每日全量 mysqldump 保留 7 天；每月 1 号备份转月备份保留 1 年；
# 操作日志 sys_oper_log 按季度归档说明见 DEPLOY.md。
#
# 用法：
#   1) 填写下方配置（数据库账号建议使用只读备份账号）；
#   2) crontab -e 添加：
#      30 2 * * * /bin/bash /opt/fresh-distribution/deploy/backup.sh >> /var/log/fresh-backup.log 2>&1
# ============================================================
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-backup}"
DB_PASS="${DB_PASS:-请填写备份账号密码}"
DB_NAME="${DB_NAME:-fresh-distribution-dsh}"

BACKUP_DIR="${BACKUP_DIR:-/opt/fresh-distribution/backup}"
DAILY_DIR="$BACKUP_DIR/daily"      # 每日备份，保留 7 天
MONTHLY_DIR="$BACKUP_DIR/monthly"  # 月备份，保留 1 年（365 天）
LOG_FILE="${LOG_FILE:-/var/log/fresh-backup.log}"

mkdir -p "$DAILY_DIR" "$MONTHLY_DIR"

DATE="$(date +%Y%m%d_%H%M%S)"
DAY="$(date +%Y%m%d)"
MONTH="$(date +%Y%m)"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG_FILE"; }

# 1) 每日全量备份
DUMP_FILE="$DAILY_DIR/${DB_NAME}_${DATE}.sql.gz"
mysqldump --single-transaction --quick --routines --triggers \
  -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" | gzip > "$DUMP_FILE"
log "daily backup ok: $DUMP_FILE ($(du -h "$DUMP_FILE" | cut -f1))"

# 2) 每日备份只保留最近 7 天
find "$DAILY_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +7 -delete
log "daily retention cleaned (keep 7 days)"

# 3) 每月 1 号转存月备份（保留 1 年）
if [ "$(date +%d)" = "01" ]; then
  cp "$DUMP_FILE" "$MONTHLY_DIR/${DB_NAME}_${MONTH}.sql.gz"
  find "$MONTHLY_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +365 -delete
  log "monthly backup saved: ${DB_NAME}_${MONTH}.sql.gz"
fi

log "backup finished"
