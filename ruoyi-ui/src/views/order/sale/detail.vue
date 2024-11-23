<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
    >
      <el-form-item label="订单编号" prop="orderCode">
        <el-input
          v-model="queryParams.orderCode"
          placeholder="请输入订单编号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="商品名称" prop="productName">
        <el-input
          v-model="queryParams.productName"
          placeholder="请输入商品名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
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
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['order:saleDetail:add']"
          >新增</el-button
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
          v-hasPermi="['order:saleDetail:edit']"
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
          v-hasPermi="['order:saleDetail:remove']"
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
          v-hasPermi="['order:saleDetail:export']"
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
      :data="saleDetailList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="主键" align="center" prop="id" />
      <el-table-column label="订单ID" align="center" prop="orderId" />
      <el-table-column label="客户ID" align="center" prop="customerId" />
      <el-table-column
        label="客户部门ID"
        align="center"
        prop="customerDeptId"
      />
      <el-table-column label="商品ID" align="center" prop="skuId" />
      <el-table-column label="订单编号" align="center" prop="orderCode" />
      <el-table-column label="商品名称" align="center" prop="productName" />
      <el-table-column label="商品单位" align="center" prop="productUnit" />
      <el-table-column label="商品单价" align="center" prop="productPrice" />
      <el-table-column label="商品规格" align="center" prop="productSpec" />
      <el-table-column label="计划数量" align="center" prop="num" />
      <el-table-column label="计划总金额" align="center" prop="expectAmount" />
      <el-table-column label="验收商品单价" align="center" prop="actualPrice" />
      <el-table-column label="验收数量" align="center" prop="actualNum" />
      <el-table-column label="验收总金额" align="center" prop="actualAmount" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
      >
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['order:saleDetail:edit']"
            >修改</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['order:saleDetail:remove']"
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

    <!-- 添加或修改销售订单详情对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="订单编号" prop="orderCode">
          <el-input v-model="form.orderCode" placeholder="请输入订单编号" />
        </el-form-item>
        <el-form-item label="商品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品单位" prop="productUnit">
          <el-input v-model="form.productUnit" placeholder="请输入商品单位" />
        </el-form-item>
        <el-form-item label="商品单价" prop="productPrice">
          <el-input v-model="form.productPrice" placeholder="请输入商品单价" />
        </el-form-item>
        <el-form-item label="商品规格" prop="productSpec">
          <el-input v-model="form.productSpec" placeholder="请输入商品规格" />
        </el-form-item>
        <el-form-item label="计划数量" prop="num">
          <el-input v-model="form.num" placeholder="请输入计划数量" />
        </el-form-item>
        <el-form-item label="计划总金额" prop="expectAmount">
          <el-input
            v-model="form.expectAmount"
            placeholder="请输入计划总金额"
          />
        </el-form-item>
        <el-form-item label="验收商品单价" prop="actualPrice">
          <el-input
            v-model="form.actualPrice"
            placeholder="请输入验收商品单价"
          />
        </el-form-item>
        <el-form-item label="验收数量" prop="actualNum">
          <el-input v-model="form.actualNum" placeholder="请输入验收数量" />
        </el-form-item>
        <el-form-item label="验收总金额" prop="actualAmount">
          <el-input
            v-model="form.actualAmount"
            placeholder="请输入验收总金额"
          />
        </el-form-item>
        <el-form-item label="订单详情排序" prop="sort">
          <el-input v-model="form.sort" placeholder="请输入订单详情排序" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入内容"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageSaleDetail,
  listSaleDetail,
  getSaleDetail,
  delSaleDetail,
  addSaleDetail,
  updateSaleDetail,
} from "@/api/order/saleDetail";

export default {
  name: "SaleDetail",
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
      // 销售订单详情表格数据
      saleDetailList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        orderId: null,
        customerId: null,
        customerDeptId: null,
        skuId: null,
        orderCode: null,
        productName: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        orderId: [
          { required: true, message: "订单ID不能为空", trigger: "blur" },
        ],
        customerId: [
          { required: true, message: "客户ID不能为空", trigger: "blur" },
        ],
        customerDeptId: [
          { required: true, message: "客户部门ID不能为空", trigger: "blur" },
        ],
        orderCode: [
          { required: true, message: "订单编号不能为空", trigger: "blur" },
        ],
        productName: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        productPrice: [
          { required: true, message: "商品单价不能为空", trigger: "blur" },
        ],
        num: [{ required: true, message: "计划数量不能为空", trigger: "blur" }],
        actualPrice: [
          { required: true, message: "验收商品单价不能为空", trigger: "blur" },
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" },
        ],
      },
    };
  },
  created() {
    this.getPageList();
  },
  methods: {
    /** 查询销售订单详情列表 */
    getList() {
      this.loading = true;
      listSaleDetail(this.queryParams).then((response) => {
        this.saleDetailList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询销售订单详情列表 */
    getPageList() {
      this.loading = true;
      pageSaleDetail(this.queryParams).then((response) => {
        this.saleDetailList = response.rows;
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
        orderId: null,
        customerId: null,
        customerDeptId: null,
        skuId: null,
        orderCode: null,
        productName: null,
        productUnit: null,
        productPrice: null,
        productSpec: null,
        num: null,
        expectAmount: null,
        actualPrice: null,
        actualNum: null,
        actualAmount: null,
        sort: null,
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
      this.queryParams.pageNum = 1;
      this.getPageList();
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
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加销售订单详情";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getSaleDetail(id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改销售订单详情";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateSaleDetail(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addSaleDetail(this.form).then((response) => {
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
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除销售订单详情编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSaleDetail(ids);
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
        "order/saleDetail/export",
        {
          ...this.queryParams,
        },
        `saleDetail_${new Date().getTime()}.xlsx`
      );
    },
  },
};
</script>
