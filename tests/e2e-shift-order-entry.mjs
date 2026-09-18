// 只读 UI 探针：验证「新增订单」页的班次选择是否按客户开关与配送点声明呈现。
// 每步独立重载页面，避免弹窗/表单状态串扰；不录入商品、不保存订单。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
mkdirSync('test-results/shift-order', { recursive: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const token = (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
  // 班次声明取自配送点主数据（业务在页面维护，探针不写死），期望选项按声明推导
  const depts = (await (await page.request.fetch(base + '/dev-api/partner/customerDept/list?customerId=11',
    { headers: { Authorization: 'Bearer ' + token } })).json()).data;
  const SHIFT_LABEL = { DAY: '白班', NIGHT: '夜班' };
  const declaredOptions = deptName => {
    const dept = depts.find(d => d.name === deptName && Number(d.parentId) !== 0 && !d.isDeleted);
    assert.ok(dept, `未找到配送点 ${deptName}`);
    return (dept.shiftCodes || '').split(',').map(x => x.trim()).filter(Boolean).map(c => SHIFT_LABEL[c] || c).sort();
  };

  /** 关闭提示弹窗（如「同配送点+同日期已有草稿单」）：一律选「重开新单」，
   *  绝不进入「去编辑已有订单」——避免碰到他人/历史的真实草稿 */
  async function dismissDialogs() {
    const box = page.locator('.el-message-box:visible').first();
    if (await box.count() === 0) return;
    const text = (await box.innerText()).replace(/\s+/g, ' ').trim();
    console.log('  [提示弹窗]', text.slice(0, 100));
    const fresh = box.locator('button', { hasText: '重开新单' });
    if (await fresh.count() > 0) await fresh.first().click();
    else await box.locator('button').last().click();
    await page.waitForTimeout(800);
  }

  /** 全新打开下单页 → 选「客户 → 配送点」→ 返回班次表单项的文本与选项 */
  async function pickAndReadShift(customerName, deptName) {
    await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await dismissDialogs();
    // 先设一个远期配送日期：与既有草稿（真实业务数据）不同日期，避免触发「已有草稿」弹窗，
    // 从而不必去动别人的草稿
    const dateInput = page.locator('.el-form .el-date-editor input').first();
    await dateInput.fill('2026-12-31');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(800);
    const cascader = page.locator('.dept-cascader-line .el-cascader').first();
    const menus = page.locator('.el-cascader-menu');
    await cascader.click();
    await page.waitForTimeout(500);
    await menus.nth(0).getByText(customerName, { exact: true }).first().hover();
    await page.waitForTimeout(800);
    await menus.nth(1).getByText(deptName, { exact: true }).first().click();
    await page.waitForTimeout(1500);
    await dismissDialogs();
    const item = page.locator('.el-form-item', { hasText: '班次' }).first();
    if (await item.count() === 0) return null;
    const select = item.locator('.el-select').first();
    const text = await select.innerText();
    await select.click();
    await page.waitForTimeout(600);
    const options = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map(t => t.trim()).sort();
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
    const disabled = await select.locator('.el-select__wrapper').evaluate(el => el.classList.contains('is-disabled'));
    return { text, options, disabled };
  }

  // 1) 未启用班次的客户（金满楼）：不出现班次字段
  assert.equal(await pickAndReadShift('金满楼', '中厨'), null, '未启用班次的客户不应出现班次字段');
  console.log('PASS 未启用班次的客户不显示班次字段');

  // 2) 大长江 + 华铃（声明白+夜）：出现班次，选项为白/夜，且不自动选中
  const hualing = await pickAndReadShift('大长江', '华铃');
  assert.ok(hualing, '启用班次的客户必须出现班次字段');
  assert.ok(!hualing.text.includes('白班') && !hualing.text.includes('夜班'), `多班次不应自动选中，实际「${hualing.text}」`);
  assert.deepEqual(hualing.options, declaredOptions('华铃'), `选项应与华铃声明一致，实际 ${JSON.stringify(hualing.options)}`);
  console.log('PASS 大长江 + 华铃：班次选项=声明班次且默认留空');

  // 3) 大长江 + 棠下（声明白班）：班次仍默认留空，需显式选择（不自动带出）
  const tangxia = await pickAndReadShift('大长江', '棠下');
  assert.ok(!tangxia.text.includes('白班') && !tangxia.text.includes('夜班'), `班次默认应留空，实际「${tangxia.text}」`);
  assert.ok(!tangxia.disabled, '单班次也应可点开选择（不再自动带出后置灰）');
  assert.deepEqual(tangxia.options, declaredOptions('棠下'), `选项应与棠下声明一致，实际 ${JSON.stringify(tangxia.options)}`);
  console.log('PASS 大长江 + 棠下：班次默认留空、选项=声明班次');

  // 4) 大长江 + 自营食堂点（声明白班）：同样默认留空
  const own = await pickAndReadShift('大长江', '大长江');
  assert.ok(!own.text.includes('白班') && !own.text.includes('夜班'), `班次默认应留空，实际「${own.text}」`);
  assert.deepEqual(own.options, declaredOptions('大长江'), `选项应与自营食堂点声明一致，实际 ${JSON.stringify(own.options)}`);
  console.log('PASS 大长江 + 自营食堂点：班次默认留空、选项=声明班次');

  assert.deepEqual(errors, [], `页面存在 JS 错误：${errors.join('; ')}`);
} finally { await browser.close(); }
