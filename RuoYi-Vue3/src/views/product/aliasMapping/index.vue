<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== 全局别名 ==================== -->
      <el-tab-pane label="全局别名" name="alias">
        <quick-table
          ref="aliasQuickTable"
          id="product-alias-table"
          v-model:showSearch="showAliasSearch"
          :columns="aliasColumns"
          :data="aliasList"
          :loading="aliasLoading"
          :show-pager="false"
          :batch-actions="[]"
          :shortcuts="false"
          @query="aliasHandleQuery"
          @reset="aliasResetQuery"
          @selection-change="aliasHandleSelectionChange"
          @add="aliasHandleAdd"
          @edit="aliasHandleUpdate"
          @delete="aliasHandleQuickDelete"
        >
          <template #search>
            <el-form :model="aliasQueryParams" ref="aliasQueryForm" size="small" :inline="true" label-width="80px">
              <el-form-item label="别名" prop="alias">
                <el-input v-model="aliasQueryParams.alias" placeholder="请输入别名" clearable style="width: 160px" @keyup.enter="aliasHandleQuery" />
              </el-form-item>
              <el-form-item label="类型" prop="aliasType">
                <el-select v-model="aliasQueryParams.aliasType" placeholder="全部" clearable style="width: 120px">
                  <el-option v-for="item in aliasTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-form>
          </template>

          <template #buttons>
            <el-button type="primary" plain :icon="Plus" size="small" @click="aliasHandleAdd"
              v-hasPermi="['product:alias:add']">新增</el-button>
          </template>

          <template #col_type="{ row }">{{ aliasTypeLabel(row.aliasType) }}</template>
          <template #col_op="{ row }">
            <el-button size="small" link :icon="Edit" @click="aliasHandleUpdate(row)"
              v-hasPermi="['product:alias:edit']">修改</el-button>
            <el-button size="small" link :icon="Delete" @click="aliasHandleDelete(row)"
              v-hasPermi="['product:alias:remove']">删除</el-button>
          </template>
        </quick-table>
      </el-tab-pane>

      <!-- ==================== 客户SKU映射 ==================== -->
      <el-tab-pane label="客户SKU映射" name="mapping">
        <quick-table
          ref="mappingQuickTable"
          id="product-mapping-table"
          v-model:showSearch="showMappingSearch"
          :columns="mappingColumns"
          :data="mappingList"
          :loading="mappingLoading"
          :show-pager="false"
          :batch-actions="[]"
          :shortcuts="false"
          @query="mappingHandleQuery"
          @reset="mappingResetQuery"
          @selection-change="mappingHandleSelectionChange"
          @add="mappingHandleAdd"
          @edit="mappingHandleUpdate"
          @delete="mappingHandleQuickDelete"
        >
          <template #search>
            <el-form :model="mappingQueryParams" ref="mappingQueryForm" size="small" :inline="true" label-width="80px">
              <el-form-item label="客户" prop="customerId">
                <el-select v-model="mappingQueryParams.customerId" placeholder="全部" clearable filterable style="width: 160px">
                  <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="客户叫法" prop="customerAlias">
                <el-input v-model="mappingQueryParams.customerAlias" placeholder="请输入客户叫法" clearable style="width: 160px" @keyup.enter="mappingHandleQuery" />
              </el-form-item>
            </el-form>
          </template>

          <template #buttons>
            <el-button type="primary" plain :icon="Plus" size="small" @click="mappingHandleAdd"
              v-hasPermi="['product:mapping:add']">新增</el-button>
          </template>

          <template #col_op="{ row }">
            <el-button size="small" link :icon="Edit" @click="mappingHandleUpdate(row)"
              v-hasPermi="['product:mapping:edit']">修改</el-button>
            <el-button size="small" link :icon="Delete" @click="mappingHandleDelete(row)"
              v-hasPermi="['product:mapping:remove']">删除</el-button>
          </template>
        </quick-table>
      </el-tab-pane>

      <!-- ==================== 临时商品 ==================== -->
      <el-tab-pane label="临时商品" name="temp">
        <quick-table
          ref="tempQuickTable"
          id="product-temp-table"
          v-model:showSearch="showTempSearch"
          :columns="tempColumns"
          :data="tempList"
          :loading="tempLoading"
          :show-pager="false"
          :batch-actions="[]"
          :shortcuts="false"
          @query="tempHandleQuery"
          @reset="tempResetQuery"
          @selection-change="tempHandleSelectionChange"
          @add="tempHandleAdd"
          @edit="tempHandleUpdate"
          @delete="tempHandleQuickDelete"
        >
          <template #search>
            <el-form :model="tempQueryParams" ref="tempQueryForm" size="small" :inline="true" label-width="80px">
              <el-form-item label="客户" prop="customerId">
                <el-select v-model="tempQueryParams.customerId" placeholder="全部（含客户专用）" clearable filterable style="width: 180px" @change="tempHandleQuery">
                  <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="名称" prop="name">
                <el-input v-model="tempQueryParams.name" placeholder="请输入商品名称" clearable style="width: 160px" @keyup.enter="tempHandleQuery" />
              </el-form-item>
            </el-form>
          </template>

          <template #buttons>
            <el-button type="primary" plain :icon="Plus" size="small" @click="tempHandleAdd"
              v-hasPermi="['product:temp:add']">新增</el-button>
          </template>

          <template #col_customer="{ row }">
            <el-tag v-if="!row.customerId" type="info" size="small">全局</el-tag>
            <span v-else>{{ customerName(row.customerId) }}</span>
          </template>
          <template #col_status="{ row }">
            <el-tag v-if="row.convertedSkuId" type="success" size="small">已转正</el-tag>
            <el-tag v-else type="warning" size="small">未转正</el-tag>
          </template>
          <template #col_op="{ row }">
            <el-button size="small" link type="primary" :icon="Promotion" @click="tempHandleConvert(row)"
              v-hasPermi="['product:temp:convert']" :disabled="row.convertedSkuId != null">转正</el-button>
            <el-button size="small" link :icon="Edit" @click="tempHandleUpdate(row)"
              v-hasPermi="['product:temp:edit']">修改</el-button>
            <el-button size="small" link :icon="Delete" @click="tempHandleDelete(row)"
              v-hasPermi="['product:temp:remove']">删除</el-button>
          </template>
        </quick-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 全局别名新增/修改对话框 -->
    <el-dialog align-center :title="aliasTitle" v-model="aliasOpen" width="560px" append-to-body>
      <el-form ref="aliasForm" :model="aliasForm" :rules="aliasRules" label-width="90px">
        <el-form-item label="别名" prop="alias">
          <el-input v-model="aliasForm.alias" placeholder="请输入别名" />
        </el-form-item>
        <el-form-item label="别名类型" prop="aliasType">
          <el-select v-model="aliasForm.aliasType" placeholder="请选择别名类型" style="width: 100%">
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
    <el-dialog align-center :title="mappingTitle" v-model="mappingOpen" width="560px" append-to-body>
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
    <el-dialog align-center :title="tempTitle" v-model="tempOpen" width="560px" append-to-body>
      <el-form ref="tempForm" :model="tempForm" :rules="tempRules" label-width="90px">
        <el-form-item label="所属客户" prop="customerId">
          <el-select v-model="tempForm.customerId" placeholder="不选=全局临时商品" clearable filterable style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <div class="form-tip">选择客户后，该临时商品仅该客户录单时可见；不选则所有客户可见</div>
        </el-form-item>
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

    <!-- 临时商品转正对话框（需选客户 + 分类） -->
    <el-dialog align-center title="临时商品转正为正式SKU" v-model="convertOpen" width="560px" append-to-body>
      <el-form ref="convertForm" :model="convertForm" :rules="convertRules" label-width="90px">
        <el-form-item label="临时商品">
          <el-input :value="convertForm.name" disabled />
        </el-form-item>
        <el-form-item label="归属客户" prop="customerId">
          <el-select v-model="convertForm.customerId" placeholder="请选择客户" filterable style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-cascader
            v-model="convertForm.categoryOptions"
            placeholder="请选择商品分类"
            :options="categoryOptions"
            @change="handleConvertCategoryChange"
            :props="{ expandTrigger: 'hover' }"
            :show-all-levels="false"
            filterable
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input v-model="convertForm.mnemonicCode" placeholder="不填则按商品名称拼音首字母生成" />
        </el-form-item>
        <el-alert type="info" :closable="false"
          title="转正将创建标准SKU（相同名称+规格+单位已存在则直接复用），并自动关联到该客户商品池" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="tempSubmitConvert">确 定</el-button>
          <el-button @click="convertOpen = false">取 消</el-button>
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
import { listCategory } from "@/api/product/category";
import { Search, Refresh, Plus, Edit, Delete, Promotion } from "@element-plus/icons-vue";

