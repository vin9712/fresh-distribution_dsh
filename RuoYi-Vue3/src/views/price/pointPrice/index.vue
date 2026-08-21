<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
      <el-form-item label="配送点" prop="deliveryPointId">
        <el-select v-model="queryParams.deliveryPointId" placeholder="请选择配送点" clearable filterable style="width: 220px">
          <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="商品名称" prop="skuName">
        <el-input v-model="queryParams.skuName" placeholder="请输入商品名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="可见" prop="isAvailable">
        <el-select v-model="queryParams.isAvailable" placeholder="全部" clearable style="width: 120px">
          <el-option label="可见" :value="1" />
          <el-option label="隐藏" :value="0" />
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
          v-hasPermi="['price:delivery-override:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['price:delivery-override:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch="showSearch" @update:showSearch="showSearch = $event" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="overrideList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="配送点" align="center" prop="deliveryPointName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="商品" align="center" prop="skuName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="是否可见" align="center" prop="isAvailable" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isAvailable == 1 ? 'success' : 'danger'">
            {{ scope.row.isAvailable == 1 ? "可见" : "隐藏" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="价格覆盖" align="center" prop="priceOverride" width="100">
        <template #default="scope">
          <span v-if="scope.row.priceOverride != null">{{ scope.row.priceOverride }}</span>
          <span v-else class="text-muted">继承客户价</span>
        </template>
      </el-table-column>
      <el-table-column label="别名覆盖" align="center" prop="aliasOverride" width="120" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.aliasOverride">{{ scope.row.aliasOverride }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="有效期" align="center" min-width="190">
        <template #default="scope">{{ formatRange(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="130">
        <template #default="scope">
          <el-button size="small" link :icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['price:delivery-override:add']">修改</el-button>
          <el-button size="small" link :icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['price:delivery-override:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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

export default {
  name: "DeliverySkuOverride",
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
      ids: [],
      multiple: true,
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
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
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
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除配送点【"' + (row.deliveryPointName || "") + '"】对商品【"' + (row.skuName || "") + '"】的覆盖？')
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
