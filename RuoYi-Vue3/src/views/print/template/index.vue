<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="模板名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入模板名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="绑定类型" prop="bindType">
        <el-select v-model="queryParams.bindType" placeholder="请选择绑定类型" clearable>
          <el-option label="客户+配送点组合" :value="1" />
          <el-option label="客户" :value="2" />
          <el-option label="全局默认" :value="3" />
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
          v-hasPermi="['print:template:add']"
          >新增</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          :icon="Edit"
          size="small"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['print:template:edit']"
          >修改</el-button
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
          v-hasPermi="['print:template:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Design"
          size="small"
          @click="openDesigner"
          >打开打印设计器</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模板编号" align="center" prop="code" width="180" />
      <el-table-column label="模板名称" align="center" prop="name" />
      <el-table-column label="类型" align="center" prop="type" width="90">
        <template #default="scope">
          <dict-tag :options="dict.type.t_print_template_type" :value="scope.row.type" />
        </template>
      </el-table-column>
      <el-table-column label="渲染引擎" align="center" prop="renderEngine" width="110" />
      <el-table-column label="绑定类型" align="center" width="130">
        <template #default="scope">
          <el-tag size="small" :type="bindTagType(scope.row.bindType)">{{ bindTypeText(scope.row.bindType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="客户ID" align="center" prop="customerId" width="90" />
      <el-table-column label="配送点ID" align="center" prop="deliveryPointId" width="100" />
      <el-table-column label="联数" align="center" prop="copies" width="70" />
      <el-table-column label="JimuReport模板ID" align="center" prop="content" width="190" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['print:template:edit']"
            >修改</el-button
          >
          <el-button
            size="small"
            link
            :icon="View"
            v-if="scope.row.content"
            @click="openView(scope.row)"
            >预览</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['print:template:remove']"
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

    <!-- 添加或修改打印模板对话框 -->
    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="模板编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入模板编号" />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="0">送货单</el-radio>
            <el-radio :value="1">汇总表</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="绑定类型" prop="bindType">
          <el-radio-group v-model="form.bindType">
            <el-radio :value="1">客户+配送点组合</el-radio>
            <el-radio :value="2">客户</el-radio>
            <el-radio :value="3">全局默认</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.bindType === 1 || form.bindType === 2" label="客户ID" prop="customerId">
          <el-input-number v-model="form.customerId" :min="0" :controls="false" placeholder="客户ID（0=通用）" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.bindType === 1" label="配送点ID" prop="deliveryPointId">
          <el-input-number v-model="form.deliveryPointId" :min="1" :controls="false" placeholder="配送点ID" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.bindType === 3" label="设为全局默认" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio value="1">是</el-radio>
            <el-radio value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联数（打印份数）" prop="copies">
          <el-input-number v-model="form.copies" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="JimuReport模板ID" prop="content">
          <el-input v-model="form.content" placeholder="在设计器中保存后复制模板ID到此处" />
          <div class="hint">在打印设计器中设计并保存报表，把报表 ID 填入此处；打印时按此 ID 渲染。</div>
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
import {
  pagePrintTemplate,
  getPrintTemplate,
  addPrintTemplate,
  updatePrintTemplate,
  delPrintTemplate,
} from "@/api/print/template";
import { getToken } from "@/utils/auth";
import { Search, Refresh, Plus, Delete, Edit, View, Brush } from "@element-plus/icons-vue";

export default {
  name: "PrintTemplate",
  dicts: ["t_print_template_type"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, View, Design: Brush };
  },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      templateList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        bindType: null,
      },
      form: {},
      rules: {
        code: [{ required: true, message: "模板编号不能为空", trigger: "blur" }],
        name: [{ required: true, message: "模板名称不能为空", trigger: "blur" }],
        bindType: [{ required: true, message: "请选择绑定类型", trigger: "change" }],
        copies: [{ required: true, message: "联数不能为空", trigger: "change" }],
      },
    };
  },
  created() {
    this.getPageList();
  },
  methods: {
    getPageList() {
      this.loading = true;
      pagePrintTemplate(this.queryParams).then((response) => {
        this.templateList = response.rows;
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
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    bindTypeText(bindType) {
      return { 1: "客户+配送点", 2: "客户", 3: "全局默认" }[bindType] || "-";
    },
    bindTagType(bindType) {
      return { 1: "warning", 2: "primary", 3: "success" }[bindType] || "info";
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        id: null,
        customerId: 0,
        code: null,
        name: null,
        content: null,
        type: 0,
        renderEngine: "jimureport",
        bindType: 3,
        deliveryPointId: null,
        copies: 1,
        isDefault: "0",
        remark: null,
      };
      this.resetForm("form");
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加打印模板";
    },
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getPrintTemplate(id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改打印模板";
      });
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updatePrintTemplate(this.form).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addPrintTemplate(this.form).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getPageList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除打印模板编号为"' + ids + '"的数据项？')
        .then(() => delPrintTemplate(ids))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 打开 JimuReport 在线打印设计器（新窗口） */
    openDesigner() {
      const token = getToken();
      window.open("/jmreport/list?token=" + token, "_blank");
    },
    /** 预览 JimuReport 模板视图 */
    openView(row) {
      const token = getToken();
      window.open("/jmreport/view/" + row.content + "?token=" + token, "_blank");
    },
  },
};
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
</style>
