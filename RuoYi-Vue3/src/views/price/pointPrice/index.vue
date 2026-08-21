<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="price-delivery-override-table"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="overrideList"
      :loading="loading"
      :show-pager="false"
      :batch-actions="batchActions"
      @query="handleQuery"
      @reset="resetQuery"
      @selection-change="handleSelectionChange"
      @add="handleAdd"
      @edit="handleUpdate"
      @delete="handleQuickDelete"
      @batch-action="handleQuickBatchAction"
    >
      <template #search>
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="48px">
          <el-form-item label="配送点" prop="deliveryPointId">
            <el-select v-model="queryParams.deliveryPointId" placeholder="请选择配送点" clearable filterable style="width: 200px">
              <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品" prop="skuName">
            <el-input v-model="queryParams.skuName" placeholder="请输入商品名称" clearable style="width: 160px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="可见" prop="isAvailable">
            <el-select v-model="queryParams.isAvailable" placeholder="全部" clearable style="width: 100px">
              <el-option label="可见" :value="1" />
              <el-option label="隐藏" :value="0" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #buttons>
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['price:delivery-override:add']">新增</el-button>
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['price:delivery-override:remove']">删除</el-button>
      </template>

      <template #col_available="{ row }">
        <el-tag :type="row.isAvailable == 1 ? 'success' : 'danger'" size="small">
          {{ row.isAvailable == 1 ? "可见" : "隐藏" }}
        </el-tag>
      </template>
      <template #col_price="{ row }">
        <span v-if="row.priceOverride != null">{{ row.priceOverride }}</span>
        <span v-else class="text-muted">继承客户价</span>
      </template>
      <template #col_alias="{ row }">
        <span v-if="row.aliasOverride">{{ row.aliasOverride }}</span>
        <span v-else class="text-muted">-</span>
      </template>
      <template #col_range="{ row }">{{ formatRange(row) }}</template>
      <template #col_op="{ row }">
        <el-button size="small" link :icon="Edit" @click="handleUpdate(row)"
          v-hasPermi="['price:delivery-override:add']">修改</el-button>
        <el-button size="small" link :icon="Delete" @click="handleDelete(row)"
          v-hasPermi="['price:delivery-override:remove']">删除</el-button>
      </template>
    </quick-table>

    <!-- 添加或修改配送点覆盖对话框 -->
    <el-dialog :title="title" v-model="open" width="580px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="配送点" prop="deliveryPointId">
          <el-select v-model="form.deliveryPointId" placeholder="请选择配送点" filterable clearable style="width: 100%">
            <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="skuId">
          <el-select v-model="form.skuId" placeholder="请选择SKU" filterable clearable style="width: 100%">
            <el-option
              v-for="item in skuOptions"
              :key="item.id"
              :label="item.name + (item.specName ? '（' + item.specName + '）' : '') + ' / ' + item.unit"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否可见" prop="isAvailable">
          <el-radio-group v-model="form.isAvailable">
            <el-radio :value="1">可见</el-radio>
            <el-radio :value="0">隐藏（录单不显示）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="价格覆盖" prop="priceOverride">
          <el-input-number v-model="form.priceOverride" :min="0" :precision="2" controls-position="right"
            placeholder="留空则继承客户级价格" style="width: 100%" />
        </el-form-item>
        <el-form-item label="别名覆盖" prop="aliasOverride">
          <el-input v-model="form.aliasOverride" placeholder="该配送点对商品的叫法（可空）" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="可空" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="expireDate">
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="可空" clearable style="width: 100%" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="同一配送点+商品已存在覆盖时保存将直接更新（upsert）" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listDeliverySkuOverride, getDeliverySkuOverride, saveDeliverySkuOverride, delDeliverySkuOverride } from "@/api/price/pointPrice";
import { listCustomerDept } from "@/api/partner/customerDept";
import { listSku } from "@/api/product/sku";
import { Search, Refresh, Plus, Edit, Delete } from "@element-plus/icons-vue";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";

export default {
  name: "DeliverySkuOverride",
  mixins: [quickTableMixin],
  setup() {
    return { Search, Refresh, Plus, Edit, Delete };
  },
  data() {
    return {
      loading: false,
      showSearch: true,
      overrideList: [],
      queryParams: {
        deliveryPointId: null,
        skuName: null,
        isAvailable: null,
      },
      deptOptions: [],
      skuOptions: [],
      // 表格列配置
      columns: [
        { field: "deliveryPointName", title: "配送点", minWidth: 140, fixed: "left" },
        { field: "skuName", title: "商品", minWidth: 140 },
        { field: "isAvailable", title: "是否可见", width: 90, align: "center", slots: { default: "col_available" } },
        { field: "priceOverride", title: "价格覆盖", width: 110, align: "right", slots: { default: "col_price" } },
        { field: "aliasOverride", title: "别名覆盖", width: 120, align: "center", slots: { default: "col_alias" } },
        { field: "range", title: "有效期", minWidth: 190, align: "center", slots: { default: "col_range" } },
        { field: "op", title: "操作", width: 130, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
      form: {},
      open: false,
      title: "",
      rules: {
        deliveryPointId: [{ required: true, message: "配送点不能为空", trigger: "change" }],
        skuId: [{ required: true, message: "商品不能为空", trigger: "change" }],
        isAvailable: [{ required: true, message: "是否可见不能为空", trigger: "change" }],
      },
    };
  },
  created() {
    this.getList();
    this.getDeptOptions();
    this.getSkuOptions();
  },
  methods: {
    getList() {
      this.loading = true;
      const params = {
        deliveryPointId: this.queryParams.deliveryPointId || undefined,
        isAvailable: this.queryParams.isAvailable != null ? this.queryParams.isAvailable : undefined,
      };
      listDeliverySkuOverride(params).then((response) => {
        let list = response.data || [];
        if (this.queryParams.skuName) {
          const kw = this.queryParams.skuName;
          list = list.filter((item) => (item.skuName || "").indexOf(kw) !== -1);
        }
        this.overrideList = list;
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
    },
    getDeptOptions() {
      listCustomerDept({}).then((response) => {
        this.deptOptions = response.data || [];
      });
    },
    getSkuOptions() {
      listSku({}).then((response) => {
        this.skuOptions = response.data || [];
      });
    },
    formatRange(row) {
      if (!row.effectiveDate && !row.expireDate) return "长期有效";
      return (row.effectiveDate || "") + " ~ " + (row.expireDate || "");
    },
    handleQuery() {
      this.getList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.getList();
    },
    reset() {
      this.form = {
        id: null,
        deliveryPointId: null,
        skuId: null,
        isAvailable: 1,
        priceOverride: null,
        aliasOverride: null,
        effectiveDate: null,
        expireDate: null,
      };
      this.resetForm("form");
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加配送点覆盖";
    },
    handleUpdate(row) {
      this.reset();
      getDeliverySkuOverride(row.id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改配送点覆盖";
      });
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          saveDeliverySkuOverride(this.form).then(() => {
            this.$modal.msgSuccess("保存成功");
            this.open = false;
            this.getList();
          }).catch(() => {});
        }
      });
    },
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除配送点【"' + ((row && row.deliveryPointName) || "") + '"】对商品【"' + ((row && row.skuName) || "") + '"】的覆盖？')
        .then(function () {
          return delDeliverySkuOverride(ids);
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

<style scoped>
.text-muted {
  color: #c0c4cc;
}
</style>
