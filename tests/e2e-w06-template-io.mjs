/**
 * W0-6 E2E：模板导入导出 + 资源治理
 *   1. 上传 PNG 资源 → 资源管理弹窗显示
 *   2. 创建一个引用该资源 URL 的草稿模板
 *   3. 删除被引用资源应被拒绝（历史引用资源不可物理删除）
 *   4. 导出该模板 → 得到 JSON 包
 *   5. 改包名后导入 → 列表出现重命名的未绑定草稿
 *
 * 前置：dev 环境已启动（前端 1025，后端 8090）
 * 运行：node tests/e2e-w06-template-io.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];

function pngBytes() {
  // 1x1 PNG（透明）
  return Buffer.from(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c63000100000005000100acff299d0000000049454e44ae426082",
    "hex"
  );
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

  // ---------- 登录 ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  await page.waitForTimeout(500);

  const cookies = await page.context().cookies(BASE);
  const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
  if (!token) problems.push("[登录] 未取到 token");
  const H = { Authorization: "Bearer " + token };

  // ---------- 1. 上传 PNG 资源（API） ----------
  const pngBuf = pngBytes();
  const uploadResp = await page.request.post(API + "/print/asset/upload", {
    headers: H,
    multipart: { file: { name: "logo_w06.png", mimeType: "image/png", buffer: pngBuf } },
  });
  const uploadBody = await uploadResp.json();
  if (uploadBody.code !== 200) problems.push(`[资源] 上传失败: ${JSON.stringify(uploadBody).slice(0, 200)}`);
  const assetUrl = uploadBody.data && uploadBody.data.url;
  if (!assetUrl) problems.push("[资源] 上传返回无 url");
  console.log("[W0-6] 资源 URL:", assetUrl);

  // ---------- 2. 创建引用该资源的草稿模板（API） ----------
  const content = JSON.stringify({
    schemaVersion: 1,
    paper: "A4",
    logo: { src: assetUrl },
  });
  const createResp = await page.request.post(API + "/print/template", {
    headers: { ...H, "Content-Type": "application/json" },
    data: {
      code: "TPLW06" + Date.now(),
      name: "W0-6测试模板",
      content,
      type: 0,
      renderEngine: "jimureport",
      bindType: 3,
      copies: 1,
      isDefault: "0",
    },
  });
  const createBody = await createResp.json();
  if (createBody.code !== 200) problems.push(`[模板] 创建失败: ${JSON.stringify(createBody).slice(0, 200)}`);

  // 取该模板 id（列表里找名称）
  const listResp = await page.request.get(API + "/print/template/list", { headers: H });
  const listBody = await listResp.json();
  const rows = listBody.data || listBody.rows || [];
  const seed = rows.find((r) => r.name === "W0-6测试模板");
  if (!seed) problems.push("[模板] 未找到 W0-6 测试模板");
  console.log("[W0-6] 模板 id:", seed && seed.id);

  // ---------- 3. 删除被引用资源应被拒绝（API） ----------
  if (assetUrl) {
    const assetId = uploadBody.data.id;
    const delResp = await page.request.delete(API + `/print/asset/${assetId}`, { headers: H });
    const delBody = await delResp.json();
    if (delBody.code === 200) {
      problems.push("[资源] 被引用资源居然删除成功，未拦截");
    } else {
      console.log("[W0-6] 被引用资源删除已拒绝:", delBody.msg);
    }
  }

  // ---------- 4. 导出模板（API） ----------
  if (seed) {
    const exportResp = await page.request.get(API + `/print/template/${seed.id}/export?includeVersions=false`, { headers: H });
    const exportBody = await exportResp.json();
    if (exportBody.code !== 200) problems.push(`[导出] 失败: ${JSON.stringify(exportBody).slice(0, 200)}`);
    const pkg = exportBody.data;
    if (pkg.format !== "lin-print-template") problems.push(`[导出] format 异常: ${pkg.format}`);
    if (pkg.schemaVersion !== 1) problems.push(`[导出] schemaVersion 异常: ${pkg.schemaVersion}`);
    console.log("[W0-6] 导出包模板数:", pkg.templates && pkg.templates.length);

    // ---------- 5. 改包名后导入 → 未绑定草稿 ----------
    pkg.templates[0].name = "W0-6导入回灌";
    const importResp = await page.request.post(API + `/print/template/import`, {
      headers: { ...H, "Content-Type": "application/json" },
      data: JSON.stringify(pkg),
    });
    const importBody = await importResp.json();
    if (importBody.code !== 200) problems.push(`[导入] 失败: ${JSON.stringify(importBody).slice(0, 200)}`);
    console.log("[W0-6] 导入数量:", importBody.data);
    if (importBody.data !== 1) problems.push(`[导入] 数量异常: ${importBody.data}`);

    // 校验导入后为未绑定草稿
    const listAfter = await (await page.request.get(API + "/print/template/list", { headers: H })).json();
    const imported = (listAfter.data || listAfter.rows || []).find((r) => r.name && r.name.startsWith("导入_W0-6导入回灌_"));
    if (!imported) problems.push("[导入] 未出现重命名的未绑定草稿");
    if (imported) {
      if (imported.bindType !== 3) problems.push(`[导入] bindType 非全局: ${imported.bindType}`);
      if (imported.status !== 0) problems.push(`[导入] status 非草稿: ${imported.status}`);
      if (imported.customerId !== 0) problems.push(`[导入] customerId 非空: ${imported.customerId}`);
    }
  }

  // ---------- UI 冒烟：模板页资源管理弹窗 ----------
  await page.goto(BASE + "/print/template", { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await page.locator("button:has-text('资源管理')").first().click();
  await page.waitForSelector(".el-dialog:has-text('打印资源管理')", { timeout: 5000 }).catch(() => {
    problems.push("[UI] 资源管理弹窗未打开");
  });
  // 等待资源列表行渲染（异步加载）
  await page.waitForTimeout(1200);
  const assetDlgCount = await page.locator(".el-dialog:has-text('打印资源管理')").count();
  if (assetDlgCount >= 1) {
    const dlgVisible = await page.locator(".el-dialog:has-text('打印资源管理')").last().isVisible();
    let rowsInDlg = 0;
    if (dlgVisible) {
      rowsInDlg = await page.locator(".el-dialog:has-text('打印资源管理')").last().locator(".el-table__row").count();
    }
    if (rowsInDlg < 1) problems.push(`[UI] 资源列表为空: ${rowsInDlg}（列表 API 应有数据）`);
  }
  await page.keyboard.press("Escape");
  await page.waitForTimeout(400);

  // 导入按钮存在
  const importBtn = await page.locator("button:has-text('导入模板')").count();
  if (importBtn < 1) problems.push("[UI] 导入模板按钮未渲染");
  // 导出按钮存在
  const exportBtn = await page.locator("button:has-text('导出')").count();
  if (exportBtn < 1) problems.push("[UI] 导出按钮未渲染");

  await browser.close();

  if (problems.length) {
    console.error("\n[W0-6] FAIL:");
    for (const p of problems) console.error("  " + p);
    process.exit(1);
  }
  console.log("\n[W0-6] PASS: 模板导入导出与资源治理全部通过");
}

main().catch((e) => {
  console.error("[W0-6] crashed:", e);
  process.exit(1);
});
