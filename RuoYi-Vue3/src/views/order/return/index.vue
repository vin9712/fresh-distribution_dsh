<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="90px"
    >
      <el-form-item label="退货单号" prop="code">
        <el-input v-model="queryParams.code" placeholder="请输入退货单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 160px">
          <el-option label="草稿" :value="0" />
          <el-option label="已提交(质检中)" :value="1" />
          <el-option label="质检完成" :value="2" />
          <el-option label="已完成" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="结算口径" prop="settleScope">
        <el-select v-model="queryParams.settleScope" placeholder="请选择结算口径" clearable style="width: 160px">
          <el-option label="当期冲销" :value="0" />
          <el-option label="下期冲销" :value="1" />
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
          v-hasPermi="['return:add']"
          >新建退货单</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="returnList">
      <el-table-column label="退货单号" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="客户" align="center" prop="customerName" />
      <el-table-column label="配送点" align="center" prop="customerDeptName" />
      <el-table-column label="原验收单号" align="center" prop="acceptanceCode" :show-overflow-tooltip="true" />
      <el-table-column label="退货日期" align="center" prop="returnDate" width="110" />
      <el-table-column label="退货金额" align="center" prop="totalAmount" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="结算口径" align="center" prop="settleScope" width="100">
        <template #default="scope">
          <span v-if="scope.row.settleScope === 0">当期冲销</span>
          <el-tag v-else-if="scope.row.settleScope === 1" type="warning" size="small">下期冲销</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="质检人" align="center" prop="inspectedBy" width="90" />
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="260">
        <template #default="scope">
          <el-button size="small" link :icon="View" @click="handleView(scope.row)" v-hasPermi="['return:query']"
            >明细</el-button
          >
          <el-button
            size="small"
            link
            :icon="Edit"
            v-if="scope.row.status === 0"
            @click="handleEdit(scope.row)"
            v-hasPermi="['return:edit']"
            >录入</el-button
          >
          <el-button
            size="small"
            link
            :icon="Check"
            v-if="scope.row.status === 0"
            @click="handleSubmit(scope.row)"
            v-hasPermi="['return:submit']"
            >提交</el-button
          >
          <el-button
            size="small"
            link
            :icon="Stamp"
            v-if="scope.row.status === 1"
            @click="handleInspect(scope.row)"
            v-hasPermi="['return:inspect']"
            >质检</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            v-if="scope.row.status === 0"
            @click="handleDelete(scope.row)"
            v-hasPermi="['return:remove']"
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

    <!-- 退货单新建/录入对话框：选已提交验收单 → 按验收明细填退货数量（上限=实收-累计已退，单价锁原验收价） -->
    <el-dialog align-center :title="formTitle" v-model="formOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-form ref="returnFormRef" :model="form" :rules="rules" label-width="90px" size="small">
        <el-row>
          <el-col :span="8">
            <el-form-item label="原验收单" prop="acceptanceId">
              <el-select
                v-model="form.acceptanceId"
                placeholder="请选择已提交的验收单"
                filterable
                style="width: 100%"
                :disabled="!!form.id || accLocked"
                @change="onAcceptanceChange"
              >
                <el-option
                  v-for="a in acceptanceOptions"
                  :key="a.id"
                  :label="a.code + '（' + (a.customerName || a.customerId) + ' / ' + (a.acceptDate || '') + '）'"
                  :value="a.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker
                v-model="form.returnDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择退货日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="退货数量上限 = 实收数量 − 累计已退（草稿/已提交均占用）；单价锁定原验收价，金额由后端重算"
        style="margin-bottom: 8px"
      />
      <el-table :data="formItems" size="small" border>
        <el-table-column label="商品名称" align="center" prop="productName" min-width="140" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" width="100" />
        <el-table-column label="单位" align="center" prop="productUnit" width="60" />
        <el-table-column label="配送点" align="center" prop="customerDeptName" width="110" :show-overflow-tooltip="true" />
        <el-table-column label="实收数量" align="center" prop="actualQuantity" width="90" />
        <el-table-column label="累计已退" align="center" prop="returnedQuantity" width="90" />
        <el-table-column label="可退上限" align="center" width="90">
          <template #default="scope">
            <span :class="{ 'qty-zero': availableOf(scope.row) <= 0 }">{{ availableOf(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价(锁验收价)" align="center" prop="unitPrice" width="110" />
        <el-table-column label="本次退货数量" align="center" width="150">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.returnQuantity"
              :min="0"
              :max="availableOf(scope.row)"
              :precision="2"
              :controls="false"
              style="width: 100%"
              :disabled="availableOf(scope.row) <= 0"
            />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="saving" @click="submitForm">保 存</el-button>
          <el-button @click="formOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 退货单明细对话框（只读） -->
    <el-dialog align-center :title="'退货单明细 - ' + (viewRow.code || '')" v-model="viewOpen" width="760px" append-to-body>
      <el-descriptions :column="3" border size="small" style="margin-bottom: 10px">
        <el-descriptions-item label="客户">{{ viewRow.customerName }}</el-descriptions-item>
        <el-descriptions-item label="配送点">{{ viewRow.customerDeptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ viewRow.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="原验收单">{{ viewRow.acceptanceCode }}</el-descriptions-item>
        <el-descriptions-item label="退货金额">{{ viewRow.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="结算口径">{{ settleText(viewRow.settleScope) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="viewItems" size="small" border>
        <el-table-column label="商品名称" align="center" prop="productName" min-width="130" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" width="90" />
        <el-table-column label="单位" align="center" prop="productUnit" width="60" />
        <el-table-column label="退货数量" align="center" prop="returnQuantity" width="90" />
        <el-table-column label="单价" align="center" prop="unitPrice" width="80" />
        <el-table-column label="金额" align="center" prop="amount" width="90" />
        <el-table-column label="质检结论" align="center" width="120">
          <template #default="scope">
            <span v-if="scope.row.qualityResult == null">未质检</span>
            <dict-tag v-else :options="dict.type.return_quality_result" :value="scope.row.qualityResult" />
          </template>
        </el-table-column>
        <el-table-column label="质检备注" align="center" prop="qualityNote" :show-overflow-tooltip="true" />
      </el-table>
    </el-dialog>

    <!-- 质检对话框（已提交→质检完成，逐行记质检结论） -->
    <el-dialog align-center :title="'退货质检 - ' + (inspectRow.code || '')" v-model="inspectOpen" width="760px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="逐行登记质检结论（可再售入库 / 不可再售报损）；库存流水待库存模块，本期仅记结论与备注"
        style="margin-bottom: 8px"
      />
      <el-table :data="inspectItems" size="small" border>
        <el-table-column label="商品名称" align="center" prop="productName" min-width="140" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" width="90" />
        <el-table-column label="退货数量" align="center" prop="returnQuantity" width="90" />
        <el-table-column label="质检结论" align="center" width="160">
          <template #default="scope">
            <el-select v-model="scope.row.qualityResult" placeholder="请选择结论" style="width: 100%">
              <el-option
                v-for="d in dict.type.return_quality_result"
                :key="d.value"
                :label="d.label"
                :value="Number(d.value)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="质检备注" align="center" min-width="140">
          <template #default="scope">
            <el-input v-model="scope.row.qualityNote" placeholder="备注（可空）" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="inspecting" @click="submitInspect">完成质检</el-button>
          <el-button @click="inspectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageReturnOrder,
  getReturnOrder,
  listReturnItems,
  addReturnOrder,
  updateReturnOrder,
  submitReturnOrder,
  inspectReturnOrder,
  delReturnOrder,
} from "@/api/order/return";
import { listAcceptance, listAcceptanceItems } from "@/api/acceptance/acceptance";
import { Search, Refresh, Plus, Delete, Edit, Check, View, Stamp } from "@element-plus/icons-vue";

const STATUS_TEXT = { 0: "草稿", 1: "已提交(质检中)", 2: "质检完成", 3: "已完成" };
const STATUS_TAG = { 0: "info", 1: "warning", 2: "success", 3: "success" };

export default {
  name: "ReturnOrder",
  dicts: ["return_quality_result"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Check, View, Stamp };
  },
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      returnList: [],
      // 新建/录入对话框
      formOpen: false,
      formTitle: "",
      form: { id: null, acceptanceId: null, returnDate: null, remark: null },
      rules: {
        acceptanceId: [{ required: true, message: "原验收单不能为空", trigger: "change" }],
        returnDate: [{ required: true, message: "退货日期不能为空", trigger: "change" }],
      },
      formItems: [],
      acceptanceOptions: [],
      accLocked: false,
      saving: false,
      // 明细查看
      viewOpen: false,
      viewRow: {},
      viewItems: [],
      // 质检
      inspectOpen: false,
      inspectRow: {},
      inspectItems: [],
      inspecting: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
        settleScope: null,
      },
    };
  },
  created() {
    this.getPageList();
  },
  methods: {
    statusText(status) {
      return STATUS_TEXT[status] || status;
    },
    statusTagType(status) {
      return STATUS_TAG[status] || "info";
    },
    settleText(scope) {
      if (scope === 0) return "当期冲销";
      if (scope === 1) return "下期冲销";
      return "—";
    },
    getPageList() {
      this.loading = true;
      pageReturnOrder(this.queryParams).then((response) => {
        this.returnList = response.rows;
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
    /** 可退上限 = 实收 − 累计已退（改草稿时累计已退含自身占用，回显时加回自身数量） */
    availableOf(row) {
      return Math.max(0, Number(row.actualQuantity || 0) - Number(row.returnedQuantity || 0));
    },
    /** 新建：拉取已提交验收单（D-032：仅已提交验收可退，不撤回历史验收） */
    handleAdd() {
      listAcceptance({ status: 1 }).then((response) => {
        this.acceptanceOptions = response.data || [];
        if (!this.acceptanceOptions.length) {
          this.$modal.msgWarning("暂无已提交的验收单，验收提交后才能发起退货");
          return;
        }
        this.form = {
          id: null,
          acceptanceId: null,
          returnDate: this.parseTime(new Date(), "{y}-{m}-{d}"),
          remark: null,
        };
        this.formItems = [];
        this.accLocked = false;
        this.formTitle = "新建退货单";
        this.formOpen = true;
        this.$nextTick(() => this.$refs.returnFormRef && this.$refs.returnFormRef.clearValidate());
      });
    },
    /** 选择验收单 → 加载验收明细（含累计已退），构建退货行 */
    onAcceptanceChange(acceptanceId) {
      if (!acceptanceId) {
        this.formItems = [];
        return;
      }
      listAcceptanceItems(acceptanceId).then((response) => {
        this.formItems = (response.data || []).map((item) => ({
          acceptanceItemId: item.id,
          productName: item.productName,
          productSpec: item.productSpec,
          productUnit: item.productUnit,
          customerDeptName: item.customerDeptName,
          actualQuantity: item.actualQuantity,
          returnedQuantity: item.returnedQuantity || 0,
          unitPrice: item.unitPrice,
          returnQuantity: null,
        }));
      });
    },
    /** 录入草稿：回显已有退货数量（可退上限 = 实收 − 累计已退 + 自身占用） */
    handleEdit(row) {
      Promise.all([getReturnOrder(row.id), listReturnItems(row.id)]).then(([infoRes, itemRes]) => {
        const info = infoRes.data || {};
        const ownByItem = {};
        (itemRes.data.items || itemRes.data || []).forEach((it) => {
          ownByItem[it.acceptanceItemId] = Number(it.returnQuantity || 0);
        });
        listAcceptanceItems(info.acceptanceId).then((response) => {
          this.formItems = (response.data || []).map((item) => ({
            acceptanceItemId: item.id,
            productName: item.productName,
            productSpec: item.productSpec,
            productUnit: item.productUnit,
            customerDeptName: item.customerDeptName,
            actualQuantity: item.actualQuantity,
            returnedQuantity: Number(item.returnedQuantity || 0) - (ownByItem[item.id] || 0),
            unitPrice: item.unitPrice,
            returnQuantity: ownByItem[item.id] || null,
          }));
          this.form = {
            id: info.id,
            acceptanceId: info.acceptanceId,
            returnDate: info.returnDate,
            remark: info.remark,
          };
          this.accLocked = true;
          this.formTitle = "录入退货单 - " + info.code;
          this.formOpen = true;
          this.$nextTick(() => this.$refs.returnFormRef && this.$refs.returnFormRef.clearValidate());
        });
      });
    },
    /** 保存（新建/改草稿；数量上限与单价锁由后端二次校验） */
    submitForm() {
      this.$refs.returnFormRef.validate((valid) => {
        if (!valid) return;
        const items = this.formItems
          .filter((row) => row.returnQuantity != null && Number(row.returnQuantity) > 0)
          .map((row) => ({
            acceptanceItemId: row.acceptanceItemId,
            returnQuantity: row.returnQuantity,
          }));
        if (!items.length) {
          this.$modal.msgWarning("请至少填写一行退货数量");
          return;
        }
        this.saving = true;
        const request = this.form.id
          ? updateReturnOrder({ id: this.form.id, acceptanceId: this.form.acceptanceId, returnDate: this.form.returnDate, remark: this.form.remark, items })
          : addReturnOrder({ acceptanceId: this.form.acceptanceId, returnDate: this.form.returnDate, remark: this.form.remark, items });
        request
          .then(() => {
            this.$modal.msgSuccess("保存成功");
            this.formOpen = false;
            this.getPageList();
          })
          .catch(() => {})
          .finally(() => {
            this.saving = false;
          });
      });
    },
    /** 查看（只读明细） */
    handleView(row) {
      Promise.all([getReturnOrder(row.id), listReturnItems(row.id)]).then(([infoRes, itemRes]) => {
        this.viewRow = infoRes.data || {};
        this.viewItems = itemRes.data.items || itemRes.data || [];
        this.viewOpen = true;
      });
    },
    /** 提交（草稿→已提交质检中，settle_scope 快照） */
    handleSubmit(row) {
      this.$modal
        .confirm("确认提交退货单【" + row.code + "】？提交后进入质检中，明细不可再改")
        .then(() => submitReturnOrder(row.id))
        .then(() => {
          this.$modal.msgSuccess("提交成功，已生成结算冲销快照");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 质检（已提交→质检完成） */
    handleInspect(row) {
      listReturnItems(row.id).then((response) => {
        this.inspectRow = row;
        this.inspectItems = (itemResItems(response) || []).map((it) => ({
          ...it,
          qualityResult: it.qualityResult || null,
          qualityNote: it.qualityNote || null,
        }));
        this.inspectOpen = true;
      });
    },
    submitInspect() {
      const missing = this.inspectItems.filter((it) => it.qualityResult == null);
      if (missing.length) {
        this.$modal.msgWarning("请为全部明细行选择质检结论：" + missing[0].productName);
        return;
      }
      this.inspecting = true;
      inspectReturnOrder(this.inspectRow.id, this.inspectItems.map((it) => ({
        itemId: it.id,
        qualityResult: it.qualityResult,
        qualityNote: it.qualityNote,
      })))
        .then(() => {
          this.$modal.msgSuccess("质检完成");
          this.inspectOpen = false;
          this.getPageList();
        })
        .catch(() => {})
        .finally(() => {
          this.inspecting = false;
        });
    },
    /** 删除（仅草稿，逻辑删除保留审计） */
    handleDelete(row) {
      this.$modal
        .confirm('是否确认删除退货单编号为"' + row.code + '"的数据项？')
        .then(() => delReturnOrder(row.id))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
  },
};

/** 兼容明细接口返回结构：{ returnId, items } 或裸数组 */
function itemResItems(response) {
  const data = response.data;
  if (Array.isArray(data)) return data;
  return data && data.items;
}
</script>

<style scoped>
.qty-zero {
  color: #c0c4cc;
}
</style>
