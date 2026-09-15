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
        </template>
      </el-table-column>
      <el-table-column label="规格" prop="productSpec" width="100" :show-overflow-tooltip="true" />
      <el-table-column label="单位" prop="productUnit" width="60" align="center" />
      <el-table-column label="应采" width="80" align="right">
        <template #default="scope">{{ num(scope.row.requiredQty) }}</template>
      </el-table-column>
      <el-table-column label="已采" width="80" align="right">
        <template #default="scope">{{ num(scope.row.purchasedQty) }}</template>
      </el-table-column>
      <el-table-column label="待采" width="80" align="right">
        <template #default="scope">
          <span :class="{ 'pd-bad': Number(scope.row.pendingQty) < 0 }">{{ num(scope.row.pendingQty) }}</span>
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
          <div v-if="!scope.row.orphan" class="pd-inline-entry">
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
      <span>已填进货价 {{ filledCount }} 行（数量默认=待采数量）；回车逐行保存，或 Ctrl+Enter 一次提交。</span>
      <el-button type="primary" :icon="Select" :disabled="filledCount === 0" :loading="saving" @click="submitAll">
        提交录入（{{ filledCount }} 行）
      </el-button>
    </div>
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
} from "@/api/purchase/purchase";
import { SCOPE, registerShortcuts } from "@/utils/shortcut";
import { Plus, Delete, Refresh, Check, Select, Back } from "@element-plus/icons-vue";

export default {
  name: "PurchaseDay",
  dicts: ["t_purchase_order_status"],
  setup() {
    return { Plus, Delete, Refresh, Check, Select, Back };
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
    /** 未提交的录入内容（已填进货价，或展开行改过批次未保存） */
    hasUnsavedDraft() {
      return this.canEdit && (this.filledCount > 0 || this.batchDirty);
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
  /** 路由离开守卫：有未提交的录入内容（已填进货价/改批次未保存）先确认，避免丢数据 */
  beforeRouteLeave(to, from, next) {
    if (this._allowLeave || !this.hasUnsavedDraft) {
      next();
      return;
    }
    this.$modal
      .confirm("采购录入有未提交的内容（已填进货价或改动批次未保存），离开将丢失。确定离开吗？", "未保存提醒", {
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
      return Number(row.pendingQty) < 0 ? "pd-row-over" : "";
    },
    onExpandChange(row, expandedRows) {
      this.expandedKeys = (expandedRows || []).map((r) => r.key);
    },
    /** 展开行批次改动标记（保存成功后 load() 会清掉） */
    markBatchDirty() {
      this.batchDirty = true;
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
      this.$router.push("/purchase");
    },
    load(focusKey) {
      if (!this.orderDate) return;
      this.loading = true;
      daySummary(this.orderDate)
        .then((res) => {
          this.summary = res.data || { rows: [] };
          this.header = {
            supplierName: this.summary.supplierName || "",
            purchaser: this.summary.purchaser || "",
            remark: this.summary.remark || "",
          };
          // 行内录入初始槽：数量默认=待采数量（未录时即订单总数）；进货价必须手填；供应商默认带单头
          this.batchDirty = false;
          (this.summary.rows || []).forEach((r) => {
            const pending = Number(r.pendingQty);
            r._entry = {
              quantity: pending > 0 ? pending : null,
              unitPrice: null,
              supplierName: this.header.supplierName,
            };
          });
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
  }
}
:deep(.pd-row-over) td.el-table__cell {
  background-color: #fef0f0 !important;
}
</style>
