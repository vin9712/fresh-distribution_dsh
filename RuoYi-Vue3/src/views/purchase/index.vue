<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="采购单号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入采购单号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="采购日期" prop="dateRange">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择状态"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="dict in dict.type.t_purchase_order_status"
            :key="dict.value"
            :label="dict.label"
            :value="parseInt(dict.value)"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          :icon="EditPen"
          size="small"
          @click="handleDayEntry(null)"
          v-hasPermi="['purchase:add']"
          >采购录入</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Select"
          size="small"
          :disabled="multiple || !stockableSelection"
          @click="handleBatchStockIn"
          v-hasPermi="['purchase:edit']"
          >批量入库（确认成本）</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          :icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['purchase:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- D-056：采购单主体 = 采购日期（=配送日期），一天一单；行=进货批次，成本按批次加权 -->
    <el-table v-loading="loading" :data="purchaseList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="采购单号" align="center" prop="code" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="采购日期" align="center" prop="orderDate" width="110" />
      <el-table-column label="已采 / 应采" align="center" width="150">
        <template #default="scope">
          <span>{{ num(scope.row.purchasedQty) }} / {{ num(scope.row.requiredQty) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="批次数" align="center" prop="batchCount" width="80" />
      <el-table-column label="供应商名称" align="center" prop="supplierName" :show-overflow-tooltip="true" />
      <el-table-column label="采购员" align="center" prop="purchaser" :show-overflow-tooltip="true" width="90" />
      <el-table-column label="采购总额" align="center" prop="totalAmount" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="dict.type.t_purchase_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- S2-2.2 成本状态提示：已确认=到货/待确认成本；已入库=已确认成本 -->
      <el-table-column label="成本状态" align="center" width="120">
        <template #default="scope">
          <el-tooltip
            :disabled="scope.row.status !== 1"
            content="录入已完成，成本待入库确认；确认后计入已确认成本"
            placement="top"
          >
            <el-tag v-if="scope.row.status === 1" type="warning" size="small">待确认成本</el-tag>
            <el-tag v-else-if="scope.row.status === 2" type="success" size="small">已确认成本</el-tag>
            <span v-else>—</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="360">
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="EditPen"
            @click="handleDayEntry(scope.row)"
            v-hasPermi="['purchase:list']"
            >{{ scope.row.status === 0 ? "录入" : "查看" }}</el-button
          >
          <el-button
            size="small"
            link
            :icon="Check"
            @click="handleConfirm(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['purchase:edit']"
            >确认</el-button
          >
          <el-button
            size="small"
            link
            :icon="Select"
            @click="handleStockIn(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['purchase:edit']"
            >入库</el-button
          >
          <el-button
            size="small"
            link
            :icon="Edit"
            @click="handleAdjustCost(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['purchase:edit']"
            >调整成本</el-button
          >
          <el-button
            size="small"
            link
            :icon="OfficeBuilding"
            @click="handleBackfillSupplier(scope.row)"
            v-if="scope.row.status === 0 || scope.row.status === 1"
            v-hasPermi="['purchase:edit']"
            >供应商补录</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['purchase:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 采购录入（日应采汇总 + 分批成本）：新增/查看当日采购单 -->
    <purchase-day-entry v-model="dayEntryOpen" :order-date="dayEntryDate" @changed="getList" />

    <!-- S2-2.2 成本调整抽屉（W0-2.5 前端）：已确认采购单逐行调整数量/成本，禁止增删行 -->
    <el-drawer
      v-model="adjustOpen"
      :title="'调整采购成本（' + (adjustOrder.code || '') + '）'"
      size="680px"
      append-to-body
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅允许修改数量与采购单价（禁止增删批次）；保存后重算总额并写入调整审计（前后金额 + 明细快照）。"
        style="margin-bottom: 12px"
      />
      <el-table :data="adjustItems" size="small" border max-height="480">
        <el-table-column label="批次" align="center" prop="batchNo" width="60" />
        <el-table-column label="商品名称" align="center" prop="productName" min-width="130" />
        <el-table-column label="规格" align="center" prop="productSpec" width="90" />
        <el-table-column label="单位" align="center" prop="productUnit" width="60" />
        <el-table-column label="数量" align="center" width="120">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.quantity"
              :min="0"
              :precision="2"
              :controls="false"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="120">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.unitPrice"
              :min="0"
              :precision="2"
              :controls="false"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" align="center" width="100">
          <template #default="scope">
            <span>{{ ((Number(scope.row.quantity) || 0) * (Number(scope.row.unitPrice) || 0)).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 10px; text-align: right">
        调整后总额：¥ {{ adjustTotal }}
        <span v-if="adjustTotal !== adjustBeforeTotal" style="color: #e6a23c; margin-left: 8px">
          （原 {{ adjustBeforeTotal }}）
        </span>
      </div>
      <template #footer>
        <el-button type="primary" :loading="adjustSaving" @click="submitAdjustCost">保存调整</el-button>
        <el-button @click="adjustOpen = false">取 消</el-button>
      </template>
    </el-drawer>

    <!-- S2-2.2 供应商补录：草稿/已确认采购单补录供应商与采购员 -->
    <el-dialog v-model="supplierOpen" title="供应商补录" width="440px" append-to-body>
      <div style="margin-bottom: 8px; color: #909399; font-size: 12px">
        采购单 {{ supplierForm.code }}：默认可空，批次行可单独录供应商；补录后写入操作日志。
      </div>
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <el-input v-model="supplierForm.supplierName" placeholder="供应商名称（直填）" />
        </el-form-item>
        <el-form-item label="采购员">
          <el-input v-model="supplierForm.purchaser" placeholder="采购员姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitBackfillSupplier">保存</el-button>
        <el-button @click="supplierOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  listPurchase,
  getPurchaseItems,
  confirmPurchase,
  stockInPurchase,
  batchStockInPurchase,
  adjustPurchase,
  backfillSupplier,
  delPurchase,
} from "@/api/purchase/purchase";
import PurchaseDayEntry from "./dayEntry.vue";
import { Search, Refresh, Delete, Edit, Check, Select, OfficeBuilding, EditPen } from "@element-plus/icons-vue";

export default {
  name: "Purchase",
  components: { PurchaseDayEntry },
  dicts: ["t_purchase_order_status"],
  setup() {
    return { Search, Refresh, Delete, Edit, Check, Select, OfficeBuilding, EditPen };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 采购单表格数据
      purchaseList: [],
      // 采购日期范围
      dateRange: [],
      // 查询参数
      queryParams: {
        code: null,
        status: null,
        beginOrderDate: null,
        endOrderDate: null,
      },
      // 采购录入（日应采汇总）
      dayEntryOpen: false,
      dayEntryDate: null,
      // S2-2.2 成本调整抽屉
      adjustOpen: false,
      adjustOrder: {},
      adjustItems: [],
      adjustBeforeTotal: "0.00",
      adjustSaving: false,
      // S2-2.2 供应商补录
      supplierOpen: false,
      supplierForm: {},
    };
  },
  computed: {
    /** S2-2.2：勾选行是否全部为已确认（可批量入库/确认成本） */
    stockableSelection() {
      const rows = this.purchaseList.filter((r) => this.ids.includes(r.id));
      return rows.length > 0 && rows.every((r) => r.status === 1);
    },
    /** S2-2.2：调整后总额实时重算 */
    adjustTotal() {
      return this.adjustItems
        .reduce((sum, it) => sum + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
        .toFixed(2);
    },
  },
  created() {
    this.getList();
  },
  methods: {
    num(v) {
      if (v === null || v === undefined || v === "") return "0";
      return Number(v).toString();
    },
    /** 查询采购单列表 */
    getList() {
      this.loading = true;
      this.queryParams.beginOrderDate = this.dateRange && this.dateRange.length === 2 ? this.dateRange[0] : null;
      this.queryParams.endOrderDate = this.dateRange && this.dateRange.length === 2 ? this.dateRange[1] : null;
      listPurchase(this.queryParams).then((response) => {
        this.purchaseList = response.data || [];
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
    },
    /** 采购录入：打开当日应采汇总页（row 为空=默认明日） */
    handleDayEntry(row) {
      this.dayEntryDate = row ? row.orderDate : null;
      this.dayEntryOpen = true;
    },
    /** 确认按钮操作 */
    handleConfirm(row) {
      this.$modal
        .confirm('是否确认采购单【' + row.code + '】？确认后批次不可增删。')
        .then(() => {
          return confirmPurchase(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("确认成功");
        })
        .catch(() => {});
    },
    /** 入库按钮操作 */
    handleStockIn(row) {
      this.$modal
        .confirm('是否确认采购单【' + row.code + '】入库？')
        .then(() => {
          return stockInPurchase(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("入库成功");
        })
        .catch(() => {});
    },
    /** S2-2.2 批量入库（确认成本）：勾选行均为已确认时可用 */
    handleBatchStockIn() {
      this.$modal
        .confirm(`是否将勾选的 ${this.ids.length} 张已确认采购单批量入库（确认成本）？`)
        .then(() => {
          return batchStockInPurchase(this.ids);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("批量入库成功");
        })
        .catch(() => {});
    },
    /** S2-2.2 调整成本（W0-2.5 前端）：打开抽屉加载既有批次，仅改数量/单价 */
    handleAdjustCost(row) {
      getPurchaseItems(row.id).then((response) => {
        this.adjustOrder = row;
        this.adjustItems = (response.data || []).map((it) => ({ ...it }));
        this.adjustBeforeTotal = Number(row.totalAmount || 0).toFixed(2);
        this.adjustOpen = true;
      });
    },
    /** S2-2.2 提交成本调整：仅传行 id/数量/单价（后端校验禁止增删行并写审计） */
    submitAdjustCost() {
      const items = this.adjustItems.map((it) => ({
        id: it.id,
        quantity: Number(it.quantity) || 0,
        unitPrice: Number(it.unitPrice) || 0,
      }));
      this.adjustSaving = true;
      adjustPurchase(this.adjustOrder.id, { items })
        .then(() => {
          this.$modal.msgSuccess("调整成功，已写入审计日志");
          this.adjustOpen = false;
          this.getList();
        })
        .catch(() => {})
        .finally(() => {
          this.adjustSaving = false;
        });
    },
    /** S2-2.2 供应商补录：草稿/已确认采购单补录供应商与采购员 */
    handleBackfillSupplier(row) {
      this.supplierForm = {
        id: row.id,
        code: row.code,
        supplierName: row.supplierName,
        purchaser: row.purchaser,
      };
      this.supplierOpen = true;
    },
    submitBackfillSupplier() {
      if (!this.supplierForm.supplierName && !this.supplierForm.purchaser) {
        this.$modal.msgWarning("供应商与采购员至少填写其一");
        return;
      }
      backfillSupplier(this.supplierForm.id, {
        supplierName: this.supplierForm.supplierName,
        purchaser: this.supplierForm.purchaser,
      }).then(() => {
        this.$modal.msgSuccess("补录成功");
        this.supplierOpen = false;
        this.getList();
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除采购单编号为"' + ids + '"的数据项？')
        .then(() => {
          return delPurchase(ids);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
  },
};
</script>
