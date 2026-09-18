// 临时探针（验证后删除）：销售订单页客户视角前端交互，网络层 mock 后端响应
// 运行：node tests/_probe-sale-customer-view.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];

const customerRows = [
  {
    customerId: 10,
    customerName: "客户A-单点单张",
    deliveryDate: "2026-09-19",
    orderCount: 1,
    pointCount: 1,
    totalAmount: 26.0,
    draftCount: 1,
    confirmedCount: 0,
    deliveredCount: 0,
    acceptedCount: 0,
    settledCount: 0,
  },
  {
    customerId: 11,
    customerName: "客户B-多点多张",
    deliveryDate: "2026-09-19",
    orderCount: 3,
    pointCount: 3,
    totalAmount: 300.0,
    draftCount: 0,
    confirmedCount: 2,
    deliveredCount: 1,
    acceptedCount: 0,
    settledCount: 0,
  },
];

const childOrders = {
  10: [
    {
      id: 101,
      customerId: 10,
      code: "XD-A-1",
      customerDeptName: "东门店",
      shiftCode: "DAY",
      amount: 26.0,
      status: 0,
      remark: "",
      allocated: false,
    },
  ],
  11: [
    {
      id: 201,
      customerId: 11,
      code: "XD-B-1",
      customerDeptName: "西门店",
      shiftCode: null,
      amount: 100.0,
      status: 1,
      remark: "少送一箱",
      allocated: false,
    },
    {
      id: 202,
      customerId: 11,
      code: "XD-B-2",
      customerDeptName: "北门店",
      shiftCode: null,
      amount: 100.0,
      status: 1,
      remark: "",
      allocated: false,
    },
    {
      id: 203,
      customerId: 11,
      code: "XD-B-3",
      customerDeptName: "南门店",
      shiftCode: null,
      amount: 100.0,
      status: 2,
      remark: "",
      allocated: false,
    },
  ],
};

