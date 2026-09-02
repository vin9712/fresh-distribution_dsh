#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
schema 漂移体检：比对 sql/init_all.sql 期望的表/列 与 目标库实际结构。

背景：dev.sh 后端以 ~/.m2 已安装 jar 运行，且历史迁移脚本可能未在目标库执行，
      导致「Unknown column ... in 'field list'」类运行期错误（已多次踩坑：
      purchase_order 作废三列、t_month_settlement 等 8 张表、t_print_template 状态列、
      t_sale_order_detail 手工定价三列）。本脚本用于启动/回归前快速自检。

用法：
  python3 tests/check_schema_drift.py                      # 用默认 local 配置
  python3 tests/check_schema_drift.py --host H --db D --user U --password P
  python3 tests/check_schema_drift.py --ignore t_product_sku:visit_count,...
退出码：0 = 无漂移；1 = 存在缺失表/列；2 = 连接或解析失败
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INIT_SQL = ROOT / "sql" / "init_all.sql"
LOCAL_YML = ROOT / "lin-entry" / "src" / "main" / "resources" / "application-local.yml"

# 已知历史遗留：init_all 中保留了旧版定义但代码已不引用（R1 基础信息重构）
DEFAULT_IGNORE_TABLES = set()
DEFAULT_IGNORE_COLUMNS = {
    "t_product_sku": {"customer_id", "images", "properties", "spec", "visit_count"},
}


def parse_expected(sql_text: str):
    """从 init_all.sql 抽取期望的 表 -> 列集合（CREATE TABLE 块 + ALTER ADD COLUMN）"""
    expected = {}
    for m in re.finditer(r"CREATE TABLE (?:IF NOT EXISTS )?`([^`]+)`\s*\((.*?)\n\)\s*ENGINE", sql_text, re.S):
        tbl, body = m.group(1), m.group(2)
        cols = set(re.findall(r"^\s{4}`([a-z_0-9]+)`\s+\w", body, re.M))
        expected.setdefault(tbl, set()).update(cols)
    # 顶层 ALTER TABLE ... ADD COLUMN `x`
    for m in re.finditer(r"ALTER TABLE\s+`([a-z_0-9]+)`([\s\S]*?);", sql_text):
        tbl, body = m.group(1), m.group(2)
        for c in re.findall(r"ADD COLUMN\s+`([a-z_0-9]+)`", body):
            expected.setdefault(tbl, set()).add(c)
    # 幂等脚本内嵌于字符串的 ALTER：'ALTER TABLE `x` ... ADD COLUMN `y` ...'
    for m in re.finditer(r"'ALTER TABLE `([a-z_0-9]+)`([^']*)'", sql_text):
        tbl, body = m.group(1), m.group(2)
        for c in re.findall(r"ADD COLUMN `([a-z_0-9]+)`", body):
            expected.setdefault(tbl, set()).add(c)
    return expected


def fetch_actual(args):
    env = dict(os.environ)
    if args.password:
        env["MYSQL_PWD"] = args.password
    q = ("select table_name, column_name from information_schema.columns "
         "where table_schema=database()")
    cmd = ["mysql", "-h", args.host, "-P", str(args.port), "-u", args.user, "-N", "-B", args.db, "-e", q]
    out = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if out.returncode != 0:
        print("mysql 查询失败：", out.stderr.strip()[:400], file=sys.stderr)
        sys.exit(2)
    actual = {}
    for line in out.stdout.strip().splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            continue
        actual.setdefault(parts[0], set()).add(parts[1])
    return actual


def load_local_datasource():
    """从 application-local.yml 的 master 数据源解析 host/port/db/user/password（不硬编码口令）"""
    cfg = {"host": "127.0.0.1", "port": "3306", "db": "", "user": "root", "password": os.environ.get("MYSQL_PWD", "")}
    if not LOCAL_YML.exists():
        return cfg
    text = LOCAL_YML.read_text(encoding="utf-8")
    m = re.search(r"master:\s*\n\s*url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?\"\s]+)", text)
    if m:
        cfg["host"], cfg["port"], cfg["db"] = m.group(1), m.group(2), m.group(3)
    # master 块下的 username/password（取 url 之后首次出现）
    if m:
        tail = text[m.end():]
        um = re.search(r"^\s*username:\s*(\S+)", tail, re.M)
        pm = re.search(r"^\s*password:\s*(\S*)", tail, re.M)
        if um:
            cfg["user"] = um.group(1)
        if pm and pm.group(1):
            cfg["password"] = pm.group(1)
    return cfg


def main():
    local = load_local_datasource()
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default=local["host"])
    ap.add_argument("--port", default=local["port"])
    ap.add_argument("--user", default=local["user"])
    ap.add_argument("--password", default=local["password"], help="默认取 application-local.yml / MYSQL_PWD")
    ap.add_argument("--db", default=local["db"])
    ap.add_argument("--ignore", default="", help="逗号分隔：table 或 table:column")
    args = ap.parse_args()
    if not args.db:
        print("未解析到数据库名（检查 application-local.yml 或用 --db 指定）", file=sys.stderr)
        sys.exit(2)

    ignore_tables = set(DEFAULT_IGNORE_TABLES)
    ignore_cols = {k: set(v) for k, v in DEFAULT_IGNORE_COLUMNS.items()}
    for item in [x.strip() for x in args.ignore.split(",") if x.strip()]:
        if ":" in item:
            t, c = item.split(":", 1)
            ignore_cols.setdefault(t, set()).add(c)
        else:
            ignore_tables.add(item)

    if not INIT_SQL.exists():
        print("找不到", INIT_SQL, file=sys.stderr)
        sys.exit(2)
    expected = parse_expected(INIT_SQL.read_text(encoding="utf-8"))
    actual = fetch_actual(args)

    missing_tables, missing_cols = [], []
    for tbl in sorted(expected):
        if tbl in ignore_tables:
            continue
        if tbl not in actual:
            missing_tables.append(tbl)
            continue
        miss = expected[tbl] - actual[tbl] - ignore_cols.get(tbl, set())
        if miss:
            missing_cols.append((tbl, sorted(miss)))

    print(f"目标库：{args.user}@{args.host}:{args.port}/{args.db}")
    print(f"init_all.sql 期望表数：{len(expected)}，实际表数：{len(actual)}")
    if not missing_tables and not missing_cols:
        print("✅ 无 schema 漂移（表与列均齐备）")
        sys.exit(0)
    for t in missing_tables:
        print(f"  ❌ 缺失表：{t}")
    for t, cols in missing_cols:
        print(f"  ❌ 缺失列：{t} -> {', '.join(cols)}")
    print("\n处理：执行 sql/ 下对应迁移脚本（幂等），或从 init_all.sql 抽取建表/加列语句应用到目标库。")
    sys.exit(1)


if __name__ == "__main__":
    main()
