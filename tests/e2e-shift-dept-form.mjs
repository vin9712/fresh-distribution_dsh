// 只读检查：配送点编辑弹窗的班次回显是否正确（不保存，避免误写主数据）。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  await page.goto(base + '/basicInfo/customer-dept/index/11', { waitUntil: 'networkidle' });
  await page.waitForTimeout(3500);

  /** 打开指定配送点的编辑弹窗，读班次多选回显，然后取消（不保存） */
  async function shiftSelection(deptName) {
    const row = page.locator('.el-table__row', { hasText: deptName }).first();
    await row.locator('text=修改').first().click();
    await page.waitForTimeout(1500);
    const item = page.locator('.el-dialog .el-form-item', { hasText: '班次' }).first();
    if (await item.count() === 0) return null; // 客户未启用班次 → 无该字段
    const tags = await item.locator('.el-select__tags-text, .el-select__selected-item').allInnerTexts();
    await page.locator('.el-dialog').getByText('取 消').click();
    await page.waitForTimeout(800);
    // 同一 tag 会同时命中 tags-text 与 selected-item，去重后返回
    return [...new Set(tags.map(t => t.trim()).filter(Boolean))];
  }

  // 期望值取自配送点主数据声明（业务可自行维护），不写死
  const depts = (await (await page.request.fetch(base + '/dev-api/partner/customerDept/list?customerId=11',
    { headers: { Authorization: 'Bearer ' + (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value } })).json()).data;
  const LABEL = { DAY: '白班', NIGHT: '夜班' };
  const declared = name => {
    const d = depts.find(x => x.name === name && Number(x.parentId) !== 0 && !x.isDeleted);
    assert.ok(d, `未找到配送点 ${name}`);
    return (d.shiftCodes || '').split(',').map(x => x.trim()).filter(Boolean).map(c => LABEL[c] || c).sort();
  };

  for (const name of ['华铃', '棠下', '大长江']) {
    const actual = await shiftSelection(name);
    assert.deepEqual(actual.sort(), declared(name), `${name} 编辑弹窗回显应与声明一致，实际 ${JSON.stringify(actual)}`);
    console.log(`PASS ${name} 编辑弹窗回显=声明班次（${declared(name).join('+') || '无'}）`);
  }
} finally { await browser.close(); }
