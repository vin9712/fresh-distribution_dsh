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
      <el-form-item label="配送点名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入配送点名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="是否有效" prop="valid">
        <el-select v-model="queryParams.valid" placeholder="请选择是否有效" clearable>
          <el-option v-for="dict in dict.type.biz_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" size="small" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain :icon="Edit" size="small" :disabled="single" @click="handleUpdate">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain :icon="Delete" size="small" :disabled="multiple" @click="handleDelete">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain :icon="Download" size="small" @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="customerDeptList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="code" />
      <el-table-column label="配送点" align="center" prop="name" />
      <el-table-column label="是否有效" align="center" prop="valid">
        <template #default="scope">
          <dict-tag :options="dict.type.biz_yes_no" :value="scope.row.valid" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button size="small" link :icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button size="small" link :icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getPageList" />

    <!-- 添加或修改配送点对话框 -->
    <el-dialog align-center :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="当前客户" prop="customerId">
          <el-select v-model="form.customerId" disabled>
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.alias ? item.alias : item.name"
              :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配送点名称" prop="name">
          <el-input v-model="form.name" @input="handleUpdateMnemonicCode" placeholder="请输入配送点名称" />
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input v-model="form.mnemonicCode" placeholder="请输入助记码" :disabled="form.id == null" />
        </el-form-item>
        <!-- 班次（s35）：仅当当前客户启用班次时出现，矩阵/总单按「配送点×班次」出列 -->
        <el-form-item v-if="shiftEnabledForForm" label="班次">
          <el-select v-model="shiftCodeArray" multiple placeholder="请选择该配送点支持的班次" style="width: 100%">
            <el-option v-for="dict in dict.type.biz_shift_type" :key="dict.value" :label="dict.label"
              :value="dict.value" />
          </el-select>
          <div class="shift-tip">白班/夜班同属一个配送点；下单时按此处声明的范围选择班次</div>
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio v-for="dict in dict.type.biz_yes_no" :key="dict.value" :value="parseInt(dict.value)">{{ dict.label
            }}</el-radio>
          </el-radio-group>
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
import { pageCustomerDept, listCustomerDept, getCustomerDept, delCustomerDept, addCustomerDept, updateCustomerDept } from "@/api/partner/customerDept";
import { listCustomer } from "@/api/partner/customer";
import { pinyin } from "pinyin-pro";
import { Search, Refresh, Plus, Edit, Delete, Download } from "@element-plus/icons-vue";

export default {
  name: "CustomerDept",
  dicts: ['t_customer_type', 'biz_yes_no', 'biz_shift_type'],
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, Download };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 选中编号数组
      deptCodes: [],
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
      // 配送点表格数据
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
        // 默认隐藏父级配送点
        hideParent: true,
      },
      // 查询校验
      queryFormRules: {
        customerId: [
          { required: true, message: "当前客户不能为空", trigger: "change" }
        ],
      },
      // 表单参数
      form: {},
      // 班次多选绑定（与 form.shiftCodes 的逗号串互转，s35）
      shiftCodeArray: [],
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "客户ID不能为空", trigger: "change" }
        ],
        parentId: [
          { required: true, message: "上级配送点ID不能为空", trigger: "blur" }
        ],
        name: [
          { required: true, message: "配送点名称不能为空", trigger: "blur" }
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
  computed: {
    /** 当前表单客户的班次开关（未启用班次的客户不展示班次字段，s35） */
    shiftEnabledForForm() {
      const current = (this.customerOptions || []).find(item => item.id === this.form.customerId);
      return !!(current && current.shiftEnabled);
    },
  },
  methods: {
    /** 查询配送点列表 */
    getList() {
      this.loading = true;
      listCustomerDept(this.queryParams).then(response => {
        this.customerDeptList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询配送点列表 */
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
      this.shiftCodeArray = [];
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
      this.deptCodes = selection.map(item => item.code)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加配送点";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getCustomerDept(id).then(response => {
        this.form = response.data;
        this.shiftCodeArray = (response.data.shiftCodes || '').split(',').map(s => s.trim()).filter(Boolean);
        this.open = true;
        this.title = "修改配送点";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          // 班次仅对启用班次的客户生效；其余客户一律置空，避免残留脏配置
          this.form.shiftCodes = this.shiftEnabledForForm ? this.shiftCodeArray.join(',') : '';
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
      const codes = row.code || this.deptCodes;
      this.$modal.confirm('是否确认删除配送点编号为"' + codes + '"的数据项？').then(function () {
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
  }
}
</script>
