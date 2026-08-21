<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="basic-customer-sku-table"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="customerSkuList"
      :loading="loading"
      :show-pager="false"
      :batch-actions="batchActions"
      @query="handleQuery"
      @reset="resetQuery"
      @selection-change="handleSelectionChange"
      @add="handleAdd"
      @edit="handleUpdate"
      @delete="handleQuickDelete"
      @batch-action="handleBatchAction"
    >
      <template #search>
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="48px">
          <el-form-item label="客户" prop="customerId">
            <el-select
              v-model="queryParams.customerId"
              placeholder="请选择客户（必选）"
              filterable
              clearable
              style="width: 220px"
              @change="handleQuery"
            >
              <el-option
                v-for="item in customerOptions"
                :key="item.id"
                :label="item.alias ? item.alias + '（' + item.name + '）' : item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字" prop="keyword">
            <el-input
              v-model="queryParams.keyword"
              placeholder="商品名/助记码/别名/客户编码"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="可用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #buttons>
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['product:customer-sku:add']">新增</el-button>
        <el-button type="success" plain :icon="Promotion" size="small"
          v-hasPermi="['product:customer-sku:assign']" @click="handleAssign">批量赋值</el-button>
        <el-button type="warning" plain :icon="Switch" size="small" :disabled="multiple"
          v-hasPermi="['product:customer-sku:edit']" @click="handleToggleStatus">停用/启用</el-button>
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple"
          v-hasPermi="['product:customer-sku:remove']" @click="handleDelete">删除</el-button>
      </template>

      <template #col_alias="{ row }">
        <span v-if="row.alias">{{ row.alias }}</span>
        <span v-else class="text-muted">-</span>
      </template>
      <template #col_status="{ row }">
        <el-tag :type="row.status == 1 ? 'success' : 'danger'" size="small">
          {{ row.status == 1 ? "可用" : "停用" }}
        </el-tag>
      </template>
      <template #col_follow="{ row }">
        <el-tag :type="row.isFollowDefault == 1 ? 'info' : 'warning'" size="small">
          {{ row.isFollowDefault == 1 ? "跟随默认" : "已个性化" }}
        </el-tag>
      </template>
      <template #col_op="{ row }">
        <el-button size="small" link :icon="Edit" @click="handleUpdate(row)"
          v-hasPermi="['product:customer-sku:edit']">修改</el-button>
        <el-button size="small" link :icon="Delete" @click="handleDelete(row)"
          v-hasPermi="['product:customer-sku:remove']">删除</el-button>
      </template>
    </quick-table>

    <!-- 新增客户商品对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户" prop="customerId">
          <el-select v-model="form.customerId" placeholder="请选择客户" filterable clearable style="width: 100%" :disabled="form.id != null">
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="item.alias ? item.alias + '（' + item.name + '）' : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标准SKU" prop="skuId">
          <el-select
            v-model="form.skuId"
            placeholder="请选择标准SKU"
            filterable
            clearable
            style="width: 100%"
            :disabled="form.id != null"
          >
            <el-option
              v-for="item in skuOptions"
              :key="item.id"
              :label="item.name + (item.specName ? '（' + item.specName + '）' : '') + ' / ' + item.unit"
              :value="item.id"
            >
              <span>{{ item.code }} - {{ item.name }}</span>
              <span style="float: right; color: #8492a6; font-size: 12px">{{ item.specName }} / {{ item.unit }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="客户别名" prop="alias">
          <el-input v-model="form.alias" placeholder="客户对该商品的叫法（可空）" />
        </el-form-item>
        <el-form-item label="最小起订量" prop="minOrderQty">
          <el-input-number v-model="form.minOrderQty" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下单步长" prop="orderStep">
          <el-input-number v-model="form.orderStep" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 个性化修改对话框 -->
    <el-dialog :title="editTitle" v-model="editOpen" width="560px" append-to-body>
      <el-form ref="editForm" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="标准SKU">
          <el-input :value="editForm.skuName + '（' + editForm.skuSpecName + '）'" disabled />
        </el-form-item>
        <el-form-item label="客户别名" prop="alias">
          <el-input v-model="editForm.alias" placeholder="客户对该商品的叫法（可空）" />
        </el-form-item>
        <el-form-item label="最小起订量" prop="minOrderQty">
          <el-input-number v-model="editForm.minOrderQty" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下单步长" prop="orderStep">
          <el-input-number v-model="editForm.orderStep" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">可用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert type="info" :closable="false" title="保存后该商品将标记为「已个性化」，不再跟随默认模板批量赋值" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitEditForm">确 定</el-button>
          <el-button @click="editOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量赋值对话框 -->
    <el-dialog :title="assignDialog.title" v-model="assignDialog.open" width="720px" append-to-body>
      <el-form ref="assignForm" :model="assignForm" :rules="assignRules" label-width="110px">
        <el-form-item label="商品来源" prop="sourceType">
          <el-radio-group v-model="assignForm.sourceType" @change="handleAssignSourceChange">
            <el-radio :value="'template'">按模板</el-radio>
            <el-radio :value="'sku'">手动勾选SKU</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="assignForm.sourceType === 'template'" label="默认模板" prop="templateId">
          <el-select v-model="assignForm.templateId" placeholder="请选择模板" filterable clearable style="width: 100%">
            <el-option
              v-for="item in templateOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="assignForm.sourceType === 'sku'" label="选择SKU" prop="skuIds">
          <el-select
            v-model="assignForm.skuIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可搜索选择多个标准SKU"
            style="width: 100%"
          >
            <el-option
              v-for="item in allSkuOptions"
              :key="item.id"
              :label="item.name + (item.specName ? '（' + item.specName + '）' : '') + ' / ' + item.unit"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标客户" prop="customerIds">
          <el-select
            v-model="assignForm.customerIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可搜索选择多个客户"
            style="width: 100%"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="item.alias ? item.alias + '（' + item.name + '）' : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖策略" prop="strategy">
          <el-radio-group v-model="assignForm.strategy">
            <el-radio :value="1">仅新增</el-radio>
            <el-radio :value="2">覆盖未个性化</el-radio>
            <el-radio :value="3">全部覆盖（慎用）</el-radio>
          </el-radio-group>
          <div class="el-form-item-msg">
            仅新增：只为没有该 SKU 的客户创建；覆盖未个性化：更新 is_follow_default=1 的商品；全部覆盖：强制覆盖所有客户。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAssignForm">确 定</el-button>
          <el-button @click="assignDialog.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  listCustomerSkuPool,
  getCustomerSku,
  addCustomerSku,
  updateCustomerSku,
  assignCustomerSku,
  delCustomerSku,
  listDefaultSkuTemplate,
} from "@/api/product/customerSku";
import { listSku } from "@/api/product/sku";
import { listCustomer } from "@/api/partner/customer";
import { Search, Refresh, Plus, Delete, Edit, Promotion, Switch } from "@element-plus/icons-vue";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";

