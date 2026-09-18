// 验证两件事（临时单用完即删）：
// 1) 选完班次后配送点与班次字段不得消失（原 bug：班次下拉误绑配送点 change，把 customerDeptId 置空）
// 2) 草稿判重按「配送点+日期+班次」：同班次才提示重复，不同班次不拦
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { execFileSync } from 'node:child_process';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const sql = t => execFileSync('mysql', ['-h', process.env.MYSQL_HOST, '-u', process.env.MYSQL_USER, '--default-character-set=utf8mb4', '--batch', '--skip-column-names', process.env.MYSQL_DATABASE, '-e', t], { encoding: 'utf8' }).trim();
const PROBE_DATE = '2026-12-30'; // 远期日期，避开真实草稿
let orderId = null;
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const token = (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
  const call = async (path, method = 'GET', data) => {
    const r = await page.request.fetch(api + path, {
      method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      ...(data === undefined ? {} : { data }),
    });
    const j = await r.json();
    assert.equal(j.code, 200, `${method} ${path}: ${j.msg}`);
    return j;
  };
  const menus = page.locator('.el-cascader-menu');
  const shiftItem = page.locator('.el-form-item', { hasText: '班次' }).first();

  /** 全新打开下单页 → 设远期日期 → 选 大长江/华铃 → 返回班次下拉（此时应留空） */
  async function openAndPickHualing() {
    await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await page.locator('.el-form .el-date-editor input').first().fill(PROBE_DATE);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(800);
    await page.locator('.dept-cascader-line .el-cascader').first().click();
    await page.waitForTimeout(500);
    await menus.nth(0).getByText('大长江', { exact: true }).first().hover();
    await page.waitForTimeout(800);
    await menus.nth(1).getByText('华铃', { exact: true }).first().click();
    await page.waitForTimeout(1800);
    assert.ok(await shiftItem.count() > 0, '选点后应出现班次字段');
    const select = shiftItem.locator('.el-select').first();
    const text = await select.innerText();
    assert.ok(!text.includes('白班') && !text.includes('夜班'), `班次默认应留空，实际「${text}」`);
    return select;
  }

  /** 在下拉里选班次 */
  async function pickShift(select, label) {
    await select.click();
    await page.waitForTimeout(500);
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: label }).first().click();
    await page.waitForTimeout(2000);
  }

  // ---------- 1) 没有夜班草稿时：选夜班不得被拦（可直接做夜班单） ----------
  let select = await openAndPickHualing();
  await pickShift(select, '夜班');
  assert.equal(await page.locator('.el-message-box:visible').count(), 0, '无同班次草稿时不应弹重复提示');
  const deptKept = await page.locator('.dept-cascader-line input').first().inputValue();
  assert.ok(deptKept.includes('华铃'), `送货单位应保持华铃，实际「${deptKept}」`);
  assert.ok((await select.innerText()).includes('夜班'), '班次应保持为夜班');
  console.log('PASS 无夜班草稿时选夜班不被拦（配送点与班次均保持）');

  // ---------- 准备：建一条「华铃 + PROBE_DATE + 夜班」临时草稿 ----------
  const code = (await call('/order/sale/code?refresh=true')).msg;
  const created = (await call('/order/sale/create', 'POST', {
    customerId: 11, customerDeptId: 7, shiftCode: 'NIGHT', orderCode: code,
    deliveryDate: PROBE_DATE, remark: '班次判重验证（自动删除）',
    orderDetails: [{ skuId: 3, productName: '大白菜', productUnit: '斤', productPrice: 6.0, num: 1 }],
  })).data;
  orderId = created.id;
  console.log(`  临时草稿：${created.code}（华铃 / 夜班 / ${PROBE_DATE}）`);

  // ---------- 1) 接口口径：判重按班次 ----------
  const dupNight = (await call(`/order/sale/checkDraft?customerDeptId=7&deliveryDate=${PROBE_DATE}&shiftCode=NIGHT`)).data;
  assert.ok(dupNight && dupNight.id === orderId, '同班次应判为重复');
  const dupDay = (await call(`/order/sale/checkDraft?customerDeptId=7&deliveryDate=${PROBE_DATE}&shiftCode=DAY`)).data;
  assert.ok(!dupDay || !dupDay.id, `不同班次不应判为重复，实际 ${JSON.stringify(dupDay)}`);
  const jml = (await call(`/order/sale/checkDraft?customerDeptId=9&deliveryDate=${PROBE_DATE}&shiftCode=NIGHT`)).data;
  assert.ok(!jml || !jml.id, '未启用班次的客户忽略班次参数（回归）');
  console.log('PASS 判重按班次：同班次命中 / 不同班次不命中 / 未启用班次的客户忽略班次');

  // ---------- 3) UI：同班次草稿存在时，选夜班弹出提示 ----------
  select = await openAndPickHualing();
  assert.equal(await page.locator('.el-message-box:visible').count(), 0, '未选班次时不应判重');
  await pickShift(select, '夜班');
  assert.ok(await shiftItem.count() > 0, '选完班次后班次字段不应消失');
  assert.ok((await select.innerText()).includes('夜班'), '班次应保持为夜班');

  // 提示重复（同班次草稿）
  const box = page.locator('.el-message-box:visible').first();
  assert.ok(await box.count() > 0, '同班次已有草稿时应弹出提示');
  const boxText = (await box.innerText()).replace(/\s+/g, ' ').trim();
  assert.ok(boxText.includes('夜班'), `提示应带上班次，实际「${boxText}」`);
  assert.ok(boxText.includes(created.code), `提示应带上草稿单号，实际「${boxText}」`);
  console.log(`PASS 选夜班后弹出重复提示：${boxText.slice(0, 70)}…`);
  await page.screenshot({ path: 'test-results/shift-order/dup-prompt-by-shift.png', fullPage: false });
  await box.locator('button', { hasText: '重开新单' }).first().click();
  await page.waitForTimeout(1200);
} finally {
  if (orderId != null) {
    const left = sql(`SELECT COUNT(*) FROM t_sale_order WHERE id=${orderId} AND is_deleted=0`);
    if (left !== '0') {
      const p2 = await browser.newPage();
      await p2.goto(base + '/login', { waitUntil: 'networkidle' });
      await p2.fill('input[placeholder*="账号"]', process.env.ERP_USER);
      await p2.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
      await p2.click('.login-button, .el-button--primary');
      await p2.waitForURL(u => !String(u).includes('/login'));
      const tk = (await p2.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
      await p2.request.fetch(api + '/order/sale/' + orderId, { method: 'DELETE', headers: { Authorization: 'Bearer ' + tk } });
      await p2.close();
    }
    assert.equal(sql(`SELECT COUNT(*) FROM t_sale_order WHERE id=${orderId} AND is_deleted=0`), '0', '临时验证单未清理');
    console.log(`PASS 临时验证单已删除（id=${orderId}）`);
  }
  await browser.close();
}
