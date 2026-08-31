/**
 * 保存订单链路 E2E（复现并守护「手工定价落库」真实保存路径）
 *
 * 背景：S1-1.3 给 t_sale_order_detail 加了 price_source/ref_price/price_reason 三列，
 *       若目标库未执行 sql/s1_manual_price_audit.sql，保存订单会报
 *       Unknown column 'price_source' in 'field list'（RuoYi 以 HTTP 200 + body.code=500 返回，
 *       故本用例显式检查响应 body）。同时真实点「保存」可暴露同类 schema 漂移。
 *
 * 步骤：登录 → 选到有常用商品池的客户/配送点 → 插入商品 → 填数量 → 改价（手工定价）
 *      → 点保存 → 断言「新增成功」→ 接口校验 price_source=manual 落库 → 接口删除清理
 *
 * 运行：node tests/e2e-order-save-manual-price.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];
const MANUAL_PRICE = "88.88";
let createdOrderId = null; // 从 /order/sale/create 响应体捕获

/** 取 detail.vue 组件实例的表达式（在页面上下文执行） */
const VM_FN = `(el => { let c = el && el.__vueParentComponent; while (c && !(c.ctx && c.ctx.orderDetailList)) c = c.parent; return c ? c.ctx : null; })`;

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });
  // 保存接口：RuoYi 把后端异常包成 HTTP 200 + body.code=500，必须看 body
  page.on("response", async (r) => {
    const u = r.url();
    if (!/\/order\/sale\/(create|update)$/.test(u)) return;
    try {
      const b = await r.json();
      if (b && b.code && b.code !== 200) {
        problems.push(`[保存接口] code=${b.code} ${String(b.msg || "").slice(0, 300)}`);
      }
      if (b && b.code === 200 && b.data && b.data.id) createdOrderId = b.data.id;
    } catch (e) {
      /* 非 JSON 忽略 */
    }
  });

  // ---------- 登录 ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });

  // ---------- 进入录单页（先清本地草稿，避免恢复旧数据干扰） ----------
  await page.evaluate(() => {
    Object.keys(localStorage)
      .filter((k) => k.startsWith("saleDraft:"))
      .forEach((k) => localStorage.removeItem(k));
  });
  await page.goto(BASE + "/order/sale-detail/index/", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 20000 });
  await page.waitForTimeout(1500);

  // ---------- 选客户 + 配送点：遍历一级节点直到出现常用商品 ----------
  await page.click(".right-tabs .el-tabs__item:has-text('常用')").catch(() => {});
  await page.waitForTimeout(300);

  let productRowIdx = -1;
  const level1Count = await (async () => {
    await page.locator(".order-card .el-cascader").first().click();
    await page.waitForTimeout(500);
    const n = await page.locator(".el-cascader-menu").first().locator(".el-cascader-node").count();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(300);
    return n;
  })();
  console.log("一级客户节点数:", level1Count);

  for (let i = 0; i < Math.max(level1Count, 1) && productRowIdx < 0; i++) {
    await page.locator(".order-card .el-cascader").first().click();
    await page.waitForTimeout(500);
    const node = page.locator(".el-cascader-menu").first().locator(".el-cascader-node").nth(i);
    if ((await node.count()) === 0) {
      await page.keyboard.press("Escape");
      break;
    }
    await node.hover();
    await page.waitForTimeout(500);
    const level2 = page.locator(".el-cascader-menu").nth(1).locator(".el-cascader-node");
    if ((await level2.count()) === 0) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(300);
      continue;
    }
    await level2.first().click();
    await page.waitForTimeout(1200); // 等商品池/取价
    await page.keyboard.press("Escape");
    await page.waitForTimeout(300);

    // 插入商品：常用优先，搜索兜底
    let inserted = false;
    await page.click(".right-tabs .el-tabs__item:has-text('常用')").catch(() => {});
    await page.waitForTimeout(500);
    if ((await page.locator(".frequent-item").count()) > 0) {
      await page.locator(".frequent-item").first().click();
      inserted = true;
    } else {
      await page.keyboard.press("F3");
      await page.waitForTimeout(400);
      await page.fill(".search-bar input", "").catch(() => {});
      await page.click(".search-bar .el-button").catch(() => {});
      await page.waitForTimeout(900);
      if ((await page.locator(".search-list .frequent-item").count()) > 0) {
        await page.locator(".search-list .frequent-item").first().click();
        inserted = true;
      }
    }
    if (!inserted) continue;
    await page.waitForTimeout(800);
    // 定位真正有商品名的明细行（插入可能落在非首行，空行会被保存过滤）
    productRowIdx = await page.evaluate(
      (fn) => {
        const getVm = eval(fn);
        const vm = getVm(document.querySelector(".order-card"));
        if (!vm) return -1;
        return (vm.orderDetailList || []).findIndex((r) => r.productName);
      },
      VM_FN
    );
    console.log(`客户候选#${i + 1} 商品所在行索引:`, productRowIdx);
  }

  if (productRowIdx < 0) {
    problems.push("[保存] 未能插入商品行（所有客户候选均无可用商品池），无法执行保存链路验证");
  } else {
    const colIndexOf = (label) =>
      page.evaluate((lb) => {
        const headers = [...document.querySelectorAll(".order-card .vxe-header--row th")];
        return headers.findIndex((th) => th.innerText.trim() === lb);
      }, label);
    const numIdx = await colIndexOf("数量");
    const priceIdx = await colIndexOf("单价");
    console.log("数量列:", numIdx, "单价列:", priceIdx);
    if (numIdx < 0 || priceIdx < 0) {
      problems.push(`[保存] 未找到数量/单价列（num=${numIdx}, price=${priceIdx}）`);
    } else {
      const row = page.locator(".order-card .vxe-body--row").nth(productRowIdx);
      await row.locator("td").nth(numIdx).dblclick();
      await page.waitForTimeout(400);
      const numInput = row.locator("td").nth(numIdx).locator("input:visible").first();
      await numInput.fill("10");
      await numInput.press("Tab");
      await page.waitForTimeout(400);

      await row.locator("td").nth(priceIdx).dblclick();
      await page.waitForTimeout(400);
      const priceInput = row.locator("td").nth(priceIdx).locator("input:visible").first();
      await priceInput.fill(MANUAL_PRICE);
      await priceInput.press("Tab");
      await page.waitForTimeout(800);
      const manualTag = await page.locator(".order-card .price-manual-tag").count();
      console.log("手工定价「手」标签:", manualTag);
      if (manualTag < 1) problems.push("[保存] 改价后未标记手工定价（「手」标签缺失）");

      // ---------- 点保存 ----------
      await page.locator(".order-card button:has-text('保存')").first().click();
      const okMsg = await page
        .waitForSelector(".el-message--success", { timeout: 15000 })
        .then((el) => el.textContent())
        .catch(() => null);
      console.log("保存结果提示:", okMsg && okMsg.trim());
      if (!okMsg) {
        problems.push("[保存] 保存订单未成功（未出现成功提示；若见「金额为 0」提示说明明细行未真正填好）");
      } else if (!okMsg.includes("新增成功")) {
        problems.push(`[保存] 保存提示异常：${okMsg.trim()}`);
      }
      await page.waitForTimeout(1500);
    }
  }

  await browser.close();

  // ---------- 接口校验落库 + 清理（无论前面是否失败都尝试清理） ----------
  if (createdOrderId) {
    const ctx = await chromium.launch({ channel: "chrome", headless: true });
    const p2 = await ctx.newPage();
    await p2.goto(BASE + "/login", { waitUntil: "networkidle" });
    await p2.fill('input[placeholder*="账号"]', "admin");
    await p2.fill('input[placeholder*="密码"]', "admin123");
    await p2.click(".login-button, .el-button--primary");
    await p2.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
    const c2 = await p2.context().cookies(BASE);
    const H2 = { Authorization: "Bearer " + (c2.find((x) => x.name === "Admin-Token") || {}).value };

    if (!problems.length) {
      const detailBody = await (
        await p2.request.get(`${API}/order/saleDetail/list?orderId=${createdOrderId}`, { headers: H2 })
      ).json();
      const details = detailBody.data || detailBody.rows || [];
      const manualRow = details.find((d) => Number(d.productPrice) === Number(MANUAL_PRICE));
      console.log(
        "落库明细:",
        manualRow ? `priceSource=${manualRow.priceSource} refPrice=${manualRow.refPrice}` : "<未找到>"
      );
      if (!manualRow) {
        problems.push(`[保存] 未查到单价 ${MANUAL_PRICE} 的明细行（落库校验失败）`);
      } else if (manualRow.priceSource !== "manual") {
        problems.push(`[保存] 手工定价来源未落库（期望 manual，实际 ${manualRow.priceSource}）`);
      }
    }

    const delBody = await (
      await p2.request.delete(`${API}/order/sale/${createdOrderId}`, { headers: H2 })
    ).json();
    console.log(
      "清理测试订单:",
      delBody.code === 200 ? "已删除 id=" + createdOrderId : JSON.stringify(delBody).slice(0, 150)
    );
    await ctx.close();
  }

  if (problems.length) {
    console.error("\n[保存链路] FAIL:");
    for (const p of problems) console.error("  " + p);
    process.exit(1);
  }
  console.log("\n[保存链路] PASS: 手工定价订单保存成功且 price_source=manual 落库");
}

main().catch((e) => {
  console.error("[保存链路] crashed:", e);
  process.exit(1);
});
