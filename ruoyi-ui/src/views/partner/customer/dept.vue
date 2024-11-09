<template>
  <div class="app-container">
    <el-form :model="queryParams" :rules="queryFormRules" ref="queryForm" size="small" :inline="true" v-show="showSearch"
      label-width="80px">
      <el-form-item label="当前客户" prop="customerId">
        <el-select v-model="queryParams.customerId" filterable @change="handleQuery">
          <el-option v-for="item in customerOptions" :key="item.id" :label="item.alias ? item.alias : item.name"
            :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入部门名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="是否有效" prop="valid">
        <el-select v-model="queryParams.valid" placeholder="请选择是否有效" clearable>
          <el-option v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['partner:customerDept:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['partner:customerDept:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['partner:customerDept:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['partner:customerDept:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="customerDeptList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="主键" align="center" prop="id" />
      <el-table-column label="部门名称" align="center" prop="name" />
      <el-table-column label="是否有效" align="center" prop="valid">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.biz_yes_no" :value="scope.row.valid" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['partner:customerDept:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['partner:customerDept:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getPageList" />

    <!-- 添加或修改客户部门对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="当前客户" prop="customerId">
          <el-select v-model="form.customerId" disabled>
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.alias ? item.alias : item.name"
              :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input v-model="form.mnemonicCode" placeholder="请输入助记码" />
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label
            }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="逻辑删除" prop="isDeleted">
          <el-input v-model="form.isDeleted" placeholder="请输入逻辑删除" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
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
import { pageCustomerDept, listCustomerDept, getCustomerDept, delCustomerDept, addCustomerDept, updateCustomerDept } from "@/api/partner/customerDept";
import { listCustomer } from "@/api/partner/customer";

export default {
  name: "CustomerDept",
  dicts: ['t_customer_type', 'biz_yes_no'],
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
      // 默认客户ID
      defaultCustomerId: null,
      // 客户列表数据
      customerOptions: [],
      // 客户部门表格数据
      customerDeptList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: null,
        name: null,
        mnemonicCode: null,
        valid: null,
      },
      // 查询校验
      queryFormRules: {
        customerId: [
          { required: true, message: "当前客户不能为空", trigger: "change" }
        ],
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "客户ID不能为空", trigger: "change" }
        ],
        parentId: [
          { required: true, message: "上级部门ID不能为空", trigger: "blur" }
        ],
        name: [
          { required: true, message: "部门名称不能为空", trigger: "blur" }
        ],
        mnemonicCode: [
          { required: true, message: "助记码不能为空", trigger: "blur" }
        ],
        valid: [
          { required: true, message: "是否有效不能为空", trigger: "change" }
        ],
        isDeleted: [
          { required: true, message: "逻辑删除不能为空", trigger: "blur" }
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" }
        ],
      }
    };
  },
  created() {
    // 从路由获取参数
    this.defaultCustomerId = this.$route.params && parseInt(this.$route.params.customerId);
    // 设置查询参数
    this.queryParams.customerId = this.defaultCustomerId;
    this.getCustomerList();
    this.getPageList();
  },
  methods: {
    /** 查询客户部门列表 */
    getList() {
      this.loading = true;
      listCustomerDept(this.queryParams).then(response => {
        this.customerDeptList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询客户部门列表 */
    getPageList() {
      this.loading = true;
      pageCustomerDept(this.queryParams).then(response => {
        this.customerDeptList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then(response => {
        this.customerOptions = response.data;
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
        customerId: this.defaultCustomerId,
        parentId: null,
        name: null,
        mnemonicCode: null,
        address: null,
        location: null,
        valid: 1,
        isDeleted: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null
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
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加客户部门";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getCustomerDept(id).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改客户部门";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateCustomerDept(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addCustomerDept(this.form).then(response => {
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
      this.$modal.confirm('是否确认删除客户部门编号为"' + ids + '"的数据项？').then(function () {
        return delCustomerDept(ids);
      }).then(() => {
        this.getPageList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => { });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('partner/customerDept/export', {
        ...this.queryParams
      }, `customerDept_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>