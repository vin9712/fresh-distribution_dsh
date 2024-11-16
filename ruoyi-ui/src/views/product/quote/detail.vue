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
          <el-form-item label="报价备注" prop="remark">
            <el-input v-model="quoteForm.remark" placeholder="请输入报价备注">
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
          keep-source
          :row-config="{ isHover: true }"
          :mouse-config="{ selected: true }"
          :keyboard-config="{
            isArrow: true,
            isDel: true,
            isEnter: true,
            isTab: true,
            isEdit: true,
            isChecked: true,
          }"
          :edit-rules="validRules"
          :edit-config="{
            trigger: 'click',
            mode: 'cell',
            showStatus: true,
          }"
          :data="skuQuoteList"
        >
          <vxe-column type="seq" width="70"></vxe-column>
          <vxe-column field="categoryName" title="商品分类"> </vxe-column>
          <vxe-column field="productCode" title="商品编号"></vxe-column>
          <vxe-column field="productName" title="商品名称"></vxe-column>
          <vxe-column field="productUnit" title="商品单位"></vxe-column>
          <vxe-column
            field="price"
            title="商品单价"
            cell-type="number"
            :formatter="priceFormatter"
            :edit-render="{ name: 'input', autoselect: true }"
          >
          </vxe-column>
          <vxe-column field="productSpec" title="商品规格"></vxe-column>
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

import XEUtils from "xe-utils";
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
        remark: null,
      },
      // 客户列表数据
      customerOptions: [],
      // 客户 sku 报价列表
      skuQuoteList: [],
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
      // 报价明细列表校验
      validRules: {
        price: [{ type: "number", min: 0, message: "请输入正数" }],
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
    // 初始化表格
    this.initSkuQuoteList();
  },
  methods: {
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
      });
    },
    /** 获取当前报价单号 */
    getQuoteCode() {
      genQuoteCode().then((response) => {
        this.quoteForm.quoteCode = response.msg;
      });
    },
    /** 初始化报价明细列表 */
    initSkuQuoteList() {
      // todo 从后端获取数据
      if (this.quoteForm.quoteId) {
        this.skuQuoteList = [];
        return;
      }

      // 从sku列表初始化报价明细列表
      let param = { customerId: this.quoteForm.customerId };
      listSku(param).then((response) => {
        const skuList = response.data || [];
        skuList.map((item) => {
          this.skuQuoteList.push({
            customerId: item.customerId,
            categoryName: item.categoryName,
            quoteId: null,
            skuId: item.id,
            productCode: item.code,
            productName: item.name,
            productUnit: item.unit,
            productSpec: item.spec,
            price: "0.00",
          });
        });
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["quoteForm"].validate((valid) => {
        if (valid) {
          console.log("this.skuQuoteList", this.skuQuoteList);
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
    /** 格式化商品单价 */
    priceFormatter({ row }) {
      let formatPrice = XEUtils.commafy(XEUtils.toNumber(row.price), {
        digits: 2,
      });
      if (formatPrice < 0) {
        formatPrice = "0.00";
      }
      // 将格式化后的值赋值回去
      row.price = formatPrice;
      return formatPrice;
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