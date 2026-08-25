<template>
  <div class="app-container">
    <quick-table
      ref="quickTable"
      id="basic-quote-table"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      v-model:showSearch="showSearch"
      :columns="columns"
      :data="quoteList"
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
        <el-form :model="queryParams" ref="queryForm" :rules="queryFormRules" size="small" :inline="true"
          label-width="80px">
          <el-form-item label="报价客户" prop="customerId">
            <el-select v-model="queryParams.customerId" filterable clearable style="width: 150px">
              <el-option
                v-for="item in customerOptions"
                :key="item.id"
                :label="item.alias ? item.alias : item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="报价编号" prop="code">
            <el-input
              v-model="queryParams.code"
              placeholder="请输入报价编号"
              clearable
              style="width: 150px"
              @keyup.enter="handleQuery"
            />
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
          <el-form-item label="报价时间" prop="createTimeRange">
            <el-date-picker
              v-model="queryParams.createTimeRange"
              type="daterange"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :picker-options="datePickerOptions"
              :default-time="['00:00:00', '23:59:59']"
              style="width: 230px"
            >
            </el-date-picker>
          </el-form-item>
          <el-form-item label="生效时间" prop="effectiveDateRange">
            <el-date-picker
              clearable
              v-model="queryParams.effectiveDateRange"
              type="daterange"
              unlink-panels
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :picker-options="datePickerOptions"
              style="width: 230px"
            >
            </el-date-picker>
          </el-form-item>
        </el-form>
      </template>

      <template #buttons>
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd"
          v-hasPermi="['product:quote:add']">新增报价</el-button>
        <el-button type="success" plain :icon="Edit" size="small" :disabled="single" @click="handleUpdate"
          v-hasPermi="['product:quote:edit']">修改</el-button>
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['product:quote:remove']">删除</el-button>
        <el-button type="warning" plain :icon="Download" size="small" @click="handleExport"
          v-hasPermi="['product:quote:export']">导出</el-button>
        <el-button type="info" plain :icon="Upload" size="small" @click="handleImport"
          v-hasPermi="['product:quote:import']">导入</el-button>
        <el-button type="warning" plain :icon="DocumentAdd" size="small" @click="handlePriceImport"
          v-hasPermi="['product:quote:import']">导入价格表</el-button>
      </template>

      <!-- 列插槽 -->
      <template #col_customer="{ row }">
        <span>{{ formatCustomerName(row) }}</span>
      </template>
      <template #col_start="{ row }">
        <span>{{ parseTime(row.effectiveStartDate, "{y}-{m}-{d}") }}</span>
      </template>
      <template #col_end="{ row }">
        <span>{{ parseTime(row.effectiveEndDate, "{y}-{m}-{d}") }}</span>
        <el-tag
          v-if="isQuoteExpired(row)"
          type="danger"
          size="small"
          style="margin-left: 6px"
          >已过期</el-tag
        >
      </template>
      <template #col_status="{ row }">
        <dict-tag :options="dict.type.t_sku_quote_status" :value="row.status" />
      </template>
      <template #col_valid="{ row }">
        <dict-tag :options="dict.type.biz_yes_no" :value="row.valid" />
      </template>
      <template #col_op="{ row }">
        <el-button size="small" link :icon="View" @click="handleView(row)">查看</el-button>
        <el-button size="small" link :icon="DocumentCopy" @click="handleCopy(row)"
          v-hasPermi="['product:quote:edit']">复制</el-button>
        <!-- 变更状态 -->
        <el-button
          v-if="row.status == quoteStatus.NEW.code"
          size="small"
          link
          :icon="Operation"
          @click="handleUpdateStatus(row, quoteStatus.PUBLISHED.name)"
          v-hasPermi="['product:quote:edit']"
          >发布</el-button
        >
        <el-dropdown
          size="small"
          v-if="row.valid == '0' && row.status == quoteStatus.PUBLISHED.code"
          @command="(command) => handleStatusCommand(command, row)"
        >
          <el-button size="small" link :icon="Link">取消</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="revoke">撤销</el-dropdown-item>
              <el-dropdown-item command="invalid">失效</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 更多操作 -->
        <el-dropdown
          v-if="row.valid == '0'"
          size="small"
          @command="(command) => handleMoreCommand(command, row)"
        >
          <el-button size="small" link :icon="DArrowRight">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                command="edit"
                :icon="Edit"
                v-hasPermi="['product:quote:edit']"
                >修改报价</el-dropdown-item
              >
              <el-dropdown-item
                command="delete"
                :icon="Delete"
                v-hasPermi="['product:quote:remove']"
                >删除报价</el-dropdown-item
              >
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </quick-table>

    <!-- 客户报价导入对话框 -->
    <el-dialog align-center :title="upload.title" v-model="upload.open" width="400px" append-to-body>
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
            <span>仅允许导入xls、xlsx格式文件；多客户多行，按客户聚合生成报价单。</span>
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
    <!-- 价格表导入向导 -->
    <el-dialog align-center :title="priceImport.step === 1 ? '导入价格表 - 粘贴' : '导入价格表 - 确认匹配'" v-model="priceImport.open" width="860px" append-to-body>
      <template v-if="priceImport.step === 1">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
          <template #title>
            每行：「商品叫法 [单位] 价格」，如「土豆 2.5」「黄心土豆 斤 2.5」「黄心土豆/5斤装 箱 45」；单位可省略；系统自动按名称/助记码/别名匹配标准SKU，下一步可人工修正。
          </template>
        </el-alert>
        <el-form label-width="80px">
          <el-form-item label="客户" required>
            <el-select v-model="priceImport.customerId" placeholder="请选择客户" filterable style="width: 100%">
              <el-option
                v-for="item in customerOptions"
                :key="item.id"
                :label="item.alias ? item.alias + '（' + item.name + '）' : item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="价格表">
            <el-input
              v-model="priceImport.text"
              type="textarea"
              :rows="12"
              placeholder="土豆 2.5\n西红柿 3.8\n黄心土豆 45\nPG 1.2"
            />
            <div style="margin-top: 8px">
              <el-upload
                :show-file-list="false"
                accept=".xlsx,.xls"
                :http-request="uploadPriceExcel"
              >
                <el-button size="small" type="primary" plain :icon="Upload" :loading="priceImport.uploading">
                  {{ priceImport.uploading ? "解析中..." : "上传Excel解析" }}
                </el-button>
              </el-upload>
              <el-button size="small" link type="primary" @click="downloadPriceTemplate">下载模板</el-button>
              <span class="el-form-item-msg">
                支持 .xlsx/.xls；每行：名称 [单位] 价格，单位选填（斤/箱/袋等常见单位自动识别）
              </span>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 12px">
          <span style="font-size: 13px; color: #909399">
            共 {{ priceImport.rows.length }} 行：已匹配 {{ matchedRowCount }} 条，未匹配 {{ unmatchedRows.length }} 条；已匹配行可直接改选SKU；未匹配行处理方式：
          </span>
          <el-radio-group v-model="priceImport.unmatchedMode" size="small">
            <el-radio-button value="batchCreate">批量建品</el-radio-button>
            <el-radio-button value="toTemp">转临时商品</el-radio-button>
            <el-radio-button value="skip">跳过</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="previewRows" height="380" size="small" border>
          <el-table-column label="行号" prop="lineNo" width="55" align="center" />
          <el-table-column label="原始名" prop="rawName" min-width="120" show-overflow-tooltip />
          <el-table-column label="挂钩SKU" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.skuId" filterable clearable size="small" placeholder="未匹配，点击选择" style="width: 100%">
                <el-option
                  v-for="item in allSkuOptions"
                  :key="item.id"
                  :label="item.code + ' ' + item.name + (item.specName ? '（' + item.specName + '）' : '')"
                  :value="item.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="priceImport.unmatchedMode === 'batchCreate'" label="标准名" min-width="130">
            <template #default="{ row }">
              <el-input
                v-if="!row.skuId && !row.error"
                v-model="row.standardName"
                size="small"
                placeholder="建品标准名"
              />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column v-if="priceImport.unmatchedMode === 'batchCreate'" label="分类" min-width="150">
            <template #default="{ row }">
              <el-tree-select
                v-if="!row.skuId && !row.error"
                v-model="row.categoryId"
                :data="categoryOptions"
                :props="{ value: 'id', label: 'name', children: 'children' }"
                value-key="id"
                size="small"
                filterable
                check-strictly
                placeholder="选择分类"
                style="width: 100%"
              />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ row.unit || skuUnitOf(row) }}</template>
          </el-table-column>
          <el-table-column label="规格" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ skuSpecOf(row) }}</template>
          </el-table-column>
          <el-table-column label="价格" prop="price" width="90" align="right" />
          <el-table-column label="状态" width="130" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.error" type="danger" size="small">{{ row.error }}</el-tag>
              <el-tag v-else-if="row.skuId" type="success" size="small">已匹配（{{ row.matchType }}）</el-tag>
              <el-tag v-else type="info" size="small">{{
                priceImport.unmatchedMode === "batchCreate"
                  ? "批量建品"
                  : priceImport.unmatchedMode === "toTemp"
                  ? "转临时商品"
                  : "跳过"
              }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="priceImport.step === 2" @click="priceImport.step = 1">上一步</el-button>
          <el-button v-if="priceImport.step === 1" type="primary" :loading="priceImport.submitting" @click="parsePriceImport">解析预览</el-button>
          <el-button v-if="priceImport.step === 2" type="primary" :loading="priceImport.submitting" @click="submitPriceImport">确认导入</el-button>
          <el-button @click="priceImport.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageQuote,
  listQuote,
  getQuote,
  delQuote,
  addQuote,
  updateQuote,
  updateQuoteStatus,
  previewQuoteImport,
  confirmQuoteImport,
  previewQuoteImportExcel,
} from "@/api/product/quote";
import { listCustomer } from "@/api/partner/customer";
import { listSku } from "@/api/product/sku";
import { listCategory } from "@/api/product/category";
import { getToken } from "@/utils/auth";
import quickTableMixin from "@/components/QuickTable/quickTableMixin";
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Download,
  View,
  DocumentCopy,
  Operation,
  Link,
  DArrowRight,
  Upload,
  DocumentAdd,
} from "@element-plus/icons-vue";

