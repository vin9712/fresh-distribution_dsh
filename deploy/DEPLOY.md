# 生鲜配送系统 部署手册（S6-3）

> 基线：国内云 Linux 单机 2C4G、JDK 17、MySQL 8、Nginx 1.24+、Redis（可选缓存）。
> 架构：Nginx（HTTPS + 前端 dist 静态托管 + `/prod-api`、`/jmreport` 反代）→ Spring Boot Jar（8080）→ MySQL。

## 1. 服务器准备

```bash
# 用户与目录
sudo useradd -m -s /bin/bash fresh
sudo mkdir -p /opt/fresh-distribution/{backup/daily,backup/monthly,dist}
sudo chown -R fresh:fresh /opt/fresh-distribution
sudo mkdir -p /home/fresh/uploadPath /var/log/fresh-distribution
sudo chown fresh:fresh /home/fresh/uploadPath /var/log/fresh-distribution

# 依赖
sudo apt install -y openjdk-17-jdk nginx mysql-server redis-server
```

## 2. 构建与发布

```bash
# 本机打包（Windows 开发机）
mvn -q -T 1C clean package -DskipTests
# 前端
cd RuoYi-Vue3 && npm run build:prod

# 上传服务器（jar 与 dist）
scp lin-entry/target/lin-entry.jar fresh@<服务器>:/opt/fresh-distribution/
scp -r RuoYi-Vue3/dist/* fresh@<服务器>:/opt/fresh-distribution/dist/
```

## 3. 生产配置（不提交仓库）

1. 复制 `lin-entry/src/main/resources/application-prod.yml.example` → `application-prod.yml`（**仓库 .gitignore 已排除该文件**），填写：数据库账号密码、Redis 密码（如有）、**更换 token.secret**（64 位十六进制随机串：`openssl rand -hex 32`）、`user.password.lockTime: 30`（登录失败 5 次锁定 30 分钟，DESIGN §11）。
2. 初始化数据库：依次执行 `sql/` 下 `ry_20240629.sql`、`init_fresh_distribution.sql`、`new_added_sql.sql` 及全部 `s0_* / s1_* / s2_* / s3_* / s4_* / s5_* / s6_*` 增量脚本（**空库基线 + 增量**）。
3. 导入 JimuReport 初始化脚本前：`SET GLOBAL max_allowed_packet=67108864;`（见 DEV-ENV-NOTES §7）。

## 4. 服务启动（systemd）

```bash
sudo cp deploy/fresh-distribution.service.example /etc/systemd/system/fresh-distribution.service
# 编辑单元中 jar 路径与 JVM 参数后：
sudo systemctl daemon-reload && sudo systemctl enable --now fresh-distribution
journalctl -u fresh-distribution -f   # 查看启动日志
```

## 5. Nginx 与 HTTPS

```bash
# 1) 安装证书（certbot，自动配置 80 跳转与 443）
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d <域名>

# 2) 合并本仓库站点配置（覆盖 server 段，或整体替换 /etc/nginx/sites-available/default）
sudo cp deploy/nginx.conf.example /etc/nginx/sites-available/fresh-distribution.conf
# 替换 <域名> 与证书路径后：
sudo ln -sf /etc/nginx/sites-available/fresh-distribution.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

前端 `.env.production` 已配置 `VITE_APP_BASE_API=/prod-api`；Nginx 将 `/prod-api/` 反代到 `127.0.0.1:8080/`。

## 6. 数据库备份（每日 7 天 + 月度 1 年）

```bash
sudo cp deploy/backup.sh /opt/fresh-distribution/deploy/
# 填写脚本内 DB_USER/DB_PASS（建议创建只读备份账号）
sudo crontab -e
30 2 * * * /bin/bash /opt/fresh-distribution/deploy/backup.sh >> /var/log/fresh-backup.log 2>&1
```

- 每日 02:30 全量 mysqldump（gzip），保留 7 天；每月 1 号转存月备份保留 1 年。
- 操作日志（sys_oper_log / sys_logininfor）保留 5 年：按季度归档——每季度末执行
  `mysqldump fresh-distribution-dsh sys_oper_log sys_logininfor | gzip > backup/quarterly/log_2026Q2.sql.gz`，
  归档后可在库内删除超过 5 年的历史行（按 create_time）。

## 7. 恢复演练（上线检查必做，DEVELOPMENT §8）

```bash
# 1) 演练库恢复（不碰生产库）
mysql -e "CREATE DATABASE IF NOT EXISTS fresh_distribution_drill CHARACTER SET utf8mb4"
bash deploy/restore.sh backup/daily/fresh-distribution-dsh_<日期>_023000.sql.gz fresh_distribution_drill
# 2) 按脚本末尾提示逐项核验：行数、登录、菜单权限、各业务页、单号序列、打印视图
```

## 8. 上线检查清单

| # | 检查项 | 方法 |
|---|---|---|
| 1 | HTTPS 生效、HTTP 301 跳转 | `curl -I http://<域名>` |
| 2 | 登录失败 5 次锁定 30 分钟 | 连错 5 次密码后第 6 次观察提示 |
| 3 | 权限：普通账号越权访问返回 403（非空结果掩盖） | 文员账号直调 `/system/user/list` |
| 4 | 四类单号（XD/PC/HS/YS）前缀与每日重置 | 各建一张测试单 |
| 5 | 打印模板三级绑定 + 打印计数 | 送货单打印后 print_count+1、sys_oper_log 有记录 |
| 6 | 备份任务运行、恢复演练通过 | /var/log/fresh-backup.log + §7 |
| 7 | 生产配置不含真实密码进仓库 | `git ls-files | grep application-prod` 为空 |

## 9. 生产环境特别注意

- **JimuReport 数据集地址**：S6-2 预置数据集的 `api_url` 写死 `http://localhost:8090/print/...`（开发端口），生产改为后端 8080：

  ```sql
  UPDATE jimu_report_db SET api_url =
    CASE WHEN db_code='hd' THEN 'http://127.0.0.1:8080/print/deliveryHead?deliveryOrderId=${deliveryOrderId}'
         WHEN db_code='dd' THEN 'http://127.0.0.1:8080/print/deliveryData?deliveryOrderId=${deliveryOrderId}'
         ELSE api_url END
  WHERE id IN ('2099000000000000002','2099000000000000003');
  ```

- `/print/deliveryData`、`/print/deliveryHead` 为 JimuReport 服务端调用放行（只读，仅暴露送货单打印字段）；如需收紧可在 Nginx 限制仅本机访问该路径。
- 若验收/采购/报价单打印需要专用模板，在打印设计器（打印模板页"打开打印设计器"）复制送货单模板后改数据集 SQL 即可，绑定表 t_print_template 支持三级绑定（1 客户+配送点组合 > 2 客户 > 3 全局默认）与联数（copies）。