export default {
  name: "AliasMapping",
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, Promotion };
  },
  data() {
    return {
      activeTab: "alias",
      showAliasSearch: true,
      showMappingSearch: true,
      showTempSearch: true,
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
      // 别名表格列
      aliasColumns: [
        { field: "alias", title: "别名", minWidth: 160, fixed: "left" },
        { field: "aliasType", title: "类型", width: 110, align: "center", slots: { default: "col_type" } },
        { field: "skuName", title: "关联SKU", minWidth: 180 },
        { field: "op", title: "操作", width: 130, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],

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
      // 映射表格列
      mappingColumns: [
        { field: "customerName", title: "客户", minWidth: 160, fixed: "left" },
        { field: "customerAlias", title: "客户叫法", minWidth: 160 },
        { field: "skuName", title: "我方SKU", minWidth: 180 },
        { field: "op", title: "操作", width: 130, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],

      // 临时商品
      tempLoading: false,
      tempList: [],
      tempQueryParams: {
        customerId: null,
        name: null,
      },
      tempForm: {},
      tempOpen: false,
      tempTitle: "",
      tempRules: {
        name: [{ required: true, message: "商品名称不能为空", trigger: "blur" }],
      },
      // 临时商品表格列
      tempColumns: [
        { field: "name", title: "名称", minWidth: 140, fixed: "left" },
        { field: "spec", title: "规格", minWidth: 100, showOverflow: true },
        { field: "unit", title: "单位", width: 70, align: "center" },
        { field: "defaultPrice", title: "默认单价", width: 100, align: "right" },
        { field: "customerId", title: "所属客户", width: 130, align: "center", slots: { default: "col_customer" } },
        { field: "convertedSkuId", title: "状态", width: 100, align: "center", slots: { default: "col_status" } },
        { field: "remark", title: "备注", minWidth: 140, showOverflow: true },
        { field: "op", title: "操作", width: 220, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 转正
      convertOpen: false,
      convertForm: {},
      convertRules: {
        customerId: [{ required: true, message: "归属客户不能为空", trigger: "change" }],
        categoryId: [{ required: true, message: "商品分类不能为空", trigger: "change" }],
      },
      // 商品分类树
      categoryOptions: [],
    };
  },
  created() {
    this.getSkuOptions();
    this.getCustomerOptions();
    this.getCategoryOptions();
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
    /** 加载商品分类树（转正用） */
    getCategoryOptions() {
      listCategory().then((response) => {
        const treeList = this.handleTree(response.data || []);
        this.categoryOptions = this.transformTreeData(treeList);
      });
    },
    /** 树形列表转换为级联列表 */
    transformTreeData(data) {
      return data.map((item) => {
        const newItem = {
          value: item.id.toString(),
          label: item.name,
        };
        if (Array.isArray(item.children) && item.children.length > 0) {
          newItem.children = this.transformTreeData(item.children);
        }
        return newItem;
      });
    },
    /** 客户名 */
    customerName(id) {
      const item = this.customerOptions.find((o) => o.id === id);
      return item ? item.name : id;
    },
    /** 别名类型文案 */
    aliasTypeLabel(value) {
      const item = this.aliasTypeOptions.find((o) => o.value === value);
      return item ? item.label : value;
    },
    /** 各 tab 选择状态（QuickTable 直接回传行对象） */
    aliasHandleSelectionChange() {},
    mappingHandleSelectionChange() {},
    tempHandleSelectionChange() {},

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
    aliasHandleQuickDelete(ids) {
      if (ids && ids.length) {
        this.aliasHandleDelete({ id: ids[0] });
      }
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
    mappingHandleQuickDelete(ids) {
      if (ids && ids.length) {
        this.mappingHandleDelete({ id: ids[0] });
      }
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
      // showAll=true：包含已转正记录（默认只查未转正）
      const params = { ...this.tempQueryParams, showAll: true };
      listTemp(params).then((response) => {
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
    tempHandleQuickDelete(ids) {
      if (ids && ids.length) {
        this.tempHandleDelete({ id: ids[0] });
      }
    },
    tempReset() {
      this.tempForm = {
        id: null,
        customerId: null,
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
      this.convertForm = {
        id: row.id,
        name: row.name,
        customerId: row.customerId || null,
        categoryId: null,
        categoryOptions: [],
        mnemonicCode: null,
      };
      this.convertOpen = true;
    },
    /** 分类级联选择回调 */
    handleConvertCategoryChange(value) {
      this.convertForm.categoryId = value ? value[value.length - 1] : null;
    },
    /** 提交转正 */
    tempSubmitConvert() {
      this.$refs["convertForm"].validate((valid) => {
        if (!valid) {
          return;
        }
        convertTemp(this.convertForm.id, {
          customerId: this.convertForm.customerId,
          categoryId: this.convertForm.categoryId,
          mnemonicCode: this.convertForm.mnemonicCode || undefined,
        }).then(() => {
          this.$modal.msgSuccess("转正成功");
          this.convertOpen = false;
          this.getTempList();
        }).catch(() => {});
      });
    },
  },
};
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
