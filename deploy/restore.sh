#!/usr/bin/env bash
# ============================================================
# 生鲜配送系统 数据库恢复脚本（恢复演练用，DESIGN §11）
# 用法：
#   bash restore.sh <备份文件.sql.gz 或 .sql> [目标库名，默认 fresh-distribution-dsh]
# 示例：
#   bash restore.sh /opt/fresh-distribution/backup/daily/fresh-distribution-dsh_20260817_023000.sql.gz
# 注意：恢复会覆盖目标库数据！生产执行前先备份当前库；建议先在演练库演练。
# ============================================================
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-请填写恢复账号密码}"
DB_NAME="${2:-fresh-distribution-dsh}"

if [ $# -lt 1 ]; then
  echo "用法: bash restore.sh <备份文件.sql[.gz]> [目标库名]"
  exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "$BACKUP_FILE" ]; then
  echo "备份文件不存在: $BACKUP_FILE"
  exit 1
fi

echo "== 恢复演练开始 =="
echo "备份文件: $BACKUP_FILE"
echo "目标库:   $DB_NAME"
read -r -p "确认恢复将覆盖目标库数据 [输入 yes 继续]: " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "已取消"
  exit 0
fi

# 重建目标库
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" \
  -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 解压恢复
if [[ "$BACKUP_FILE" == *.gz ]]; then
  gunzip -c "$BACKUP_FILE" | mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME"
else
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$BACKUP_FILE"
fi

echo "== 恢复完成 =="
echo "演练检查项（DEVELOPMENT §8）："
echo "  1. mysql -e \"SELECT COUNT(*) FROM fresh-distribution-dsh.sys_user;\" 行数与原库一致"
echo "  2. 登录系统（admin），验证：登录、菜单权限、订单/采购/送货/验收/报表各页面可打开"
echo "  3. biz_code_seq 序列值可用（新建一张订单测试单号生成）"
echo "  4. JimuReport 打印视图可打开（/jmreport/view/2099000000000000001?token=）"
