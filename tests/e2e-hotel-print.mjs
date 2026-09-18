// 只读预览验证：静态样板逐行比对源 JSON；动态总单走真实运行时渲染路径（票据+jmreport/show）。
// 丽宫用其最近真实订单日验证七列列头与行合计；不触发打印回执，不写销售订单。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { readFileSync, mkdirSync, readdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const date = process.env.DELIVERY_DATE || '2026-09-18';
for (const key of ['ERP_USER', 'ERP_PASSWORD', 'MYSQL_HOST', 'MYSQL_USER', 'MYSQL_PWD', 'MYSQL_DATABASE']) assert(process.env[key], `缺少环境变量 ${key}`);
const sql = text => execFileSync('mysql', ['-h', process.env.MYSQL_HOST, '-u', process.env.MYSQL_USER, '--default-character-set=utf8mb4', '--batch', '--skip-column-names', process.env.MYSQL_DATABASE, '-e', text], { encoding: 'utf8' }).trim();
const hotels = readdirSync(new URL('../docs/assets/print/hotels', import.meta.url))
  .filter(f => /(^|-)import-result\.json$/.test(f))
  .flatMap(f => JSON.parse(readFileSync(new URL(`../docs/assets/print/hotels/${f}`, import.meta.url))));
const JIMIN_REF_COLUMNS = ['中厨', '点心', '味部', '铁板', '上杂', '福食', '鲍鱼']; // 金满楼参照样张列
const browser = await chromium.launch({ channel: 'chrome', headless: true });
mkdirSync('test-results/hotel-print', { recursive: true });
try {
  const page = await browser.newPage({ viewport: { width: 1500, height: 1400 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const token = (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
  async function api(path, data) {
    const r = await page.request.fetch(base + '/dev-api' + path, { method: data ? 'POST' : 'GET', headers: { Authorization: 'Bearer ' + token }, ...(data ? { data } : {}) });
    const j = await r.json(); assert.equal(j.code, 200, j.msg); return j;
  }
  for (const hotel of hotels) {
    const key = hotel.datasetCode.replace('Ds', '');
    const source = JSON.parse(readFileSync(new URL(`../docs/assets/print/hotels/${key}-source.json`, import.meta.url)));
    const fields = source.table.columns.slice(2).map(c => c.field);
    const ownDate = sql(`SELECT delivery_date FROM t_delivery_order WHERE customer_id=${Number(hotel.customerId)} GROUP BY delivery_date ORDER BY delivery_date DESC LIMIT 1`);
    // 配送点：启用点必须与导入结果一致；槽位数≥启用点数（差额为历史停用点的 adHoc 补列）
    const points = (await api('/partner/customerDept/list?customerId=' + hotel.customerId)).data.filter(p => Number(p.parentId) !== 0);
    const enabled = points.filter(p => p.valid === 1);
    const allDeptNames = points.map(p => p.name);
    assert(enabled.every(p => p.valid === 1), `${hotel.name} 启用点状态异常`);
    assert.deepEqual(enabled.map(p => p.name).sort(), hotel.deptNames, `${hotel.name} 启用配送点与导入结果不一致`);
    assert(enabled.length <= hotel.slots, `${hotel.name} 启用点超过模板槽位`);
    const modes = ownDate ? ['sample', 'matrix'] : ['sample', 'matrix-empty'];
    for (const mode of modes) {
      const customerId = hotel.customerId;
      const usedDate = mode === 'matrix' ? ownDate : date;
      const bizKey = `matrix:${customerId}:${usedDate}`;
      const ticket = (await api('/print/ticket', mode === 'sample' ? {} : { bizKey })).ticket;
      assert(ticket);
      const reportId = mode === 'sample' ? hotel.sampleReportId : hotel.reportId;
      const showPromise = page.waitForResponse(r => r.url().includes('/jmreport/show'));
      await page.goto(`${base}/jmreport/view/${reportId}?token=${ticket}&ticket=${ticket}&customerId=${customerId}&deliveryDate=${usedDate}&colBlock=1`, { waitUntil: 'domcontentloaded' });
      const show = await (await showPromise).json();
      assert.equal(show.code, 200, JSON.stringify(show));
      const data = show.result.dataList;
      if (mode === 'sample') {
        const actual = data[hotel.datasetCode].list;
        assert.equal(actual.length, hotel.rows);
        for (let i = 0; i < actual.length; i++) {
          const expected = source.datasets[0].jsonData[i];
          assert.equal(actual[i].name, expected.name);
          assert.equal(actual[i].unit, expected.unit);
          for (const field of fields) assert.equal(Number(actual[i][field] || 0), expected[field]);
          assert.equal(Number(actual[i].sum1), fields.reduce((sum, f) => sum + expected[f], 0));
          for (const color of ['textColor', 'nameColor', 'unitColor', 'bgColor']) if (expected[color]) assert.equal(actual[i][color], expected[color]);
        }
        console.log(`PASS ${hotel.name} sample: ${hotel.rows}行逐项/合计/颜色一致`);
      } else {
        const hm = data.hm.list[0];
        assert.equal(hm.printTitle, hotel.name + '总单');
        assert.equal(hm.deliveryDate, usedDate);
        assert.equal(Number(hm.totalColBlocks), 1);
        if (mode === 'matrix') {
          // 真实订单渲染：期望列全部出现在槽位内；槽位外的列必须为空（不得把有数据的列挤出模板）
          const rendered = Array.from({ length: hotel.slots }, (_, i) => hm['c' + (i + 1) + 'Name']);
          for (const name of hotel.expectedColumns) assert(rendered.includes(name), `期望列 ${name} 未进入模板槽位`);
          for (const name of rendered.filter(Boolean)) assert(allDeptNames.includes(name) || hotel.expectedColumns.includes(name), `槽位出现未知列 ${name}`);
          assert(data.dm.list.length > 0, '真实订单日明细为空');
          for (const row of data.dm.list) {
            for (let i = hotel.slots + 1; i <= 7; i++) assert(!row['c' + i], `槽位外存在数据: ${row.productName} c${i}=${row['c' + i]}`);
            const cellSum = Array.from({ length: hotel.slots }, (_, i) => Number(row['c' + (i + 1)] || 0)).reduce((s, v) => s + v, 0);
            assert.equal(Number(row.total), cellSum, `行合计不符: ${row.productName}`);
          }
          const dbTotal = sql(`SELECT COALESCE(SUM(d.num),0) FROM t_delivery_order o JOIN t_delivery_order_detail d ON d.delivery_id=o.id WHERE o.customer_id=${Number(hotel.customerId)} AND o.delivery_date='${usedDate}' AND o.status<>4`);
          const dmTotal = data.dm.list.reduce((s, r) => s + Number(r.total || 0), 0);
          assert.equal(dmTotal, Number(dbTotal), `明细合计(${dmTotal})≠送货单明细合计(${dbTotal})`);
          console.log(`PASS ${hotel.name} matrix(真实订单 ${usedDate}): ${data.dm.list.length}行 恒等式+库内合计一致`);
        } else {
          // 无订单客户：不把静态样例数量当真实配送数量
          assert.equal(data.dm.list.length, 0);
          console.log(`PASS ${hotel.name} matrix-empty: 标题/日期正确，无示例数量串入`);
        }
      }
      await page.waitForTimeout(2000);
      await page.screenshot({ path: `test-results/hotel-print/${key}-${mode}.png` });
    }
    if (process.argv.includes('--published')) {
      const bindDate = ownDate || date;
      const resolved = (await api('/print/resolve-template?bizKey=' + encodeURIComponent(`matrix:${hotel.customerId}:${bindDate}`))).data;
      assert.equal(String(resolved.templateId), hotel.templateId);
      assert.equal(String(resolved.reportViewId), hotel.reportId);
      assert.equal(resolved.matchGlobalDefault, false);
      console.log(`PASS ${hotel.name} 客户级总单绑定`);
    }
  }
} finally { await browser.close(); }