// 初始化生效日期：获取当前日期和未来7天后的日期
function getEffectiveDateRange() {
  const now = new Date();
  const sevenDaysLater = new Date(now.getTime() + 3600 * 1000 * 24 * 7);
  return [now, sevenDaysLater];
}

export default {
  name: "SkuQuote",
  dicts: ["biz_yes_no", "t_sku_quote_status"],
  mixins: [quickTableMixin],
  setup() {
    return {
      Search,
      Refresh,
      Plus,
      Edit,
      Delete,
      Download,
      View,
      DocumentCopy,
      Operation,
      Link,
      DArrowRight,
      Upload,
      DocumentAdd,
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
      // 客户列表数据
      customerOptions: [],
      // 商品报价表格数据
      quoteList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 客户报价导入参数
      upload: {
        // 是否显示弹出层（报价导入）
        open: false,
        // 弹出层标题（报价导入）
        title: "",
        // 是否禁用上传
        isUploading: false,
        // 设置上传的请求头部
        headers: { Authorization: "Bearer " + getToken() },
        // 上传的地址
        url: import.meta.env.VITE_APP_BASE_API + "/product/quote/importData",
      },
      // 价格表导入向导
      priceImport: {
        open: false,
        step: 1,
        customerId: null,
        text: "",
        unmatchedMode: "batchCreate", // batchCreate=批量建品 | toTemp=转临时 | skip=跳过
        rows: [],
        uploading: false,
        submitting: false,
      },
      // 商品分类树（批量建品时选择分类用）
      categoryOptions: [],
      // 全部标准SKU（价格表导入时改选用）
      allSkuOptions: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: null,
        effectiveStartDate: null,
        effectiveEndDate: null,
        valid: null,

        // 额外查询参数
        effectiveDateRange: getEffectiveDateRange(),
        createTimeRange: [],
        createTimeStart: null,
        createTimeEnd: null,
      },
      // 查询校验
      queryFormRules: {
        effectiveDateRange: [
          {
            required: true,
            message: "请选择报价生效时间",
            trigger: "change",
          },
        ],
      },
      // 表单参数
      form: {},
      // 日期选择器
      datePickerOptions: {
        shortcuts: [
          {
            text: "最近一周",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
              picker.$emit("pick", [start, end]);
            },
          },
          {
            text: "最近一个月",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
              picker.$emit("pick", [start, end]);
            },
          },
          {
            text: "最近三个月",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 90);
              picker.$emit("pick", [start, end]);
            },
          },
        ],
      },
      // 报价状态枚举
      quoteStatus: {
        NEW: { code: 0, description: "新增", name: "NEW" },
        PUBLISHED: { code: 1, description: "发布", name: "PUBLISHED" },
        INVALID: { code: 3, description: "失效", name: "INVALID" },
      },
      // 表格列配置
      columns: [
        { field: "customerId", title: "客户名称", width: 110, align: "center", slots: { default: "col_customer" } },
        { field: "code", title: "报价编号", width: 150, align: "center", sortable: true },
        { field: "effectiveStartDate", title: "报价生效时间", width: 140, align: "center", slots: { default: "col_start" } },
        { field: "effectiveEndDate", title: "报价结束时间", width: 170, align: "center", slots: { default: "col_end" } },
        { field: "status", title: "状态", width: 90, align: "center", slots: { default: "col_status" } },
        { field: "valid", title: "有效", width: 80, align: "center", slots: { default: "col_valid" } },
        { field: "createTime", title: "创建时间", width: 110, align: "center" },
        { field: "remark", title: "备注", minWidth: 120, showOverflow: true },
        { field: "op", title: "操作", width: 250, fixed: "right", align: "center", slots: { default: "col_op" } },
      ],
      // 批量操作条
      batchActions: [
        { key: "delete", label: "删除", type: "danger", icon: "Delete" },
      ],
    };
  },
  watch: {
    // 监视 effectiveDateRange, 填充 startDate 和 endDate
    "queryParams.effectiveDateRange": {
      handler(newVal) {
        if (newVal && newVal.length === 2) {
          this.queryParams.effectiveStartDate = this.formatDate(newVal[0]);
          this.queryParams.effectiveEndDate = this.formatDate(newVal[1]);
        } else {
          this.queryParams.effectiveStartDate = null;
          this.queryParams.effectiveEndDate = null;
        }
      },
      deep: true,
    },
    "queryParams.createTimeRange": {
      handler(newVal) {
        if (newVal && newVal.length === 2) {
          this.queryParams.createTimeStart = this.formatDateTime(newVal[0]);
          this.queryParams.createTimeEnd = this.formatDateTime(newVal[1]);
        } else {
          this.queryParams.createTimeStart = null;
          this.queryParams.createTimeEnd = null;
        }
      },
      deep: true,
    },
  },
  computed: {
    /** 已匹配行数 */
    matchedRowCount() {
      return this.priceImport.rows.filter((r) => r.skuId).length;
    },
    /** 未匹配行 */
    unmatchedRows() {
      return this.priceImport.rows.filter((r) => !r.skuId);
    },
    previewRows() {
      return this.priceImport.rows;
    },
  },
  created() {
    this.getCustomerList();
    this.getPageList();
  },
  methods: {
    /** 查询商品报价列表 */
    getList() {
      this.loading = true;
      listQuote(this.queryParams).then((response) => {
        this.quoteList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询商品报价列表 */
    getPageList() {
      this.loading = true;
      pageQuote(this.queryParams).then((response) => {
        this.quoteList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
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
        customerId: null,
        effectiveStartDate: null,
        effectiveEndDate: null,
        valid: null,
        isDeleted: null,
        version: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null,
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.$refs["queryForm"].validate((valid) => {
        if (valid) {
          this.queryParams.pageNum = 1;
          this.getPageList();
        }
      });
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 查看详情 */
    handleView(row) {
      this.$router.push({
        path: "/basicInfo/quote-detail/index/" + row.customerId,
        query: { quoteId: row.id, mode: "view" },
      });
    },
    /** 变更状态操作 */
    handleStatusCommand(command, row) {
      switch (command) {
        case "revoke":
          this.handleUpdateStatus(row, this.quoteStatus.NEW.name);
          break;
        case "invalid":
          this.handleUpdateStatus(row, this.quoteStatus.INVALID.name);
          break;
        default:
          break;
      }
    },
    /** 更多按钮操作 */
    handleMoreCommand(command, row) {
      switch (command) {
        case "edit":
          this.handleUpdate(row);
          break;
        case "delete":
          this.handleDelete(row);
          break;
        default:
          break;
      }
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加商品报价";
      if (!this.queryParams.customerId) {
        this.$modal.msgError("请先选择报价客户");
        return;
      }
      // 跳转到新增详情
      this.$router.push({
        path: "/basicInfo/quote-detail/index/" + this.queryParams.customerId,
        query: { quoteId: null, mode: "add" },
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.$router.push({
        path: "/basicInfo/quote-detail/index/" + row.customerId,
        query: { quoteId: row.id, mode: "edit" },
      });
    },
    /** 复制按钮操作 */
    handleCopy(row) {
      this.$router.push({
        path: "/basicInfo/quote-detail/index/" + row.customerId,
        query: { quoteId: row.id, mode: "copy" },
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = (row && row.id) || this.ids;
      this.$modal
        .confirm('是否确认删除商品报价编号为"' + ids + '"的数据项？')
        .then(function () {
          return delQuote(ids);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 变更状态按钮操作 */
    handleUpdateStatus(row, status) {
      let param = {
        quoteId: row.id,
        status: status,
      };
      updateQuoteStatus(param).then((res) => {
        this.getPageList();
        this.$modal.msgSuccess("变更状态成功");
      });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download(
        "product/quote/export",
        {
          ...this.queryParams,
        },
        `quote_${new Date().getTime()}.xlsx`
      );
    },
    /** 格式化日期为 yyyy-MM-dd 格式 */
    formatDate(date) {
      if (!(date instanceof Date) || isNaN(date.getTime())) {
        return "Invalid Date";
      }

      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");

      return `${year}-${month}-${day}`;
    },
    /** 格式化日期时间为 yyyy-MM-dd HH:mm:ss 格式 */
    formatDateTime(date) {
      if (!(date instanceof Date) || isNaN(date.getTime())) {
        return "Invalid Date";
      }

      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      const hours = String(date.getHours()).padStart(2, "0");
      const minutes = String(date.getMinutes()).padStart(2, "0");
      const seconds = String(date.getSeconds()).padStart(2, "0");
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    },
    /** 格式化表格客户名称 */
    formatCustomerName(row) {
      const customer = this.customerOptions.find(
        (customer) => customer.id === row.customerId
      );
      return customer ? (customer.alias ? customer.alias : customer.name) : "";
    },
    /** 报价是否已过期（失效日早于今天） */
    isQuoteExpired(row) {
      if (!row.effectiveEndDate) {
        return false;
      }
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const end = new Date(row.effectiveEndDate);
      return end < today;
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "客户报价导入";
      this.upload.open = true;
    },
    /** 价格表导入向导：打开 */
    handlePriceImport() {
      this.priceImport.customerId = this.queryParams.customerId || null;
      this.priceImport.text = "";
      this.priceImport.rows = [];
      this.priceImport.step = 1;
      this.priceImport.unmatchedMode = "batchCreate";
      if (this.allSkuOptions.length === 0) {
        listSku({}).then((response) => {
          this.allSkuOptions = response.data || [];
        });
      }
      this.loadCategoryOptions();
      this.priceImport.open = true;
    },
    /** 加载商品分类树（批量建品选择分类用，带缓存） */
    loadCategoryOptions() {
      if (this.categoryOptions.length > 0) return;
      listCategory().then((response) => {
        this.categoryOptions = this.handleTree(response.data || []);
      });
    },
    /** 下载价格表导入模板 */
    downloadPriceTemplate() {
      this.download(
        "product/quote/importPriceTemplate",
        {},
        `price_import_template_${new Date().getTime()}.xlsx`
      );
    },
    /** 上传Excel解析并进入预览 */
    uploadPriceExcel(options) {
      if (!this.priceImport.customerId) {
        this.$modal.msgWarning("请先选择客户再上传Excel");
        return;
      }
      const formData = new FormData();
      formData.append("file", options.file);
      formData.append("customerId", this.priceImport.customerId);
      this.priceImport.uploading = true;
      previewQuoteImportExcel(formData)
        .then((response) => {
          this.setImportRows(response.data || []);
          if (!this.priceImport.rows.length) {
            this.$modal.msgWarning("Excel中未解析到有效数据行");
            return;
          }
          this.priceImport.step = 2;
        })
        .finally(() => {
          this.priceImport.uploading = false;
        });
    },
    /** 解析价格表并自动匹配 */
    parsePriceImport() {
      if (!this.priceImport.customerId) {
        this.$modal.msgWarning("请先选择客户");
        return;
      }
      if (!this.priceImport.text.trim()) {
        this.$modal.msgWarning("请先粘贴价格表");
        return;
      }
      this.priceImport.submitting = true;
      previewQuoteImport({
        customerId: this.priceImport.customerId,
        text: this.priceImport.text,
      })
        .then((response) => {
          this.setImportRows(response.data || []);
          if (!this.priceImport.rows.length) {
            this.$modal.msgWarning("未解析到有效行，请检查格式");
            return;
          }
          this.priceImport.step = 2;
        })
        .finally(() => {
          this.priceImport.submitting = false;
        });
    },
    /** 设置导入预览行：为未匹配行初始化批量建品字段（标准名默认=原始叫法） */
    setImportRows(rows) {
      this.priceImport.rows = rows.map((r) =>
        r.matched || r.error ? r : { ...r, standardName: r.standardName ?? r.rawName, categoryId: r.categoryId ?? null }
      );
    },
    /** 行内 SKU 单位展示（跟随改选结果） */
    skuUnitOf(row) {
      const sku = this.allSkuOptions.find((s) => s.id === row.skuId);
      return sku ? sku.unit : row.skuUnit || "-";
    },
    skuSpecOf(row) {
      const sku = this.allSkuOptions.find((s) => s.id === row.skuId);
      return sku ? sku.specName : row.skuSpec || "-";
    },
    /** 确认导入：已匹配/建品行生成报价单，未匹配行按模式处理 */
    submitPriceImport() {
      const mode = this.priceImport.unmatchedMode;
      // 批量建品模式下校验未匹配行的标准名与分类
      if (mode === "batchCreate") {
        const bad = this.priceImport.rows.filter(
          (r) => !r.error && r.price != null && !r.skuId && (!r.standardName || !r.standardName.trim() || !r.categoryId)
        );
        if (bad.length) {
          this.$modal.msgWarning(`有 ${bad.length} 条未匹配行未填写标准名或分类（行号：${bad.map((r) => r.lineNo).join("、")}），请补全后重试`);
          return;
        }
      }
      const rows = this.priceImport.rows
        .filter((r) => !r.error && r.price != null)
        .map((r) => ({
          rawName: r.rawName,
          skuId: r.skuId,
          price: r.price,
          unit: r.unit || null,
          // 未匹配行 + 批量建品模式：携带建品信息
          createFlag: mode === "batchCreate" && !r.skuId ? true : undefined,
          standardName: mode === "batchCreate" && !r.skuId ? (r.standardName || "").trim() : undefined,
          categoryId: mode === "batchCreate" && !r.skuId ? r.categoryId : undefined,
        }));
      if (!rows.length) {
        this.$modal.msgWarning("没有可导入的行");
        return;
      }
      this.priceImport.submitting = true;
      confirmQuoteImport({
        customerId: this.priceImport.customerId,
        unmatchedToTemp: mode === "toTemp",
        rows,
      })
        .then((response) => {
          this.priceImport.open = false;
          this.$alert(
            "<div style='overflow: auto;max-height: 70vh;padding: 10px 20px 0;'>" +
              response.msg +
              "</div>",
            "导入结果",
            { dangerouslyUseHTMLString: true }
          );
          this.getList();
        })
        .finally(() => {
          this.priceImport.submitting = false;
        });
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download(
        "product/quote/importTemplate",
        {},
        `skuQuote_template_${new Date().getTime()}.xlsx`
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
