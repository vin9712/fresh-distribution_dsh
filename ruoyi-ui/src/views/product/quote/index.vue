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
          icon="el-icon-search"
          size="mini"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery"
          >重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-popover placement="bottom" trigger="click"> </el-popover>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['product:quote:add']"
          >新增报价</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
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
          icon="el-icon-delete"
          size="mini"
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
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['product:quote:export']"
          >导出</el-button
        >
      </el-col>
      <right-toolbar
        :showSearch.sync="showSearch"
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
        <template slot-scope="scope">
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
        <template slot-scope="scope">
          <span>{{
            parseTime(scope.row.effectiveEndDate, "{y}-{m}-{d}")
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" />
      <el-table-column label="是否有效" align="center" prop="valid">
        <template slot-scope="scope">
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
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleView(scope.row)"
            >查看</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['product:quote:edit']"
            >修改</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-document-copy"
            @click="handleCopy(scope.row)"
            v-hasPermi="['product:quote:edit']"
            >复制</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['product:quote:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getPageList"
    />
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
} from "@/api/product/quote";
import { listCustomer } from "@/api/partner/customer";

export default {
  name: "SkuQuote",
  dicts: ["biz_yes_no"],
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
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: null,
        effectiveStartDate: null,
        effectiveEndDate: null,
        valid: null,

        // 额外查询参数
        effectiveDateRange: [
          new Date(),
          new Date().getTime() + 3600 * 1000 * 24 * 7,
        ],
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
  },
};
</script>
