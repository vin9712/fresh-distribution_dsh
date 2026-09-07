/**
 * 生鲜配送 · 打印预览标注增强（JimuReport view 页 js 增强）
 *
 * 由模板 js_str 引导加载（见 sql/s24_print_view_enhance.sql），仅在
 * /jmreport/view/{id}、/jmreport/shareView/{id} 生效。提供 Word 式打印标注：
 *   1. 自定义文本框（标注）：任意位置插入、拖动、缩放、改字号/加粗/颜色/底色/边框；
 *   2. 改表格值：白底色块覆盖在单元格上输入新值（打印后即所见即所得）；
 *   3. 打印（含标注）：整表分页截图 + 标注合成，走浏览器打印，标注全部呈现；
 *      顶部自带「默认打印」按钮不含标注，需标注时请用本工具栏的打印按钮。
 * 标注按「模板ID」存 localStorage（本机浏览器），重开自动恢复，可一键清空。
 *
 * 无第三方依赖；打印用 canvas 分片截图 + 标注合成（见 printAnno）。
 * 代码版本：v1（js_str 引导串里的 ?v= 与此同步递增以破缓存）
 */
(function () {
  "use strict";
  if (window.__linPrintAnno) { return; }
  window.__linPrintAnno = true;

  // 仅报表查看页生效（设计器列表/数据源等页面不注入）
  if (!/\/jmreport\/(view|shareView)\//.test(window.location.pathname)) { return; }

  // ---------- 常量 ----------
  var TPL_ID = (window.location.pathname.match(/\/jmreport\/(?:view|shareView)\/([A-Za-z0-9_-]+)/) || [])[1] || "unknown";
  var LS_KEY = "jmAnno:" + TPL_ID;
  var DEFAULTS = {
    note:  { w: 180, h: 44, fs: 14, family: "宋体", bold: false, italic: false, color: "#333333", bg: "transparent", border: true,  align: "left" },
    value: { w: 90,  h: 26, fs: 14, family: "宋体", bold: false, italic: false, color: "#000000", bg: "#ffffff",     border: true,  align: "center" }
  };
  var MIN_W = 24, MIN_H = 18;

  // ---------- 状态 ----------
  var wrap = null;          // #jm-sheet-wrapper
  var scroller = null;      // 实际滚动容器（可能就是 wrap，也可能是内层）
  var gridCanvas = null;    // 主表格画布（取最大）
  var overlay = null;       // 标注层（绝对定位，随滚动同步位移）
  var boxes = [];           // 标注数据数组
  var boxEls = [];          // 与 boxes 平行的 DOM 引用
  var activeEl = null;      // 当前选中 DOM
  var saveTimer = null;

  // ---------- 启动：等待画布渲染 ----------
  var tries = 0;
  var bootTimer = setInterval(function () {
    tries += 1;
    var w = document.getElementById("jm-sheet-wrapper");
    if (w) {
      var canvases = w.querySelectorAll("canvas");
      var best = null;
      for (var i = 0; i < canvases.length; i++) {
        var c = canvases[i];
        if (!best || c.clientWidth * c.clientHeight > best.clientWidth * best.clientHeight) { best = c; }
      }
      if (best && best.clientWidth > 0) {
        clearInterval(bootTimer);
        setup(w, best);
      }
    }
    if (tries > 300) { clearInterval(bootTimer); }
  }, 200);

  function setup(w, cv) {
    wrap = w; gridCanvas = cv;
    if (getComputedStyle(wrap).position === "static") { wrap.style.position = "relative"; }

    overlay = document.createElement("div");
    overlay.className = "jm-anno-overlay";
    overlay.style.cssText = "position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:9999;";
    wrap.appendChild(overlay);

    scroller = findScroller();
    if (scroller) { syncOverlay(); }

    buildToolbar();
    buildBackButton();
    restore();
    updateOrientLabel();
    applyDesk();

    // 滚动/窗口尺寸变化时同步标注层（canvas 由 jmsheet 重绘，DOM 层需手动平移）
    window.addEventListener("scroll", syncOverlay, true);
    window.addEventListener("resize", syncOverlay);
    window.addEventListener("resize", applyDesk);
  }

  // ---------- 工作台视觉（灰底 + 点阵网格 + A4 纸卡阴影） ----------
  var deskTimer = null;
  function applyDesk() {
    if (!deskOn()) { document.body.classList.remove("jm-anno-desk"); setSheetCardWidth(0); return; }
    document.body.classList.add("jm-anno-desk");
    setSheetCardWidth(0);
    // 让 jmsheet 按新容器宽重绘 canvas（纸卡宽 = 纸张宽）
    clearTimeout(deskTimer);
    deskTimer = setTimeout(function () { window.dispatchEvent(new Event("resize")); }, 80);
  }

  function deskOn() {
    var v = null;
    try { v = localStorage.getItem("jmAnnoDesk:" + TPL_ID); } catch (e) { v = null; }
    return v !== "0";   // 默认开
  }

  /** 同步工具栏纸向按钮文案 */
  function updateOrientLabel() {
    var btn = document.querySelector('.jm-anno-bar button[data-act=orient]');
    if (btn) { btn.textContent = orientOverride() === "landscape" ? "纸向:横" : "纸向:纵"; }
  }

  function setSheetCardWidth(unused) {
    var sheet = document.querySelector(".jm-sheet-sheet");
    if (!sheet) { return; }
    var pc = paperConfig();
    var paperPx = Math.round(pc.w * 96 / 25.4);      // A4 纵向 ≈ 794px
    var padX = 24;
    var avail = Math.max(320, window.innerWidth - 32);
    var w = Math.min(paperPx + padX * 2, avail);
    sheet.style.width = w + "px";
    sheet.style.margin = "0 auto";
    sheet.style.padding = "16px " + padX + "px 28px";
    sheet.style.boxSizing = "border-box";
  }

  /** 从画布向上、在 wrap 内找真实滚动容器 */
  function findScroller() {
    var el = gridCanvas;
    while (el && el !== wrap) {
      if (el.scrollHeight > el.clientHeight + 2 || el.scrollWidth > el.clientWidth + 2) {
        var ov = getComputedStyle(el).overflowY;
        if (ov === "auto" || ov === "scroll" || el.scrollHeight > el.clientHeight + 2) { return el; }
      }
      el = el.parentElement;
    }
    return wrap.scrollHeight > wrap.clientHeight + 2 ? wrap : null;
  }

  /** 画布相对 wrap 内容原点的偏移（含滚动补偿） */
  function canvasOffset() {
    var wr = wrap.getBoundingClientRect();
    var cr = gridCanvas.getBoundingClientRect();
    var sx = scroller ? scroller.scrollLeft : 0;
    var sy = scroller ? scroller.scrollTop : 0;
    return { x: cr.left - wr.left + sx, y: cr.top - wr.top + sy };
  }

  function syncOverlay() {
    if (!overlay) { return; }
    var sx = scroller ? scroller.scrollLeft : 0;
    var sy = scroller ? scroller.scrollTop : 0;
    overlay.style.transform = "translate(" + (-sx) + "px," + (-sy) + "px)";
  }

  // ---------- 返回系统按钮（左上角） ----------
  function buildBackButton() {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "jm-anno-back";
    btn.title = "返回系统（关闭打印预览）";
    btn.innerHTML = "‹ 返回系统";
    btn.addEventListener("click", function () {
      // 常规流程：预览页由系统 window.open 打开，可直接关闭标签页
      window.close();
      // 直开场景（粘贴链接/收藏夹）：关不掉则回系统首页
      setTimeout(function () {
        if (!window.closed) { window.location.href = "/index"; }
      }, 250);
    });
    document.body.appendChild(btn);
  }

  // ---------- 工具栏 ----------
  function buildToolbar() {
    var bar = document.createElement("div");
    bar.className = "jm-anno-bar";
    bar.innerHTML =
      '<div class="jm-anno-tab">标注</div>' +
      '<div class="jm-anno-panel" style="display:none">' +
      '  <button type="button" data-act="note">＋文本框</button>' +
      '  <button type="button" data-act="value">＋改值</button>' +
      '  <span class="jm-anno-sep"></span>' +
      '  <label>字号 <select data-act="fs">' +
      '    <option>12</option><option selected>14</option><option>16</option><option>18</option><option>20</option><option>24</option><option>28</option>' +
      '  </select></label>' +
      '  <label>字体 <select data-act="family">' +
      '    <option>宋体</option><option>黑体</option><option>楷体</option><option>仿宋</option><option>微软雅黑</option><option>Arial</option>' +
      '  </select></label>' +
      '  <button type="button" data-act="bold" title="加粗">B</button>' +
      '  <button type="button" data-act="italic" title="倾斜"><i>I</i></button>' +
      '  <label class="jm-anno-color" title="文字颜色"><input type="color" data-act="color" value="#333333"></label>' +
      '  <label class="jm-anno-color" title="底色（改值=白底遮盖）"><input type="color" data-act="bg" value="#ffffff"></label>' +
      '  <button type="button" data-act="align" title="对齐">⇔</button>' +
      '  <span class="jm-anno-sep"></span>' +
      '  <button type="button" data-act="del" title="删除所选">删除</button>' +
      '  <button type="button" data-act="clear" title="清空本页全部标注">清空</button>' +
      '  <button type="button" data-act="desk" title="工作台背景/纸卡模式开关">工作台</button>' +
      '  <button type="button" data-act="orient" title="切换纸张方向（仅预览页覆盖，不改模板）">纸向:纵</button>' +
      '  <span class="jm-anno-sep"></span>' +
      '  <button type="button" class="jm-anno-preview" data-act="preview" title="Word 式纸张预览（内容居中，可从此打印）">纸张预览</button>' +
      '  <button type="button" class="jm-anno-print" data-act="print">打印(含标注)</button>' +
      '</div>';

    var tab = bar.querySelector(".jm-anno-tab");
    var panel = bar.querySelector(".jm-anno-panel");
    tab.addEventListener("click", function () {
      var open = panel.style.display !== "none";
      panel.style.display = open ? "none" : "flex";
    });

    panel.addEventListener("click", function (e) {
      var btn = e.target.closest("button[data-act]");
      if (!btn) { return; }
      var act = btn.getAttribute("data-act");
      if (act === "note") { addBox("note"); }
      else if (act === "value") { addBox("value"); }
      else if (act === "desk") {
        var on = !deskOn();
        try { localStorage.setItem("jmAnnoDesk:" + TPL_ID, on ? "1" : "0"); } catch (e2) { /* ignore */ }
        applyDesk();
      }
      else if (act === "orient") {
        var cur = orientOverride() || "portrait";
        try { localStorage.setItem("jmAnnoOrient:" + TPL_ID, cur === "portrait" ? "landscape" : "portrait"); } catch (e3) { /* ignore */ }
        updateOrientLabel();
        applyDesk();
        if (previewMask && pvBig) { pvPages = buildPages(pvBig, {}); renderPreviewCards(); }
      }
      else if (act === "bold") { applyToActive(function (b, el) { b.bold = !b.bold; }); }
      else if (act === "italic") { applyToActive(function (b, el) { b.italic = !b.italic; }); }
      else if (act === "align") { applyToActive(function (b) { b.align = b.align === "center" ? "left" : "center"; }); }
      else if (act === "del") { removeActive(); }
      else if (act === "preview") { openPreview(); }
      else if (act === "clear") {
        if (boxes.length && window.confirm("清空本页全部标注？")) { clearAll(); }
      } else if (act === "print") { printAnno(); }
    });
    panel.addEventListener("input", function (e) {
      var t = e.target;
      if (t.matches("select[data-act=fs]")) { applyToActive(function (b) { b.fs = parseInt(t.value, 10) || 14; }); }
      else if (t.matches("select[data-act=family]")) { applyToActive(function (b) { b.family = t.value; }); }
      else if (t.matches("input[data-act=color]")) { applyToActive(function (b) { b.color = t.value; }); }
      else if (t.matches("input[data-act=bg]")) { applyToActive(function (b) { b.bg = t.value; }); }
    });

    document.body.appendChild(bar);
  }

  // ---------- 标注框 ----------
  function addBox(type) {
    var d = DEFAULTS[type] || DEFAULTS.note;
    var off = canvasOffset();
    // 新框落在当前视口中心（画布坐标）
    var vx = scroller ? scroller.scrollLeft : 0;
    var vy = scroller ? scroller.scrollTop : 0;
    var vw = (scroller || wrap).clientWidth, vh = (scroller || wrap).clientHeight;
    var b = {
      type: type, x: Math.max(0, vx + vw / 2 - d.w / 2 - off.x), y: Math.max(0, vy + vh / 2 - d.h / 2 - off.y),
      w: d.w, h: d.h, t: "", fs: d.fs, bold: d.bold, italic: d.italic, color: d.color, bg: d.bg, border: d.border, align: d.align
    };
    boxes.push(b);
    boxEls.push(renderBox(b));
    reindex();
    var el = boxEls[boxEls.length - 1];
    setActive(el);
    var textEl = el.querySelector(".jm-anno-text");
    setTimeout(function () { textEl.focus(); }, 0);
    scheduleSave();
  }

  function renderBox(b) {
    var el = document.createElement("div");
    el.className = "jm-anno-box jm-anno-" + b.type;
    el.innerHTML =
      '<div class="jm-anno-grip" title="拖动"></div>' +
      '<div class="jm-anno-text" contenteditable="true" spellcheck="false"></div>' +
      '<div class="jm-anno-rs" title="缩放"></div>' +
      '<div class="jm-anno-del" title="删除">×</div>';
    var textEl = el.querySelector(".jm-anno-text");
    textEl.setAttribute("data-ph", b.type === "value" ? "输入修正值" : "输入标注");
    textEl.addEventListener("input", function () { b.t = textEl.innerText; scheduleSave(); });
    el.addEventListener("mousedown", function (e) { setActive(el); e.stopPropagation(); });
    el.addEventListener("dblclick", function () { textEl.focus(); });
    textEl.addEventListener("blur", scheduleSave);

    var grip = el.querySelector(".jm-anno-grip");
    grip.addEventListener("mousedown", function (e) { startDrag(e, b, el, "move"); });
    el.querySelector(".jm-anno-rs").addEventListener("mousedown", function (e) { startDrag(e, b, el, "resize"); });
    el.querySelector(".jm-anno-del").addEventListener("mousedown", function (e) { e.stopPropagation(); });
    el.querySelector(".jm-anno-del").addEventListener("click", function (e) {
      e.stopPropagation();
      boxes.splice(boxes.indexOf(b), 1);
      el.remove();
      if (activeEl === el) { activeEl = null; }
      scheduleSave();
    });

    styleBox(b, el);
    overlay.appendChild(el);
    return el;
  }

  function styleBox(b, el) {
    el.style.left = b.x + "px";
    el.style.top = b.y + "px";
    el.style.width = b.w + "px";
    el.style.height = b.h + "px";
    var textEl = el.querySelector(".jm-anno-text");
    textEl.style.fontSize = b.fs + "px";
    textEl.style.fontFamily = (b.family || "宋体") + ", sans-serif";
    textEl.style.fontWeight = b.bold ? "bold" : "normal";
    textEl.style.fontStyle = b.italic ? "italic" : "normal";
    textEl.style.color = b.color;
    textEl.style.background = b.bg;
    textEl.style.textAlign = b.align;
    el.classList.toggle("jm-anno-noborder", !b.border);
    el.setAttribute("data-idx", boxes.indexOf(b));
  }

  function applyToActive(fn) {
    if (!activeEl) { return; }
    var idx = boxEls.indexOf(activeEl);
    var b = boxes[idx];
    if (!b) { return; }
    fn(b, activeEl);
    styleBox(b, activeEl);
    scheduleSave();
  }

  function setActive(el) {
    if (activeEl && activeEl !== el) { activeEl.classList.remove("jm-anno-active"); }
    activeEl = el;
    if (el) { el.classList.add("jm-anno-active"); }
  }

  function clearActive() {
    if (activeEl) { activeEl.classList.remove("jm-anno-active"); activeEl = null; }
  }

  function removeActive() {
    if (!activeEl) { return; }
    var idx = boxEls.indexOf(activeEl);
    if (idx > -1) { boxes.splice(idx, 1); boxEls.splice(idx, 1); }
    activeEl.remove();
    activeEl = null;
    reindex();
    scheduleSave();
  }

  function clearAll() {
    boxes = [];
    boxEls = [];
    overlay.innerHTML = "";
    activeEl = null;
    scheduleSave();
  }

  /** 增删后重排 data-idx（styleBox 依赖） */
  function reindex() {
    for (var i = 0; i < boxEls.length; i++) { boxEls[i].setAttribute("data-idx", String(i)); }
  }

  function startDrag(e, b, el, mode) {
    if (e.button !== 0) { return; }
    e.preventDefault();
    e.stopPropagation();
    var startX = e.clientX, startY = e.clientY, bx = b.x, by = b.y, bw = b.w, bh = b.h;
    function onMove(ev) {
      var dx = ev.clientX - startX, dy = ev.clientY - startY;
      if (mode === "move") {
        b.x = Math.max(0, bx + dx); b.y = Math.max(0, by + dy);
      } else {
        b.w = Math.max(MIN_W, bw + dx); b.h = Math.max(MIN_H, bh + dy);
      }
      styleBox(b, el);
    }
    function onUp() {
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
      scheduleSave();
    }
    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  }

  // 点击空白处取消选中；ESC 关预览
  document.addEventListener("mousedown", function (e) {
    if (!overlay) { return; }
    if (e.target.closest(".jm-anno-box") || e.target.closest(".jm-anno-bar")) { return; }
    clearActive();
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && previewMask) { closePreview(); }
  });

  // ---------- 持久化（localStorage，按模板ID） ----------
  function scheduleSave() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, 300);
  }
  function save() {
    try { localStorage.setItem(LS_KEY, JSON.stringify(boxes)); } catch (e) { /* 隐私模式等忽略 */ }
  }
  function restore() {
    var raw = null;
    try { raw = localStorage.getItem(LS_KEY); } catch (e) { return; }
    if (!raw) { return; }
    var list;
    try { list = JSON.parse(raw); } catch (e) { return; }
    if (!list || typeof list !== "object" || !list.length) { return; }
    boxes = list;
    for (var i = 0; i < boxes.length; i++) { boxEls.push(renderBox(boxes[i])); }
    reindex();
  }

  // ---------- 打印/纸张预览（整表 + 标注合成） ----------
  /**
   * 不引第三方库：主表格是 canvas，滚动分片直接 drawImage 拼成整表位图，
   * 再用 canvas 2D 把标注画上去；然后按纸张分页（内容自适应缩放 + 可居中），
   * 供「纸张预览」弹窗与 iframe 打印共用。
   */
  var lastDpr = 1;

  /** 整表截图拼接（不含标注），完成回调 done(bigCanvas) */
  function composeBig(done) {
    var cssW = gridCanvas.clientWidth, cssH = gridCanvas.clientHeight;
    if (!cssW || !cssH) { window.alert("报表尚未渲染完成，请稍后再试"); return; }
    var fullW = scroller ? scroller.scrollWidth : cssW;
    var fullH = scroller ? scroller.scrollHeight : cssH;
    var dpr = Math.max(1, window.devicePixelRatio || 1);
    lastDpr = dpr;
    var needScroll = fullH > cssH + 2 || fullW > cssW + 2;

    overlay.style.visibility = "hidden";
    var big = document.createElement("canvas");
    big.width = Math.round(fullW * dpr); big.height = Math.round(fullH * dpr);
    var ctx = big.getContext("2d");
    ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, big.width, big.height);

    function finish() {
      if (scroller) { scroller.scrollTop = 0; scroller.scrollLeft = 0; }
      syncOverlay();
      drawBoxes(big, dpr);
      overlay.style.visibility = "";
      done(big);
    }

    if (!needScroll) {
      ctx.drawImage(gridCanvas, 0, 0);
      finish();
      return;
    }
    var oldScrollTop = scroller.scrollTop, oldScrollLeft = scroller.scrollLeft;
    var stepH = Math.max(1, cssH - 10), stepW = Math.max(1, cssW - 10); // 略重叠防裁切
    var jobs = [];
    for (var y = 0; y < fullH; y += stepH) {
      for (var x = 0; x < fullW; x += stepW) { jobs.push({ x: x, y: y }); }
    }
    var idx = 0;
    (function next() {
      if (idx >= jobs.length) {
        if (scroller) { scroller.scrollTop = oldScrollTop; scroller.scrollLeft = oldScrollLeft; }
        finish(); return;
      }
      var job = jobs[idx++];
      scroller.scrollTop = job.y; scroller.scrollLeft = job.x;
      syncOverlay();
      setTimeout(function () {
        try { ctx.drawImage(gridCanvas, Math.round(job.x * dpr), Math.round(job.y * dpr)); }
        catch (e) { /* 单片失败继续 */ }
        next();
      }, 150); // 等待 jmsheet 滚动重绘
    })();
  }

  /** 将标注画到成品图上（CSS px 坐标 × dpr） */
  function drawBoxes(bigCanvas, dpr) {
    if (!boxes.length) { return; }
    var ctx = bigCanvas.getContext("2d");
    ctx.save();
    ctx.scale(dpr, dpr);
    var off = canvasOffset();
    for (var i = 0; i < boxes.length; i++) {
      var b = boxes[i];
      var x = off.x + b.x, y = off.y + b.y;
      ctx.save();
      if (b.bg && b.bg !== "transparent") { ctx.fillStyle = b.bg; ctx.fillRect(x, y, b.w, b.h); }
      if (b.border) {
        ctx.lineWidth = 1;
        ctx.strokeStyle = b.type === "value" ? "#409eff" : "#e6a23c";
        if (b.type !== "value") { ctx.setLineDash([4, 3]); }
        ctx.strokeRect(x + 0.5, y + 0.5, b.w - 1, b.h - 1);
        ctx.setLineDash([]);
      }
      // 文本
      ctx.fillStyle = b.color;
      var fam = b.family || "宋体";
      ctx.font = (b.italic ? "italic " : "") + (b.bold ? "bold " : "") + b.fs + "px \"" + fam + "\", sans-serif";
      ctx.textBaseline = "top";
      var lh = Math.round(b.fs * 1.4);
      var pad = 4;
      var lines = String(b.t || "").split("\n");
      var wrapped = [];
      for (var li = 0; li < lines.length; li++) {
        var line = lines[li];
        if (!line) { wrapped.push(""); continue; }
        var cur = "";
        for (var ci = 0; ci < line.length; ci++) {
          var test = cur + line.charAt(ci);
          if (ctx.measureText(test).width > b.w - pad * 2 && cur) { wrapped.push(cur); cur = line.charAt(ci); }
          else { cur = test; }
        }
        wrapped.push(cur);
      }
      var ty = y + pad;
      for (var wi = 0; wi < wrapped.length; wi++) {
        var tw = ctx.measureText(wrapped[wi]).width;
        var tx = b.align === "center" ? x + (b.w - tw) / 2 : x + pad;
        ctx.fillText(wrapped[wi], tx, ty);
        ty += lh;
      }
      ctx.restore();
    }
    ctx.restore();
  }

  /**
   * 成品图按纸张分页：内容自适应缩放到页宽（不放大）+ 可选居中。
   * 返回 { pages:[dataURL], w, h, pad, scale, contentW, contentH, fullH, offX, offY }
   * 供纸张预览弹窗与 iframe 打印共用，保证所见即所得。
   */
  function buildPages(bigCanvas, opts) {
    opts = opts || {};
    var centerH = opts.centerH !== false;   // 水平居中（默认开）
    var centerV = !!opts.centerV;          // 垂直居中（仅单页时生效）
    var pc = paperConfig();
    var mm2px = 96 / 25.4;
    var pageW = Math.round(pc.w * mm2px), pageH = Math.round(pc.h * mm2px);
    var pad = Math.round((opts.marginMm != null ? opts.marginMm : 8) * mm2px);
    var contentW = pageW - pad * 2, contentH = pageH - pad * 2;

    // 内容真实边界（去多余白边）
    var bb = contentBBox(bigCanvas);
    var bw = Math.max(1, bb.x2 - bb.x), bh = Math.max(1, bb.y2 - bb.y);
    // 内容自适应缩放到页宽，但不放大（打印像素更锐利）
    var fit = Math.min(contentW / bw, 1);
    var fullH = Math.round(bh * fit);
    var pages = Math.max(1, Math.ceil(fullH / contentH));
    var offX = centerH ? Math.max(0, Math.round((contentW - bw * fit) / 2)) : 0;

    var slices = [];
    var tmp = document.createElement("canvas");
    var tctx = tmp.getContext("2d");
    for (var p = 0; p < pages; p++) {
      var sliceH = Math.min(contentH, fullH - p * contentH);
      tmp.width = contentW; tmp.height = sliceH;
      tctx.fillStyle = "#ffffff"; tctx.fillRect(0, 0, contentW, sliceH);
      // 从成品图取该页对应的内容行（含白边裁剪偏移），画到页内（含居中偏移）
      tctx.drawImage(bigCanvas,
        bb.x, Math.round(p * contentH / fit) + bb.y, bw, Math.round(sliceH / fit),
        offX, 0, Math.round(bw * fit), sliceH);
      slices.push(tmp.toDataURL("image/png"));
    }
    return { pages: slices, w: pc.w, h: pc.h, landscape: !!pc.landscape, pad: pad, pageW: pageW, pageH: pageH,
             contentW: contentW, contentH: contentH, fit: fit, fullH: fullH, pageCount: pages,
             offX: offX, centerV: centerV };
  }

  /** 成品图内容真实边界（扫描非白像素，步进取样足够准确且快） */
  function contentBBox(cv) {
    var ctx = cv.getContext("2d");
    var w = cv.width, h = cv.height;
    var step = 4;
    var minX = w, minY = h, maxX = -1, maxY = -1;
    var data = ctx.getImageData(0, 0, w, h).data;
    for (var y = 0; y < h; y += step) {
      for (var x = 0; x < w; x += step) {
        var i = (y * w + x) * 4;
        if (data[i] < 245 || data[i + 1] < 245 || data[i + 2] < 245) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (maxX < 0) { return { x: 0, y: 0, x2: w, y2: h }; }
    return { x: minX, y: minY, x2: Math.min(w, maxX + step), y2: Math.min(h, maxY + step) };
  }

  /** 组装打印 HTML（与预览同一分页结果，保证一致） */
  function buildPrintHtml(pv) {
    var html = '<!doctype html><html><head><meta charset="utf-8"><style>' +
      '@page{size:' + pv.w + 'mm ' + pv.h + 'mm;margin:0}' +
      'html,body{margin:0;padding:0;background:#fff}' +
      '.pg{width:' + pv.w + 'mm;height:' + pv.h + 'mm;box-sizing:border-box;padding:' + Math.round(pv.pad / (96 / 25.4)) + 'mm;page-break-after:always;overflow:hidden;text-align:center}' +
      '.pg:last-child{page-break-after:auto}' +
      '.pg img{max-width:100%;max-height:100%;}' +
      '</style></head><body>';
    for (var p = 0; p < pv.pages.length; p++) {
      html += '<div class="pg"><img src="' + pv.pages[p] + '"></div>';
    }
    html += '</body></html>';
    return html;
  }

  /** 打印（含标注）：合成 → 分页 → 隐藏 iframe 唤起打印 */
  function printAnno() {
    clearActive();
    composeBig(function (big) {
      var pv = buildPages(big, {});
      var frame = document.createElement("iframe");
      frame.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;";
      document.body.appendChild(frame);
      var doc = frame.contentWindow.document;
      doc.open(); doc.write(buildPrintHtml(pv)); doc.close();
      setTimeout(function () {
        try { frame.contentWindow.focus(); frame.contentWindow.print(); }
        catch (e) { window.alert("唤起打印失败，请重试"); }
        setTimeout(function () { frame.remove(); }, 60000);
      }, 300);
    });
  }

  /** Word 式纸张预览：灰底 + 居中纸卡（可多页），可从此弹窗直接打印；支持缩放 */
  var previewMask = null;
  var pvBig = null;        // 合成位图缓存（含标注）
  var pvPages = null;      // buildPages 结果（随纸向变，重分页不重截图）
  var pvZoom = 0;          // 0 = 适应窗口；否则 0.25~3 缩放比

  function openPreview() {
    if (previewMask) { return; }
    clearActive();
    previewMask = document.createElement("div");
    previewMask.className = "jm-anno-preview-mask";
    previewMask.innerHTML =
      '<div class="jm-anno-preview-bar">' +
      '  <span class="jm-anno-preview-title">纸张预览（与打印输出一致）</span>' +
      '  <span class="jm-anno-preview-info"></span>' +
      '  <span class="jm-anno-zoom">' +
      '    <button type="button" data-a="zoomout" title="缩小">－</button>' +
      '    <button type="button" data-a="zoomfit" title="适应窗口">适应</button>' +
      '    <button type="button" data-a="zoomin" title="放大">＋</button>' +
      '    <span class="jm-anno-zoom-val"></span>' +
      '  </span>' +
      '  <button type="button" data-a="orient">切换纸向</button>' +
      '  <button type="button" data-a="print">打印</button>' +
      '  <button type="button" data-a="close">关闭</button>' +
      '</div>' +
      '<div class="jm-anno-preview-scroll"></div>';
    document.body.appendChild(previewMask);

    var printing = false;

    previewMask.addEventListener("click", function (e) {
      var btn = e.target.closest("button[data-a]");
      if (!btn) { return; }
      var a = btn.getAttribute("data-a");
      if (a === "close") { closePreview(); }
      else if (a === "zoomin" || a === "zoomout") {
        pvZoom = pvZoom === 0 ? fitZoom() : pvZoom;
        pvZoom = Math.min(3, Math.max(0.25, pvZoom * (a === "zoomin" ? 1.25 : 0.8)));
        renderPreviewCards();
      }
      else if (a === "zoomfit") { pvZoom = 0; renderPreviewCards(); }
      else if (a === "orient") {
        var cur = orientOverride() || "portrait";
        try { localStorage.setItem("jmAnnoOrient:" + TPL_ID, cur === "portrait" ? "landscape" : "portrait"); } catch (e4) { /* ignore */ }
        updateOrientLabel();
        applyDesk();
        if (pvBig) { pvPages = buildPages(pvBig, {}); renderPreviewCards(); }
      }
      else if (a === "print" && !printing) {
        printing = true;
        btn.textContent = "打印中…";
        composeBig(function (big) {
          var pv = buildPages(big, {});
          var frame = document.createElement("iframe");
          frame.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;";
          document.body.appendChild(frame);
          var doc = frame.contentWindow.document;
          doc.open(); doc.write(buildPrintHtml(pv)); doc.close();
          setTimeout(function () {
            try { frame.contentWindow.focus(); frame.contentWindow.print(); } catch (e2) { /* ignore */ }
            setTimeout(function () { frame.remove(); }, 60000);
          }, 300);
          printing = false;
          btn.textContent = "打印";
        });
      }
    });

    // Ctrl+滚轮缩放
    previewMask.addEventListener("wheel", function (e) {
      if (!e.ctrlKey) { return; }
      e.preventDefault();
      pvZoom = pvZoom === 0 ? fitZoom() : pvZoom;
      pvZoom = Math.min(3, Math.max(0.25, pvZoom * (e.deltaY < 0 ? 1.15 : 0.87)));
      renderPreviewCards();
    }, { passive: false });

    composeBig(function (big) {
      if (!previewMask) { return; }
      pvBig = big;
      pvPages = buildPages(pvBig, {});
      renderPreviewCards();
    });
  }

  /** 适应窗口：预览区内完整显示整页（宽高取小） */
  function fitZoom() {
    if (!pvPages || !previewMask) { return 1; }
    var scrollEl = previewMask.querySelector(".jm-anno-preview-scroll");
    var availW = Math.max(200, (scrollEl ? scrollEl.clientWidth : window.innerWidth) - 64);
    var availH = Math.max(200, (scrollEl ? scrollEl.clientHeight : window.innerHeight) - 120);
    return Math.max(0.25, Math.min(3, Math.min(availW / pvPages.pageW, availH / pvPages.pageH)));
  }

  function closePreview() {
    if (previewMask) { previewMask.remove(); previewMask = null; }
  }

  /** 纸张配置：优先 jmsheet printConfig（mm），缺省 A4 纵向；支持预览页覆盖纵/横 */
  function paperConfig() {
    var pc = null;
    try { pc = window.xs && window.xs.data && window.xs.data.printConfig; } catch (e) { pc = null; }
    var w = pc && pc.width ? pc.width : 210;
    var h = pc && pc.height ? pc.height : 297;
    if (w > h) { var t0 = w; w = h; h = t0; }   // 先归一为短边×长边
    var ov = orientOverride();
    if (ov === "landscape") { var t = w; w = h; h = t; }
    return { w: w, h: h, landscape: w > h, layout: w > h ? "landscape" : "portrait" };
  }

  /** 纸向覆盖：null=跟随模板；portrait/landscape=预览页强制（不改模板） */
  function orientOverride() {
    var v = null;
    try { v = localStorage.getItem("jmAnnoOrient:" + TPL_ID); } catch (e) { v = null; }
    return v === "landscape" || v === "portrait" ? v : null;
  }

  /** 纸向切换/缩放后重排预览纸卡（免重合成，直接用缓存分页图） */
  function renderPreviewCards() {
    if (!previewMask || !pvPages) { return; }
    var scrollEl = previewMask.querySelector(".jm-anno-preview-scroll");
    var infoEl = previewMask.querySelector(".jm-anno-preview-info");
    if (!scrollEl || !infoEl) { return; }
    var z = pvZoom === 0 ? fitZoom() : pvZoom;
    scrollEl.innerHTML = "";
    infoEl.textContent = (pvPages.landscape ? "横向 " : "纵向 ") + pvPages.w + "×" + pvPages.h + "mm · " + pvPages.pages.length + " 页 · " + Math.round(z * 100) + "%";
    var zv = previewMask.querySelector(".jm-anno-zoom-val");
    if (zv) { zv.textContent = Math.round(z * 100) + "%"; }
    for (var p = 0; p < pvPages.pages.length; p++) {
      var card = document.createElement("div");
      card.className = "jm-anno-page";
      card.style.width = Math.round(pvPages.pageW * z) + "px";
      card.style.height = Math.round(pvPages.pageH * z) + "px";
      var img = document.createElement("img");
      img.src = pvPages.pages[p];
      card.appendChild(img);
      scrollEl.appendChild(card);
    }
  }

  /** 窗口尺寸变化时，适应模式下重排 */
  window.addEventListener("resize", function () {
    if (previewMask && pvZoom === 0) { renderPreviewCards(); }
  });

  // ---------- 样式 ----------
  function injectStyle() {
    var css = "" +
      ".jm-anno-bar{position:fixed;right:12px;top:96px;z-index:2147483000;font-family:'Helvetica Neue',Arial,'PingFang SC','Microsoft YaHei',sans-serif;}" +
      // 左上角返回系统按钮
      ".jm-anno-back{position:fixed;left:10px;top:46px;z-index:2147483000;cursor:pointer;border:none;background:rgba(45,58,75,.85);color:#fff;border-radius:6px;padding:5px 12px;font-size:13px;letter-spacing:1px;box-shadow:0 2px 8px rgba(0,0,0,.25);font-family:'Helvetica Neue',Arial,'PingFang SC','Microsoft YaHei',sans-serif;}" +
      ".jm-anno-back:hover{background:#2d3a4b;}" +
      ".jm-anno-bar .jm-anno-tab{writing-mode:vertical-lr;letter-spacing:4px;padding:10px 4px;background:#409eff;color:#fff;border-radius:6px 0 0 6px;cursor:pointer;font-size:13px;box-shadow:0 2px 8px rgba(0,0,0,.2);}" +
      ".jm-anno-bar .jm-anno-panel{position:absolute;right:100%;top:0;margin-right:6px;display:flex;flex-wrap:wrap;gap:6px;align-items:center;width:300px;padding:10px;background:#fff;border:1px solid #dcdfe6;border-radius:6px;box-shadow:0 4px 16px rgba(0,0,0,.15);font-size:13px;color:#333;}" +
      ".jm-anno-bar button{cursor:pointer;border:1px solid #dcdfe6;background:#fff;border-radius:4px;padding:3px 8px;font-size:13px;line-height:18px;}" +
      ".jm-anno-bar button:hover{border-color:#409eff;color:#409eff;}" +
      ".jm-anno-bar .jm-anno-print{background:#409eff;border-color:#409eff;color:#fff;font-weight:bold;}" +
      ".jm-anno-bar .jm-anno-sep{width:1px;height:18px;background:#e4e7ed;}" +
      ".jm-anno-bar label{display:inline-flex;align-items:center;gap:2px;}" +
      ".jm-anno-bar select{border:1px solid #dcdfe6;border-radius:4px;padding:2px;}" +
      ".jm-anno-bar input[type=color]{width:22px;height:22px;padding:0;border:1px solid #dcdfe6;border-radius:4px;background:#fff;cursor:pointer;}" +
      ".jm-anno-box{position:absolute;pointer-events:auto;box-sizing:border-box;min-width:24px;min-height:18px;}" +
      ".jm-anno-box .jm-anno-text{width:100%;height:100%;box-sizing:border-box;padding:3px 4px;outline:none;overflow:hidden;white-space:pre-wrap;word-break:break-all;line-height:1.4;}" +
      ".jm-anno-box .jm-anno-text:empty:before{content:attr(data-ph);color:#c0c4cc;}" +
      ".jm-anno-note .jm-anno-text{border:1px dashed #e6a23c;}" +
      ".jm-anno-value .jm-anno-text{border:1px solid #409eff;}" +
      ".jm-anno-box.jm-anno-noborder .jm-anno-text{border:none;}" +
      ".jm-anno-grip,.jm-anno-rs,.jm-anno-del{display:none;}" +
      ".jm-anno-box.jm-anno-active{outline:1px solid #409eff;outline-offset:1px;}" +
      ".jm-anno-box.jm-anno-active .jm-anno-grip{display:block;position:absolute;left:0;top:-16px;width:100%;height:14px;background:#409eff;border-radius:3px;cursor:move;opacity:.85;}" +
      ".jm-anno-box.jm-anno-active .jm-anno-rs{display:block;position:absolute;right:-5px;bottom:-5px;width:10px;height:10px;background:#409eff;border:2px solid #fff;border-radius:50%;cursor:nwse-resize;}" +
      ".jm-anno-box.jm-anno-active .jm-anno-del{display:block;position:absolute;right:-9px;top:-9px;width:18px;height:18px;line-height:16px;text-align:center;background:#f56c6c;color:#fff;border-radius:50%;cursor:pointer;font-size:14px;}" +
      // 查询栏残余收起头（s24 已关 izOpenQueryBar，表单本体已 0px，仅剩折叠头）彻底隐藏
      ".jm-query-collapse{display:none !important;}" +
      // 工作台：灰底 + 点阵网格，canvas 变 A4 纸卡（白底投影居中）
      "body.jm-anno-desk .jm-sheet{background-color:#e9ecf1 !important;background-image:radial-gradient(rgba(90,100,118,.28) 1px, transparent 1.4px) !important;background-size:18px 18px !important;}" +
      "body.jm-anno-desk .jm-sheet-sheet{background:transparent !important;overflow:auto !important;}" +
      "body.jm-anno-desk .jm-sheet-sheet canvas{background:#fff !important;box-shadow:0 3px 18px rgba(31,45,61,.22),0 0 0 1px rgba(31,45,61,.08) !important;border-radius:2px;}" +
      // 工作台滚动条细化（内容超纸宽时出现，如总单宽表）
      "body.jm-anno-desk .jm-sheet-sheet::-webkit-scrollbar{width:8px;height:8px;}" +
      "body.jm-anno-desk .jm-sheet-sheet::-webkit-scrollbar-thumb{background:rgba(100,110,125,.35);border-radius:4px;}" +
      "body.jm-anno-desk .jm-sheet-sheet::-webkit-scrollbar-track{background:transparent;}" +
      "body.jm-anno-desk .jm-sheet-sheet .scrollbarY,body.jm-anno-desk .jm-sheet-sheet .scrollbarX{right:8px;bottom:8px;}" +
      // Word 式纸张预览弹窗
      ".jm-anno-preview-mask{position:fixed;inset:0;z-index:2147483001;background:rgba(0,0,0,.55);display:flex;flex-direction:column;}" +
      ".jm-anno-preview-bar{flex:none;display:flex;align-items:center;gap:10px;padding:10px 16px;background:#2d3a4b;color:#fff;}" +
      ".jm-anno-preview-title{font-size:14px;font-weight:bold;}" +
      ".jm-anno-preview-info{font-size:12px;opacity:.75;margin-right:auto;}" +
      ".jm-anno-preview-bar button{cursor:pointer;border:none;border-radius:4px;padding:5px 14px;font-size:13px;background:#409eff;color:#fff;}" +
      ".jm-anno-preview-bar button:hover{opacity:.9;}" +
      ".jm-anno-preview-scroll{flex:1;overflow:auto;padding:24px;display:flex;flex-direction:column;align-items:center;gap:16px;}" +
      ".jm-anno-page{background:#fff;box-shadow:0 2px 12px rgba(0,0,0,.4);box-sizing:border-box;overflow:hidden;}" +
      ".jm-anno-page img{width:100%;height:100%;display:block;}" +
      "";
    var style = document.createElement("style");
    style.setAttribute("type", "text/css");
    style.textContent = css;
    document.head.appendChild(style);
  }

  injectStyle();
})();
