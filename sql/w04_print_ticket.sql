-- ============================================================
-- W0-4.1 打印鉴权改短时一次性打印票据，废除 URL 携带长期 JWT
-- 幂等脚本，可重复执行
--
-- 背景：
--   原链路前端以 /jmreport/view/{templateId}?token=<长期JWT>&deliveryOrderId=...
--   打开打印视图，JWT 进入浏览器历史/代理/日志；且 /print/deliveryHead、
--   /print/deliveryData 完全无凭据（凭 deliveryOrderId 即可越权拉取含价格数据）。
--
-- 改造：
--   1. 后端新增 POST /print/ticket 签发短时一次性票据（ptk_ 前缀，Redis TTL 300 秒，
--      一次性兑换 + 600 秒会话宽限 + 送货单绑定），由 JimuReport 桥接兑换；
--   2. 本脚本将数据集 api_url 追加 &ticket=${ticket}，使 JimuReport 服务端回调
--      deliveryHead/deliveryData 时透传票据，接口侧强校验票据与单据绑定。
--
-- 回滚：将 api_url 还原为不带 ticket 的版本即可（见注释）。
-- ============================================================

-- 1) 数据集 URL 追加票据透传（hd 表头 / dd 明细）
UPDATE `jimu_report_db`
SET `api_url` = CASE WHEN `db_code` = 'hd'
                     THEN 'http://localhost:8090/print/deliveryHead?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}'
                     ELSE 'http://localhost:8090/print/deliveryData?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}' END
WHERE `db_type` = '1'
  AND `api_url` LIKE 'http://localhost:8090/print/%'
  AND `api_url` NOT LIKE '%&ticket=%';

-- 回滚参考（如需回退 W0-4.1，恢复无票据 URL）：
-- UPDATE `jimu_report_db`
-- SET `api_url` = REPLACE(`api_url`, '&ticket=${ticket}', '')
-- WHERE `db_type` = '1' AND `api_url` LIKE 'http://localhost:8090/print/%';
