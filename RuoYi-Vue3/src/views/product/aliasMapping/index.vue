<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== 全局别名 ==================== -->
      <el-tab-pane label="全局别名" name="alias">
        <el-form :model="aliasQueryParams" ref="aliasQueryForm" size="small" :inline="true" label-width="68px">
          <el-form-item label="别名" prop="alias">
            <el-input v-model="aliasQueryParams.alias" placeholder="请输入别名" clearable @keyup.enter="aliasHandleQuery" />
          </el-form-item>
          <el-form-item label="类型" prop="aliasType">
            <el-select v-model="aliasQueryParams.aliasType" placeholder="请选择类型" clearable>
              <el-option v-for="item in aliasTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" size="small" @click="aliasHandleQuery">搜索</el-button>
            <el-button :icon="Refresh" size="small" @click="aliasResetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain :icon="Plus" size="small" @click="aliasHandleAdd"
              v-hasPermi="['product:alias:add']">新增</el-button>
          </el-col>
        </el-row>

        <el-table v-loading="aliasLoading" :data="aliasList">
          <el-table-column label="别名" align="center" prop="alias" :show-overflow-tooltip="true" />
          <el-table-column label="类型" align="center" prop="aliasType">
            <template #default="scope">{{ aliasTypeLabel(scope.row.aliasType) }}</template>
          </el-table-column>
          <el-table-column label="关联SKU" align="center" prop="skuName" :show-overflow-tooltip="true" />
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160">
            <template #default="scope">
              <el-button size="small" link :icon="Edit" @click="aliasHandleUpdate(scope.row)"
                v-hasPermi="['product:alias:edit']">修改</el-button>
              <el-button size="small" link :icon="Delete" @click="aliasHandleDelete(scope.row)"
                v-hasPermi="['product:alias:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 客户SKU映射 ==================== -->
      <el-tab-pane label="客户SKU映射" name="mapping">
        <el-form :model="mappingQueryParams" ref="mappingQueryForm" size="small" :inline="true" label-width="80px">
          <el-form-item label="客户" prop="customerId">
            <el-select v-model="mappingQueryParams.customerId" placeholder="请选择客户" clearable filterable>
              <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户叫法" prop="customerAlias">
            <el-input v-model="mappingQueryParams.customerAlias" placeholder="请输入客户叫法" clearable @keyup.enter="mappingHandleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" size="small" @click="mappingHandleQuery">搜索</el-button>
            <el-button :icon="Refresh" size="small" @click="mappingResetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain :icon="Plus" size="small" @click="mappingHandleAdd"
              v-hasPermi="['product:mapping:add']">新增</el-button>
          </el-col>
        </el-row>

        <el-table v-loading="mappingLoading" :data="mappingList">
          <el-table-column label="客户" align="center" prop="customerName" :show-overflow-tooltip="true" />
          <el-table-column label="客户叫法" align="center" prop="customerAlias" :show-overflow-tooltip="true" />
          <el-table-column label="我方SKU" align="center" prop="skuName" :show-overflow-tooltip="true" />
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160">
            <template #default="scope">
              <el-button size="small" link :icon="Edit" @click="mappingHandleUpdate(scope.row)"
                v-hasPermi="['product:mapping:edit']">修改</el-button>
              <el-button size="small" link :icon="Delete" @click="mappingHandleDelete(scope.row)"
                v-hasPermi="['product:mapping:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 临时商品 ==================== -->
      <el-tab-pane label="临时商品" name="temp">
        <el-form :model="tempQueryParams" ref="tempQueryForm" size="small" :inline="true" label-width="68px">
          <el-form-item label="名称" prop="name">
            <el-input v-model="tempQueryParams.name" placeholder="请输入商品名称" clearable @keyup.enter="tempHandleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" size="small" @click="tempHandleQuery">搜索</el-button>
            <el-button :icon="Refresh" size="small" @click="tempResetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain :icon="Plus" size="small" @click="tempHandleAdd"
              v-hasPermi="['product:temp:add']">新增</el-button>
          </el-col>
        </el-row>

        <el-table v-loading="tempLoading" :data="tempList">
          <el-table-column label="名称" align="center" prop="name" :show-overflow-tooltip="true" />
          <el-table-column label="规格" align="center" prop="spec" :show-overflow-tooltip="true" />
          <el-table-column label="单位" align="center" prop="unit" />
          <el-table-column label="默认单价" align="center" prop="defaultPrice" />
          <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220">
            <template #default="scope">
              <el-button size="small" link :icon="Promotion" @click="tempHandleConvert(scope.row)"
                v-hasPermi="['product:temp:convert']">转正</el-button>
              <el-button size="small" link :icon="Edit" @click="tempHandleUpdate(scope.row)"
                v-hasPermi="['product:temp:edit']">修改</el-button>
              <el-button size="small" link :icon="Delete" @click="tempHandleDelete(scope.row)"
                v-hasPermi="['product:temp:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 全局别名新增/修改对话框 -->
    <el-dialog :title="aliasTitle" v-model="aliasOpen" width="560px" append-to-body>
      <el-form ref="aliasForm" :model="aliasForm" :rules="aliasRules" label-width="90px">
        <el-form-item label="别名" prop="alias">
          <el-input v-model="aliasForm.alias" placeholder="请输入别名" />
        </el-form-item>
        <el-form-item label="别名类型" prop="aliasType">
          <el-select v-model="aliasForm.aliasType" placeholder="请选择别名类型">
            <el-option v-for="item in aliasTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联SKU" prop="skuId">
          <el-select v-model="aliasForm.skuId" placeholder="请选择SKU" filterable clearable style="width: 100%">
            <el-option v-for="item in skuOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="aliasSubmitForm">确 定</el-button>
          <el-button @click="aliasCancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 客户SKU映射新增/修改对话框 -->
    <el-dialog :title="mappingTitle" v-model="mappingOpen" width="560px" append-to-body>
      <el-form ref="mappingForm" :model="mappingForm" :rules="mappingRules" label-width="90px">
        <el-form-item label="客户" prop="customerId">
          <el-select v-model="mappingForm.customerId" placeholder="请选择客户" filterable clearable style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户叫法" prop="customerAlias">
          <el-input v-model="mappingForm.customerAlias" placeholder="请输入客户侧叫法/编码" />
        </el-form-item>
        <el-form-item label="我方SKU" prop="skuId">
          <el-select v-model="mappingForm.skuId" placeholder="请选择SKU" filterable clearable style="width: 100%">
            <el-option v-for="item in skuOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="mappingSubmitForm">确 定</el-button>
          <el-button @click="mappingCancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 临时商品新增/修改对话框 -->
    <el-dialog :title="tempTitle" v-model="tempOpen" width="560px" append-to-body>
      <el-form ref="tempForm" :model="tempForm" :rules="tempRules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="tempForm.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="规格" prop="spec">
          <el-input v-model="tempForm.spec" placeholder="请输入规格" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="tempForm.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="默认单价" prop="defaultPrice">
          <el-input-number v-model="tempForm.defaultPrice" controls-position="right" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="tempForm.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="tempSubmitForm">确 定</el-button>
          <el-button @click="tempCancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listAlias, getAlias, delAlias, addAlias, updateAlias } from "@/api/product/alias";
