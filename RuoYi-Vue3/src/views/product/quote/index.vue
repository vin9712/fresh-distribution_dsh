<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      :rules="queryFormRules"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="报价客户" prop="customerId">
        <el-select v-model="queryParams.customerId" filterable clearable>
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
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否有效" prop="valid">
        <el-select
          v-model="queryParams.valid"
          placeholder="请选择是否有效"
          clearable
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
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :icon="Search"
          size="small"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button :icon="Refresh" size="small" @click="resetQuery"
          >重置</el-button
        >
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
          v-hasPermi="['product:quote:add']"
          >新增报价</el-button
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
          v-hasPermi="['product:quote:edit']"
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
          v-hasPermi="['product:quote:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Download"
          size="small"
          @click="handleExport"
          v-hasPermi="['product:quote:export']"
          >导出</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          :icon="Upload"
          size="small"
          @click="handleImport"
          v-hasPermi="['product:quote:import']"
          >导入</el-button
        >
      </el-col>
      <right-toolbar
        v-model:showSearch="showSearch"
        @queryTable="getPageList"
      ></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="quoteList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column
        label="客户名称"
        align="center"
        width="100"
        prop="customerId"
        :formatter="formatCustomerName"
      />
      <el-table-column
        label="报价编号"
        align="center"
        prop="code"
        width="140"
      />
      <el-table-column
        label="报价生效时间"
        align="center"
        prop="effectiveStartDate"
        width="140"
      >
        <template #default="scope">
          <span>{{
            parseTime(scope.row.effectiveStartDate, "{y}-{m}-{d}")
          }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="报价结束时间"
        align="center"
        prop="effectiveEndDate"
        width="140"
      >
        <template #default="scope">
          <span>{{
            parseTime(scope.row.effectiveEndDate, "{y}-{m}-{d}")
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag
            :options="dict.type.t_sku_quote_status"
            :value="scope.row.status"
          />
        </template>
      </el-table-column>
      <el-table-column label="有效" align="center" prop="valid">
        <template #default="scope">
          <dict-tag :options="dict.type.biz_yes_no" :value="scope.row.valid" />
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        width="100"
        prop="createTime"
      />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        width="210"
        class-name="small-padding fixed-width"
      >
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="View"
            @click="handleView(scope.row)"
            >查看</el-button
          >
          <el-button
            size="small"
            link
            :icon="DocumentCopy"
            @click="handleCopy(scope.row)"
            v-hasPermi="['product:quote:edit']"
            >复制</el-button
          >
          <!-- 变更状态 -->
          <el-button
            v-if="scope.row.status == quoteStatus.NEW.code"
            size="small"
            link
            :icon="Operation"
            @click="handleUpdateStatus(scope.row, quoteStatus.PUBLISHED.name)"
            v-hasPermi="['product:quote:edit']"
            >发布</el-button
          >
          <el-dropdown
            size="small"
            v-if="
              scope.row.valid == '0' &&
              scope.row.status == quoteStatus.PUBLISHED.code
            "
            @command="(command) => handleStatusCommand(command, scope.row)"
          >
            <el-button size="small" link :icon="Link"
              >取消</el-button
            >
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="revoke">撤销</el-dropdown-item>
                <el-dropdown-item command="invalid">失效</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 更多操作 -->
          <el-dropdown
            v-if="scope.row.valid == '0'"
            size="small"
            @command="(command) => handleMoreCommand(command, scope.row)"
          >
            <el-button size="small" link :icon="DArrowRight"
              >更多</el-button
            >
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
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 客户报价导入对话框 -->
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
} from "@/api/product/quote";
import { listCustomer } from "@/api/partner/customer";
import { getToken } from "@/utils/auth";
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
      const ids = row.id || this.ids;
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
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "客户报价导入";
      this.upload.open = true;
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
