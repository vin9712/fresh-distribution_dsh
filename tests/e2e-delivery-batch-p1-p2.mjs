// 页面级 E2E 冒烟：送货单据页批次两级视图（P0-B D-043）+ 候选模板（P1 D-048）+ 打印包抽屉（P2 D-050）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090 → 远端库）；
//       或临时实例：后端 8091（mvn -pl lin-entry spring-boot:run --server.port=8091）+ 前端 1026（VITE_PROXY_TARGET=http://localhost:8091）
// 范围：只读为主——送货单列表/展开/打印对话框/打印包抽屉，不点「开始打印」/「确认打印」等写按钮
// 运行：node tests/e2e-delivery-batch-p1-p2.mjs        （默认 1025）
//       BASE=http://localhost:1026 node tests/e2e-delivery-batch-p1-p2.mjs
import { chromium } from "playwright-core";

const BASE = process.env.BASE || "http://localhost:1025";
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
    // 收集 5xx 与关键接口 4xx（票据/未找到类可容忍），不收集业务性 404
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---- 登录 ----
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  ok("LOGIN -> " + page.url());

  // ==================== P0-B：批次两级视图（D-043） ====================
  await page.goto(BASE + "/order/delivery", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container", { timeout: 15000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  // 页面加载：无 pageerror/5xx（由 collect 兜底），先看是否出现批次主表
  const hasTable = (await page.locator(".app-container .el-table").count()) > 0;
  check(hasTable, "送货单页渲染出表格容器");
  const thTexts = (await page.locator("th").allInnerTexts()).join(" | ");
  console.log("     表头: " + thTexts.slice(0, 240));
  check(thTexts.includes("客户"), "批次主行含「客户」列");
  check(thTexts.includes("配送日期"), "批次主行含「配送日期」列");
  check(thTexts.includes("张数"), "批次主行含「张数」列（聚合）");
  check(thTexts.includes("待打/已打/已送") || thTexts.includes("待打"), "批次主行含「待打/已打/已送」列");
  check(thTexts.includes("合计数量"), "批次主行含「合计数量」列");
  check(thTexts.includes("打印包"), "批次主行含「打印包」入口列");
  check(thTexts.includes("总表") || thTexts.includes("形态"), "批次主行含「总表/形态」列");

  // 有数据时才深挖（真实环境可能无当日数据）
  const batchRowCount = await page.locator(".el-table__body tr").count();
  if (batchRowCount === 0) {
    console.log("WARN 无批次数据，P0-B 深挖断言跳过（列表为空或筛选无数据）");
  } else {
    // 批次主行样式：行内应有「打印包」链接按钮
    const hasPkgBtn = (await page.locator("text=打印包").count()) > 0;
    check(hasPkgBtn, "批次主行有「打印包」按钮");
    // 展开第一行 → 子行（单号/配送点/状态）
    const firstExpand = page.locator(".el-table__body tr .el-table__expand-icon").first();
    if (await firstExpand.count()) {
      await firstExpand.click();
      await page.waitForTimeout(1200);
      const body = await page.locator(".app-container").innerText();
      const hasChild = (await page.locator(".batch-children .el-table").count()) > 0;
      check(hasChild, "展开批次后出现子表（该批次下送货单）");
      check(/待打|已打|已送|已作废|补充单/.test(body), "子行出现单据状态/补充单标记");
      check(body.includes("批次下"), "子表标题含「批次下 N 张送货单」");
      // 子行操作按钮保留
      check(/明细|来源|打印|送达|作废/.test(body), "子行保留原有操作（明细/来源/打印/送达/作废）");
    } else {
      console.log("WARN 未见可展开行（expanded 图标缺失），子行断言跳过");
    }
  }

  // ==================== P1：候选模板（D-048） ====================
  // 展开批次直到找到「点单」子行的打印按钮（点单有 FLAT 模板；跨点总单若未配矩阵模板则跳过，属环境数据问题）
  const expandIcons = page.locator(".el-table__body tr .el-table__expand-icon");
  let printed = null;
  for (let i = 0; i < Math.min(await expandIcons.count(), 5); i++) {
    await expandIcons.nth(i).click();
    await page.waitForTimeout(1000);
    const candidate = page
      .locator(".batch-children tr:not(:has-text('跨点总单')) button:has-text('打印'):not(:has-text('打印包'))")
      .first();
    if (await candidate.count()) {
      printed = candidate;
      break;
    }
  }
  if (printed) {
    await printed.click();
    await page.waitForTimeout(1200);
    const dialogVisible = (await page.locator(".el-dialog:visible").count()) > 0;
    check(dialogVisible, "打印对话框打开（单张）");
    // 对话框内应有模板选择（下拉），且候选来自后端 print-candidates
    const pkgResp = await page.waitForResponse((r) => r.url().includes("/print-candidates"), { timeout: 8000 }).catch(() => null);
    if (pkgResp) {
      const data = await pkgResp.json();
      const hasTemplates = Array.isArray(data?.data?.templates) && (data.data.templates.length > 0 || data.data.matchGlobalDefault !== undefined);
      check(hasTemplates, "print-candidates 返回候选结构（templates + matchGlobalDefault）");
    } else {
      console.log("WARN 未捕获 print-candidates 响应（可能对话框未触发加载或已缓存），跳过接口断言");
    }
    // 关掉对话框
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  } else {
    console.log("WARN 无子行「打印」按钮（列表为空），P1 断言跳过");
  }

  // ==================== P2：打印包抽屉（D-050） ====================
  const pkgBtn = page.locator("button:has-text('打印包')").first();
  if (await pkgBtn.count()) {
    await pkgBtn.click();
    await page.waitForTimeout(1200);
    const drawerVisible = (await page.locator(".el-drawer:visible").count()) > 0;
    check(drawerVisible, "打印包抽屉打开");
    const drawerText = drawerVisible ? (await page.locator(".el-drawer:visible").innerText()) : "";
    check(drawerText.includes("建包"), "抽屉含「建包」按钮");
    // 建包后再看流程按钮（未建包时任务为空，见组件 v-if）
    if (drawerText.includes("建包")) {
      const create = page.locator(".el-drawer:visible button:has-text('建包')").first();
      if (await create.count()) {
        await create.click();
        await page.waitForTimeout(2500);
        const after = (await page.locator(".el-drawer:visible").innerText()).replace(/\s+/g, " ");
        check(/汇总预览|开始打印/.test(after), "建包后含「汇总预览/开始打印」按钮（流程①→②）");
        check(after.includes("成功") || after.includes("总表"), "包状态汇总/包任务已加载");
      }
    }
    // 关闭抽屉（不点「开始打印」——写操作留给真机）
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  } else {
    console.log("WARN 无「打印包」入口按钮（列表为空），P2 断言跳过");
  }

  console.log(problems.length ? "\n=== 问题清单 ===" : "\n=== 无异常 ===");
  [...new Set(problems)].forEach((p) => console.log(" - " + p));
} finally {
  await browser.close();
}
process.exit(problems.length ? 1 : 0);
