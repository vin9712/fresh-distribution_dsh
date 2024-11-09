<template>
  <div class="app-container">
    <div class="table-head">
      <div class="table-head-back">
        <el-button type="text" icon="el-icon-back" size="medium" @click="goBack">返回</el-button>
      </div>
      <div class="table-head-content">
        <span class="table-head-title">当前客户</span>
        <el-dropdown>
          <span class="table-head-dropdown">
            下拉菜单<i class="el-icon-arrow-down el-icon--right"></i>
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item>黄金糕</el-dropdown-item>
            <el-dropdown-item>狮子头</el-dropdown-item>
            <el-dropdown-item>螺蛳粉</el-dropdown-item>
            <el-dropdown-item disabled>双皮奶</el-dropdown-item>
            <el-dropdown-item divided>蚵仔煎</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div>
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
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
          v-hasPermi="['partner:customer/dept:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['partner:customer/dept:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['partner:customer/dept:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['partner:customer/dept:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="customer / deptList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="主键" align="center" prop="id" />
      <el-table-column label="客户ID" align="center" prop="customerId" />
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
            v-hasPermi="['partner:customer/dept:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['partner:customer/dept:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改客户部门对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input v-model="form.mnemonicCode" placeholder="请输入助记码" />
        </el-form-item>
        <el-form-item label="客户配送地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入客户配送地址" />
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
import { listCustomerDept, getCustomerDept, delCustomerDept, addCustomerDept, updateCustomerDept } from "@/api/partner/customerDept";

export default {
  name: "Customer/dept",
  dicts: ['biz_yes_no'],
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
        valid: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "客户ID不能为空", trigger: "blur" }
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
    this.getList();
  },
  methods: {
    /** 查询客户部门列表 */
    getList() {
      this.loading = true;
      listCustomerDept(this.queryParams).then(response => {
        this.customerDeptList = response.rows;
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
        customerId: null,
        parentId: null,
        name: null,
        mnemonicCode: null,
        address: null,
        location: null,
        valid: null,
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
      this.getList();
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
      getCustomer / dept(id).then(response => {
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
              this.getList();
            });
          } else {
            addCustomerDept(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
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
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => { });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('partner/customer/dept/export', {
        ...this.queryParams
      }, `customer/dept_${new Date().getTime()}.xlsx`)
    },
    /** 返回上一级页面 */
    goBack() {
      this.$router.go(-1);
    },
  }
}
</script>

<style lang="scss" scoped>
::v-deep .table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  border-bottom: 1px solid #e1e1e1;
  margin-bottom: 20px;
}

::v-deep .table-head-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-right: 10px;
}

::v-deep .table-head-back {
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
}

::v-deep .table-head-back:hover {
  color: #66b1ff;
}

::v-deep .table-head-dropdown {
  margin-left: 5px;
  color: #409eff;
}
</style>