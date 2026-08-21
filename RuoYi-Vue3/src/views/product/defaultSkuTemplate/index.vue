<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="70px">
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
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
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
          v-hasPermi="['product:default-sku-template:add']"
          >新增模板</el-button
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
          v-hasPermi="['product:default-sku-template:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar :showSearch="showSearch" @update:showSearch="showSearch = $event" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="模板名称" align="center" prop="name" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="SKU数量" align="center" width="100">
        <template #default="scope">
          <el-button link type="primary" @click="handleViewItems(scope.row)">{{ scope.row.skuCount || 0 }} 个</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status == 1 ? 'success' : 'info'">
            {{ scope.row.status == 1 ? "启用" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createdAt" width="170" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
        <template #default="scope">
          <el-button size="small" link :icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['product:default-sku-template:edit']">修改</el-button>
          <el-button size="small" link :icon="View" @click="handleViewItems(scope.row)"
            v-hasPermi="['product:default-sku-template:query']">明细</el-button>
          <el-button size="small" link :icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['product:default-sku-template:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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

export default {
  name: "DefaultSkuTemplate",
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, View };
  },
  data() {
    return {
      loading: false,
      showSearch: true,
      templateList: [],
      skuOptions: [],
      ids: [],
      multiple: true,
      queryParams: {
        name: null,
        status: null,
      },
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
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
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
      const ids = row.id || this.ids;
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
