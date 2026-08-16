<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['price:template:add']">新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="templateList">
      <el-table-column label="模板名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">{{ statusLabel(scope.row.status) }}</template>
      </el-table-column>
      <el-table-column label="有效期" align="center">
        <template #default="scope">{{ formatRange(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="是否默认" align="center" prop="isDefault">
        <template #default="scope">{{ isDefaultLabel(scope.row.isDefault) }}</template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="320">
        <template #default="scope">
          <el-button size="small" link :icon="Star" @click="handleSetDefault(scope.row)"
            v-hasPermi="['price:template:edit']">设为默认</el-button>
          <el-button size="small" link :icon="PriceTag" @click="handleSkuPrice(scope.row)"
            v-hasPermi="['price:template:edit']">SKU价格</el-button>
          <el-button size="small" link :icon="User" @click="handleBindCustomer(scope.row)"
            v-hasPermi="['price:template:add']">绑定客户</el-button>
          <el-button size="small" link :icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['price:template:edit']">修改</el-button>
          <el-button size="small" link :icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['price:template:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改报价模板对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="expireDate">
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择失效日期" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- SKU价格对话框 -->
    <el-dialog :title="skuTitle" v-model="skuOpen" width="860px" append-to-body>
      <div class="mb8">
        <el-button type="primary" plain :icon="Plus" size="small" @click="skuAddRow">新增行</el-button>
      </div>
      <el-table :data="skuRows">
        <el-table-column label="SKU" align="center" min-width="200">
          <template #default="scope">
            <el-select v-model="scope.row.skuId" placeholder="请选择SKU" filterable clearable style="width: 100%">
              <el-option v-for="item in skuOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="140">
          <template #default="scope">
            <el-input-number v-model="scope.row.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" width="160">
          <template #default="scope">
            <el-date-picker v-model="scope.row.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="生效日期" clearable style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" width="160">
          <template #default="scope">
            <el-date-picker v-model="scope.row.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="失效日期" clearable style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="70">
          <template #default="scope">
            <el-button size="small" link :icon="Delete" @click="skuDeleteRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="skuSubmitForm">保 存</el-button>
          <el-button @click="skuOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 绑定客户对话框 -->
    <el-dialog :title="customerTitle" v-model="customerOpen" width="560px" append-to-body>
      <el-alert type="info" :closable="false" title="提示：一个客户最多绑定一个模板，重新绑定将自动替换原绑定。" class="mb8" />
      <el-form label-width="90px">
        <el-form-item label="选择客户">
          <el-select v-model="customerForm.customerIds" multiple filterable placeholder="请选择客户" style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="customerSubmitForm">保 存</el-button>
          <el-button @click="customerOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listTemplate, getTemplate, delTemplate, addTemplate, updateTemplate, listTemplateSkus, addTemplateSku, updateTemplateSku, delTemplateSku, listTemplateCustomers, bindCustomer, setDefault } from "@/api/price/template";
import { listSku } from "@/api/product/sku";
import { listCustomer } from "@/api/partner/customer";
import { Search, Refresh, Plus, Edit, Delete, PriceTag, User, Star } from "@element-plus/icons-vue";

export default {
  name: "PriceTemplate",
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, PriceTag, User, Star };
  },
  data() {
    return {
      loading: false,
      templateList: [],
      queryParams: {
        name: null,
        status: null,
      },
      form: {},
      open: false,
      title: "",
      rules: {
        name: [{ required: true, message: "模板名称不能为空", trigger: "blur" }],
      },
      statusOptions: [
        { label: "启用", value: "0" },
        { label: "停用", value: "1" },
      ],

      // SKU 下拉
      skuOptions: [],
      // SKU价格弹窗
      skuOpen: false,
      skuTitle: "",
      currentTemplateId: null,
      skuRows: [],
      deletedSkuIds: [],

      // 客户下拉与绑定弹窗
      customerOptions: [],
      customerOpen: false,
      customerTitle: "",
      customerForm: {
        customerIds: [],
      },
    };
  },
  created() {
    this.getList();
    this.getSkuOptions();
    this.getCustomerOptions();
  },
  methods: {
    getList() {
      this.loading = true;
      listTemplate(this.queryParams).then((response) => {
        this.templateList = response.data || [];
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
    },
    getSkuOptions() {
      listSku({}).then((response) => {
        this.skuOptions = response.data || [];
      });
    },
    getCustomerOptions() {
      listCustomer({}).then((response) => {
        this.customerOptions = response.data || [];
      });
    },
    statusLabel(value) {
      const item = this.statusOptions.find((o) => o.value === value);
      return item ? item.label : value;
    },
    isDefaultLabel(value) {
      return value === "1" ? "是" : "否";
    },
    formatRange(row) {
      if (!row.effectiveDate && !row.expireDate) return "-";
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
        name: null,
        status: "0",
        effectiveDate: null,
        expireDate: null,
        isDefault: null,
        remark: null,
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
      this.title = "添加报价模板";
    },
    handleUpdate(row) {
      this.reset();
      getTemplate(row.id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改报价模板";
      });
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateTemplate(this.form).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addTemplate(this.form).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      this.$modal
        .confirm('是否确认删除报价模板【"' + row.name + '"】及其SKU价格与客户绑定？')
        .then(function () {
          return delTemplate(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    handleSetDefault(row) {
      this.$modal
        .confirm('是否确认将报价模板【"' + row.name + '"】设为默认模板？')
        .then(function () {
          return setDefault(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("设置成功");
        })
        .catch(() => {});
    },

    /** ==================== SKU价格 ==================== */
    handleSkuPrice(row) {
      this.currentTemplateId = row.id;
      this.skuTitle = "SKU价格 - " + row.name;
      this.deletedSkuIds = [];
      this.skuRows = [];
      listTemplateSkus(row.id).then((response) => {
        this.skuRows = (response.data || []).map((item) => ({ ...item }));
        this.skuOpen = true;
      });
    },
    skuAddRow() {
      this.skuRows.push({ id: null, skuId: null, skuName: null, unitPrice: null, effectiveDate: null, expireDate: null });
    },
    skuDeleteRow(index) {
      const row = this.skuRows[index];
      if (row.id != null) {
        this.deletedSkuIds.push(row.id);
      }
      this.skuRows.splice(index, 1);
    },
    skuSubmitForm() {
      const tasks = [];
      this.deletedSkuIds.forEach((id) => tasks.push(delTemplateSku(id)));
      this.skuRows.forEach((row) => {
        if (row.id != null) {
          tasks.push(updateTemplateSku(row.id, row));
        } else if (row.skuId != null) {
          tasks.push(addTemplateSku(this.currentTemplateId, row));
        }
      });
      if (tasks.length === 0) {
        this.$modal.msgWarning("请添加至少一条SKU价格");
        return;
      }
      Promise.all(tasks).then(() => {
        this.$modal.msgSuccess("保存成功");
        this.skuOpen = false;
      }).catch(() => {});
    },

    /** ==================== 绑定客户 ==================== */
    handleBindCustomer(row) {
      this.currentTemplateId = row.id;
      this.customerTitle = "绑定客户 - " + row.name;
      this.customerForm.customerIds = [];
      listTemplateCustomers(row.id).then((response) => {
        this.customerForm.customerIds = (response.data || []).map((item) => item.customerId);
        this.customerOpen = true;
      });
    },
    customerSubmitForm() {
      if (!this.customerForm.customerIds || this.customerForm.customerIds.length === 0) {
        this.$modal.msgWarning("请选择客户");
        return;
      }
      const tasks = this.customerForm.customerIds.map((cid) => bindCustomer(this.currentTemplateId, cid));
      Promise.all(tasks).then(() => {
        this.$modal.msgSuccess("绑定成功");
        this.customerOpen = false;
      }).catch(() => {});
    },
  },
};
</script>