const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${m.text().slice(0, 300)}`);
  });
  await page.addInitScript(() => localStorage.setItem("sale:listView", "customer"));

  // ---- mock 两个接口 ----
  await page.route("**/dev-api/order/sale/customer-page**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ code: 200, msg: "ok", total: 2, rows: customerRows }),
    })
  );
  await page.route("**/dev-api/order/sale/list**", (route) => {
    const url = new URL(route.request().url());
    const cid = Number(url.searchParams.get("customerId"));
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ code: 200, msg: "ok", data: childOrders[cid] || [] }),
    });
  });

  // ---- 登录 ----
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });

  await page.goto(BASE + "/order/sale", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container", { timeout: 15000 });
  await page.waitForTimeout(1500);

  const headers = () =>
    page.locator(".app-container .el-table__header th").allInnerTexts().then((t) => t.map((s) => s.trim()).filter(Boolean));

  console.log("客户视角表头:", (await headers()).join(" | "));

  // 1) 客户A（单张）应自动展开，子表出现订单号
  const autoExpanded = await page.locator("text=XD-A-1").count();
  console.log("单张客户自动展开:", autoExpanded > 0 ? "OK" : "FAIL");
  if (!autoExpanded) problems.push("[auto-expand] 单张客户未自动展开");

  // 2) 进度圆点 + 主行层级样式
  const progress = await page.locator(".progress-dot").count();
  console.log("进度圆点数:", progress, progress > 0 ? "OK" : "FAIL");
  const custNameWeight = await page
    .locator(".cust-name")
    .first()
    .evaluate((el) => getComputedStyle(el).fontWeight);
  console.log("客户名字重:", custNameWeight);

  // 2.1) 班次列只在有班次数据的客户子表出现
  const childHeaders = async (idx) =>
    (
      await page.locator(".customer-children").nth(idx).locator("th").allInnerTexts()
    )
      .map((s) => s.trim())
      .filter(Boolean);
  const hA = await childHeaders(0); // 客户A 的子表：订单带 shiftCode=DAY
  console.log("客户A子表表头:", hA.join(" | "));
  console.log("  A 有班次列:", hA.includes("班次") ? "OK" : "FAIL");
  if (!hA.includes("班次")) problems.push("[shift-col] 有班次数据的客户子表未出班次列");
  const shiftTags = await page
    .locator(".customer-children")
    .nth(0)
    .locator("td .el-tag")
    .allInnerTexts();
  console.log("  A 班次文案:", shiftTags.map((s) => s.trim()).join("/"));

  // 2.2) 子区层级样式（底色 + 左侧引导线）
  const boxStyle = await page
    .locator(".customer-children")
    .nth(0)
    .evaluate((el) => {
      const s = getComputedStyle(el);
      return { bg: s.backgroundColor, borderLeft: s.borderLeftWidth + " " + s.borderLeftColor };
    });
  console.log("子区层级样式:", JSON.stringify(boxStyle));

  // 3) 勾选客户A子行 → 批量条出现
  const batchText = () => page.locator(".batch-action-bar").innerText().catch(() => "(无)");
  console.log("初始批量条:", await page.locator(".batch-action-bar").count() ? "可见(异常)" : "隐藏 OK");

  await page.locator(".customer-children .el-table__body tr").first().locator(".el-checkbox").first().click();
  await page.waitForTimeout(300);
  console.log("勾选A后批量条:", (await batchText()).replace(/\s+/g, " ").trim());

  // 4) 展开客户B并勾选其中一单 → 覆盖 2 个客户
  // 注意：展开行 <tr> 内含整张子表文本，必须用 .el-table__row 限定到真正的客户行
  const custRow = (name) =>
    page.locator(".app-container .el-table__body > tbody > tr.el-table__row", { hasText: name }).first();
  const childRow = (code) =>
    page.locator(".customer-children .el-table__body tr", { hasText: code }).first();

  await custRow("客户B-多点多张").locator(".el-table__expand-icon").first().click();
  await page.waitForTimeout(800);
  await childRow("XD-B-2").locator(".el-checkbox").first().click();
  await page.waitForTimeout(300);
  console.log("勾选B后批量条:", (await batchText()).replace(/\s+/g, " ").trim());

  // 4.1) 客户B（无班次）的子表不应出现班次列
  const hB = await childHeaders(1);
  console.log("客户B子表表头:", hB.join(" | "));
  console.log("  B 无班次列:", !hB.includes("班次") ? "OK" : "FAIL");
  if (hB.includes("班次")) problems.push("[shift-col] 无班次数据的客户子表仍出班次列");
  console.log("  A 无备注列(无备注数据):", !hA.includes("备注") ? "OK" : "FAIL");
  console.log("  B 有备注列(有备注数据):", hB.includes("备注") ? "OK" : "FAIL");
  if (hA.includes("备注")) problems.push("[remark-col] 无备注数据的子表仍出备注列");
  if (!hB.includes("备注")) problems.push("[remark-col] 有备注数据的子表未出备注列");

  // 5) 收起再展开 B → 勾选应回填
  await custRow("客户B-多点多张").locator(".el-table__expand-icon").first().click();
  await page.waitForTimeout(300);
  await custRow("客户B-多点多张").locator(".el-table__expand-icon").first().click();
  await page.waitForTimeout(800);
  const checkedAfter = await childRow("XD-B-2").locator(".el-checkbox.is-checked").count();
  console.log("收起再展开后勾选回填:", checkedAfter > 0 ? "OK" : "FAIL");
  if (!checkedAfter) problems.push("[restore-selection] 收起再展开后勾选丢失");
  console.log("回填后批量条:", (await batchText()).replace(/\s+/g, " ").trim());

  // 6) 主行整组动作按钮（只取客户行自身的按钮，不含子表）
  const groupBtns = await page
    .locator(".app-container .el-table__body > tbody > tr.el-table__row .el-button")
    .allInnerTexts();
  console.log("主行按钮:", groupBtns.map((s) => s.trim()).filter(Boolean).join(" / "));

  // 6.1) 截图留档（人工看层级/配色）
  await page.screenshot({ path: "test-results/sale-customer-view.png" });
  console.log("截图: test-results/sale-customer-view.png");

  // 7) 切明细视角
  await page.locator("label", { hasText: "明细视角" }).first().click();
  await page.waitForTimeout(1200);
  console.log("明细视角表头:", (await headers()).join(" | "));
  await page.screenshot({ path: "test-results/sale-detail-view.png" });
  console.log("localStorage:", await page.evaluate(() => localStorage.getItem("sale:listView")));

  // 8) 切回客户视角
  await page.locator("label", { hasText: "客户视角" }).first().click();
  await page.waitForTimeout(1200);
  console.log("切回客户视角表头:", (await headers()).join(" | "));
} finally {
  await browser.close();
  console.log("\n=== problems ===");
  console.log(problems.length ? problems.join("\n") : "none");
}
