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
      <el-form-item label="验收单号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入验收单号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="dict in dict.type.t_acceptance_status"
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
          :icon="Plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['acceptance:add']"
          >新增验收单</el-button
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
          v-hasPermi="['acceptance:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="acceptanceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="验收单号" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="送货单号" align="center" prop="deliveryCode" :show-overflow-tooltip="true" />
      <el-table-column label="客户" align="center" prop="customerName" />
      <el-table-column label="配送点" align="center" prop="customerDeptName" />
      <el-table-column label="验收日期" align="center" prop="acceptDate" width="120" />
      <el-table-column label="验收总额" align="center" prop="totalAmount" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="dict.type.t_acceptance_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="250">
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['acceptance:query']"
            >明细</el-button
          >
          <el-button
            size="small"
            link
            :icon="Edit"
            v-if="scope.row.status === 0"
            @click="handleEdit(scope.row)"
            v-hasPermi="['acceptance:edit']"
            >录入</el-button
          >
          <el-button
            size="small"
            link
            :icon="Check"
            v-if="scope.row.status === 0"
            @click="handleSubmit(scope.row)"
            v-hasPermi="['acceptance:submit']"
            >提交</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            v-if="scope.row.status === 0"
            @click="handleDelete(scope.row)"
            v-hasPermi="['acceptance:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 新增验收单：选择已送达送货单（一单一验） -->
    <el-dialog :title="addTitle" v-model="addOpen" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="送货单">
          <el-select v-model="addForm.deliveryOrderId" placeholder="请选择已送达的送货单" filterable style="width: 100%">
            <el-option
              v-for="d in deliveryOptions"
              :key="d.id"
              :label="d.code + '（' + (d.customerName || d.customerId) + ' / ' + d.customerDeptName + ' / ' + d.deliveryDate + '）'"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <div class="hint">仅列出"已送达"且未生成过验收单的送货单（一单一验）。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAddForm">确 定</el-button>
          <el-button @click="addOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 验收明细对话框（查看/录入共用，录入时实收数量与损耗原因可编辑） -->
    <el-dialog :title="detailTitle" v-model="detailOpen" width="900px" append-to-body>
      <template #header>
        <div class="detail-dialog-header">
          <span class="detail-dialog-title">{{ detailTitle }}</span>
          <el-button
            v-if="editMode && detailList.length"
            type="primary"
            plain
            size="small"
            :icon="Iphone"
            @click="toggleMobileMode"
            >{{ mobileMode ? '退出移动模式' : '移动模式' }}</el-button
          >
        </div>
      </template>

      <!-- 标准模式（表格录入） -->
      <template v-if="!mobileMode">
        <el-form v-if="editMode" label-width="90px" :inline="true" size="small">
        <el-form-item label="验收日期">
          <el-date-picker
            v-model="editForm.acceptDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择验收日期"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="备注" style="width: 240px" />
        </el-form-item>
      </el-form>
      <el-table :data="detailList" size="small" border>
        <el-table-column label="商品名称" align="center" prop="productName" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" width="120" />
        <el-table-column label="单位" align="center" prop="productUnit" width="70" />
        <el-table-column label="送货数量" align="center" prop="deliveredQuantity" width="90" />
        <el-table-column label="实收数量" align="center" width="140">
          <template #default="scope">
            <el-input-number
              v-if="editMode"
              v-model="scope.row.actualQuantity"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
            <span v-else>{{ scope.row.actualQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" prop="unitPrice" width="90" />
        <el-table-column label="损耗" align="center" width="100">
          <template #default="scope">
            <span :class="{ 'loss-negative': lossOf(scope.row) < 0 }">{{ lossOf(scope.row).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="损耗原因（负损耗必填）" align="center" min-width="160">
          <template #default="scope">
            <el-input v-if="editMode" v-model="scope.row.lossReason" placeholder="损耗为负时必填" />
            <span v-else>{{ scope.row.lossReason }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实收金额" align="center" width="110">
          <template #default="scope">{{ amountOf(scope.row).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
        <div class="detail-total">合计：{{ totalAmount.toFixed(2) }}</div>
      </template>

      <!-- 移动模式：仅商品名称+实收数量大输入，每屏 3 条，扫码枪连续扫码定位并累加 -->
      <template v-else>
        <div class="mobile-scan-bar">
          <el-input
            ref="scanInput"
            v-model="scanCode"
            placeholder="扫码枪扫码定位商品（回车确认，支持 码*数量）"
            clearable
            size="large"
            @keyup.enter="handleScan"
          >
            <template #append>
              <el-button :icon="Search" @click="handleScan">定位</el-button>
            </template>
          </el-input>
        </div>
        <div class="mobile-list">
          <div
            v-for="item in mobilePageItems"
            :key="item.id"
            class="mobile-card"
            :class="{
              'mobile-card-active': item.id === mobileActiveId,
              'mobile-card-ok': Number(item.actualQuantity) >= Number(item.deliveredQuantity),
            }"
          >
            <div class="mobile-card-name">
              <span class="mobile-card-name-text">{{ item.productName }}</span>
              <el-tag v-if="item.productSpec" size="small" type="info">{{ item.productSpec }}</el-tag>
              <span class="mobile-card-unit">{{ item.productUnit }}</span>
            </div>
            <div class="mobile-card-qty">
              <span class="mobile-qty-label">实收</span>
              <el-input-number
                v-model="item.actualQuantity"
                :min="0"
                :precision="2"
                :controls="false"
                class="mobile-qty-input"
                size="large"
              />
              <span class="mobile-qty-unit">{{ item.productUnit }}</span>
              <span class="mobile-qty-deliver">送货 {{ item.deliveredQuantity }}</span>
            </div>
          </div>
          <div v-if="!mobilePageItems.length" class="mobile-empty">本页暂无商品</div>
        </div>
        <div class="mobile-pager">
          <el-button size="small" :icon="ArrowLeft" :disabled="mobilePage <= 0" @click="mobilePage--">上一页</el-button>
          <span class="mobile-page-info">{{ mobilePage + 1 }} / {{ mobileTotalPages }}</span>
          <el-button size="small" :disabled="mobilePage >= mobileTotalPages - 1" @click="mobilePage++">下一页</el-button>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="editMode" type="primary" @click="submitEditForm">保 存</el-button>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageAcceptance,
  listAcceptanceItems,
  createAcceptance,
  updateAcceptance,
  submitAcceptance,
  delAcceptance,
} from "@/api/acceptance/acceptance";
import { listDelivery } from "@/api/order/delivery";
import { Search, Refresh, Plus, Delete, Edit, Check, View, Iphone, ArrowLeft } from "@element-plus/icons-vue";

export default {
  name: "Acceptance",
  dicts: ["t_acceptance_status"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Check, View, Iphone, ArrowLeft };
  },
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      acceptanceList: [],
      // 选中数组
      ids: [],
      multiple: true,
      // 新增对话框
      addOpen: false,
      addTitle: "",
      addForm: { deliveryOrderId: null },
      deliveryOptions: [],
      // 明细对话框
      detailOpen: false,
      detailTitle: "",
      editMode: false,
      editForm: { acceptDate: null, remark: null },
      detailList: [],
      currentAcceptanceId: null,
      // 移动模式（验收录入，仅改布局不改保存逻辑）
      mobileMode: false,
      mobilePage: 0,
      mobilePageSize: 3,
      scanCode: "",
      mobileActiveId: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
      },
    };
  },
  computed: {
    totalAmount() {
      return this.detailList.reduce((sum, row) => sum + this.amountOf(row), 0);
    },
    /** 移动模式总页数 */
    mobileTotalPages() {
      return Math.max(1, Math.ceil(this.detailList.length / this.mobilePageSize));
    },
    /** 当前移动模式页显示的商品（每屏 3 条） */
    mobilePageItems() {
      const start = this.mobilePage * this.mobilePageSize;
      return this.detailList.slice(start, start + this.mobilePageSize);
    },
  },
  created() {
    this.getPageList();
  },
  methods: {
    getPageList() {
      this.loading = true;
      pageAcceptance(this.queryParams).then((response) => {
        this.acceptanceList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
    },
    /** 新增：选择已送达送货单 */
    handleAdd() {
      this.addForm = { deliveryOrderId: null };
      this.addTitle = "新增验收单";
      listDelivery({ status: 2 }).then((response) => {
        this.deliveryOptions = response.data || [];
        if (this.deliveryOptions.length === 0) {
          this.$modal.msgWarning("暂无已送达的送货单");
          return;
        }
        this.addOpen = true;
      });
    },
    submitAddForm() {
      if (!this.addForm.deliveryOrderId) {
        this.$modal.msgWarning("请选择送货单");
        return;
      }
      createAcceptance({ deliveryOrderId: this.addForm.deliveryOrderId })
        .then(() => {
          this.$modal.msgSuccess("验收单已生成，请录入实收数量");
          this.addOpen = false;
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 查看明细（只读） */
    handleView(row) {
      this.editMode = false;
      this.currentAcceptanceId = row.id;
      this.detailTitle = "验收单明细 - " + row.code;
      this.loadDetail(row.id);
    },
    /** 录入（草稿可编辑） */
    handleEdit(row) {
      this.editMode = true;
      this.currentAcceptanceId = row.id;
      this.detailTitle = "验收录入 - " + row.code;
      this.editForm = { acceptDate: row.acceptDate, remark: row.remark };
      this.loadDetail(row.id);
    },
    loadDetail(id) {
      listAcceptanceItems(id).then((response) => {
        this.detailList = (response.data || []).map((item) => ({
          ...item,
          lossReason: item.lossReason || null,
        }));
        this.resetMobileState();
        this.detailOpen = true;
      });
    },
    lossOf(row) {
      return Number(row.actualQuantity || 0) - Number(row.deliveredQuantity || 0);
    },
    amountOf(row) {
      return Number(row.actualQuantity || 0) * Number(row.unitPrice || 0);
    },
    /** 重置移动模式状态 */
    resetMobileState() {
      this.mobileMode = false;
      this.mobilePage = 0;
      this.scanCode = "";
      this.mobileActiveId = null;
    },
    /** 切换移动模式 */
    toggleMobileMode() {
      this.mobileMode = !this.mobileMode;
      this.mobilePage = 0;
      this.scanCode = "";
      this.mobileActiveId = null;
      if (this.mobileMode) {
        this.$nextTick(() => this.focusScanInput());
      }
    },
    /** 聚焦扫码输入框（保持连续扫码） */
    focusScanInput() {
      const inp = this.$refs.scanInput;
      if (inp && inp.focus) {
        inp.focus();
      }
    },
    /**
     * 扫码处理：按商品编码/SKU ID/名称定位商品，并累加实收数量。
     * 支持 "码*数量"（如 S00000003*5），默认每次 +1。
     */
    handleScan() {
      const raw = String(this.scanCode || "").trim();
      if (!raw) return;
      let code = raw;
      let qty = 1;
      const starIdx = raw.indexOf("*");
      if (starIdx > -1) {
        code = raw.slice(0, starIdx).trim();
        const q = parseFloat(raw.slice(starIdx + 1));
        if (!isNaN(q) && q > 0) qty = q;
      }
      const idx = this.detailList.findIndex(
        (item) =>
          item.skuCode === code ||
          String(item.skuId) === code ||
          item.productName === code
      );
      if (idx === -1) {
        this.$modal.msgWarning("未找到商品：" + code);
        return;
      }
      const item = this.detailList[idx];
      item.actualQuantity =
        Math.round((Number(item.actualQuantity || 0) + qty) * 100) / 100;
      // 跳转到该商品所在页并高亮
      this.mobilePage = Math.floor(idx / this.mobilePageSize);
      this.mobileActiveId = item.id;
      // 清空输入并重新聚焦（连续扫码）
      this.scanCode = "";
      this.$nextTick(() => this.focusScanInput());
    },
    /** 保存录入 */
    submitEditForm() {
      const items = this.detailList.map((row) => ({
        id: row.id,
        actualQuantity: row.actualQuantity,
        lossReason: row.lossReason,
      }));
      updateAcceptance({
        id: this.currentAcceptanceId,
        acceptDate: this.editForm.acceptDate,
        remark: this.editForm.remark,
        items: items,
      })
        .then(() => {
          this.$modal.msgSuccess("保存成功");
          this.detailOpen = false;
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 提交 */
    handleSubmit(row) {
      this.$modal
        .confirm("确认提交验收单【" + row.code + "】？（同组订单将进入已验收状态，验收总额作为结算依据）")
        .then(() => submitAcceptance(row.id))
        .then(() => {
          this.$modal.msgSuccess("提交成功");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 删除 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除验收单编号为"' + (row.code || ids) + '"的数据项？')
        .then(() => delAcceptance(ids))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
  },
};
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
.detail-total {
  margin-top: 12px;
  text-align: right;
  font-weight: bold;
}
.loss-negative {
  color: #f56c6c;
  font-weight: bold;
}

/* 验收明细对话框头部：标题 + 移动模式切换 */
.detail-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.detail-dialog-title {
  font-weight: 600;
}

/* 移动模式 */
.mobile-scan-bar {
  margin-bottom: 12px;
}
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fafafa;
  transition: box-shadow 0.2s, border-color 0.2s, background-color 0.2s;
}
.mobile-card-active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  background: #ecf5ff;
}
.mobile-card-ok {
  border-color: #67c23a;
}
.mobile-card-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 10px;
}
.mobile-card-name-text {
  word-break: break-all;
}
.mobile-card-unit {
  color: #909399;
  font-weight: 400;
  font-size: 13px;
}
.mobile-card-qty {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mobile-qty-label {
  font-size: 14px;
  color: #606266;
}
.mobile-qty-input {
  flex: 1;
  max-width: 220px;
}
.mobile-qty-unit {
  color: #909399;
}
.mobile-qty-deliver {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}
.mobile-empty {
  text-align: center;
  color: #909399;
  padding: 20px 0;
}
.mobile-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}
.mobile-page-info {
  color: #606266;
}
</style>
