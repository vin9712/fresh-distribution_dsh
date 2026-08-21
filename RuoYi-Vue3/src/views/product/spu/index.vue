<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="basic-spu-table"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="spuList"
      :total="total"
      :loading="loading"
      :batch-actions="batchActions"
      @query="handleQuery"
      @reset="resetQuery"
      @page-change="getPageList"
      @selection-change="handleSelectionChange"
      @add="handleAdd"
      @edit="handleUpdate"
      @delete="handleQuickDelete"
      @batch-action="handleQuickBatchAction"
    >
      <template #search>
        <el-form
          :model="queryParams"
          ref="queryForm"
          size="small"
          :inline="true"
          label-width="60px"
        >
          <el-form-item label="商品名称" prop="name">
            <el-input
              v-model="queryParams.name"
              placeholder="商品名称/助记码"
              clearable
              style="width: 160px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="商品分类" prop="categoryId">
            <el-cascader
              v-model="querySelectedOptions"
              placeholder="请选择商品分类"
              @change="handleQueryCascaderChange"
              :options="categoryOptions"
              :props="{ expandTrigger: 'hover' }"
              :show-all-levels="false"
              filterable
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item label="上架" prop="saleable">
            <el-select
              v-model="queryParams.saleable"
              placeholder="全部"
              clearable
              style="width: 100px"
            >
              <el-option
                v-for="dict in dict.type.biz_yes_no"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="有效" prop="valid">
            <el-select
              v-model="queryParams.valid"
              placeholder="全部"
              clearable
              style="width: 100px"
            >
              <el-option
                v-for="dict in dict.type.biz_yes_no"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
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
          v-hasPermi="['product:spu:add']"
          >新增</el-button
        >
        <el-button
          type="success"
          plain
          :icon="Edit"
          size="small"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['product:spu:edit']"
          >修改</el-button
        >
        <el-button
          type="danger"
          plain
          :icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['product:spu:remove']"
          >删除</el-button
        >
        <el-button
          type="warning"
          plain
          :icon="Download"
          size="small"
          @click="handleExport"
          v-hasPermi="['product:spu:export']"
          >导出</el-button
        >
        <el-button
          type="info"
          plain
          :icon="Upload"
          size="small"
          @click="handleImport"
          v-hasPermi="['product:spu:import']"
          >导入</el-button
        >
      </template>

      <!-- 列插槽 -->
      <template #col_category="{ row }">
        <span>{{ categoryFormatter(row) }}</span>
      </template>
      <template #col_saleable="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.saleable" />
      </template>
      <template #col_valid="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.valid" />
      </template>
      <template #col_op="{ row }">
        <el-button
          size="small"
          link
          :icon="Edit"
          @click="handleUpdate(row)"
          v-hasPermi="['product:spu:edit']"
          >修改</el-button
        >
        <el-button
          size="small"
          link
          :icon="Delete"
          @click="handleDelete(row)"
          v-hasPermi="['product:spu:remove']"
          >删除</el-button
        >
      </template>
    </quick-table>

    <!-- 添加或修改商品spu对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="商品分类" prop="categoryId">
          <el-cascader
            v-model="formSelectedOptions"
            placeholder="请选择商品分类"
            :options="categoryOptions"
            @change="handleFormOptionsChanged"
            :props="{ expandTrigger: 'hover' }"
            :show-all-levels="false"
            filterable
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="商品名称" prop="name">
          <el-input
            v-model="form.name"
            @input="handleInputSpuName"
            placeholder="请输入商品名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            placeholder="请输入商品描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input
            v-model="form.mnemonicCode"
            placeholder="请输入助记码"
            maxlength="50"
            show-word-limit
            :disabled="form.id == null"
          />
        </el-form-item>
        <el-form-item label="是否上架" prop="saleable">
          <el-radio-group v-model="form.saleable">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :value="dict.value"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :value="dict.value"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 商品库导入对话框 -->
    <el-dialog :title="upload.title" v-model="upload.open" width="400px" append-to-body>
      <el-upload
        ref="upload"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url"
        :disabled="upload.isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleFileSuccess"
        :auto-upload="false"
        drag
      >
        <el-icon><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            <span>仅允许导入xls、xlsx格式文件。</span>
            <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="importTemplate">下载模板</el-link>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFileForm">确 定</el-button>
          <el-button @click="upload.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  listSpu,
  pageSpu,
  getSpu,
  delSpu,
  addSpu,
  updateSpu,
} from "@/api/product/spu";
import { listCategory } from "@/api/product/category";
import { pinyin } from "pinyin-pro";
import { getToken } from "@/utils/auth";
import { Search, Refresh, Plus, Edit, Delete, Download, Upload } from "@element-plus/icons-vue";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";

