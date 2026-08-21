<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="basic-sku-template-table"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="templateList"
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
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="60px">
          <el-form-item label="模板名称" prop="name">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入模板名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #buttons>
        <el-button
          type="primary"
          plain
          :icon="Plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['product:default-sku-template:add']"
          >新增模板</el-button
        >
        <el-button
          type="danger"
          plain
          :icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['product:default-sku-template:remove']"
          >删除</el-button
        >
      </template>

      <template #col_count="{ row }">
        <el-button link type="primary" size="small" @click="handleViewItems(row)">{{ row.skuCount || 0 }} 个</el-button>
      </template>
      <template #col_status="{ row }">
        <el-tag :type="row.status == 1 ? 'success' : 'info'" size="small">
          {{ row.status == 1 ? "启用" : "停用" }}
        </el-tag>
      </template>
      <template #col_op="{ row }">
        <el-button size="small" link :icon="Edit" @click="handleUpdate(row)"
          v-hasPermi="['product:default-sku-template:edit']">修改</el-button>
        <el-button size="small" link :icon="View" @click="handleViewItems(row)"
          v-hasPermi="['product:default-sku-template:query']">明细</el-button>
        <el-button size="small" link :icon="Delete" @click="handleDelete(row)"
          v-hasPermi="['product:default-sku-template:remove']">删除</el-button>
      </template>
    </quick-table>

    <!-- 新增/修改模板对话框 -->
    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="如：食堂常用商品 / 蔬菜周配" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模板SKU" prop="skuIds">
          <el-select
            v-model="form.skuIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可搜索选择多个标准SKU"
            style="width: 100%"
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
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 模板明细对话框 -->
    <el-dialog title="模板SKU明细" v-model="itemsOpen" width="680px" append-to-body>
      <el-table :data="itemsList" border>
        <el-table-column label="编码" align="center" prop="code" width="110" />
        <el-table-column label="商品名称" align="center" prop="name" min-width="140" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="specName" width="110" />
        <el-table-column label="单位" align="center" prop="unit" width="70" />
        <el-table-column label="参考售价" align="center" prop="salePrice" width="90" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import {
  listDefaultSkuTemplate,
  getDefaultSkuTemplate,
  listDefaultSkuTemplateItems,
  addDefaultSkuTemplate,
  updateDefaultSkuTemplate,
  delDefaultSkuTemplate,
} from "@/api/product/customerSku";
import { listSku } from "@/api/product/sku";
import { Search, Refresh, Plus, Delete, Edit, View } from "@element-plus/icons-vue";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";

export default {
  name: "DefaultSkuTemplate",
  mixins: [quickTableMixin],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, View };
  },
  data() {
    return {
      loading: false,
      showSearch: true,
      templateList: [],
      skuOptions: [],
      queryParams: {
        name: null,
        status: null,
      },
      // 表格列配置
      columns: [
        { field: "name", title: "模板名称", minWidth: 180, fixed: "left" },
        { field: "skuCount", title: "SKU数量", width: 100, align: "center", slots: { default: "col_count" } },
        { field: "status", title: "状态", width: 90, align: "center", slots: { default: "col_status" } },
        { field: "createdAt", title: "创建时间", width: 170, align: "center" },
        { field: "op", title: "操作", width: 180, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
      form: {},
      open: false,
      title: "",
      rules: {
        name: [{ required: true, message: "模板名称不能为空", trigger: "blur" }],
        skuIds: [{ required: true, type: "array", min: 1, message: "请至少选择一个SKU", trigger: "change" }],
      },
      itemsOpen: false,
      itemsList: [],
      skuMap: {},
    };
  },
  created() {
    this.getList();
    this.getSkuOptions();
  },
  methods: {
    getList() {
      this.loading = true;
      listDefaultSkuTemplate(this.queryParams).then((response) => {
        const list = response.data || [];
        // 客户端统计每个模板的SKU数量（模板接口返回头信息）
        Promise.all(
          list.map((tpl) =>
            listDefaultSkuTemplateItems(tpl.id).then((res) => {
              tpl.skuCount = (res.data || []).length;
            }).catch(() => {
              tpl.skuCount = 0;
            })
          )
        ).finally(() => {
          this.templateList = list;
          this.loading = false;
        });
      }).catch(() => {
        this.loading = false;
      });
    },
    getSkuOptions() {
      listSku({}).then((response) => {
        const list = response.data || [];
        this.skuOptions = list;
        this.skuMap = list.reduce((map, item) => {
          map[item.id] = item;
          return map;
        }, {});
      });
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
        status: 1,
        skuIds: [],
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
      this.title = "新增默认SKU模板";
    },
    handleUpdate(row) {
      this.reset();
      getDefaultSkuTemplate(row.id).then((response) => {
        this.form = {
          id: response.data.id,
          name: response.data.name,
          status: response.data.status,
          skuIds: [],
        };
        // 回显模板SKU
        return listDefaultSkuTemplateItems(row.id);
      }).then((res) => {
        this.form.skuIds = (res.data || []).map((item) => item.skuId);
        this.open = true;
        this.title = "修改默认SKU模板";
      }).catch(() => {});
    },
    handleViewItems(row) {
      listDefaultSkuTemplateItems(row.id).then((response) => {
        const items = response.data || [];
        this.itemsList = items.map((item) => this.skuMap[item.skuId]).filter(Boolean);
        this.itemsOpen = true;
      }).catch(() => {});
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (!valid) {
          return;
        }
        const payload = {
          name: this.form.name,
          status: this.form.status,
          skuIds: this.form.skuIds,
        };
        if (this.form.id != null) {
          updateDefaultSkuTemplate({ id: this.form.id, ...payload }).then(() => {
            this.$modal.msgSuccess("修改成功");
            this.open = false;
            this.getList();
          }).catch(() => {});
        } else {
          addDefaultSkuTemplate(payload).then(() => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          }).catch(() => {});
        }
      });
    },
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除选中的默认SKU模板？')
        .then(function () {
          return delDefaultSkuTemplate(ids);
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