import { listMapping, getMapping, delMapping, addMapping, updateMapping } from "@/api/product/mapping";
import { listTemp, getTemp, delTemp, addTemp, updateTemp, convertTemp } from "@/api/product/temp";
import { listSku } from "@/api/product/sku";
import { listCustomer } from "@/api/partner/customer";
import { Search, Refresh, Plus, Edit, Delete, Promotion } from "@element-plus/icons-vue";

export default {
  name: "AliasMapping",
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, Promotion };
  },
  data() {
    return {
      activeTab: "alias",
      // 别名类型（写死，不引字典）
      aliasTypeOptions: [
        { label: "名称", value: 1 },
        { label: "拼音", value: 2 },
        { label: "英文缩写", value: 3 },
      ],
      // 下拉选项
      skuOptions: [],
      customerOptions: [],

      // 全局别名
      aliasLoading: false,
      aliasList: [],
      aliasQueryParams: {
        alias: null,
        aliasType: null,
      },
      aliasForm: {},
      aliasOpen: false,
      aliasTitle: "",
      aliasRules: {
        alias: [{ required: true, message: "别名不能为空", trigger: "blur" }],
        aliasType: [{ required: true, message: "别名类型不能为空", trigger: "change" }],
        skuId: [{ required: true, message: "关联SKU不能为空", trigger: "change" }],
      },

      // 客户SKU映射
      mappingLoading: false,
      mappingList: [],
      mappingQueryParams: {
        customerId: null,
        customerAlias: null,
      },
      mappingForm: {},
      mappingOpen: false,
      mappingTitle: "",
      mappingRules: {
        customerId: [{ required: true, message: "客户不能为空", trigger: "change" }],
        customerAlias: [{ required: true, message: "客户叫法不能为空", trigger: "blur" }],
        skuId: [{ required: true, message: "我方SKU不能为空", trigger: "change" }],
      },

      // 临时商品
      tempLoading: false,
      tempList: [],
      tempQueryParams: {
        name: null,
      },
      tempForm: {},
      tempOpen: false,
      tempTitle: "",
      tempRules: {
        name: [{ required: true, message: "商品名称不能为空", trigger: "blur" }],
      },
    };
  },
  created() {
    this.getSkuOptions();
    this.getCustomerOptions();
    this.getAliasList();
    this.getMappingList();
    this.getTempList();
  },
  methods: {
    /** 加载SKU下拉选项 */
    getSkuOptions() {
      listSku({}).then((response) => {
        this.skuOptions = response.data || [];
      });
    },
    /** 加载客户下拉选项 */
    getCustomerOptions() {
      listCustomer({}).then((response) => {
        this.customerOptions = response.data || [];
      });
    },
    /** 别名类型文案 */
    aliasTypeLabel(value) {
      const item = this.aliasTypeOptions.find((o) => o.value === value);
      return item ? item.label : value;
    },

    /** ==================== 全局别名 ==================== */
    getAliasList() {
      this.aliasLoading = true;
      listAlias(this.aliasQueryParams).then((response) => {
        this.aliasList = response.data || [];
        this.aliasLoading = false;
      }).catch(() => {
        this.aliasLoading = false;
      });
    },
    aliasHandleQuery() {
      this.getAliasList();
    },
    aliasResetQuery() {
      this.resetForm("aliasQueryForm");
      this.getAliasList();
    },
    aliasReset() {
      this.aliasForm = {
        id: null,
        aliasType: null,
        alias: null,
        skuId: null,
      };
      this.resetForm("aliasForm");
    },
    aliasCancel() {
      this.aliasOpen = false;
      this.aliasReset();
    },
    aliasHandleAdd() {
      this.aliasReset();
      this.aliasOpen = true;
      this.aliasTitle = "添加全局别名";
    },
    aliasHandleUpdate(row) {
      this.aliasReset();
      getAlias(row.id).then((response) => {
        this.aliasForm = response.data;
        this.aliasOpen = true;
        this.aliasTitle = "修改全局别名";
      });
    },
    aliasSubmitForm() {
      this.$refs["aliasForm"].validate((valid) => {
        if (valid) {
          if (this.aliasForm.id != null) {
            updateAlias(this.aliasForm).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.aliasOpen = false;
              this.getAliasList();
            });
          } else {
            addAlias(this.aliasForm).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.aliasOpen = false;
              this.getAliasList();
            });
          }
        }
      });
    },
    aliasHandleDelete(row) {
      this.$modal
        .confirm('是否确认删除别名【"' + row.alias + '"】的数据项？')
        .then(function () {
          return delAlias(row.id);
        })
        .then(() => {
          this.getAliasList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },

    /** ==================== 客户SKU映射 ==================== */
    getMappingList() {
      this.mappingLoading = true;
      listMapping(this.mappingQueryParams).then((response) => {
        this.mappingList = response.data || [];
        this.mappingLoading = false;
      }).catch(() => {
        this.mappingLoading = false;
      });
    },
    mappingHandleQuery() {
      this.getMappingList();
    },
    mappingResetQuery() {
      this.resetForm("mappingQueryForm");
      this.getMappingList();
    },
    mappingReset() {
      this.mappingForm = {
        id: null,
        customerId: null,
        customerAlias: null,
        skuId: null,
      };
      this.resetForm("mappingForm");
    },
    mappingCancel() {
      this.mappingOpen = false;
      this.mappingReset();
    },
    mappingHandleAdd() {
      this.mappingReset();
      this.mappingOpen = true;
      this.mappingTitle = "添加客户SKU映射";
    },
    mappingHandleUpdate(row) {
      this.mappingReset();
      getMapping(row.id).then((response) => {
        this.mappingForm = response.data;
        this.mappingOpen = true;
        this.mappingTitle = "修改客户SKU映射";
      });
    },
    mappingSubmitForm() {
      this.$refs["mappingForm"].validate((valid) => {
        if (valid) {
          if (this.mappingForm.id != null) {
            updateMapping(this.mappingForm).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.mappingOpen = false;
              this.getMappingList();
            });
          } else {
            addMapping(this.mappingForm).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.mappingOpen = false;
              this.getMappingList();
            });
          }
        }
      });
    },
    mappingHandleDelete(row) {
      this.$modal
        .confirm('是否确认删除客户叫法【"' + row.customerAlias + '"】的映射数据项？')
        .then(function () {
          return delMapping(row.id);
        })
        .then(() => {
          this.getMappingList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },

    /** ==================== 临时商品 ==================== */
    getTempList() {
      this.tempLoading = true;
      listTemp(this.tempQueryParams).then((response) => {
        this.tempList = response.data || [];
        this.tempLoading = false;
      }).catch(() => {
        this.tempLoading = false;
      });
    },
    tempHandleQuery() {
      this.getTempList();
    },
    tempResetQuery() {
      this.resetForm("tempQueryForm");
      this.getTempList();
    },
    tempReset() {
      this.tempForm = {
        id: null,
        name: null,
        spec: null,
        unit: null,
        defaultPrice: null,
        remark: null,
      };
      this.resetForm("tempForm");
    },
    tempCancel() {
      this.tempOpen = false;
      this.tempReset();
    },
    tempHandleAdd() {
      this.tempReset();
      this.tempOpen = true;
      this.tempTitle = "添加临时商品";
    },
    tempHandleUpdate(row) {
      this.tempReset();
      getTemp(row.id).then((response) => {
        this.tempForm = response.data;
        this.tempOpen = true;
        this.tempTitle = "修改临时商品";
      });
    },
    tempSubmitForm() {
      this.$refs["tempForm"].validate((valid) => {
        if (valid) {
          if (this.tempForm.id != null) {
            updateTemp(this.tempForm).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.tempOpen = false;
              this.getTempList();
            });
          } else {
            addTemp(this.tempForm).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.tempOpen = false;
              this.getTempList();
            });
          }
        }
      });
    },
    tempHandleDelete(row) {
      this.$modal
        .confirm('是否确认删除临时商品【"' + row.name + '"】的数据项？')
        .then(function () {
          return delTemp(row.id);
        })
        .then(() => {
          this.getTempList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    tempHandleConvert(row) {
      this.$modal
        .confirm('是否确认将临时商品【"' + row.name + '"】转正为正式SKU？')
        .then(function () {
          return convertTemp(row.id, {});
        })
        .then(() => {
          this.getTempList();
          this.$modal.msgSuccess("转正成功");
        })
        .catch(() => {});
    },
  },
};
</script>
