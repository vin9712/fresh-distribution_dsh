// 最近订单面板「多状态 + 无感模式条 + 跨配送日期不误弹」回归（2026-09-19）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 覆盖：
//   1. 后端 /order/sale/recent/list 默认只含「草稿+已确认」；statuses 参数可按状态过滤
//   2. 右侧「最近」面板：状态列（复用 orderStatusCell）+ 状态筛选（草稿+已确认/仅草稿/仅已确认/全部）
//   3. 无感切换：点已确认行 → 只读模式条 + 「撤回并编辑」；点草稿行 → 「编辑草稿」，且不再弹模态确认
//   4. 模式条「返回我的新单」可恢复被暂存的录入工作区
//   5. 跨配送日期程序性载入不误弹确认；但用户手改配送日期仍应提示（反向保障）
//   6. 严格复现（新单已录明细 → 点跨日期草稿）自清理：仅删除本次用例新建的 new: 草稿（本地+服务端）
// 运行：ERP_USER=admin ERP_PASSWORD=admin123 node tests/e2e-recent-status-strip.mjs
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const user = process.env.ERP_USER || 'admin';
const pwd = process.env.ERP_PASSWORD || 'admin123';

const problems = [];
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } });
  page.on('pageerror', (e) => problems.push('[pageerror] ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push('[console.error] ' + m.text().slice(0, 200));
  });
  page.on('response', (r) => {
    if (r.status() >= 500) problems.push('[http' + r.status() + '] ' + r.url());
  });

  // ---- 登录 ----
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', user);
  await page.fill('input[placeholder*="密码"]', pwd);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL((u) => !String(u).includes('/login'), { timeout: 20000 });
  const token = (await page.context().cookies(base)).find((c) => c.name === 'Admin-Token').value;
  const call = async (path, method = 'GET') => {
    const r = await page.request.fetch(api + path, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    });
    const j = await r.json();
    assert.equal(j.code, 200, `${method} ${path}: ${j.msg}`);
    return j.data || [];
  };

  // ---- 1. 接口：默认草稿+已确认；statuses 可过滤 ----
  const def = await call('/order/sale/recent/list?recentDays=30');
  console.log('默认(不传 statuses):', def.length, '状态集合=', [...new Set(def.map((o) => o.status))].join(','));
  assert.ok(def.every((o) => o.status === 0 || o.status === 1), '默认应只含草稿/已确认');

  const draftOnly = await call('/order/sale/recent/list?recentDays=30&statuses=0');
  assert.ok(draftOnly.every((o) => o.status === 0), 'statuses=0 应只含草稿');
  const confOnly = await call('/order/sale/recent/list?recentDays=30&statuses=1');
  assert.ok(confOnly.every((o) => o.status === 1), 'statuses=1 应只含已确认');
  const all = await call('/order/sale/recent/list?recentDays=30&statuses=0,1,2,3,4');
  console.log('全部状态:', all.length, '状态集合=', [...new Set(all.map((o) => o.status))].join(','));
  assert.ok(all.length >= def.length, '全部状态应不少于默认');
  console.log('PASS 最近订单 statuses 过滤');

  // 选一张明细行数最多的已确认单（用于左侧滚动复位校验）
  let multiRow = null;
  for (const o of confOnly) {
    const d = await call('/order/saleDetail/list?orderId=' + o.id);
    if (!multiRow || d.length > multiRow.rows) multiRow = { code: o.code, rows: d.length };
  }
  console.log('多行单候选:', multiRow ? `${multiRow.code}(${multiRow.rows}行)` : '无');

  // ---- 2. 面板：状态列 + 状态筛选 ----
  await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.recent-order-card', { timeout: 15000 });
  await page.waitForTimeout(1200);

  const filterBtns = await page.locator('.recent-status-group .el-radio-button').allInnerTexts();
  for (const t of ['草稿+已确认', '仅草稿', '仅已确认', '全部']) {
    assert.ok(filterBtns.some((x) => x.includes(t)), '缺少筛选按钮 ' + t);
  }
  const headers = await page.locator('.recent-order-card .vxe-header--row th').allInnerTexts();
  assert.ok(headers.some((h) => h.includes('状态')), '最近表缺少状态列');
  const statusTag = '.recent-order-card .order-status-cell .order-status-dict .el-tag';
  assert.ok((await page.locator(statusTag).count()) > 0, '状态列未渲染状态标签');
  console.log('PASS 状态列 + 状态筛选渲染');

  // ---- 3. 无感切换：已确认 → 只读 + 撤回并编辑 ----
  await page.click('.recent-status-group .el-radio-button:has-text("仅已确认")');
  await page.waitForTimeout(900);
  const confTags = await page.locator(statusTag).allInnerTexts();
  assert.ok(confTags.length > 0, '仅已确认应有数据');
  assert.ok(confTags.every((t) => t.includes('已确认')), '仅已确认筛出了非已确认行');

  await page.locator('.recent-order-card .vxe-body--row').first().click();
  await page.waitForTimeout(1500);
  const strip = page.locator('.order-mode-strip');
  assert.ok(await strip.isVisible(), '未显示订单模式条');
  const stripText = (await strip.innerText()).replace(/\s+/g, ' ');
  console.log('已确认模式条:', stripText);
  assert.ok(stripText.includes('已确认'), '已确认单模式条应标注已确认');
  assert.ok(
    await strip.locator('button:has-text("撤回并编辑")').isVisible(),
    '已确认单应提供「撤回并编辑」'
  );
  console.log('PASS 已确认单只读模式条 + 就地撤回入口');

  // ---- 3b. 左右联动：右侧对应行高亮；切换筛选重载列表后仍保持；左侧明细回第一行 ----
  const codeMatch = stripText.match(/【([^】]+)】/);
  const openCode = codeMatch ? codeMatch[1] : '';
  const curRow = page.locator('.recent-order-card .vxe-body--row.row--current');
  console.log('高亮行数(应为1):', await curRow.count());
  assert.equal(await curRow.count(), 1, '当前打开订单应在右侧列表高亮');
  if (openCode) {
    assert.ok((await curRow.first().innerText()).includes(openCode), '高亮行应为当前打开的订单');
  }
  // 重载列表（切筛选再切回）后高亮应保持
  await page.click('.recent-status-group .el-radio-button:has-text("仅草稿")');
  await page.waitForTimeout(800);
  await page.click('.recent-status-group .el-radio-button:has-text("仅已确认")');
  await page.waitForTimeout(1000);
  const curRowAfterReload = await page.locator('.recent-order-card .vxe-body--row.row--current').count();
  console.log('重载后高亮行数(应为1):', curRowAfterReload);
  assert.equal(curRowAfterReload, 1, '列表重载后应保持当前行高亮');
  await page.screenshot({ path: 'test-results/recent-highlight.png', fullPage: false });
  console.log('PASS 左右联动高亮（重载后保持）');

  // ---- 3c. 左侧「订单区」切换订单后滚动条复位（用多行单 + 小视口确保可滚） ----
  if (multiRow && multiRow.rows >= 6) {
    // 查询天数=30，确保能搜到较旧的多行单
    await page.locator('.recent-order-card .el-select').first().click();
    await page.waitForTimeout(400);
    await page.locator('.el-select-dropdown__item:has-text("30天")').last().click();
    await page.waitForTimeout(900);
    const kw = page.locator('.recent-order-card input[placeholder*="订单编号"]').first();
    await kw.fill(multiRow.code);
    await page.locator('.recent-order-card .el-button--primary.is-circle').first().click();
    await page.waitForTimeout(900);
    await page.locator('.recent-order-card .vxe-body--row').first().click();
    await page.waitForTimeout(1500);
    await page.setViewportSize({ width: 1680, height: 420 });
    await page.waitForTimeout(500);
    // 订单区实际滚动条在 .order-card .el-card__body
    const pre = await page.evaluate(() => {
      const el = document.querySelector('.order-card .el-card__body');
      if (!el) return -1;
      el.scrollTop = el.scrollHeight;
      return el.scrollTop;
    });
    console.log(`多行单 ${multiRow.code}(${multiRow.rows}行) 订单区预滚动 scrollTop=${pre}`);
    if (pre > 0) {
      // 先清搜索词，再切仅草稿（否则关键词会过滤掉草稿行）
      await kw.fill('');
      await page.locator('.recent-order-card .el-button--primary.is-circle').first().click();
      await page.waitForTimeout(600);
      await page.click('.recent-status-group .el-radio-button:has-text("仅草稿")');
      await page.waitForTimeout(900);
      await page.locator('.recent-order-card .vxe-body--row').first().click();
      await page.waitForTimeout(1500);
      const after = await page.evaluate(() => {
        const el = document.querySelector('.order-card .el-card__body');
        return el ? el.scrollTop : -1;
      });
      console.log('切换订单后订单区 scrollTop(应为0):', after);
      assert.equal(after, 0, '切换订单后订单区滚动条应复位到顶部');
      console.log('PASS 切换订单后订单区滚动条复位');
    }
    // 还原：清搜索词 + 恢复视口
    await kw.fill('');
    await page.locator('.recent-order-card .el-button--primary.is-circle').first().click();
    await page.waitForTimeout(700);
    await page.setViewportSize({ width: 1680, height: 1000 });
    await page.waitForTimeout(300);
  } else {
    console.log('SKIP 无多行单，跳过订单区滚动复位校验');
  }

  // ---- 4. 草稿 → 编辑草稿（无模态）+ 用户手改日期仍提示 + 返回 ----
  await page.click('.recent-status-group .el-radio-button:has-text("仅草稿")');
  await page.waitForTimeout(900);
  const draftRows = await page.locator('.recent-order-card .vxe-body--row').count();
  if (draftRows > 0) {
    await page.locator('.recent-order-card .vxe-body--row').first().click();
    await page.waitForTimeout(1500);
    const t2 = (await page.locator('.order-mode-strip').innerText()).replace(/\s+/g, ' ');
    console.log('草稿模式条:', t2);
    assert.ok(t2.includes('编辑草稿'), '草稿单应进入编辑草稿态');
    assert.equal(await page.locator('.el-message-box:visible').count(), 0, '点行不应再弹模态确认');

    // 4a-1) 左右联动：草稿行高亮
    assert.equal(
      await page.locator('.recent-order-card .vxe-body--row.row--current').count(),
      1,
      '草稿单应在右侧列表高亮'
    );
    console.log('PASS 草稿行高亮');

    // 4b) 反向保障：用户手改配送日期仍应提示（抑制标志不能误伤真实手改）
    const dateInput = page.locator('.order-card .el-date-editor input').first();
    await dateInput.click();
    await page.waitForTimeout(300);
    await dateInput.fill('2026-12-31');
    await dateInput.press('Enter');
    await page.waitForTimeout(800);
    const userModal = await page.locator('.el-message-box:visible').count();
    console.log('用户手改日期确认框(应为1):', userModal);
    assert.ok(userModal >= 1, '用户手改配送日期应仍弹确认（未被误抑制）');
    await page
      .click('.el-message-box__btns .el-button:not(.el-button--primary)')
      .catch(() => {});
    await page.waitForTimeout(500);
    console.log('PASS 用户手改日期仍弹确认（未被误抑制）');

    const backBtn = page.locator('.order-mode-strip button:has-text("返回")');
    assert.ok(await backBtn.isVisible(), '切换后应提供返回入口');
    await backBtn.click();
    await page.waitForTimeout(800);
    assert.equal(await page.locator('.order-mode-strip').count(), 0, '返回新单后不应再显示模式条');
    console.log('PASS 草稿单无感编辑 + 返回暂存工作区');
  } else {
    console.log('SKIP 当前窗口无草稿行，跳过草稿态校验');
  }

  // ---- 5. 严格复现：新单已录明细 → 点跨配送日期草稿，不应误弹确认（自清理） ----
  const draftKeysBefore = await page.evaluate(() =>
    Object.keys(localStorage).filter((k) => k.startsWith('saleDraft:') && k.includes(':new:'))
  );
  await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.order-card .el-cascader', { timeout: 15000 });
  await page.waitForTimeout(600);
  await page.locator('.order-card .el-cascader').first().click();
  await page.waitForTimeout(500);
  const cascaderNodes = await page.locator('.el-cascader-panel .el-cascader-node').count();
  let reproDone = false;
  if (cascaderNodes > 0) {
    await page.locator('.el-cascader-panel .el-cascader-node').first().hover();
    await page.waitForTimeout(500);
    const level2 = page.locator('.el-cascader-menu').nth(1).locator('.el-cascader-node');
    if ((await level2.count()) > 0) {
      await level2.first().click();
      await page.waitForTimeout(1000);
      await page.keyboard.press('Escape');
      await page.waitForTimeout(300);
      // 插入商品 → hasDetailContent=true
      await page.click(".right-tabs .el-tabs__item:has-text('常用')");
      await page.waitForTimeout(500);
      let inserted = false;
      if ((await page.locator('.frequent-item').count()) > 0) {
        await page.locator('.frequent-item').first().click();
        inserted = true;
      } else {
        await page.keyboard.press('F3');
        await page.click('.search-bar .el-button');
        await page.waitForTimeout(800);
        if ((await page.locator('.search-list .frequent-item').count()) > 0) {
          await page.locator('.search-list .frequent-item').first().click();
          inserted = true;
        }
      }
      if (inserted) {
        await page.waitForTimeout(600);
        const curDate = await page.locator('.order-card .el-date-editor input').first().inputValue();
        await page.click(".right-tabs .el-tabs__item:has-text('最近')");
        await page.waitForTimeout(400);
        await page.click('.recent-status-group .el-radio-button:has-text("仅草稿")');
        await page.waitForTimeout(900);
        const rows = await page.locator('.recent-order-card .vxe-body--row').count();
        let targetIdx = -1;
        let targetDate = '';
        for (let i = 0; i < rows; i++) {
          const txt = (await page.locator('.recent-order-card .vxe-body--row').nth(i).innerText()).trim();
          const m = txt.match(/\d{4}-\d{2}-\d{2}/);
          if (m && m[0] !== curDate) {
            targetIdx = i;
            targetDate = m[0];
            break;
          }
        }
        if (targetIdx >= 0) {
          console.log(`跨日期复现：当前新单 ${curDate} → 点草稿 ${targetDate}`);
          await page.locator('.recent-order-card .vxe-body--row').nth(targetIdx).click();
          await page.waitForTimeout(1600);
          const crossModal = await page.locator('.el-message-box:visible').count();
          console.log('跨日期载入模态框(应为0):', crossModal);
          assert.equal(crossModal, 0, '程序性跨日期载入不应弹配送日期变更确认');
          console.log('PASS 跨配送日期程序性载入不误弹确认');
          reproDone = true;
        } else {
          console.log('SKIP 草稿中无与当前新单不同日期的行，跳过严格复现');
        }
      } else {
        console.log('SKIP 无可插入商品，跳过严格复现');
      }
    } else {
      console.log('SKIP 客户下无配送点，跳过严格复现');
    }
  } else {
    console.log('SKIP 无客户种子数据，跳过严格复现');
  }

  // 清理：删除本次严格复现新建的 new: 草稿（本地 + 服务端）；不动任何历史草稿
  const draftKeysAfter = await page.evaluate(() =>
    Object.keys(localStorage).filter((k) => k.startsWith('saleDraft:') && k.includes(':new:'))
  );
  const created = draftKeysAfter.filter((k) => !draftKeysBefore.includes(k));
  for (const k of created) {
    await call('/order/saleDraft/' + encodeURIComponent(k), 'DELETE').catch(() => {});
    await page.evaluate((key) => localStorage.removeItem(key), k);
    console.log('已清理本次新建草稿:', k);
  }
  if (reproDone && !created.length) console.log('（本次复现未新建草稿，无需清理）');

  await page.screenshot({ path: 'test-results/recent-status-strip.png', fullPage: false });
} finally {
  await browser.close();
}

if (problems.length) {
  console.error('\n❌ 验证未通过:');
  problems.forEach((p) => console.error(' -', p));
  process.exit(1);
}
console.log('\n✅ 最近订单多状态 + 无感模式条 + 跨日期不误弹 验证通过');
