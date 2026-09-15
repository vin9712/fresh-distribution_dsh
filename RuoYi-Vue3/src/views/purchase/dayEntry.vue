<template>
  <el-dialog
    :model-value="visible"
    title="采购录入（当日应采汇总 + 分批成本）"
    width="96%"
    top="4vh"
    append-to-body
    destroy-on-close
    @update:model-value="close"
  >
    <!-- 操作条 -->
    <div class="pd-bar">
      <el-form :inline="true" size="small" class="pd-bar-form">
        <el-form-item label="采购日期">
          <el-date-picker
            v-model="date"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            :disabled="loading"
            @change="load"
          />
        </el-form-item>
        <el-form-item label="单号">
          <span>{{ summary.code || "（首次录入自动创建）" }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <dict-tag v-if="summary.status != null" :options="dict.type.t_purchase_order_status" :value="summary.status" />
          <el-tag v-else type="info" size="small" effect="plain">未建单</el-tag>
        </el-form-item>
      </el-form>
      <div class="pd-bar-actions">
        <el-button type="primary" plain size="small" :icon="Plus" :disabled="!canEdit" @click="openBulk">批量录入</el-button>
        <el-button size="small" :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="success" size="small" :icon="Check" :disabled="!canConfirm" @click="handleConfirm">确认采购单</el-button>
        <el-button type="warning" size="small" :icon="Select" :disabled="!canStockIn" @click="handleStockIn">入库（确认成本）</el-button>
      </div>
    </div>

    <!-- 统计条 -->
    <div class="pd-stats">
      <span>应采品种 <b>{{ summary.requiredItemCount || 0 }}</b></span>
      <span>已录品种 <b class="ok">{{ summary.purchasedItemCount || 0 }}</b></span>
      <span>未录品种 <b class="warn">{{ Math.max(0, (summary.requiredItemCount || 0) - (summary.purchasedItemCount || 0)) }}</b></span>
      <el-divider direction="vertical" />
      <span>应采总量 <b>{{ num(summary.requiredQty) }}</b></span>
      <span>已采总量 <b class="ok">{{ num(summary.purchasedQty) }}</b></span>
      <span>待采总量 <b :class="{ bad: Number(summary.pendingQty) < 0 }">{{ num(summary.pendingQty) }}</b></span>
      <el-divider direction="vertical" />
      <span>当日采购总额 <b class="amount">¥ {{ money(summary.totalAmount) }}</b></span>
      <span v-if="summary.overCount > 0" class="bad">超采 {{ summary.overCount }} 行（不影响下单与验收）</span>
    </div>

    <!-- 单头默认供应商/采购员（草稿可改；已确认走供应商补录） -->
    <div v-if="canEdit" class="pd-head">
      <el-form :inline="true" size="small">
        <el-form-item label="默认供应商">
          <el-input v-model="header.supplierName" placeholder="可空，批次可覆盖" style="width: 190px" />
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

    <!-- 汇总表 -->
    <el-table
      v-loading="loading"
      :data="summary.rows || []"
      size="small"
      border
      max-height="540"
      :row-class-name="rowClass"
      empty-text="该采购日期没有应采商品（订单明细为空）"
    >
      <el-table-column type="expand">
        <template #default="scope">
          <div class="pd-batches">
            <el-table :data="scope.row.batches" size="small" border>
              <el-table-column label="批次" prop="batchNo" width="70" align="center" />
              <el-table-column label="数量" width="100" align="right">
                <template #default="b">{{ num(b.row.quantity) }}</template>
              </el-table-column>
              <el-table-column label="进货价" width="100" align="right">
                <template #default="b">{{ money(b.row.unitPrice) }}</template>
              </el-table-column>
              <el-table-column label="小计" width="110" align="right">
                <template #default="b">{{ money(b.row.subtotal) }}</template>
              </el-table-column>
              <el-table-column label="供应商" prop="supplierName" min-width="130" show-overflow-tooltip />
              <el-table-column label="录入人" prop="createBy" width="100" align="center" />
              <el-table-column label="录入时间" prop="createTime" width="160" align="center" />
              <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
              <el-table-column v-if="canEdit" label="操作" width="110" align="center" fixed="right">
                <template #default="b">
                  <el-button link size="small" type="primary" @click="openEditBatch(scope.row, b.row)">改</el-button>
                  <el-button link size="small" type="danger" @click="removeBatch(scope.row, b.row)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!scope.row.batches || !scope.row.batches.length" class="pd-empty">暂无进货批次</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="商品" prop="productName" min-width="150" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.productName }}
          <el-tag v-if="scope.row.orphan" size="small" type="danger" effect="plain">订单已撤回</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规格" prop="productSpec" width="110" show-overflow-tooltip />
      <el-table-column label="单位" prop="productUnit" width="70" align="center" />
      <el-table-column label="应采" width="90" align="right">
        <template #default="scope">{{ num(scope.row.requiredQty) }}</template>
      </el-table-column>
      <el-table-column label="已采" width="90" align="right">
        <template #default="scope">{{ num(scope.row.purchasedQty) }}</template>
      </el-table-column>
      <el-table-column label="待采" width="90" align="right">
        <template #default="scope">
          <span :class="{ bad: Number(scope.row.pendingQty) < 0 }">{{ num(scope.row.pendingQty) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="批次数" prop="batchCount" width="80" align="center" />
      <el-table-column label="加权均价" width="100" align="right">
        <template #default="scope">
          {{ Number(scope.row.purchasedQty) > 0 ? Number(scope.row.avgPrice).toFixed(4) : "—" }}
        </template>
      </el-table-column>
      <el-table-column label="采购金额" width="110" align="right">
        <template #default="scope">{{ money(scope.row.amount) }}</template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="90" align="center" fixed="right">
        <template #default="scope">
          <el-button
            link
            size="small"
            type="primary"
            :icon="Plus"
            :disabled="scope.row.orphan"
            @click="openAddBatch(scope.row)"
          >录入</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 单行录入/修改 -->
    <el-dialog v-model="batchOpen" :title="batchForm.itemId ? '修改批次' : '录入进货批次'" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="商品">
          <span>{{ batchForm.productName }} {{ batchForm.productSpec }} / {{ batchForm.productUnit }}</span>
        </el-form-item>
        <el-form-item label="应采数量"><span>{{ num(batchForm.requiredQty) }}</span></el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="batchForm.quantity" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="进货价">
          <el-input-number v-model="batchForm.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="本批供应商">
          <el-input v-model="batchForm.supplierName" placeholder="可空（同一供应商同一天可多次录入）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" placeholder="发票号/车次等" />
        </el-form-item>
        <el-form-item label="小计">
          <b>¥ {{ batchSubtotal }}</b>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="submitBatch">保存</el-button>
        <el-button @click="batchOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 批量录入 -->
    <el-drawer v-model="bulkOpen" title="批量录入采购批次" size="840px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="商品只能从当日订单商品（应采清单）中选择；同一商品可多行录入不同进货价/供应商。"
        style="margin-bottom: 10px"
      />
      <el-table :data="bulkRows" size="small" border max-height="440">
        <el-table-column label="商品" min-width="230">
          <template #default="scope">
            <el-select v-model="scope.row.key" filterable placeholder="选择当日订单商品" style="width: 100%">
              <el-option v-for="r in selectableRows" :key="r.key" :label="optionLabel(r)" :value="r.key" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="scope">
            <el-input-number v-model="scope.row.quantity" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="进货价" width="120">
          <template #default="scope">
            <el-input-number v-model="scope.row.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="150">
          <template #default="scope"><el-input v-model="scope.row.supplierName" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="scope"><el-input v-model="scope.row.remark" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="scope">
            <el-button link type="danger" :icon="Delete" @click="bulkRows.splice(scope.$index, 1)" />
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 8px">
        <el-button type="primary" plain size="small" :icon="Plus" @click="addBulkRow">添加一行</el-button>
      </div>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="submitBulk">提交（{{ bulkRows.length }} 行）</el-button>
        <el-button @click="bulkOpen = false">取消</el-button>
      </template>
    </el-drawer>
  </el-dialog>
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
import { Plus, Delete, Refresh, Check, Select } from "@element-plus/icons-vue";

export default {
  name: "PurchaseDayEntry",
  dicts: ["t_purchase_order_status"],
  props: {
    modelValue: { type: Boolean, default: false },
    orderDate: { type: String, default: null },
  },
  emits: ["update:modelValue", "changed"],
  setup() {
    return { Plus, Delete, Refresh, Check, Select };
  },
  data() {
    return {
      loading: false,
      saving: false,
      date: null,
      summary: { rows: [], batches: [] },
      header: { supplierName: "", purchaser: "", remark: "" },
      batchOpen: false,
      batchForm: {},
      bulkOpen: false,
      bulkRows: [],
    };
  },
  computed: {
    visible() {
      return this.modelValue;
    },
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
    batchSubtotal() {
      const q = Number(this.batchForm.quantity) || 0;
      const p = Number(this.batchForm.unitPrice) || 0;
      return (q * p).toFixed(2);
    },
    /** 批量录入可选商品：当日订单商品（排除订单已撤回的遗留行） */
    selectableRows() {
      return (this.summary.rows || []).filter((r) => !r.orphan);
    },
  },
  watch: {
    modelValue(v) {
      if (v) {
        this.date = this.orderDate || this.defaultDate();
        this.load();
      }
    },
  },
  methods: {
    close(v) {
      this.$emit("update:modelValue", v);
    },
    /** 默认采购日期：明日（对齐「当晚录次日配送」节奏）。手动格式化，避免 toISOString 的 UTC 时区偏移（东八区 0-8 点会错成今天） */
    defaultDate() {
      const d = new Date();
      d.setDate(d.getDate() + 1);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${y}-${m}-${day}`;
    },
    num(v) {
      if (v === null || v === undefined || v === "") return "0";
      return Number(v).toString();
    },
    money(v) {
      return (Number(v) || 0).toFixed(2);
    },
    optionLabel(r) {
      const spec = r.productSpec ? " " + r.productSpec : "";
      const unit = r.productUnit ? " / " + r.productUnit : "";
      return r.productName + spec + unit + "（应采 " + this.num(r.requiredQty) + "）";
    },
    rowClass({ row }) {
      return Number(row.pendingQty) < 0 ? "pd-row-over" : "";
    },
    load() {
      if (!this.date) return;
      this.loading = true;
      daySummary(this.date)
        .then((res) => {
          this.summary = res.data || { rows: [] };
          this.header = {
            supplierName: this.summary.supplierName || "",
            purchaser: this.summary.purchaser || "",
            remark: this.summary.remark || "",
          };
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
      return dayOrder(this.date).then((res) => {
        this.summary.purchaseId = res.data.id;
        this.summary.code = res.data.code;
        this.summary.status = res.data.status;
        return res.data.id;
      });
    },
    openAddBatch(row) {
      this.batchForm = {
        itemId: null,
        key: row.key,
        skuId: row.skuId,
        productName: row.productName,
        productSpec: row.productSpec,
        productUnit: row.productUnit,
        requiredQty: row.requiredQty,
        quantity: undefined,
        unitPrice: undefined,
        supplierName: this.header.supplierName || "",
        remark: "",
      };
      this.batchOpen = true;
    },
    openEditBatch(row, batch) {
      this.batchForm = {
        itemId: batch.id,
        key: row.key,
        skuId: row.skuId,
        productName: row.productName,
        productSpec: row.productSpec,
        productUnit: row.productUnit,
        requiredQty: row.requiredQty,
        quantity: Number(batch.quantity),
        unitPrice: Number(batch.unitPrice),
        supplierName: batch.supplierName || "",
        remark: batch.remark || "",
      };
      this.batchOpen = true;
    },
    batchPayload() {
      return {
        skuId: this.batchForm.skuId,
        productName: this.batchForm.productName,
        productSpec: this.batchForm.productSpec,
        productUnit: this.batchForm.productUnit,
        quantity: Number(this.batchForm.quantity),
        unitPrice: Number(this.batchForm.unitPrice),
        supplierName: this.batchForm.supplierName || null,
        remark: this.batchForm.remark || null,
      };
    },
    submitBatch() {
      if (!(Number(this.batchForm.quantity) > 0)) {
        this.$modal.msgError("请输入大于 0 的采购数量");
        return;
      }
      if (this.batchForm.unitPrice === undefined || this.batchForm.unitPrice === null || Number(this.batchForm.unitPrice) < 0) {
        this.$modal.msgError("请输入进货价");
        return;
      }
      this.saving = true;
      const done = () => {
        this.saving = false;
        this.batchOpen = false;
        this.load();
        this.$emit("changed");
      };
      this.ensurePurchaseId()
        .then((purchaseId) => {
          const payload = this.batchPayload();
          if (this.batchForm.itemId) {
            return updateBatch(purchaseId, this.batchForm.itemId, payload);
          }
          return addBatch(purchaseId, payload);
        })
        .then(() => {
          this.$modal.msgSuccess("已保存");
          done();
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
          this.$modal.msgSuccess("已删除");
          this.load();
          this.$emit("changed");
        })
        .catch(() => {});
    },
    openBulk() {
      this.bulkRows = [];
      this.addBulkRow();
      this.bulkOpen = true;
    },
    addBulkRow() {
      this.bulkRows.push({
        key: null,
        quantity: undefined,
        unitPrice: undefined,
        supplierName: this.header.supplierName || "",
        remark: "",
      });
    },
    submitBulk() {
      if (!this.bulkRows.length) {
        this.$modal.msgError("请至少添加一行");
        return;
      }
      const rows = [];
      for (const r of this.bulkRows) {
        if (!r.key) {
          this.$modal.msgError("请为每一行选择商品");
          return;
        }
        if (!(Number(r.quantity) > 0)) {
          this.$modal.msgError("每一行都必须填写大于 0 的数量");
          return;
        }
        if (r.unitPrice === undefined || r.unitPrice === null) {
          this.$modal.msgError("每一行都必须填写进货价");
          return;
        }
        const target = (this.summary.rows || []).find((x) => x.key === r.key);
        if (!target) {
          this.$modal.msgError("所选商品不在当日订单商品内");
          return;
        }
        rows.push({
          skuId: target.skuId,
          productName: target.productName,
          productSpec: target.productSpec,
          productUnit: target.productUnit,
          quantity: Number(r.quantity),
          unitPrice: Number(r.unitPrice),
          supplierName: r.supplierName || null,
          remark: r.remark || null,
        });
      }
      this.saving = true;
      this.ensurePurchaseId()
        .then((purchaseId) => addBatchBulk(purchaseId, rows))
        .then((res) => {
          this.$modal.msgSuccess(`已录入 ${res.data} 行批次`);
          this.saving = false;
          this.bulkOpen = false;
          this.load();
          this.$emit("changed");
        })
        .catch(() => {
          this.saving = false;
        });
    },
    saveHeader() {
      // supplierId 必须原样回传：updatePurchaseHeader 是全字段 SET，缺了会把已补录的供应商 ID 抹成 NULL
      updatePurchaseHeader({
        id: this.summary.purchaseId,
        supplierId: this.summary.supplierId ?? null,
        supplierName: this.header.supplierName,
        purchaser: this.header.purchaser,
        remark: this.header.remark,
      }).then(() => {
        this.$modal.msgSuccess("单头已保存");
        this.load();
        this.$emit("changed");
      });
    },
    handleConfirm() {
      this.$modal
        .confirm(`确认采购单【${this.summary.code || ""}】？确认后批次不可增删（可走调整成本）。`)
        .then(() => confirmPurchase(this.summary.purchaseId))
        .then(() => {
          this.$modal.msgSuccess("已确认（成本待确认）");
          this.load();
          this.$emit("changed");
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
          this.$emit("changed");
        })
        .catch(() => {});
    },
  },
};
</script>

<style lang="scss" scoped>
.pd-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  .pd-bar-form {
    margin-bottom: 0;
  }
  .pd-bar-actions {
    display: flex;
    gap: 6px;
    padding-top: 2px;
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
  b.ok {
    color: #67c23a;
  }
  b.warn {
    color: #e6a23c;
  }
  b.bad,
  .bad {
    color: #f56c6c;
  }
  b.amount {
    color: #409eff;
  }
}
.pd-head {
  margin-bottom: 6px;

  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
}
.pd-batches {
  padding: 6px 12px;
}
.pd-empty {
  color: #909399;
  font-size: 12px;
  padding: 6px 0;
}
:deep(.pd-row-over) td.el-table__cell {
  background-color: #fef0f0 !important;
}
</style>