<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="basic-sku-table"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="skuList"
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
      @batch-action="handleBatchAction"
    >
      <template #search>
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="60px">
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
          <el-form-item label="商品名称" prop="name">
            <el-input
              v-model="queryParams.name"
              placeholder="名称/助记码/别名"
              clearable
              style="width: 160px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="上架" prop="saleable">
            <el-select v-model="queryParams.saleable" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="有效" prop="valid">
            <el-select v-model="queryParams.valid" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="匹配" prop="matchedSpu">
            <el-select v-model="queryParams.matchedSpu" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #buttons>
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['product:sku:add']">新增</el-button>
        <el-dropdown split-button type="success" size="small" :disabled="ids.length == 0" @command="handleCommand">
          关联商品库
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="matched">批量关联</el-dropdown-item>
              <el-dropdown-item command="undoMatched">批量取消关联</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['product:sku:remove']">删除</el-button>
        <el-button type="info" plain :icon="UploadFilled" size="small" @click="handleImport">导入</el-button>
        <el-button type="warning" plain :icon="Download" size="small" @click="handleExport"
          v-hasPermi="['product:sku:export']">导出</el-button>
      </template>

      <!-- 列插槽 -->
      <template #col_category="{ row }">
        <span>{{ categoryFormatter(row) }}</span>
      </template>
      <template #col_weighted="{ row }">
        <el-tag :type="row.isWeighted == 1 ? 'warning' : 'info'" size="small">
          {{ row.isWeighted == 1 ? "称重" : "非称重" }}
        </el-tag>
      </template>
      <template #col_saleable="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.saleable" />
      </template>
      <template #col_valid="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.valid" />
      </template>
      <template #col_matched="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.spuId ? '1' : '0'" />
      </template>
      <template #col_op="{ row }">
        <el-button size="small" link :icon="Edit" @click="handleUpdate(row)"
          v-hasPermi="['product:sku:edit']">修改</el-button>
        <el-button size="small" link :icon="Delete" @click="handleDelete(row)"
          v-hasPermi="['product:sku:remove']">删除</el-button>
        <el-dropdown
          v-if="row.spuId ? true : false"
          size="small"
          @command="(command) => handleMoreCommand(command, row)"
        >
          <el-button size="small" link :icon="DArrowRight">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="undoMatched" :icon="Delete">取消关联</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </quick-table>

    <!-- 添加或修改商品信息对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
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
          />
        </el-form-item>
        <el-form-item prop="name">
          <template #label>
            商品名称
            <el-tooltip
              v-if="!form.categoryId"
              content="请先选择商品分类"
              placement="top"
            >
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-autocomplete
            v-model="form.name"
            @select="handleSelectSpu"
            @change="handleUpdateSkuName"
            @input="handleUpdateMnemonicCode"
            :fetch-suggestions="querySpuList"
            placeholder="请输入商品名称"
            :disabled="!form.categoryId"
            clearable
          ></el-autocomplete>
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input
            v-model="form.mnemonicCode"
            placeholder="自动根据商品名称生成，可手动修改"
            :disabled="form.id == null"
          />
        </el-form-item>
        <el-form-item label="商品规格" prop="specName">
          <el-input v-model="form.specName" placeholder="如：大果 / 5斤/箱" />
        </el-form-item>
        <el-form-item label="商品单位" prop="unit">
          <el-select
            v-model="form.unit"
            placeholder="请选择商品单位"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="dict in dict.type.t_sku_unit"
              :key="dict.value"
              :label="dict.label"
              :value="dict.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否称重" prop="isWeighted">
          <el-switch
            v-model="form.isWeighted"
            :active-value="1"
            :inactive-value="0"
            active-text="称重"
            inactive-text="非称重"
          />
          <div class="el-form-item-msg">称重商品按斤/公斤计价，非称重按固定包装（如箱）下单</div>
        </el-form-item>
        <el-form-item label="基础单位" prop="baseUnit">
          <el-select
            v-model="form.baseUnit"
            placeholder="跨SKU汇总的基础单位（可选）"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="dict in dict.type.t_sku_unit"
              :key="dict.value"
              :label="dict.label"
              :value="dict.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="换算率" prop="conversionRate">
          <el-input-number
            v-model="form.conversionRate"
            :min="0"
            :precision="2"
            placeholder="与基础单位的换算率（可选）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="参考售价" prop="salePrice">
          <el-input-number
            v-model="form.salePrice"
            :min="0"
            :precision="2"
            placeholder="仅展示，非交易价格"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="是否上架" prop="saleable">
          <el-radio-group v-model="form.saleable">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :value="parseInt(dict.value)"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :value="parseInt(dict.value)"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入内容"
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

    <!-- 批量关联商品库弹窗 -->
    <el-dialog
      v-model="matchDialog.open"
      title="批量关联商品库"
      width="1000px"
      append-to-body
    >
      <el-table ref="matchSkuTable" :data="selectedSkuList" row-key="columnId">
        <el-table-column
          label="序号"
          type="index"
          min-width="5%"
          class-name="allowDrag"
        />
        <el-table-column label="商品编号" prop="code" />
        <el-table-column label="商品分类" prop="categoryName" />
        <el-table-column label="商品名称" prop="name" />
        <el-table-column label="关联商品库" prop="spuId">
          <template #default="scope">
            <el-autocomplete
              v-model="scope.row.spuName"
              @select="handleSelectMatchSpu($event, scope.row)"
              :fetch-suggestions="
                (queryString, cb) =>
                  queryMatchSpuList(queryString, cb, scope.row)
              "
              placeholder="请输入商品名称"
              clearable
            >
            </el-autocomplete>
          </template>
        </el-table-column>
        <el-table-column label="商品单位" prop="unit" />
        <el-table-column label="商品规格" prop="specName" />
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitMatchForm">确 定</el-button>
          <el-button @click="matchDialog.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 商品导入对话框 -->
    <el-dialog
      :title="upload.title"
      v-model="upload.open"
      width="400px"
      append-to-body
    >
      <el-upload
        ref="upload"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url + '?updateSupport=' + upload.updateSupport"
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
            <el-link
              type="primary"
              :underline="false"
              style="font-size: 12px; vertical-align: baseline"
              @click="importTemplate"
              >下载模板</el-link
            >
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
  pageSku,
  getSku,
  delSku,
  addSku,
  updateSku,
  matchSku,
  undoMatchSku,
} from "@/api/product/sku";
import { listCategory } from "@/api/product/category";
import { listSpu } from "@/api/product/spu";
import { pinyin } from "pinyin-pro";
import { getToken } from "@/utils/auth";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";
import {
  Search,
  Refresh,
  Plus,
  Delete,
  Download,
  Edit,
  Upload,
  UploadFilled,
  DArrowRight,
  QuestionFilled,
} from "@element-plus/icons-vue";

