/** 抓取 /jmreport/show 的真实响应体：判定服务端返回的是数据还是错误。 */
import { chromium } from "playwright-core";
import { writeFileSync } from "node:fs";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const REPORT_ID = "7196889299099535232";

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1500, height: 1000 } });
page.on('pageerror', e => console.log('PAGE ERROR:', e.stack));
await page.goto(BASE + "/login", { waitUntil: "networkidle" });
await page.fill('input[placeholder*="账号"]', "admin");
await page.fill('input[placeholder*="密码"]', "admin123");
await page.click(".login-button, .el-button--primary");
await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
const cookies = await page.context().cookies(BASE);
const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
const ticket = (await page.request.post(API + "/print/ticket", {
  headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
  data: {},
}).then((r) => r.json())).ticket;

let showBody = null;
page.on("response", async (r) => {
  if (r.url().includes("/jmreport/show")) {
    try { showBody = await r.json(); } catch (e) { showBody = { parseError: e.message }; }
  }
});

await page.goto(BASE + "/jmreport/view/" + REPORT_ID + "?token=" + ticket + "&ticket=" + ticket, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(8000);
if (showBody) {
  const s = JSON.stringify(showBody);
  console.log("show 响应长度:", s.length);
  console.log("code:", showBody.code, "message:", showBody.message || showBody.msg || "");
  writeFileSync('/tmp/jinman-show-response.json', JSON.stringify(showBody, null, 2));
  const data = showBody.result;
  if (data) {
    console.log("data 顶层键:", Object.keys(data).join(","));
    for (const key of Object.keys(data)) {
      const v = data[key];
      if (Array.isArray(v)) console.log(`data.${key}: 数组[${v.length}]`, JSON.stringify(v[0] || {}).slice(0, 220));
      else if (v && typeof v === "object") console.log(`data.${key}: 对象键=`, Object.keys(v).slice(0, 12).join(","), JSON.stringify(v).slice(0, 260));
      else console.log(`data.${key}:`, String(v).slice(0, 120));
    }
  } else {
    console.log("show 响应无 data 字段:", s.slice(0, 400));
  }
} else {
  console.log("未捕获 /jmreport/show 响应");
}
await page.screenshot({ path: "/tmp/jinman-show.png" });
await browser.close();