export default {
  name: "CustomerSku",
  mixins: [quickTableMixin],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Promotion, Switch };
  },
  data() {
    return {
      loading: false,
      showSearch: true,
      // 客户商品列表
      customerSkuList: [],
      // 客户选项
      customerOptions: [],
      // 标准SKU选项（新增用，剔除已分配的）
      skuOptions: [],
      // 全部标准SKU选项（批量赋值用）
      allSkuOptions: [],
      // 模板选项
      templateOptions: [],
      // 查询参数
      queryParams: {
        customerId: null,
        keyword: null,
        status: null,
      },
      // 表格列配置
      columns: [
        { field: "customerCode", title: "客户商品编码", width: 120, align: "center", sortable: true },
        { field: "alias", title: "别名", width: 120, align: "center", slots: { default: "col_alias" } },
        { field: "skuName", title: "标准SKU", minWidth: 140, fixed: "left" },
        { field: "skuCode", title: "标准编码", width: 110, align: "center" },
        { field: "skuSpecName", title: "规格", width: 110, align: "center", showOverflow: true },
        { field: "skuUnit", title: "单位", width: 70, align: "center" },
        { field: "minOrderQty", title: "起订量", width: 80, align: "right" },
        { field: "orderStep", title: "步长", width: 70, align: "right" },
        { field: "status", title: "状态", width: 80, align: "center", slots: { default: "col_status" } },
        { field: "isFollowDefault", title: "个性化", width: 90, align: "center", slots: { default: "col_follow" } },
        { field: "op", title: "操作", width: 130, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "toggle", label: "停用/启用", type: "warning", icon: "Switch" },
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
      // 新增
      form: {},
      open: false,
      title: "",
      rules: {
        customerId: [{ required: true, message: "客户不能为空", trigger: "change" }],
        skuId: [{ required: true, message: "标准SKU不能为空", trigger: "change" }],
      },
      // 个性化修改
      editForm: {},
      editOpen: false,
      editTitle: "",
      editRules: {},
      // 批量赋值
      assignDialog: {
        open: false,
        title: "批量赋值默认SKU",
      },
      assignForm: {
        sourceType: "template",
        templateId: null,
        skuIds: [],
        customerIds: [],
        strategy: 1,
      },
      assignRules: {
        templateId: [{ required: true, message: "请选择模板", trigger: "change" }],
        skuIds: [{ required: true, type: "array", min: 1, message: "请至少选择一个SKU", trigger: "change" }],
        customerIds: [{ required: true, type: "array", min: 1, message: "请至少选择一个客户", trigger: "change" }],
        strategy: [{ required: true, message: "请选择覆盖策略", trigger: "change" }],
      },
    };
  },
  created() {
    this.getCustomerOptions();
    this.getTemplateOptions();
    this.getList();
  },
  methods: {
    /** 查询客户商品池 */
    getList() {
      if (!this.queryParams.customerId) {
        this.customerSkuList = [];
        return;
      }
      this.loading = true;
      listCustomerSkuPool(this.queryParams).then((response) => {
        this.customerSkuList = response.data || [];
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
    },
    /** 批量操作条分发 */
    handleBatchAction(key, rows) {
      if (key === "toggle") {
        this.handleToggleStatus();
      } else if (key === "delete") {
        this.handleDelete();
      }
    },
    /** 客户下拉 */
    getCustomerOptions() {
      listCustomer().then((response) => {
        this.customerOptions = response.data || [];
      });
    },
    /** 模板下拉 */
    getTemplateOptions() {
      listDefaultSkuTemplate({}).then((response) => {
        this.templateOptions = response.data || [];
      });
    },
    handleQuery() {
      this.getList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.getList();
    },
    /** 新增 */
    handleAdd() {
      if (!this.queryParams.customerId) {
        this.$modal.msgWarning("请先在搜索区选择客户");
        return;
      }
      this.reset();
      // 过滤已分配的SKU
      const assignedIds = this.customerSkuList.map((cs) => cs.skuId);
      listSku({}).then((response) => {
        this.allSkuOptions = response.data || [];
        this.skuOptions = this.allSkuOptions.filter((sku) => !assignedIds.includes(sku.id));
      });
      this.open = true;
      this.title = "新增客户商品";
    },
    reset() {
      this.form = {
        customerId: this.queryParams.customerId,
        skuId: null,
        alias: null,
        minOrderQty: 1,
        orderStep: 1,
      };
      this.resetForm("form");
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          addCustomerSku(this.form).then(() => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          }).catch(() => {});
        }
      });
    },
    /** 个性化修改 */
    handleUpdate(row) {
      getCustomerSku(row.id).then((response) => {
        const data = response.data;
        this.editForm = {
          id: data.id,
          skuName: data.skuName,
          skuSpecName: data.skuSpecName,
          alias: data.alias,
          minOrderQty: data.minOrderQty,
          orderStep: data.orderStep,
          status: data.status,
        };
        this.editOpen = true;
        this.editTitle = "个性化修改：" + data.skuName;
      });
    },
    submitEditForm() {
      updateCustomerSku(this.editForm).then(() => {
        this.$modal.msgSuccess("修改成功");
        this.editOpen = false;
        this.getList();
      }).catch(() => {});
    },
    /** 停用/启用 */
    handleToggleStatus() {
      const rows = this.customerSkuList.filter((cs) => this.ids.includes(cs.id));
      if (rows.length === 0) {
        return;
      }
      const target = rows.every((r) => r.status == 0) ? 1 : 0;
      const action = target == 1 ? "启用" : "停用";
      this.$modal
        .confirm('是否确认' + action + '选中的 ' + rows.length + ' 个客户商品？')
        .then(() => {
          const reqs = rows.map((row) => updateCustomerSku({ id: row.id, status: target }));
          return Promise.all(reqs);
        })
        .then(() => {
          this.$modal.msgSuccess(action + "成功");
          this.getList();
        })
        .catch(() => {});
    },
    /** 删除 */
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除选中的客户商品数据项？')
        .then(function () {
          return delCustomerSku(ids);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 批量赋值 */
    handleAssign() {
      this.assignForm = {
        sourceType: "template",
        templateId: null,
        skuIds: [],
        customerIds: [this.queryParams.customerId].filter((id) => id),
        strategy: 1,
      };
      this.assignDialog.open = true;
      // 预加载全部SKU（供手动勾选模式）
      if (this.allSkuOptions.length === 0) {
        listSku({}).then((response) => {
          this.allSkuOptions = response.data || [];
        });
      }
    },
    handleAssignSourceChange() {
      this.assignForm.templateId = null;
      this.assignForm.skuIds = [];
    },
    submitAssignForm() {
      this.$refs["assignForm"].validate((valid) => {
        if (!valid) {
          return;
        }
        const payload = {
          customerIds: this.assignForm.customerIds,
          strategy: this.assignForm.strategy,
        };
        if (this.assignForm.sourceType === "template") {
          payload.templateId = this.assignForm.templateId;
        } else {
          payload.skuIds = this.assignForm.skuIds;
        }
        assignCustomerSku(payload).then((response) => {
          const count = response.data != null ? response.data : "";
          this.$modal.msgSuccess("批量赋值成功" + (count !== "" ? "（" + count + " 条）" : ""));
          this.assignDialog.open = false;
          this.getList();
        }).catch(() => {});
      });
    },
  },
};
</script>

<style scoped>
.text-muted {
  color: #c0c4cc;
}
.el-form-item-msg {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  width: 100%;
}
</style>
