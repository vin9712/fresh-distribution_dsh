<template>
  <div class="app-container purchase-day-page">
    <!-- 顶部工作条 -->
    <div class="pd-toolbar">
      <div class="pd-toolbar-left">
        <el-date-picker
          v-model="orderDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled="loading"
          @change="load()"
        />
        <dict-tag v-if="summary.status != null" :options="dict.type.t_purchase_order_status" :value="summary.status" />
        <el-tag v-else type="info" size="small" effect="plain">未建单</el-tag>
        <span class="pd-code" :title="summary.code || ''">{{ summary.code || "首次录入自动创建" }}</span>
      </div>
      <div class="pd-toolbar-right">
        <el-button size="small" :icon="Refresh" @click="load()">刷新</el-button>
        <el-button size="small" :icon="Back" @click="goBack">返回列表</el-button>
        <el-button
          v-hasPermi="['purchase:edit']"
          type="success"
          size="small"
          :icon="Check"
          :disabled="!canConfirm"
          @click="handleConfirm"
          >确认采购单</el-button
        >
        <el-button
          v-hasPermi="['purchase:edit']"
          type="warning"
          size="small"
          :icon="Select"
          :disabled="!canStockIn"
          @click="handleStockIn"
          >入库（确认成本）</el-button
        >
      </div>
    </div>

    <!-- 统计条 -->
    <div class="pd-stats">
      <span>应采品种 <b>{{ summary.requiredItemCount || 0 }}</b></span>
      <span>已录品种 <b class="pd-ok">{{ summary.purchasedItemCount || 0 }}</b></span>
      <span>未录品种 <b class="pd-warn">{{ Math.max(0, (summary.requiredItemCount || 0) - (summary.purchasedItemCount || 0)) }}</b></span>
      <el-divider direction="vertical" />
      <span>应采总量 <b>{{ num(summary.requiredQty) }}</b></span>
      <span>已采总量 <b class="pd-ok">{{ num(summary.purchasedQty) }}</b></span>
      <span>待采总量 <b :class="{ 'pd-bad': Number(summary.pendingQty) < 0 }">{{ num(summary.pendingQty) }}</b></span>
      <el-divider direction="vertical" />
      <span>当日采购总额 <b class="pd-amount">¥ {{ money(summary.totalAmount) }}</b></span>
      <span v-if="summary.overCount > 0" class="pd-bad">超采 {{ summary.overCount }} 行（不影响下单与验收）</span>
    </div>

    <!-- 单头默认供应商/采购员（草稿/已确认可改，作为行内录入默认值） -->
    <div v-if="canEditHeader" class="pd-head">
      <el-form :inline="true" size="small">
        <el-form-item label="默认供应商">
          <el-input v-model="header.supplierName" placeholder="可空，行内录入自动带入" style="width: 200px" />
        </el-form-item>
        <el-form-item label="采购员">
          <el-input v-model="header.purchaser" placeholder="可空" style="width: 130px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="header.remark" placeholder="可空" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button size="small" :disabled="!summary.purchaseId" @click="saveHeader">保存单头</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 录入提示（草稿） -->
    <el-alert
      v-if="canEdit"
      type="info"
      :closable="false"
      show-icon
      class="pd-hint"
      title="数量已默认按【待采数量】填充（未录时即订单总数），只需填进货价：回车即保存一个批次并自动跳到下一行进货价；供应商可留空（跟单头）；Ctrl+Enter 一次提交所有已填进货价的行。同一商品可多次加入（分批进货、加权算成本）。"
    />

    <!-- 主表：应采清单 + 行内录入 -->
    <el-table
      ref="mainTable"
      v-loading="loading"
      :data="rows"
      row-key="key"
      :expand-row-keys="expandedKeys"
      size="small"
      border
      :row-class-name="rowClass"
      class="pd-table"
      @expand-change="onExpandChange"
    >
      <el-table-column type="expand">
        <template #default="scope">
          <div class="pd-batches">
            <el-table :data="scope.row.batches" size="small" border>
              <el-table-column label="批次" prop="batchNo" width="60" align="center" />
              <el-table-column label="数量" width="130" align="right">
                <template #default="b">
                  <el-input-number
                    v-if="canEdit"
                    v-model="b.row.quantity"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    size="small"
                    style="width: 100%"
                    @change="markBatchDirty"
                  />
                  <span v-else>{{ num(b.row.quantity) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="进货价" width="130" align="right">
                <template #default="b">
                  <el-input-number
                    v-if="canEdit"
                    v-model="b.row.unitPrice"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    size="small"
                    style="width: 100%"
                    @change="markBatchDirty"
                  />
                  <span v-else>{{ money(b.row.unitPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="小计" width="110" align="right">
                <template #default="b">
                  <b>{{ money(Number(b.row.quantity) * Number(b.row.unitPrice)) }}</b>
                </template>
              </el-table-column>
              <el-table-column label="供应商" min-width="150">
                <template #default="b">
                  <el-input
                    v-if="canEdit"
                    v-model="b.row.supplierName"
                    size="small"
                    placeholder="可空（同供应商可多批）"
                    @change="markBatchDirty"
                  />
                  <span v-else>{{ b.row.supplierName || "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="录入人" prop="createBy" width="90" align="center" />
              <el-table-column label="录入时间" prop="createTime" width="155" align="center" />
              <el-table-column label="备注" prop="remark" min-width="110" show-overflow-tooltip />
              <el-table-column label="操作" width="140" align="center">
                <template #default="b">
                  <template v-if="canEdit">
                    <el-button link size="small" type="primary" @click="saveBatch(scope.row, b.row)">保存</el-button>
                    <el-button link size="small" type="danger" @click="removeBatch(scope.row, b.row)">删除</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!scope.row.batches || !scope.row.batches.length" class="pd-empty">暂无进货批次</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="商品" prop="productName" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ scope.row.productName }}
          <el-tag v-if="scope.row.orphan" size="small" type="danger" effect="plain">订单已撤回</el-tag>
          <el-tag v-else-if="scope.row.manual" size="small" type="primary" effect="plain">新增商品</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规格" prop="productSpec" width="100" :show-overflow-tooltip="true" />
      <el-table-column label="单位" prop="productUnit" width="90" align="center">
        <template #default="scope">
          <el-input
            v-if="canEdit && scope.row.manual"
            v-model="scope.row.productUnit"
            size="small"
            placeholder="单位"
            style="width: 100%"
            @change="onManualUnitChange(scope.row)"
          />
          <span v-else>{{ scope.row.productUnit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="应采" width="80" align="right">
        <template #default="scope">{{ scope.row.manual ? "—" : num(scope.row.requiredQty) }}</template>
      </el-table-column>
      <el-table-column label="已采" width="80" align="right">
        <template #default="scope">{{ num(scope.row.purchasedQty) }}</template>
      </el-table-column>
      <el-table-column label="待采" width="80" align="right">
        <template #default="scope">
          <span v-if="scope.row.manual">—</span>
          <span v-else :class="{ 'pd-bad': Number(scope.row.pendingQty) < 0 }">{{ num(scope.row.pendingQty) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="批次" prop="batchCount" width="60" align="center" />
      <el-table-column label="加权均价" width="90" align="right">
        <template #default="scope">
          {{ Number(scope.row.purchasedQty) > 0 ? Number(scope.row.avgPrice).toFixed(4) : "—" }}
        </template>
      </el-table-column>
      <el-table-column label="采购金额" width="100" align="right">
        <template #default="scope">{{ money(scope.row.amount) }}</template>
      </el-table-column>
      <!-- 行内录入（仅草稿，无弹窗） -->
      <el-table-column v-if="canEdit" label="本次录入" fixed="right" width="360" align="center">
        <template #default="scope">
          <div
            v-if="!scope.row.orphan"
            class="pd-inline-entry"
            @keydown.capture="onEntryKeydown"
            @focusin="onEntryFocus"
            @mousedown="onEntryMouseDown"
          >
            <el-input-number
              v-model="scope.row._entry.quantity"
              :min="0.01"
              :precision="2"
              :controls="false"
              size="small"
              class="pd-entry-qty"
              placeholder="数量"
              @keyup.enter="focusNextEntry($event)"
            />
            <el-input-number
              v-model="scope.row._entry.unitPrice"
              :min="0"
              :precision="2"
              :controls="false"
              size="small"
              class="pd-entry-price"
              placeholder="进货价"
              @keyup.enter="onPriceEnter($event, scope.row)"
            />
            <el-input
              v-model="scope.row._entry.supplierName"
              size="small"
              class="pd-entry-supplier"
              placeholder="供应商"
              @keyup.enter="onSupplierEnter($event, scope.row)"
            />
            <el-button size="small" type="primary" plain :icon="Plus" @click="submitRow(scope.row)">加入</el-button>
          </div>
          <span v-else class="pd-orphan-tip">—</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 底部批量提交（多行一次性录入） -->
    <div v-if="canEdit" class="pd-footer">
      <el-button class="pd-add-product" :icon="Plus" @click="openProductPicker">新增商品</el-button>
      <span>已填进货价 {{ filledCount }} 行（数量默认=待采数量）；回车逐行保存，或 Ctrl+Enter 一次提交。</span>
      <el-button type="primary" :icon="Select" :disabled="filledCount === 0" :loading="saving" @click="submitAll">
        提交录入（{{ filledCount }} 行）
      </el-button>
    </div>

    <!-- 新增商品：数据来源=商品库 SKU；已在列表中的不可重复添加；搜不到可记为临时商品 -->
    <el-dialog align-center title="新增商品" v-model="pickerOpen" width="760px" append-to-body>
      <div class="pd-picker-bar">
        <el-input
          v-model="pickerKeyword"
          placeholder="商品名称 / 助记码，回车检索商品库"
          clearable
          style="width: 320px"
          @keyup.enter="searchProducts"
          @clear="onPickerClear"
        />
        <el-button type="primary" :icon="Search" :loading="pickerLoading" @click="searchProducts">搜索</el-button>
        <span class="pd-picker-hint">数据来源：商品库 SKU（已在列表中的商品不可重复添加）</span>
      </div>
      <el-table v-loading="pickerLoading" :data="pickerResults" size="small" border max-height="380">
        <el-table-column label="商品" prop="name" min-width="170" show-overflow-tooltip />
        <el-table-column label="规格" prop="specName" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="助记码" prop="mnemonicCode" width="100" align="center" />
        <el-table-column label="操作" width="110" align="center">
          <template #default="scope">
            <el-button
              v-if="!isProductPicked(scope.row)"
              link
              type="primary"
              size="small"
              @click="addPickedProduct(scope.row)"
              >添加</el-button
            >
            <el-tag v-else size="small" type="info">已在列表</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="pickerSearched && !pickerResults.length" class="pd-picker-empty">
        <span>商品库无匹配「{{ pickerKeyword }}」</span>
        <el-button link type="warning" :disabled="!pickerKeyword" @click="addTempProduct">记为临时商品</el-button>
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  daySummary,
  dayOrder,
  addBatch,
  addBatchBulk,
  updateBatch,
  delBatch,
  updatePurchaseHeader,
  confirmPurchase,
  stockInPurchase,
  skuOptions,
} from "@/api/purchase/purchase";
import { SCOPE, registerShortcuts } from "@/utils/shortcut";
import { Plus, Delete, Refresh, Check, Select, Back, Search } from "@element-plus/icons-vue";

export default {
  name: "PurchaseDay",
  dicts: ["t_purchase_order_status"],
  setup() {
    return { Plus, Delete, Refresh, Check, Select, Back, Search };
  },
  data() {
    return {
      loading: false,
      saving: false,
      orderDate: null,
      summary: { rows: [] },
      header: { supplierName: "", purchaser: "", remark: "" },
      // 受控展开行（无 row-key 时展开行内编辑会触发折叠）
      expandedKeys: [],
      // 展开行内批次是否改过但未保存（离开守卫用）
      batchDirty: false,
      // 单头保存快照（离开守卫比对单头是否改过）
      headerSaved: null,
      // 新增商品选择器
      pickerOpen: false,
      pickerKeyword: "",
      pickerResults: [],
      pickerSearched: false,
      pickerLoading: false,
    };
  },
  computed: {
    /** 草稿（或未建单）可录入/改批次 */
    canEdit() {
      return this.summary.status == null || this.summary.status === 0;
    },
    canConfirm() {
      return this.summary.status === 0;
    },
    canStockIn() {
      return this.summary.status === 1;
    },
    canEditHeader() {
      return this.summary.status === 0 || this.summary.status === 1;
    },
    rows() {
      return this.summary.rows || [];
    },
    filledCount() {
      // 数量已默认填（=待采数量，未录时即订单总数），因此以「是否填了进货价」为准
      return this.rows.filter((r) => !r.orphan && this.priceFilled(r) && Number(r._entry.quantity) > 0).length;
    },
    /** 未提交的录入内容（行内已填进货价/改过数量、展开行批次改过未保存、单头改过未保存） */
    hasUnsavedDraft() {
      return (
        this.canEdit &&
        (this.batchDirty || this.headerDirty || this.rows.some((r) => this.entryDirty(r)))
      );
    },
    /** 单头是否改过未保存（与 load() 时的快照比对） */
    headerDirty() {
      const h = this.header;
      const s = this.headerSaved;
      if (!s) return false;
      return (
        (h.supplierName || "") !== (s.supplierName || "") ||
        (h.purchaser || "") !== (s.purchaser || "") ||
        (h.remark || "") !== (s.remark || "")
      );
    },
  },
  created() {
    this.orderDate = this.$route.query.orderDate || this.defaultDate();
    this.load();
  },
  mounted() {
    this.registerPageShortcuts();
    window.addEventListener("beforeunload", this.onBeforeUnload);
  },
  activated() {
    this.registerPageShortcuts();
    // keep-alive 缓存实例：复位离开放行标记，否则上次确认离开后本次守卫被静默击穿
    this._allowLeave = false;
    window.addEventListener("beforeunload", this.onBeforeUnload);
  },
  deactivated() {
    this.unregisterPageShortcuts();
    window.removeEventListener("beforeunload", this.onBeforeUnload);
  },
  beforeUnmount() {
    this.unregisterPageShortcuts();
    window.removeEventListener("beforeunload", this.onBeforeUnload);
  },
  /** 路由离开守卫：有未提交的录入内容（行内已填进货价/改数量、批次或单头未保存）先确认，避免丢数据 */
  beforeRouteLeave(to, from, next) {
    if (this._allowLeave || !this.hasUnsavedDraft) {
      next();
      return;
    }
    this.$modal
      .confirm("采购录入有未提交的内容（已填进货价、改过数量/批次或单头未保存），离开将丢失。确定离开吗？", "未保存提醒", {
        confirmButtonText: "离开",
        cancelButtonText: "继续编辑",
        type: "warning",
      })
      .then(() => {
        this._allowLeave = true;
        next();
      })
      .catch(() => next(false));
  },
  methods: {
    defaultDate() {
      const d = new Date();
      d.setDate(d.getDate() + 1);
      return d.toISOString().slice(0, 10);
    },
    num(v) {
      if (v === null || v === undefined || v === "") return "0";
      return Number(v).toString();
    },
    money(v) {
      return (Number(v) || 0).toFixed(2);
    },
    rowClass({ row }) {
      return Number(row.pendingQty) < 0 && !row.manual ? "pd-row-over" : "";
    },
    onExpandChange(row, expandedRows) {
      this.expandedKeys = (expandedRows || []).map((r) => r.key);
    },
    /** 展开行批次改动标记（保存成功后 load() 会清掉） */
    markBatchDirty() {
      this.batchDirty = true;
    },
    /** 行内录入是否改过（填了进货价，或数量偏离默认待采数；orphan 行不参与） */
    entryDirty(row) {
      if (!row || row.orphan || !row._entry) return false;
      if (this.priceFilled(row)) return true;
      const q = Number(row._entry.quantity);
      if (!(q > 0)) return false;
      const pending = Number(row.pendingQty);
      return !(pending > 0) || q !== pending;
    },
    /** 浏览器关闭/刷新：有未提交内容则弹原生确认 */
    onBeforeUnload(e) {
      if (!this.hasUnsavedDraft) return;
      e.preventDefault();
      e.returnValue = "";
      return "";
    },
    // ==================== 纯键盘录入（对齐订单录入：Enter 逐行连打） ====================
    /** Ctrl+Enter = 提交所有已填进货价的行（表格作用域，输入框内也生效；不与 QuickTable 的 Ctrl+S 撞键） */
    registerPageShortcuts() {
      if (this._unregShortcuts) return;
      this._unregShortcuts = registerShortcuts([
        {
          scope: SCOPE.TABLE,
          key: "ctrl+enter",
          description: "提交录入（已填进货价的行）",
          owner: "PurchaseDay",
          allowInInput: true,
          handler: () => this.submitAll(),
        },
      ]);
    },
    unregisterPageShortcuts() {
      if (this._unregShortcuts) {
        this._unregShortcuts();
        this._unregShortcuts = null;
      }
    },
    priceFilled(row) {
      const p = row._entry && row._entry.unitPrice;
      return p !== null && p !== undefined && p !== "";
    },
    /** 行内录入的数据行 DOM（不含展开行） */
    entryRows() {
      const el = this.$refs.mainTable && this.$refs.mainTable.$el;
      if (!el) return [];
      return Array.from(el.querySelectorAll("tbody tr")).filter((tr) => tr.querySelector(".pd-inline-entry"));
    },
    /** 行内录入输入框按「行 × 列」展开（列：数量 / 进货价 / 供应商） */
    entryGrid() {
      return this.entryRows().map((tr) => Array.from(tr.querySelectorAll(".pd-inline-entry input")));
    },
    /** 聚焦指定行列单元格并全选，方便直接输入覆盖 */
    focusCell(rowIdx, colIdx) {
      const grid = this.entryGrid();
      if (rowIdx < 0 || rowIdx >= grid.length) return;
      const cols = grid[rowIdx];
      if (!cols.length) return;
      const input = cols[Math.max(0, Math.min(colIdx, cols.length - 1))];
      if (!input) return;
      input._cellAllSelected = true;
      input.focus();
      if (input.select) {
        try {
          input.select();
        } catch (e) {
          /* number 输入框不支持 selection API 时忽略 */
        }
      }
    },
    /** 激活单元格（focusin，首次点击或键盘导航）即全选；已聚焦后再点一下可放光标 */
    onEntryFocus(e) {
      const input = e.target;
      if (!input || input.tagName !== "INPUT" || !input.select) return;
      input._cellAllSelected = true;
      // 等点击的默认光标定位结束后再全选，避免被覆盖
      setTimeout(() => {
        if (document.activeElement !== input) return;
        try {
          input.select();
        } catch (err) {
          /* 忽略 */
        }
      }, 0);
    },
    /** 已聚焦的单元格再次按下鼠标 = 用户要放光标 → 退出全选态（左右键改为移动光标） */
    onEntryMouseDown(e) {
      const input = e.target;
      if (input && input.tagName === "INPUT" && document.activeElement === input) {
        input._cellAllSelected = false;
      }
    },
    /**
     * 行内单元格方向键导航（capture 阶段：先于 el-input-number 上下键改值拦截）：
     * - 上下：切同列的上一/下一行单元格；
     * - 左右：当前单元格处于「全选/空」时切上/下一单元格（跨行回绕），否则放行默认光标移动。
     */
    onEntryKeydown(e) {
      const key = e.key;
      const input = e.target;
      if (!input || input.tagName !== "INPUT") return;
      if (key !== "ArrowUp" && key !== "ArrowDown" && key !== "ArrowLeft" && key !== "ArrowRight") {
        // 其它按键（打字/删除）视为退出全选态
        input._cellAllSelected = false;
        return;
      }
      const grid = this.entryGrid();
      let r = -1;
      let c = -1;
      for (let i = 0; i < grid.length; i++) {
        const j = grid[i].indexOf(input);
        if (j >= 0) {
          r = i;
          c = j;
          break;
        }
      }
      if (r < 0 || c < 0) return;
      if (key === "ArrowUp" || key === "ArrowDown") {
        e.preventDefault();
        e.stopPropagation();
        this.focusCell(r + (key === "ArrowDown" ? 1 : -1), c);
        return;
      }
      // 左右：仅当当前单元格为「全选/空」时切单元格；否则放行默认光标移动
      const val = input.value == null ? "" : String(input.value);
      const allSelected =
        val.length === 0 ||
        (input.type === "number"
          ? input._cellAllSelected === true
          : input.selectionStart === 0 && input.selectionEnd === val.length);
      if (!allSelected) {
        input._cellAllSelected = false;
        return;
      }
      e.preventDefault();
      e.stopPropagation();
      const cols = grid[r].length;
      let nr = r;
      let nc = c + (key === "ArrowRight" ? 1 : -1);
      if (nc < 0) {
        nr = r - 1;
        nc = cols - 1;
      } else if (nc >= cols) {
        nr = r + 1;
        nc = 0;
      }
      this.focusCell(nr, nc);
    },
    focusEntryAt(index, selector, attempt = 0) {
      this.$nextTick(() => {
        const tr = this.entryRows()[index];
        const input = tr && tr.querySelector(selector);
        if (input) {
          input.focus();
          if (input.select) input.select();
          return;
        }
        // 表格行可能还没渲染出来（首次加载/切日期），短重试
        if (attempt < 10) {
          setTimeout(() => this.focusEntryAt(index, selector, attempt + 1), 50);
        }
      });
    },
    focusPriceByKey(key) {
      const index = this.rows.findIndex((r) => r.key === key);
      if (index < 0) return;
      const doFocus = () => this.focusEntryAt(index, ".pd-entry-price input");
      doFocus();
      // 切日期时日期面板关闭会抢焦点，补一次
      setTimeout(doFocus, 250);
    },
    focusQtyByKey(key) {
      const index = this.rows.findIndex((r) => r.key === key);
      if (index >= 0) this.focusEntryAt(index, ".pd-entry-qty input");
    },
    /** 采购汇总键：与后端 summaryKey 一致（sku|品名|规格|单位） */
    summaryKeyOf(skuId, name, spec, unit) {
      return (
        (skuId == null || skuId === "" ? "_" : skuId) +
        "|" + (name || "") + "|" + (spec || "") + "|" + (unit || "")
      );
    },
    openProductPicker() {
      this.pickerKeyword = "";
      this.pickerResults = [];
      this.pickerSearched = false;
      this.pickerOpen = true;
    },
    onPickerClear() {
      this.pickerResults = [];
      this.pickerSearched = false;
    },
    /** 检索商品库 SKU（品名/助记码/别名） */
    searchProducts() {
      const kw = (this.pickerKeyword || "").trim();
      if (!kw) {
        this.$modal.msgWarning("请输入商品名称或助记码");
        return;
      }
      this.pickerLoading = true;
      skuOptions(kw)
        .then((res) => {
          this.pickerResults = res.data || [];
          this.pickerSearched = true;
        })
        .finally(() => {
          this.pickerLoading = false;
        });
    },
    /** 该 SKU 是否已在录入列表（按 skuId 去重，不允许重复商品） */
    isProductPicked(sku) {
      if (!sku || sku.id == null) return false;
      return this.rows.some((r) => r.skuId != null && Number(r.skuId) === Number(sku.id));
    },
    addPickedProduct(sku) {
      if (this.isProductPicked(sku)) {
        this.$modal.msgWarning("该商品已在列表中，无需重复添加");
        return;
      }
      this.appendManualRow({
        skuId: sku.id,
        productName: sku.name,
        productSpec: sku.specName || "",
        productUnit: sku.unit || "",
      });
    },
    /** 商品库无匹配时：以关键词记为临时商品（无 SKU，默认单位「斤」，可自行修改） */
    addTempProduct() {
      const name = (this.pickerKeyword || "").trim();
      if (!name) return;
      if (this.rows.some((r) => r.skuId == null && r.productName === name)) {
        this.$modal.msgWarning("临时商品【" + name + "】已在列表中");
        return;
      }
      this.appendManualRow({ skuId: null, productName: name, productSpec: "", productUnit: "斤" });
    },
    /** 手动新增行改单位：重算采购汇总键（sku|品名|规格|单位），保持与后端/去重口径一致 */
    onManualUnitChange(row) {
      row.productUnit = (row.productUnit || "").trim();
      if (Number(row.batchCount) > 0) {
        // 已录批次在 DB 里按旧单位固化：改单位后新批次会与旧批次分行显示（历史批次不变）
        this.$modal.msgWarning(
          `该商品已有 ${row.batchCount} 个批次按原单位记录；修改单位后新批次将与旧批次分行显示，请确认是否录入时用错单位`
        );
      }
      row.key = this.summaryKeyOf(row.skuId, row.productName, row.productSpec, row.productUnit);
      this.batchDirty = true;
    },
    /** 手动新增一行（SKU 选品/临时商品）：应采=0、可继续录入；提交后由后端标记 is_manual */
    appendManualRow({ skuId, productName, productSpec, productUnit }) {
      const key = this.summaryKeyOf(skuId, productName, productSpec, productUnit);
      const exist = this.rows.find((r) => r.key === key);
      if (exist) {
        this.$modal.msgWarning("该商品已在列表中，无需重复添加");
        this.pickerOpen = false;
        this.$nextTick(() => this.focusPriceByKey(exist.key));
        return;
      }
      this.summary.rows.push({
        key,
        skuId,
        productName,
        productSpec,
        productUnit,
        requiredQty: 0,
        purchasedQty: 0,
        pendingQty: 0,
        batchCount: 0,
        avgPrice: 0,
        amount: 0,
        orphan: false,
        manual: true,
        batches: [],
        _entry: { quantity: null, unitPrice: null, supplierName: this.header.supplierName },
      });
      const manualRow = this.summary.rows[this.summary.rows.length - 1];
      if (!this._localManualRows) this._localManualRows = [];
      if (!this._localManualRows.some((r) => r.key === key)) {
        this._localManualRows.push(manualRow);
      }
      this.pickerOpen = false;
      this.$nextTick(() => this.focusPriceByKey(key));
    },
    /** 下一个待录入行（末尾回绕），用于回车后连打 */
    nextEntryKey(fromKey) {
      const list = this.rows.filter((r) => !r.orphan);
      if (!list.length) return null;
      const i = list.findIndex((r) => r.key === fromKey);
      return list[(i + 1) % list.length].key;
    },
    /** 进货价回车：数量已默认填好→直接提交；数量清空→回数量（Ctrl+Enter 交给快捷键层，避免双重提交） */
    onPriceEnter(e, row) {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (!(Number(row._entry.quantity) > 0)) {
        this.focusQtyByKey(row.key);
        return;
      }
      this.submitRow(row);
    },
    /** 供应商回车 = 提交本行（Ctrl+Enter 交给快捷键层） */
    onSupplierEnter(e, row) {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      this.submitRow(row);
    },
    goBack() {
      this.$router.push("/order/purchase");
    },
    load(focusKey) {
      if (!this.orderDate) return;
      this.loading = true;
      // 捕获本地未提交的手动行（新增商品后未录批次前，服务端汇总里没有，
      // 不保留会在 load 重建时被静默清掉）
      const prevManualRows = this._localManualRows || [];
      daySummary(this.orderDate)
        .then((res) => {
          this.summary = res.data || { rows: [] };
          this.header = {
            supplierName: this.summary.supplierName || "",
            purchaser: this.summary.purchaser || "",
            remark: this.summary.remark || "",
          };
          this.headerSaved = { ...this.header };
          // 行内录入初始槽：数量默认=待采数量（未录时即订单总数）；进货价必须手填；供应商默认带单头
          this.batchDirty = false;
          // 复位离开放行标记：重新载入后数据已落库，不能沿用上次确认离开的放行态
          this._allowLeave = false;
          (this.summary.rows || []).forEach((r) => {
            const pending = Number(r.pendingQty);
            r._entry = {
              quantity: pending > 0 ? pending : null,
              unitPrice: null,
              supplierName: this.header.supplierName,
            };
          });
          // 回填本地未提交的手动行（已提交的会由服务端返回，按 key 去重跳过；
          // 保留原行对象以保留已输入的 _entry 现场）
          if (prevManualRows.length && this.canEdit) {
            const serverKeys = new Set((this.summary.rows || []).map((r) => r.key));
            prevManualRows.forEach((m) => {
              if (m && m.manual && !serverKeys.has(m.key)) {
                this.summary.rows.push(m);
              }
            });
          }
          this._localManualRows = (this.summary.rows || []).filter((r) => r.manual);
          // 纯键盘：录入态自动聚焦首行（或指定行）的进货价
          if (this.canEdit) {
            const key = focusKey || (this.rows.find((r) => !r.orphan) || {}).key;
            if (key) this.focusPriceByKey(key);
          }
        })
        .finally(() => {
          this.loading = false;
        });
    },
    /** 未建单时先惰性创建当日采购单，返回 purchaseId */
    ensurePurchaseId() {
      if (this.summary.purchaseId) {
        return Promise.resolve(this.summary.purchaseId);
      }
      return dayOrder(this.orderDate).then((res) => {
        this.summary.purchaseId = res.data.id;
        this.summary.code = res.data.code;
        this.summary.status = res.data.status;
        return res.data.id;
      });
    },
    /** 行内回车：数量→进货价→供应商（带 Ctrl/Alt 修饰键时不处理，交给快捷键层） */
    focusNextEntry(e) {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const wrap = e.target.closest(".pd-inline-entry");
      if (!wrap) return;
      const inputs = wrap.querySelectorAll("input");
      const idx = Array.from(inputs).indexOf(e.target);
      if (idx >= 0 && idx < inputs.length - 1) {
        inputs[idx + 1].focus();
      }
    },
    rowPayload(row) {
      return {
        skuId: row.skuId,
        productName: row.productName,
        productSpec: row.productSpec,
        productUnit: row.productUnit,
        quantity: Number(row._entry.quantity),
        unitPrice: Number(row._entry.unitPrice),
        supplierName: row._entry.supplierName || null,
        remark: null,
      };
    },
    validateRow(row) {
      if (!(Number(row._entry.quantity) > 0)) {
        this.$modal.msgError(`【${row.productName}】请输入大于 0 的采购数量`);
        return false;
      }
      if (row._entry.unitPrice === undefined || row._entry.unitPrice === null || row._entry.unitPrice === "") {
        this.$modal.msgError(`【${row.productName}】请填写进货价`);
        return false;
      }
      if (Number(row._entry.unitPrice) < 0) {
        this.$modal.msgError(`【${row.productName}】进货价不能为负`);
        return false;
      }
      return true;
    },
    /** 单行「加入」/ 进货价回车（成功后焦点落到下一行进货价，便于连打） */
    submitRow(row) {
      if (!this.validateRow(row)) return;
      const nextKey = this.nextEntryKey(row.key);
      this.saving = true;
      this.ensurePurchaseId()
        .then((purchaseId) => addBatch(purchaseId, this.rowPayload(row)))
        .then(() => {
          this.saving = false;
          this.load(nextKey);
        })
        .catch(() => {
          this.saving = false;
        });
    },
    /** 底部/Ctrl+S 批量提交（只提交已填进货价的行，任一行非法整体回滚） */
    submitAll() {
      const filled = this.rows.filter((r) => !r.orphan && this.priceFilled(r) && Number(r._entry.quantity) > 0);
      if (!filled.length) {
        this.$modal.msgWarning("请先填写进货价（数量已默认按待采数量填充）");
        return;
      }
      for (const r of filled) {
        if (!this.validateRow(r)) return;
      }
      const firstKey = (this.rows.find((r) => !r.orphan) || {}).key;
      this.saving = true;
      this.ensurePurchaseId()
        .then((purchaseId) => addBatchBulk(purchaseId, filled.map((r) => this.rowPayload(r))))
        .then((res) => {
          this.$modal.msgSuccess(`已提交 ${res.data} 行批次`);
          this.saving = false;
          this.load(firstKey);
        })
        .catch(() => {
          this.saving = false;
        });
    },
    /** 展开行内改批次（数量/进货价/供应商） */
    saveBatch(row, batch) {
      if (!(Number(batch.quantity) > 0)) {
        this.$modal.msgError("数量必须大于 0");
        return;
      }
      if (batch.unitPrice === undefined || batch.unitPrice === null || Number(batch.unitPrice) < 0) {
        this.$modal.msgError("进货价不能为空或为负");
        return;
      }
      this.saving = true;
      updateBatch(this.summary.purchaseId, batch.id, {
        skuId: row.skuId,
        productName: row.productName,
        productSpec: row.productSpec,
        productUnit: row.productUnit,
        quantity: Number(batch.quantity),
        unitPrice: Number(batch.unitPrice),
        supplierName: batch.supplierName || null,
        remark: batch.remark || null,
      })
        .then(() => {
          this.$modal.msgSuccess("批次已保存");
          this.saving = false;
          this.load();
        })
        .catch(() => {
          this.saving = false;
        });
    },
    removeBatch(row, batch) {
      this.$modal
        .confirm(`确认删除批次（第 ${batch.batchNo} 批：${this.num(batch.quantity)} × ${this.money(batch.unitPrice)}）？`)
        .then(() => delBatch(this.summary.purchaseId, batch.id))
        .then(() => {
          this.$modal.msgSuccess("批次已删除");
          this.load();
        })
        .catch(() => {});
    },
    saveHeader() {
      updatePurchaseHeader({
        id: this.summary.purchaseId,
        supplierName: this.header.supplierName,
        purchaser: this.header.purchaser,
        remark: this.header.remark,
      }).then(() => {
        this.$modal.msgSuccess("单头已保存");
        this.load();
      });
    },
    handleConfirm() {
      this.$modal
        .confirm(`确认采购单【${this.summary.code || ""}】？确认后批次不可增删（可走调整成本）。`)
        .then(() => confirmPurchase(this.summary.purchaseId))
        .then(() => {
          this.$modal.msgSuccess("已确认（成本待确认）");
          this.load();
        })
        .catch(() => {});
    },
    handleStockIn() {
      this.$modal
        .confirm("确认入库？入库后成本冻结（已确认成本），月结后需走下月调整单。")
        .then(() => stockInPurchase(this.summary.purchaseId))
        .then(() => {
          this.$modal.msgSuccess("已入库（成本已确认）");
          this.load();
        })
        .catch(() => {});
    },
  },
};
</script>

<style lang="scss" scoped>
.purchase-day-page {
  .pd-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 10px;

    .pd-toolbar-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .pd-toolbar-right {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .pd-code {
      color: #909399;
      font-size: 13px;
    }
  }
  .pd-stats {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding: 8px 12px;
    margin-bottom: 8px;
    background: #f5f7fa;
    border-radius: 4px;
    font-size: 13px;
    color: #606266;

    b {
      color: #303133;
    }
    .pd-ok {
      color: #67c23a;
    }
    .pd-warn {
      color: #e6a23c;
    }
    .pd-bad {
      color: #f56c6c;
    }
    .pd-amount {
      color: #409eff;
    }
  }
  .pd-head {
    margin-bottom: 4px;

    :deep(.el-form-item) {
      margin-bottom: 8px;
    }
  }
  .pd-hint {
    margin-bottom: 8px;
  }
  .pd-inline-entry {
    display: flex;
    align-items: center;
    gap: 4px;

    .pd-entry-qty {
      width: 72px;
    }
    .pd-entry-price {
      width: 76px;
    }
    .pd-entry-supplier {
      width: 100px;
    }
  }
  .pd-orphan-tip {
    color: #c0c4cc;
  }
  .pd-batches {
    padding: 6px 12px;
  }
  .pd-empty {
    color: #909399;
    font-size: 12px;
    padding: 6px 0;
  }
  .pd-footer {
    margin-top: 10px;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    font-size: 13px;
    color: #606266;

    .pd-add-product {
      margin-right: auto;
    }
  }
  .pd-picker-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;

    .pd-picker-hint {
      font-size: 12px;
      color: #909399;
    }
  }
  .pd-picker-empty {
    margin-top: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #909399;
  }
}
:deep(.pd-row-over) td.el-table__cell {
  background-color: #fef0f0 !important;
}
</style>
