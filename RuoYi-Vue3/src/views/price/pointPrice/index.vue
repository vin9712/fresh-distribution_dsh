<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
      <el-form-item label="配送点" prop="deliveryPointId">
        <el-select v-model="queryParams.deliveryPointId" placeholder="请选择配送点" clearable filterable>
          <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="SKU名称" prop="skuName">
        <el-input v-model="queryParams.skuName" placeholder="请输入SKU名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['price:point:add']">新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="pointPriceList">
      <el-table-column label="配送点名称" align="center" prop="deliveryPointName" :show-overflow-tooltip="true" />
      <el-table-column label="SKU名称" align="center" prop="skuName" :show-overflow-tooltip="true" />
      <el-table-column label="单价" align="center" prop="unitPrice" />
      <el-table-column label="有效期" align="center">
        <template #default="scope">{{ formatRange(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="140">
        <template #default="scope">
          <el-button size="small" link :icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['price:point:edit']">修改</el-button>
          <el-button size="small" link :icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['price:point:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改配送点报价对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="配送点" prop="deliveryPointId">
          <el-select v-model="form.deliveryPointId" placeholder="请选择配送点" filterable clearable style="width: 100%">
            <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="SKU" prop="skuId">
          <el-select v-model="form.skuId" placeholder="请选择SKU" filterable clearable style="width: 100%">
            <el-option v-for="item in skuOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number v-model="form.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" />
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
  </div>
</template>

<script>
import { listPointPrice, getPointPrice, delPointPrice, addPointPrice, updatePointPrice } from "@/api/price/pointPrice";
import { listCustomerDept } from "@/api/partner/customerDept";
import { listSku } from "@/api/product/sku";
import { Search, Refresh, Plus, Edit, Delete } from "@element-plus/icons-vue";

export default {
  name: "PointPrice",
  setup() {
    return { Search, Refresh, Plus, Edit, Delete };
  },
  data() {
    return {
      loading: false,
      pointPriceList: [],
      queryParams: {
        deliveryPointId: null,
        skuName: null,
      },
      deptOptions: [],
      skuOptions: [],
      form: {},
      open: false,
      title: "",
      rules: {
        deliveryPointId: [{ required: true, message: "配送点不能为空", trigger: "change" }],
        skuId: [{ required: true, message: "SKU不能为空", trigger: "change" }],
        unitPrice: [{ required: true, message: "单价不能为空", trigger: "blur" }],
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
      listPointPrice({ deliveryPointId: this.queryParams.deliveryPointId || undefined }).then((response) => {
        let list = response.data || [];
        if (this.queryParams.skuName) {
          const kw = this.queryParams.skuName;
          list = list.filter((item) => (item.skuName || "").indexOf(kw) !== -1);
        }
        this.pointPriceList = list;
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
        deliveryPointId: null,
        skuId: null,
        unitPrice: null,
        effectiveDate: null,
        expireDate: null,
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
      this.title = "添加配送点报价";
    },
    handleUpdate(row) {
      this.reset();
      getPointPrice(row.id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改配送点报价";
      });
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updatePointPrice(this.form).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addPointPrice(this.form).then(() => {
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
        .confirm('是否确认删除配送点【"' + row.deliveryPointName + '"】对 SKU【"' + row.skuName + '"】的报价？')
        .then(function () {
          return delPointPrice(row.id);
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
