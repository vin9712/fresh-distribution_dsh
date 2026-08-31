// S1 订单桌面工作台运行时验证（S1-1.1 ~ S1-1.5，2026-09）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 范围：登录 → /order/sale-detail/index/（真实订单录入页）
//   1. S1-1.1 分屏：SplitWorkspace 渲染做单区/选单区；拖拽中缝 → 占比变化 + localStorage 用户命名空间持久化；刷新记忆保持
//   2. S1-1.2 键盘：F3 切右侧搜索面板并聚焦输入框
//   3. S1-1.3 手工定价：改价 → 偏离报价轻提示 + 单价列「手」标签（已去除必填原因弹窗；依赖种子数据：客户+商品池）
//   4. S1-1.4 配送日期变更提示：已录明细后改日期 → 确认框；取消还原
//   5. S1-1.5 草稿箱：入口按钮 + 弹窗打开
// 运行：node tests/e2e-order-workbench.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---- 登录 ----
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  console.log("LOGIN OK");

  // ---- 打开订单工作台 ----
  await page.goto(BASE + "/order/sale-detail/index/", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 15000 });
  await page.waitForTimeout(500);

  // ---- 1. S1-1.1 分屏结构 ----
  const leftCard = await page.locator(".split-workspace__pane--left .order-card").count();
  const rightCard = await page.locator(".split-workspace__pane--right .recent-order-card").count();
  if (leftCard !== 1) problems.push(`[分屏] 左侧做单区未渲染: ${leftCard}`);
  if (rightCard !== 1) problems.push(`[分屏] 右侧选单区未渲染: ${rightCard}`);
  const tabs = await page.locator(".right-tabs .el-tabs__item").allInnerTexts();
  console.log("右侧标签页:", tabs.join("/"));
  for (const t of ["常用", "最近", "搜索"]) {
    if (!tabs.some((x) => x.includes(t))) problems.push(`[标签页] 缺少「${t}」: ${tabs.join("/")}`);
  }
  const draftBtn = await page.locator(".draft-box-btn").count();
  if (draftBtn !== 1) problems.push(`[草稿箱] 入口按钮未渲染: ${draftBtn}`);

  // ---- 1b. 拖拽中缝 → 占比变化 + 持久化 ----
  const handle = page.locator(".split-workspace__handle");
  const box = await handle.boundingBox();
  const leftWidthBefore = (await page.locator(".split-workspace__pane--left").boundingBox()).width;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 - 120, box.y + box.height / 2, { steps: 8 });
  await page.mouse.up();
  const leftWidthAfter = (await page.locator(".split-workspace__pane--left").boundingBox()).width;
  console.log(`左栏宽度: ${leftWidthBefore.toFixed(0)} -> ${leftWidthAfter.toFixed(0)}`);
  if (Math.abs(leftWidthAfter - (leftWidthBefore - 120)) > 20) problems.push(`[拖拽] 左栏宽度变化异常: ${leftWidthBefore} -> ${leftWidthAfter}`);
  const stored = await page.evaluate(() => {
    const keys = Object.keys(localStorage).filter((k) => k.startsWith("splitws:") && k.endsWith(":order-sale-detail"));
    return keys.map((k) => ({ k, v: localStorage.getItem(k) }));
  });
  if (!stored.length) problems.push("[持久化] 未找到 splitws:…:order-sale-detail 存储键");
  else console.log("存储:", JSON.stringify(stored));

  // ---- 1c. 刷新后记忆保持 ----
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 15000 });
  await page.waitForTimeout(500);
  const leftWidthReload = (await page.locator(".split-workspace__pane--left").boundingBox()).width;
  console.log("刷新后左栏宽度:", leftWidthReload.toFixed(0));
  if (Math.abs(leftWidthReload - leftWidthAfter) > 8) problems.push(`[记忆] 刷新后左栏 ${leftWidthReload.toFixed(0)} ≉ 拖拽后 ${leftWidthAfter.toFixed(0)}`);

  // ---- 2. S1-1.2 F3 聚焦搜索面板 ----
  await page.keyboard.press("F3");
  await page.waitForTimeout(300);
  const searchVisible = await page.locator(".search-panel").isVisible();
  const searchFocused = await page.evaluate(() => {
    const el = document.activeElement;
    return !!el && (!!el.closest(".search-bar") || el.classList.contains("el-input__inner"));
  });
  console.log("F3 后搜索面板可见:", searchVisible, "输入框聚焦:", searchFocused);
  if (!searchVisible) problems.push("[F3] 搜索面板未显示");
  if (!searchFocused) problems.push("[F3] 搜索输入框未聚焦");

  // ---- 5. S1-1.5 草稿箱弹窗 ----
  await page.click(".draft-box-btn");
  await page.waitForTimeout(300);
  const draftDialogVisible = await page.locator(".el-dialog:visible").filter({ hasText: "我的订单草稿" }).isVisible().catch(() => false);
  console.log("草稿箱弹窗可见:", draftDialogVisible);
  if (!draftDialogVisible) problems.push("[草稿箱] 弹窗未打开");
  await page.keyboard.press("Escape");
  await page.waitForTimeout(200);

  // ---- 3. S1-1.3 手工定价 + 4. S1-1.4 日期提示（依赖种子数据，选客户→插行→改价）----
  const cascader = page.locator(".order-card .el-cascader").first();
  await cascader.click();
  await page.waitForTimeout(400);
  const panelNodes = await page.locator(".el-cascader-panel .el-cascader-node").count();
  console.log("级联面板节点数:", panelNodes);
  if (panelNodes === 0) {
    console.log("SKIP: 无客户种子数据，跳过手工定价/日期提示场景");
  } else {
    // 选第一级第一个客户，悬停展开配送点后选第一个
    await page.locator(".el-cascader-panel .el-cascader-node").first().hover();
    await page.waitForTimeout(400);
    const level2 = page.locator(".el-cascader-menu").nth(1).locator(".el-cascader-node");
    const level2Count = await level2.count();
    if (level2Count === 0) {
      console.log("SKIP: 客户下无配送点，跳过手工定价/日期提示场景");
    } else {
      await level2.first().click();
      await page.waitForTimeout(800); // 等客户商品池/取价接口返回
      // 级联收起
      await page.keyboard.press("Escape");
      await page.waitForTimeout(200);

      // 从常用面板插入一个商品（无常用则用搜索面板检索）
      let inserted = false;
      await page.click(".right-tabs .el-tabs__item:has-text('常用')");
      await page.waitForTimeout(400);
      const freqItems = await page.locator(".frequent-item").count();
      if (freqItems > 0) {
        await page.locator(".frequent-item").first().click();
        inserted = true;
      } else {
        await page.keyboard.press("F3");
        await page.fill(".search-bar input", "");
        await page.click(".search-bar .el-button");
        await page.waitForTimeout(600);
        const searchItems = await page.locator(".search-list .frequent-item").count();
        if (searchItems > 0) {
          await page.locator(".search-list .frequent-item").first().click();
          inserted = true;
        }
      }
      console.log("商品行已插入:", inserted);
      if (!inserted) {
        console.log("SKIP: 无可插入商品，跳过手工定价/日期提示场景");
      } else {
        await page.waitForTimeout(600);
        // 明细表应至少一行有效商品 → S1-1.4：改配送日期应弹确认
        const deliveryInput = page.locator(".order-card .el-date-editor input").first();
        await deliveryInput.click();
        await page.waitForTimeout(300);
        await deliveryInput.fill("2026-12-31");
        await deliveryInput.press("Enter");
        await page.waitForTimeout(600);
        const dateConfirm = page.locator(".el-message-box:visible, .el-popconfirm, .el-overlay-message-box");
        const dateConfirmVisible = await dateConfirm.first().isVisible().catch(() => false);
        console.log("日期变更确认框可见:", dateConfirmVisible);
        if (!dateConfirmVisible) {
          problems.push("[日期提示] 已录明细后改配送日期未弹确认");
        } else {
          await page.click(".el-message-box__btns .el-button:not(.el-button--primary)").catch(() => {});
          await page.waitForTimeout(300);
        }

        // S1-1.3：进入单价单元格改价 → 偏离报价轻提示 + 「手」标签（已去除必填原因弹窗）
        // 通过列头定位「单价」列
        const priceColIndex = await page.evaluate(() => {
          const headers = [...document.querySelectorAll(".order-card .vxe-header--row th")];
          const i = headers.findIndex((th) => th.innerText.trim() === "单价");
          return i;
        });
        console.log("单价列索引:", priceColIndex);
        if (priceColIndex < 0) {
          problems.push("[手工定价] 未找到单价列");
        } else {
          const cell = page.locator(".order-card .vxe-body--row").first().locator("td").nth(priceColIndex);
          await cell.dblclick();
          await page.waitForTimeout(400);
          const input = page.locator(".order-card .vxe-body--row").first().locator("td input:visible").first();
          await input.fill("99.99");
          await input.press("Tab");
          await page.waitForTimeout(600);
          // 不应再弹出原因必填弹窗
          const manualDialog = page.locator(".el-dialog:visible").filter({ hasText: "手工定价原因" });
          const manualVisible = await manualDialog.isVisible().catch(() => false);
          if (manualVisible) {
            problems.push("[手工定价] 不应再弹原因必填弹窗（已去除）");
          }
          // 偏离报价应出现轻提示（有报价时）
          const msg = await page.locator(".el-message--warning").count();
          console.log("偏离提示:", msg);
          // 单价列应出现「手」标签（价格来源已标记 manual）
          const manualTag = await page.locator(".order-card .price-manual-tag").count();
          console.log("单价列「手」标签:", manualTag);
          if (manualTag < 1) problems.push("[手工定价] 改价后单价列未显示「手」标签");
        }
      }
    }
  }

  // 清理：恢复默认分屏宽度，避免污染其他用例
  await page.evaluate(() => {
    Object.keys(localStorage)
      .filter((k) => k.startsWith("splitws:") && k.endsWith(":order-sale-detail"))
      .forEach((k) => localStorage.removeItem(k));
  });
} finally {
  await browser.close();
}

if (problems.length) {
  console.error("\n❌ 验证未通过:");
  problems.forEach((p) => console.error(" -", p));
  process.exit(1);
}
console.log("\n✅ 订单工作台 S1 验证通过");
