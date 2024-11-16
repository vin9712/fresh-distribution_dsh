<template>
  <div class="app-container">
    <el-card>
      <!-- 报价表单 -->
      <div slot="header">
        <span>报价信息</span>
        <el-form
          ref="quoteForm"
          :model="quoteForm"
          :rules="rules"
          size="medium"
          inline
          label-width="100px"
        >
          <el-form-item label="报价客户" prop="customerId">
            <el-select
              v-model="quoteForm.customerId"
              placeholder="请选择报价客户"
              disabled
            >
              <el-option
                v-for="item in customerOptions"
                :key="item.id"
                :label="item.alias ? item.alias : item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="生效时间" prop="effectiveDateRange">
            <el-date-picker
              type="daterange"
              v-model="quoteForm.effectiveDateRange"
              format="yyyy-MM-dd"
              value-format="yyyy-MM-dd"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              clearable
            ></el-date-picker>
          </el-form-item>
          <el-form-item label="报价编号" prop="quoteCode">
            <el-input
              v-model="quoteForm.quoteCode"
              placeholder="请输入报价编号"
              disabled
            >
            </el-input>
          </el-form-item>
        </el-form>
      </div>

      <!-- 报价表格 -->
      <div>
        <span>报价表格</span>
        <vxe-table
          border
          show-overflow
          :edit-config="{
            trigger: 'dblclick',
            mode: 'row',
          }"
          :data="tableData"
        >
          <vxe-column type="seq" width="70"></vxe-column>
          <vxe-column
            field="name"
            title="Name"
            :edit-render="{ name: 'input' }"
          ></vxe-column>
          <vxe-column
            field="sex"
            title="Sex"
            :edit-render="{ name: 'input' }"
          ></vxe-column>
          <vxe-column
            field="age"
            title="Age"
            :edit-render="{ name: 'input' }"
          ></vxe-column>
        </vxe-table>
      </div>

      <!-- 底部工具栏 -->
      <el-form label-width="100px">
        <el-form-item
          style="text-align: center; margin-left: -100px; margin-top: 10px"
        >
          <el-button type="primary" @click="submitForm()">提交</el-button>
          <el-button @click="close()">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { listSku } from "@/api/product/sku";
import { genQuoteCode } from "@/api/product/quote";
import { listCustomer } from "@/api/partner/customer";

export default {
  name: "QuoteDetail",
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
      // 默认客户ID
      defaultCustomerId: null,
      // 报价表单
      quoteForm: {
        quoteId: null,
        customerId: null,
        effectiveDateRange: [],
        quoteCode: null,
      },
      // 客户列表数据
      customerOptions: [],
      // 客户 sku 列表
      skuList: [],
      // 报价明细列表
      tableData: [
        {
          id: 10001,
          name: "Test1",
          role: "Develop",
          sex: "Man",
          age: 28,
          address: "test abc",
        },
        {
          id: 10002,
          name: "Test2",
          role: "Test",
          sex: "Women",
          age: 22,
          address: "Guangzhou",
        },
        {
          id: 10003,
          name: "Test3",
          role: "PM",
          sex: "Man",
          age: 32,
          address: "Shanghai",
        },
        {
          id: 10004,
          name: "Test4",
          role: "Designer",
          sex: "Women",
          age: 24,
          address: "Shanghai",
        },
      ],
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "报价客户不能为空", trigger: "change" },
        ],
        effectiveDateRange: [
          {
            required: true,
            message: "报价生效时间不能为空",
            trigger: "change",
          },
        ],
        quoteCode: [
          { required: true, message: "报价编号不能为空", trigger: "blur" },
        ],
      },
    };
  },
  created() {
    // 从路由获取参数
    this.defaultCustomerId =
      this.$route.params && parseInt(this.$route.params.customerId);
    // 设置查询参数
    this.quoteForm.customerId = this.defaultCustomerId;
    this.getCustomerList();
    this.getQuoteCode();
  },
  methods: {
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
      });
    },
    /** 查询客户 sku 列表 */
    getSkuList() {
      listSku().then((response) => {
        this.skuList = response.data || [];
      });
    },
    /** 获取当前报价单号 */
    getQuoteCode() {
      genQuoteCode().then((response) => {
        this.quoteForm.quoteCode = response.msg;
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["quoteForm"].validate((valid) => {
        if (valid) {
          if (this.quoteForm.quoteId != null) {
            // updateSku(this.form).then((response) => {
            //   this.$modal.msgSuccess("修改成功");
            //   this.open = false;
            //   this.getPageList();
            // });
          } else {
            // addSku(this.form).then((response) => {
            //   this.$modal.msgSuccess("新增成功");
            //   this.open = false;
            //   this.getPageList();
            // });
          }
        }
      });
    },
  },
};
</script>

<style scoped>
::v-deep .el-card__header {
  padding-bottom: 0px;
}
::v-deep .el-card__body {
  padding-top: 10px;
}
</style>