export default {
  name: "Spu",
  dicts: ["biz_yes_no"],
  mixins: [quickTableMixin],
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, Download, Upload };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 商品spu表格数据
      spuList: [],
      // 商品分类map
      categoryMap: [],
      // 商品分类树选项
      categoryOptions: [],
      // 选中的商品分类
      querySelectedOptions: [],
      formSelectedOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 商品库导入参数
      upload: {
        open: false,
        title: "",
        isUploading: false,
        headers: { Authorization: "Bearer " + getToken() },
        url: import.meta.env.VITE_APP_BASE_API + "/product/spu/importData",
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        categoryId: null,
        name: null,
        description: null,
        mnemonicCode: null,
        images: null,
        saleable: null,
        sort: null,
        valid: null,
      },
      // 表格列配置
      columns: [
        { field: "id", title: "主键", width: 80, align: "center", sortable: true },
        { field: "categoryId", title: "商品分类", minWidth: 110, slots: { default: "col_category" } },
        { field: "name", title: "商品名称", minWidth: 160, fixed: "left" },
        { field: "description", title: "商品描述", minWidth: 180, showOverflow: true },
        { field: "saleable", title: "是否上架", width: 90, align: "center", slots: { default: "col_saleable" } },
        { field: "valid", title: "是否有效", width: 90, align: "center", slots: { default: "col_valid" } },
        { field: "remark", title: "备注", minWidth: 140, showOverflow: true },
        { field: "op", title: "操作", width: 130, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        categoryId: [
          { required: true, message: "商品分类不能为空", trigger: "blur" },
        ],
        name: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        mnemonicCode: [
          { required: true, message: "助记码不能为空", trigger: "blur" },
        ],
        saleable: [
          { required: true, message: "是否上架不能为空", trigger: "change" },
        ],
        sort: [
          { required: true, message: "商品排序不能为空", trigger: "blur" },
        ],
        valid: [
          { required: true, message: "是否有效不能为空", trigger: "change" },
        ],
      },
    };
  },
  created() {
    this.getPageList();
    this.getTreeselect();
  },
  methods: {
    /** 查询商品spu列表 */
    getPageList() {
      this.loading = true;
      pageSpu(this.queryParams).then((response) => {
        this.spuList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        categoryId: null,
        name: null,
        description: null,
        mnemonicCode: null,
        images: null,
        saleable: "1",
        sort: 0,
        valid: "1",
        isDeleted: 0,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null,
      };
      this.resetForm("form");
      // 清空级联选择器
      this.formSelectedOptions = [];
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.querySelectedOptions = [];
      this.handleQuery();
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加商品spu";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getSpu(id).then((response) => {
        this.form = response.data;
        // 构造级联选择器选中的数据
        this.formSelectedOptions = this.fillWithParentCategoryId(
          this.categoryOptions,
          this.form.categoryId.toString()
        );
        this.open = true;
        this.title = "修改商品spu";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateSpu(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addSpu(this.form).then((response) => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getPageList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除商品spu编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSpu(ids);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download(
        "product/spu/export",
        {
          ...this.queryParams,
        },
        `spu_${new Date().getTime()}.xlsx`
      );
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "商品库导入";
      this.upload.open = true;
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download(
        "product/spu/importTemplate",
        {},
        `spu_template_${new Date().getTime()}.xlsx`
      );
    },
    // 文件上传中处理
    handleFileUploadProgress(event, file, fileList) {
      this.upload.isUploading = true;
    },
    // 文件上传成功处理
    handleFileSuccess(response, file, fileList) {
      this.upload.open = false;
      this.upload.isUploading = false;
      this.$refs.upload.clearFiles();
      this.$alert(
        "<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" +
          response.msg +
          "</div>",
        "导入结果",
        { dangerouslyUseHTMLString: true }
      );
      this.getPageList();
    },
    // 提交上传文件
    submitFileForm() {
      this.$refs.upload.submit();
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCategory().then((response) => {
        // init categoryMap
        this.categoryMap = response.data.reduce((map, item) => {
          map[item.id] = item.name;
          return map;
        });

        // init categoryOptions
        const treeList = this.handleTree(response.data);
        this.categoryOptions = this.transformData(treeList);
      });
    },
    /** 树形列表转换为级联列表 */
    transformData(data) {
      return data.map((item) => {
        const newItem = {
          value: item.id.toString(),
          label: item.name,
        };
        if (Array.isArray(item.children) && item.children.length > 0) {
          newItem.children = this.transformData(item.children);
        }
        return newItem;
      });
    },
    /** 更新助记码，提取拼音首字母并转换为大写 */
    handleInputSpuName() {
      const value = this.form.name;
      if (!value) {
        this.form.mnemonicCode = "";
        return;
      }
      this.form.mnemonicCode = pinyin(value, {
        pattern: "first",
        toneType: "none",
        type: "array",
      })
        .join("")
        .toUpperCase();
    },
    /** 格式化商品分类 */
    categoryFormatter(row) {
      return this.categoryMap
        ? this.categoryMap[row.categoryId] || ""
        : row.categoryId;
    },
    /** 处理级联选择器，取最后一个选项 */
    handleQueryCascaderChange(value) {
      this.queryParams.categoryId = value[value.length - 1];
      this.handleQuery();
    },
    handleFormOptionsChanged(value) {
      this.form.categoryId = value[value.length - 1];
    },
    /** 根据 id 构造父节点列表，并添加自身 */
    fillWithParentCategoryId(list, id) {
      if (!id) return [];
      function getParents(nodes, targetId, path = []) {
        for (const node of nodes) {
          path.push(node.value);
          if (
            node.value === targetId ||
            (node.children && getParents(node.children, targetId, path))
          ) {
            return path;
          }
          path.pop();
        }
        return null;
      }

      return getParents(list, id) || [];
    },
  },
};
</script>
