/**
 * S2-2.1/S2-2.2 E2E：工作台日结待办链 + 采购成本状态/调整成本/供应商补录/批量入库
 *
 * 前置：dev 环境已启动（前端 1025，后端 8090）
 * 运行：node tests/e2e-s2-workbench-chain.mjs
 *
 * 说明：通过登录 token 调 /dev-api 种子数据（手工采购单→确认），
 *       再走 UI 验证 成本状态提示 / 调整成本抽屉 / 供应商补录 / 批量入库 全流程。
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];

function tomorrow() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 200)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---------- 登录（UI） ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  await page.waitForTimeout(500);

  // API token（cookie Admin-Token）
  const cookies = await page.context().cookies(BASE);
  const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
  if (!token) problems.push("[登录] 未取到 Admin-Token cookie");
  const authHeaders = { Authorization: "Bearer " + token, "Content-Type": "application/json" };

  // ---------- S2-2.1 工作台待办链 ----------
  await page.goto(BASE + "/workbench", { waitUntil: "networkidle" });
  await page.waitForTimeout(1200);

  const chainTitle = await page.locator(".chain-title").count();
  if (chainTitle !== 1) problems.push(`[待办链] 标题未渲染: ${chainTitle}`);

  const nodeNames = await page.locator(".chain-node .chain-node-name").allTextContents();
  const expectNodes = ["已确认订单", "批量采购", "送货打印", "送达登记", "独立验收"];
  for (const t of expectNodes) {
    if (!nodeNames.some((n) => n.includes(t))) problems.push(`[待办链] 缺少节点「${t}」: ${nodeNames.join("/")}`);
  }
  if (nodeNames.length !== 5) problems.push(`[待办链] 节点数量异常: ${nodeNames.length}`);

  const badges = await page.locator(".chain-node-badge").allTextContents();
  if (badges.length !== 5) problems.push(`[待办链] 徽标数量异常: ${badges.join(",")}`);

  // 点击「批量采购」节点 → 跳转 /purchase
  await page.locator(".chain-node", { hasText: "批量采购" }).first().click();
  await page.waitForURL((u) => String(u).includes("/purchase"), { timeout: 10000 });

  // ---------- 种子数据：建采购单并确认（API） ----------
  const createResp = await page.request.post(API + "/purchase", {
    headers: authHeaders,
    data: {
      orderDate: tomorrow(),
      supplierName: "E2E测试供应商S2",
      purchaser: "e2e",
      remark: "E2E-S2 种子数据",
      items: [
        { skuId: null, productName: "E2E测试白菜S2", productSpec: "", productUnit: "斤", quantity: 5, unitPrice: 1.2, subtotal: 6 },
      ],
    },
  });
  const createBody = await createResp.json();
  if (createBody.code !== 200) problems.push(`[种子] 建采购单失败: ${JSON.stringify(createBody).slice(0, 200)}`);

  // 取最新采购单列表中的该单（code 前缀 PC + 备注 E2E-S2）
  const listResp = await page.request.get(API + "/purchase/list", { headers: authHeaders });
  const listBody = await listResp.json();
  const rows = listBody.data || listBody.rows || [];
  const seed = rows.find((r) => r.remark === "E2E-S2 种子数据");
  if (!seed) problems.push("[种子] 未找到 E2E-S2 采购单");
  if (seed) {
    const confirmResp = await page.request.put(API + `/purchase/${seed.id}/confirm`, { headers: authHeaders });
    const confirmBody = await confirmResp.json();
    if (confirmBody.code !== 200) problems.push(`[种子] 确认失败: ${JSON.stringify(confirmBody).slice(0, 200)}`);
  }

  // ---------- S2-2.2 采购页：成本状态提示 ----------
  await page.goto(BASE + "/purchase", { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  const costColHeader = await page.locator("th:has-text('成本状态')").count();
  if (costColHeader !== 1) problems.push(`[采购] 成本状态列表头未渲染: ${costColHeader}`);

  const batchBtn = await page.locator("button:has-text('批量入库')").count();
  if (batchBtn !== 1) problems.push(`[采购] 批量入库按钮未渲染: ${batchBtn}`);

  await page.waitForSelector(".el-tag:has-text('待确认成本')", { timeout: 8000 }).catch(() => {
    problems.push("[采购] 已确认采购单未出现「待确认成本」标签");
  });

  // ---------- 调整成本抽屉 ----------
  await page.locator("button:has-text('调整成本')").first().click();
  await page.waitForSelector(".el-drawer .el-table__row", { timeout: 8000 }).catch(() => {
    problems.push("[采购] 调整成本抽屉未打开");
  });
  if (problems.filter((p) => p.includes("抽屉未打开")).length === 0) {
    const drawerTitle = await page.locator(".el-drawer__header").first().textContent();
    if (!drawerTitle.includes("调整采购成本")) problems.push(`[采购] 抽屉标题异常: ${drawerTitle}`);

    // 修改第一行数量 5 → 6（数量为该行第一个 input）
    const qtyInput = page.locator(".el-drawer .el-table__row").first().locator("input").first();
    await qtyInput.fill("6");
    await page.waitForTimeout(300);
    await page.locator(".el-drawer button:has-text('保存调整')").click();
    await page.waitForSelector(".el-message:has-text('调整成功')", { timeout: 8000 }).catch(() => {
      problems.push("[采购] 调整成本保存未提示成功");
    });
    await page.waitForTimeout(1200);
    // 审计日志接口应有记录
    if (seed) {
      const logResp = await page.request.get(API + `/purchase/${seed.id}/modify-logs`, { headers: authHeaders });
      const logBody = await logResp.json();
      if (!(logBody.data && logBody.data.length >= 1)) problems.push("[采购] 调整审计日志为空");
    }
  }

  // ---------- 供应商补录 ----------
  await page.locator("button:has-text('供应商补录')").first().click();
  await page.waitForSelector(".el-dialog:has-text('供应商补录')", { timeout: 5000 }).catch(() => {
    problems.push("[采购] 供应商补录对话框未打开");
  });
  const dlgCount = await page.locator(".el-dialog:has-text('供应商补录')").count();
  if (dlgCount >= 1) {
    const dlg = page.locator(".el-dialog:has-text('供应商补录')").last();
    await dlg.locator("input[placeholder*='供应商名称']").fill("E2E补录供应商");
    await dlg.locator("input[placeholder*='采购员']").fill("王五");
    await dlg.locator("button:has-text('保存')").click();
    await page.waitForSelector(".el-message:has-text('补录成功')", { timeout: 8000 }).catch(() => {
      problems.push("[采购] 供应商补录未提示成功");
    });
    await page.waitForTimeout(1000);
    // 校验列表数据已更新
    const listAfter = await (await page.request.get(API + "/purchase/list", { headers: authHeaders })).json();
    const seedAfter = (listAfter.data || listAfter.rows || []).find((r) => seed && r.id === seed.id);
    if (seedAfter && seedAfter.supplierName !== "E2E补录供应商") {
      problems.push(`[采购] 补录后供应商未更新: ${seedAfter.supplierName}`);
    }
  }

  // ---------- 批量入库（确认成本） ----------
  const firstRowCheckbox = page.locator(".el-table__body .el-table__row").first().locator(".el-checkbox");
  await firstRowCheckbox.click();
  await page.waitForTimeout(300);
  await page.locator("button:has-text('批量入库')").click();
  await page.waitForSelector(".el-message-box", { timeout: 5000 });
  await page.locator(".el-message-box button:has-text('确定')").click();
  await page.waitForSelector(".el-message:has-text('批量入库成功')", { timeout: 8000 }).catch(() => {
    problems.push("[采购] 批量入库未提示成功");
  });
  await page.waitForTimeout(1500);
  const confirmedTags = await page.locator(".el-tag:has-text('已确认成本')").count();
  if (confirmedTags < 1) problems.push("[采购] 批量入库后未出现「已确认成本」标签");

  // ---------- S2-2.1 待办链跳转：送达登记（送货单页 status=1 预置筛选） ----------
  await page.goto(BASE + "/order/delivery?status=1", { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  const statusFilterText = await page.evaluate(() => {
    const sel = document.querySelector(".el-form .el-select");
    return sel ? sel.textContent.trim() : "";
  });
  console.log(`[S2] 送货单状态筛选显示: ${statusFilterText}`);
  if (!statusFilterText.includes("已打印")) problems.push(`[送货单] status=1 预置筛选未生效: ${statusFilterText}`);

  // 回到工作台点击「独立验收」节点
  await page.goto(BASE + "/workbench", { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);
  await page.locator(".chain-node", { hasText: "独立验收" }).first().click();
  await page.waitForURL((u) => String(u).includes("/order/acceptance"), { timeout: 10000 });

  await browser.close();

  if (problems.length) {
    console.error("\n[S2] FAIL:");
    for (const p of problems) console.error("  " + p);
    process.exit(1);
  }
  console.log("\n[S2] PASS: 待办链与采购成本全流程（调整/补录/批量入库）全部通过");
}

main().catch((e) => {
  console.error("[S2] crashed:", e);
  process.exit(1);
});
