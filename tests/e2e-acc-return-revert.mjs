// OA 回归：订单页「退货 → 回退」必须真正取消（验收行应送/实收双向恢复）
//
// 背景（2026-09-14 修复）：
//   A. acceptance_item 更新缺 delivered_quantity 列 → 退货归零只改了实收，应送仍为原值，差异算成 -应送
//   B. syncMissingItems 只做「标退货→归零」单向同步，回退后验收行不恢复（实收仍 0）→ 观感「退货无法取消」
//   C. restoreReturnedRow 未清 change_group/change_remark/change_original_num → 行上残留「退货」备注
//
// 用法：node tests/e2e-acc-return-revert.mjs [orderId]   默认 92（需该订单有草稿验收单）
// 前置：后端 8090 + 前端 1025 已启动；脚本会写库，但结束时必定还原（退货→回退）
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const ORDER_ID = Number(process.argv[2] || 92);
const ROW = ".order-table .vxe-table--body-wrapper tbody tr";
const checks = [];
const errors = [];
const ok = (name, pass, detail = "") => {
  checks.push(!!pass);
  console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await (await browser.newContext()).newPage();
page.on("pageerror", (e) => errors.push("[pageerror] " + e.message));
page.on("console", (m) => { if (m.type() === "error") errors.push("[console.error] " + m.text().slice(0, 200)); });

// ---------- 登录 ----------
await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
let cookie = "";
for (let i = 0; i < 3 && !cookie; i++) {
  await page.locator('input[type="text"]').first().fill("admin");
  await page.locator('input[type="password"]').first().fill("admin123");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  cookie = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
}
ok("登录", !!cookie, cookie ? "" : "未取到 Admin-Token");
if (!cookie) { await browser.close(); process.exit(1); }
const H = { Authorization: "Bearer " + cookie };
const getJson = (url) => page.request.get(API + url, { headers: H }).then((r) => r.json());

// ---------- 定位该订单的草稿验收单 ----------
const located = await getJson(`/acceptance/by-order/${ORDER_ID}`).then((j) => j.data || {});
const accId = located.acceptanceId;
ok("定位订单维度验收单", !!accId, `acceptanceId=${accId} status=${located.acceptanceStatus}`);
if (!accId) { await browser.close(); process.exit(1); }
const readItems = () => getJson(`/acceptance/${accId}/items`).then((j) => j.data || []);

/** 轮询验收行直到满足断言（退货/回退后的同步是异步的） */
const pollItem = async (name, pred, timeout = 20000) => {
  const t0 = Date.now();
  let last = null;
  while (Date.now() - t0 < timeout) {
    last = (await readItems()).find((i) => i.productName === name) || null;
    if (last && pred(last)) return last;
    await page.waitForTimeout(500);
  }
  return last;
};

const openAcc = async () => {
  await page.goto(BASE + `/order/sale-detail/index?mode=acceptance&orderId=${ORDER_ID}`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForSelector(ROW, { timeout: 15000 }).catch(() => {});
};
/** 等指定商品行上出现某个按钮（页面异步合并验收行，需轮询） */
const waitRowBtn = async (name, label, timeout = 20000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    if ((await page.locator(ROW).filter({ hasText: name }).locator(`button:has-text("${label}")`).count()) > 0) return true;
    await page.waitForTimeout(400);
  }
  return false;
};

// ---------- 选一个可退货行（首个应送>0 的验收行） ----------
await openAcc();
const items0 = await readItems();
const target = items0.find((i) => Number(i.deliveredQuantity) > 0);
ok("存在可退货行", !!target, target ? target.productName : "无");
if (!target) { await browser.close(); process.exit(1); }
const name = target.productName;
const originalNum = Number(target.deliveredQuantity);
ok("初始验收行应送=实收（无差异）",
  Number(target.deliveredQuantity) === Number(target.actualQuantity),
  `${name} delivered=${target.deliveredQuantity} actual=${target.actualQuantity}`);
ok("初始行应送>0 且无「退货」标记", await waitRowBtn(name, "退"), name);

// ---------- 退货 ----------
await Promise.all([
  page.waitForResponse((r) => r.url().includes("/delivery-change/") && r.url().endsWith("/return"), { timeout: 25000 })
    .then((r) => ok("退货接口 200", r.status() === 200, "status=" + r.status()))
    .catch(() => ok("退货接口 200", false, "无响应")),
  page.locator(ROW).filter({ hasText: name }).locator('button:has-text("退")').first().click()
    .then(() => page.waitForTimeout(600))
    .then(() => page.locator('.el-message-box button:has-text("确定")').first().click()),
]);

const afterReturn = await pollItem(name, (i) => Number(i.deliveredQuantity) === 0 && Number(i.actualQuantity) === 0);
ok("退货后验收行应送/实收归零（delivered 必须落库）",
  afterReturn && Number(afterReturn.deliveredQuantity) === 0 && Number(afterReturn.actualQuantity) === 0,
  afterReturn ? `delivered=${afterReturn.deliveredQuantity} actual=${afterReturn.actualQuantity} diff=${afterReturn.differenceQuantity}` : "-");

// ---------- 回退（取消退货） ----------
await openAcc();
const hasRevert = await waitRowBtn(name, "回退");
ok("退货行出现回退按钮", hasRevert, name);
await Promise.all([
  page.waitForResponse((r) => r.url().includes("/delivery-change/") && r.url().includes("/revoke"), { timeout: 25000 })
    .then((r) => ok("回退接口 200", r.status() === 200, "status=" + r.status()))
    .catch(() => ok("回退接口 200", false, "无响应")),
  page.locator(ROW).filter({ hasText: name }).locator('button:has-text("回退")').first().click()
    .then(() => page.waitForTimeout(600))
    .then(() => page.locator('.el-message-box button:has-text("确定")').first().click()),
]);

const afterRevert = await pollItem(
  name,
  (i) => Number(i.deliveredQuantity) === originalNum && Number(i.actualQuantity) === originalNum && Number(i.differenceQuantity) === 0
);
ok("回退后验收行应送/实收恢复（退货真正取消）",
  afterRevert
    && Number(afterRevert.deliveredQuantity) === originalNum
    && Number(afterRevert.actualQuantity) === originalNum
    && Number(afterRevert.differenceQuantity) === 0,
  afterRevert ? `delivered=${afterRevert.deliveredQuantity} actual=${afterRevert.actualQuantity} diff=${afterRevert.differenceQuantity}` : "-");

// 静置后再校验：防止挂起的实收自动保存带着旧值晚到、把同步结果覆盖回去（曾导致「回退看起来没生效」）
await page.waitForTimeout(5000);
const afterIdle = await pollItem(name, () => true, 5000);
ok("静置 5s 后仍保持回退结果（无旧值自动保存回写）",
  afterIdle
    && Number(afterIdle.deliveredQuantity) === originalNum
    && Number(afterIdle.actualQuantity) === originalNum
    && Number(afterIdle.differenceQuantity) === 0,
  afterIdle ? `delivered=${afterIdle.deliveredQuantity} actual=${afterIdle.actualQuantity} diff=${afterIdle.differenceQuantity}` : "-");

// ---------- 变更痕迹清理 ----------
await openAcc();
const residue = await page.evaluate((n) =>
  [...document.querySelectorAll(".order-table .vxe-table--body-wrapper tbody tr")]
    .filter((tr) => tr.innerText.includes(n))
    .some((tr) => tr.innerText.includes("退货")), name);
ok("回退后行上无「退货」标记残留", !residue);
ok("无前端报错", errors.length === 0, errors.slice(0, 2).join(" | "));

await browser.close();
console.log(`\n${checks.filter(Boolean).length}/${checks.length} 通过`);
process.exit(checks.every(Boolean) ? 0 : 1);