export default {
  name: "Sku",
  dicts: ["t_sku_unit", "biz_yes_no"],
  mixins: [quickTableMixin],
  setup() {
    return {
      Search,
      Refresh,
      Plus,
      Delete,
      Download,
      Edit,
      Upload,
      UploadFilled,
      DArrowRight,
      QuestionFilled,
    };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 商品分类map
      categoryMap: {},
      // 商品分类树选项
      categoryOptions: [],
      // 商品信息表格数据
      skuList: [],
      // 选择的商品列表
      selectedSkuList: [],
      // 商品库列表数据
      formSpuList: [],
      // 筛选后 spu 列表
      filterSpuList: [],
      // 选中的商品分类
      querySelectedOptions: [],
      formSelectedOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 批量匹配弹出层
      matchDialog: {
        open: false,
        title: "批量匹配",
      },
      // 商品导入参数
      upload: {
        open: false,
        title: "",
        isUploading: false,
        headers: { Authorization: "Bearer " + getToken() },
        url: import.meta.env.VITE_APP_BASE_API + "/product/sku/importData",
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        categoryId: null,
        spuId: null,
        name: null,
        salePrice: null,
        saleable: null,
        valid: null,
        // 辅助查询是否关联spu
        matchedSpu: null,
      },
      // 表格列配置
      columns: [
        { field: "code", title: "商品编号", width: 110, align: "center", sortable: true },
        { field: "categoryId", title: "商品分类", minWidth: 110, slots: { default: "col_category" } },
        { field: "name", title: "商品名称", minWidth: 150, fixed: "left" },
        { field: "specName", title: "商品规格", minWidth: 100, showOverflow: true },
        { field: "unit", title: "单位", width: 70, align: "center" },
        { field: "isWeighted", title: "称重", width: 80, align: "center", slots: { default: "col_weighted" } },
        { field: "baseUnit", title: "基础单位", width: 90, align: "center" },
        { field: "conversionRate", title: "换算率", width: 80, align: "center" },
        {
          field: "salePrice",
          title: "参考售价",
          width: 90,
          align: "right",
          titleHelp: { content: "参考售价，仅展示，非交易价格", placement: "top" },
          slots: { default: "col_price" },
        },
        { field: "saleable", title: "上架", width: 70, align: "center", slots: { default: "col_saleable" } },
        { field: "valid", title: "有效", width: 70, align: "center", slots: { default: "col_valid" } },
        { field: "matchedSpu", title: "关联", width: 70, align: "center", slots: { default: "col_matched" } },
        { field: "remark", title: "备注", minWidth: 140, showOverflow: true },
        { field: "op", title: "操作", width: 180, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "match", label: "批量关联", type: "success", icon: "Link" },
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        categoryId: [
          { required: true, message: "商品分类不能为空", trigger: "change" },
        ],
        name: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        unit: [
          { required: true, message: "商品单位不能为空", trigger: "blur" },
        ],
        saleable: [
          { required: true, message: "是否上架不能为空", trigger: "blur" },
        ],
        valid: [
          { required: true, message: "是否有效不能为空", trigger: "change" },
        ],
      },
    };
  },
  watch: {
    // 监听 open 变化，如果为 false 清空表单
    open(val) {
      if (!val) {
        this.reset();
      }
    },
  },
  created() {
    this.getTreeselect();
    this.getPageList();
  },
  methods: {
    /** 分页查询商品信息列表 */
    getPageList() {
      this.loading = true;
      pageSku(this.queryParams).then((response) => {
        this.skuList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 批量操作条分发 */
    handleBatchAction(key, rows) {
      if (key === "match") {
        this.selectedSkuList = rows;
        this.ids = rows.map((r) => r.id);
        this.handleMatched();
      } else if (key === "delete") {
        this.handleDelete();
      }
    },
    /** 查询商品库列表 */
    querySpuList(queryString, cb) {
      // 获取过滤后列表
      this.filterSpuList = [];
      var results = [];
      // 如果输入框为空，直接返回空数组
      if (!queryString || queryString.trim() === "") {
        cb(results);
        return;
      }

      var param = {
        name: queryString,
        categoryId: this.form.categoryId,
      };
      listSpu(param)
        .then((response) => {
          results = response.data.map((spu) => ({
            value: spu.name,
            spuId: spu.id,
          }));
          this.filterSpuList = results;
          // 调用 callback 返回建议列表
          cb(results);
        })
        .catch((error) => {
          console.error("Error fetching SPUs:", error);
          // 如果请求失败，返回空数组 cb([]);
          cb([]);
        });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset(row) {
      this.form = {
        id: null,
        categoryId: null,
        spuId: null,
        name: null,
        mnemonicCode: null,
        unit: "斤",
        specName: null,
        isWeighted: 1,
        baseUnit: null,
        conversionRate: null,
        salePrice: 0,
        saleable: 1,
        valid: 1,
        isDeleted: 0,
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
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.selectedSkuList = selection;
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加商品信息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getSku(id).then((response) => {
        this.form = response.data;
        // 构造级联选择器选中的数据
        this.formSelectedOptions = this.fillWithParentCategoryId(
          this.categoryOptions,
          this.form.categoryId.toString()
        );
        this.open = true;
        this.title = "修改商品信息";
      });
    },
    /** 更多按钮操作 */
    handleMoreCommand(command, row) {
      switch (command) {
        case "undoMatched":
          this.handleUndoMatchedSku(row);
          break;
        default:
          break;
      }
    },
    /** 批量关联按钮 */
    handleCommand(command) {
      switch (command) {
        case "matched":
          this.handleMatched();
          break;
        case "undoMatched":
          this.handleUndoMatchedSku(null);
          break;
        default:
          console.log("未定义的操作");
      }
    },
    /** 批量匹配按钮操作 */
    handleMatched() {
      // 如果 selectedSkuList 里有 spuId 不为空的，提示
      if (this.selectedSkuList.some((sku) => sku.spuId)) {
        this.$modal.msgError("已匹配的商品不能进行批量匹配");
        return;
      }
      this.matchDialog.open = true;
    },
    /** 提交匹配商品库按钮 */
    submitMatchForm() {
      var data = { skuList: this.selectedSkuList };
      matchSku(data).then((response) => {
        this.$modal.msgSuccess("匹配成功");
        this.matchDialog.open = false;
        this.getPageList();
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateSku(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addSku(this.form).then((response) => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getPageList();
            });
          }
        }
      });
    },
    /** 批量取消匹配按钮操作 */
    handleUndoMatchedSku(row) {
      var param = { skuList: row && row.id ? [row] : this.selectedSkuList };
      this.$modal
        .confirm("是否确认取消匹配选中的商品信息？")
        .then(function () {
          return undoMatchSku(param);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("取消匹配成功");
        })
        .catch(() => {});
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除商品信息编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSku(ids);
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
        "product/sku/export",
        {
          ...this.queryParams,
        },
        `sku_${new Date().getTime()}.xlsx`
      );
    },
    /** 更新助记码，提取拼音首字母并转换为大写 */
    handleUpdateMnemonicCode() {
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
    /** 处理选择的spu选项 */
    handleSelectSpu(item) {
      if (!item) {
        this.form.spuId = null;
      }
      this.form.spuId = item.id;
    },
    /** 兜底方法，若未选中元素，再尝试填充 spu 信息 */
    handleUpdateSkuName(name) {
      // init spuId
      this.form.spuId = null;
      if (!name) {
        return;
      }
      if (this.filterSpuList.length === 0) {
        return;
      }
      const spuItem = this.filterSpuList.find((spu) => spu.value === name);
      if (spuItem) {
        this.form.spuId = spuItem.spuId;
      }
    },
    /** 处理级联选择器，取最后一个选项 */
    handleQueryCascaderChange(value) {
      this.queryParams.categoryId = value ? value[value.length - 1] : null;
      this.handleQuery();
    },
    handleFormOptionsChanged(value) {
      this.form.categoryId = value ? value[value.length - 1] : null;
    },
    /** 格式化商品分类 */
    categoryFormatter(row) {
      return this.categoryMap
        ? this.categoryMap[row.categoryId] || ""
        : row.categoryId;
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
    /** 选择匹配商品库 */
    handleSelectMatchSpu(item, row) {
      if (!item) {
        row.spuId = null;
      }
      row.spuId = item.spuId;
    },
    /** 查询匹配商品库列表 */
    queryMatchSpuList(queryString, cb, row) {
      // 获取过滤后列表
      this.filterSpuList = [];
      var results = [];
      // 如果输入框为空，直接返回空数组
      if (!queryString || queryString.trim() === "") {
        cb(results);
        return;
      }

      var param = {
        name: queryString,
        categoryId: row.categoryId,
      };
      listSpu(param)
        .then((response) => {
          results = response.data.map((spu) => ({
            value: spu.name,
            spuId: spu.id,
          }));
          this.filterSpuList = results;
          // 调用 callback 返回建议列表
          cb(results);
        })
        .catch((error) => {
          console.error("Error fetching SPUs:", error);
          // 如果请求失败，返回空数组 cb([]);
          cb([]);
        });
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "商品导入";
      this.upload.open = true;
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download(
        "product/sku/importTemplate",
        {},
        `productSku_template_${new Date().getTime()}.xlsx`
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
  },
};
</script>

<style scoped>
.el-form-item-msg {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 2px;
}
</style>
