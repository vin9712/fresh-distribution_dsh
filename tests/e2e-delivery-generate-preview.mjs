// 页面级 E2E：送货单「生成 → 客户维度待生成清单 → 确认」三步式 + 客户管理页送货单视图（D-039/D-040）
// 前置：本地环境已启动（后端 8090、前端 1025，远程库）
// 范围：只读冒烟——只打开预览抽屉核对内容后取消，绝不点「确认生成 / 生成补单」，不产生业务数据
// 运行：node tests/e2e-delivery-generate-preview.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });

const ok = (label) => console.log("OK   " + label);
const bad = (label) => {
  console.log("FAIL " + label);
  problems.push(label);
};
const check = (cond, label) => (cond ? ok(label) : bad(label));

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
  ok("LOGIN -> " + page.url());

  const drawer = () => page.locator(".el-drawer:visible").last();
  /** 等预览接口返回后再断言（抽屉标题先于数据渲染，否则拿到的是空值） */
  const waitPreview = async (fn) => {
    const resp = page.waitForResponse((r) => r.url().includes("/order/delivery/group-preview"), { timeout: 15000 });
    await fn();
    await resp;
    await page.waitForTimeout(800);
  };
  const closeDrawer = async () => {
    const btn = drawer().locator("button", { hasText: "取 消" });
    if (await btn.count()) await btn.first().click();
    await page.waitForTimeout(600);
  };

  // ================= 1. 送货单据页：有遗漏订单的日期 → 客户维度清单 =================
  await page.goto(BASE + "/order/delivery", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container", { timeout: 15000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});

  const dateInput = page.locator('input[placeholder="选择配送日期"]');
  check((await dateInput.count()) === 1, "工具栏存在生成用配送日期选择器");
  await dateInput.first().click({ force: true });
  await dateInput.first().fill("2026-08-28");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Escape");
  await page.waitForTimeout(1200);

  await page.locator("button", { hasText: "生成送货单" }).first().click({ force: true });
  await drawer().locator("text=待生成清单确认").waitFor({ timeout: 15000 });
  await page.waitForResponse((r) => r.url().includes("/order/delivery/group-preview"), { timeout: 15000 });
  await page.waitForTimeout(800);
  ok("点「生成送货单」打开待生成清单抽屉（不再是一句 confirm 直接写单）");

  const summary = (await drawer().locator(".gp-summary").innerText()).replace(/\s+/g, "");
  check(/待生成客户1/.test(summary), "汇总条：待生成客户 1");
  check(/待并入订单2/.test(summary), "汇总条：待并入订单 2");
  check(/预计生成送货单2张/.test(summary), "汇总条：预计生成送货单 2 张（每点一单 → 华铃/棠下各一张）");
  check(/金额合计87\.00/.test(summary), "汇总条：金额合计 87.00");

  const rows = drawer().locator(".el-table__body-wrapper .el-table__body > tbody > tr.el-table__row");
  check((await rows.count()) === 1, "清单为「一行 = 一个客户」");
  const rowText = await rows.first().innerText();
  check(/大长江/.test(rowText), "客户行显示客户名（大长江）");
  check(/每点一单/.test(rowText), "客户行显示组单策略（每点一单）");
  check(/正常生成/.test(rowText), "客户行显示处理方式（正常生成）");
  check(/来源：客户配置/.test(rowText), "客户行显示策略来源（客户配置，D-041）");

  // 展开看该客户待并入订单
  await rows.first().locator(".el-table__expand-icon").click();
  await page.waitForTimeout(600);
  const expand = await drawer().locator(".gp-expand").innerText();
  check(/待并入销售订单（2 张/.test(expand), "展开区：待并入销售订单 2 张");
  check(/XD202608280001/.test(expand) && /XD202608280005/.test(expand), "展开区列出两张订单号");
  check(/华铃/.test(expand) && /棠下/.test(expand), "展开区显示配送点（华铃/棠下）");
  check(/未进单·本次并入/.test(expand), "展开区显示进单情况标识");
  await closeDrawer();

  // ================= 2. 送货单据页：已全部进单的日期 → 空清单 + 确认禁用 =================
  const di2 = page.locator('input[placeholder="选择配送日期"]').first();
  await waitPreview(async () => {
    await di2.click({ force: true });
    await di2.fill("2026-08-29");
    await page.keyboard.press("Enter");
    await page.keyboard.press("Escape");
    await page.waitForTimeout(600);
    await page.locator("button", { hasText: "生成送货单" }).first().click({ force: true });
  });
  const emptyText = (await drawer().innerText()).replace(/\s+/g, "");
  check(/无需再生成（幂等）/.test(emptyText), "已全部进单日期：清单空态文案正确");
  check(/另有1张草稿订单未确认/.test(emptyText), "草稿不静默漏发提示（D-042）");
  const confirmBtn = drawer().locator("button", { hasText: "确认生成" }).first();
  check(await confirmBtn.isDisabled(), "清单为空时「确认生成」按钮禁用");
  await closeDrawer();

  // ================= 3. 客户管理页：策略列 + 客户维度送货单抽屉 =================
  await page.goto(BASE + "/basicInfo/customer", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container table", { timeout: 15000 });
  await page.waitForResponse((r) => r.url().includes("/partner/customer/page"), { timeout: 15000 });
  await page.waitForTimeout(1000);
  const cols = (await page.locator("th").allInnerTexts()).filter(Boolean).join(" | ");
  check(/送货单组单策略/.test(cols), "客户列表含「送货单组单策略」列");
  check(/相同商品合并/.test(cols), "客户列表含「相同商品合并」列");
  check(/组单策略/.test(await page.locator(".app-container form").first().innerText()), "客户列表可按组单策略筛选");

  const bigRow = page.locator("tbody tr.el-table__row", { hasText: "大长江" }).first();
  check((await bigRow.count()) > 0, "客户列表存在测试客户「大长江」");
  check(/每点一单/.test(await bigRow.innerText()), "客户行直接可见组单口径（不必进编辑弹窗）");
  await waitPreview(async () => {
    await bigRow.locator("button", { hasText: "送货单" }).click({ force: true });
    await drawer().locator("text=客户送货单 ·").waitFor({ timeout: 15000 });
  });
  ok("客户行「送货单」打开客户维度抽屉");

  // 切到 2026-08-28（该客户当日有未进单订单）看完整清单
  // 注：抽屉内不可用 Escape（会连抽屉一起关掉），改用回车确认 + 点标题栏收起日期面板
  const cdDate = drawer().locator('input[placeholder="选择配送日期"]').first();
  await waitPreview(async () => {
    await cdDate.click({ force: true });
    await cdDate.fill("2026-08-28");
    await page.keyboard.press("Enter");
    await drawer().locator(".el-drawer__title").click({ force: true });
  });
  const cdText = (await drawer().innerText()).replace(/\s+/g, "");
  check(/当日生效策略/.test(cdText), "抽屉含「当日生效策略」区");
  check(/来源：客户配置（尚未建批次）|来源：当日批次快照/.test(cdText), "抽屉显示策略来源（D-041）");
  check(/待生成清单/.test(cdText), "抽屉含待生成清单区");
  check(/大长江/.test(cdText) && /正常生成/.test(cdText), "抽屉内列出该客户行（处理方式：正常生成）");
  // 展开客户行看待并入订单明细
  const cdRows = drawer().locator(".el-table__body-wrapper .el-table__body > tbody > tr.el-table__row");
  check((await cdRows.count()) === 1, "客户抽屉清单同样是一行一客户");
  await cdRows.first().locator(".el-table__expand-icon").click();
  await page.waitForTimeout(800);
  const cdExpand = (await drawer().locator(".gp-expand").innerText()).replace(/\s+/g, "");
  check(/XD202608280001/.test(cdExpand) && /XD202608280005/.test(cdExpand), "展开后列出该客户两张待并入订单");
  check(/当日送货单/.test(cdText), "抽屉含当日送货单区");
  check(/生成\/补单（预计2张）/.test(cdText), "抽屉底部按钮按客户维度给出预计张数（未点击，只读）");
  await closeDrawer();
} finally {
  await browser.close();
}

console.log("\n==== E2E RESULT（送货单客户维度预览）====");
if (problems.length === 0) {
  console.log("PASS：三步式预览 + 客户维度视图均正常，且全程未写入业务数据");
} else {
  console.log(`FAIL：${problems.length} 个问题`);
  for (const p of [...new Set(problems)]) console.log("  -", p);
  process.exit(1);
}
