// 验证：启用班次的客户，未选班次时明细录入区被拦住（与未选送货单位同一套提示）。
// 只读：不保存订单。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
const base = process.env.WEB_BASE || 'http://localhost:1025';
mkdirSync('test-results/shift-order', { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const menus = page.locator('.el-cascader-menu');

  async function openAndPick(customer, dept) {
    await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await page.locator('.el-form .el-date-editor input').first().fill('2026-12-31');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(600);
    await page.locator('.dept-cascader-line .el-cascader').first().click();
    await page.waitForTimeout(500);
    await menus.nth(0).getByText(customer, { exact: true }).first().hover();
    await page.waitForTimeout(800);
    await menus.nth(1).getByText(dept, { exact: true }).first().click();
    await page.waitForTimeout(1600);
  }
  const rowCount = () => page.locator('.vxe-table--body tbody tr').count();
  const toastText = async () => (await page.locator('.el-message').allInnerTexts()).join('|');

  // 1) 选点未选班次：商品面板提示「请选择班次」（与「请先选择送货单位」同一形态）
  await openAndPick('大长江', '华铃');
  const panelText = await page.locator('.frequent-panel').innerText();
  assert.ok(panelText.includes('请选择班次'), `商品面板应提示请选择班次，实际「${panelText.replace(/\s+/g, ' ').slice(0, 60)}」`);
  console.log('PASS 未选班次时商品面板提示「请选择班次」');

  // 2) 未选班次时点「加行 +」应被拦（提示 + 行数不变）
  const before = await rowCount();
  await page.locator('.vxe-table--body tbody tr').first().hover();
  await page.waitForTimeout(600);
  // 首列动作 span：0=加行(+)，1=删行(-)
  await page.locator('.vxe-table--body tbody tr').first().locator('td').first().locator('span').first().click();
  await page.waitForTimeout(1200);
  assert.ok((await toastText()).includes('请选择班次'), `加行应提示请选择班次，实际「${await toastText()}」`);
  assert.equal(await rowCount(), before, '未选班次时不应新增明细行');
  console.log('PASS 未选班次时加行被拦（提示 + 行数不变）');
  await page.screenshot({ path: 'test-results/shift-order/detail-gated-by-shift.png', fullPage: false });

  // 3) 选了班次后门禁解除
  const select = page.locator('.el-form-item', { hasText: '班次' }).first().locator('.el-select').first();
  await select.click();
  await page.waitForTimeout(500);
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click();
  await page.waitForTimeout(1500);
  const panelAfter = await page.locator('.frequent-panel').innerText();
  assert.ok(!panelAfter.includes('请选择班次'), '选了班次后不应再提示请选择班次');
  console.log('PASS 选了班次后门禁解除（面板不再提示）');

  // 4) 未启用班次的客户：面板不出现班次提示（回归）
  await openAndPick('金满楼', '中厨');
  const jmlPanel = await page.locator('.frequent-panel').innerText();
  assert.ok(!jmlPanel.includes('请选择班次'), `未启用班次的客户不应提示班次，实际「${jmlPanel.replace(/\s+/g, ' ').slice(0, 60)}」`);
  console.log('PASS 未启用班次的客户无班次提示');
} finally { await browser.close(); }
