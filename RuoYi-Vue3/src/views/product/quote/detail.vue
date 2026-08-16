<template>
  <div class="app-container">
    <el-card>
      <!-- 报价表单 -->
      <template #header>
        <span>报价信息</span>
        <el-form
          ref="quoteForm"
          :model="quoteForm"
          :rules="rules"
          size="medium"
          inline
          label-width="100px"
          :disabled="isViewMode"
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
          <el-form-item prop="quoteCode">
            <template #label>
              报价编号
              <el-icon
                v-if="isAddMode || isCopyMode"
                @click="refreshQuoteCode"
                style="cursor: pointer"
              ><Refresh /></el-icon>
            </template>
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
      </template>

      <!-- 报价表格 -->
      <div>
        <span>报价表格</span>
        <vxe-table
          border
          show-overflow
          keep-source
          ref="xTable"
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
            activeMethod: checkTableActive,
          }"
          :data="skuQuoteList"
        >
          <vxe-column type="seq" width="70"></vxe-column>
          <vxe-column field="categoryName" title="商品分类"> </vxe-column>
          <vxe-column field="productCode" title="商品编号"></vxe-column>
          <vxe-column
            field="productName"
            title="商品名称"
            :filters="[{ data: '' }]"
            :filter-method="filterProductNameMethod"
          >
            <template #filter="{ $panel, column }">
              <el-input
                type="type"
                v-for="(option, index) in column.filters"
                :key="index"
                v-model="option.data"
                @input="$panel.changeOption($event, !!option.data, option)"
              />
            </template>
          </vxe-column>
          <vxe-column field="productUnit" title="商品单位"></vxe-column>
          <vxe-column
            field="price"
            title="商品单价"
            cell-type="number"
            :formatter="priceFormatter"
            :edit-render="{ name: '$input', autoselect: true }"
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
          <el-button type="primary" @click="submitForm()" :disabled="isViewMode"
            >提交</el-button
          >
          <el-button @click="close()">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { listSku } from "@/api/product/sku";
import {
  getQuote,
  genQuoteCode,
  createSkuQuote,
  updateSkuQuote,
} from "@/api/product/quote";
import { listQuoteDetail } from "@/api/product/quoteDetail";
import { listCustomer } from "@/api/partner/customer";
import { Refresh } from "@element-plus/icons-vue";

import XEUtils from "xe-utils";
const quotePage = { path: "/basicInfo/quote" };

export default {
  name: "QuoteDetail",
  dicts: ["biz_yes_no"],
  setup() {
    return { Refresh };
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
      // 默认客户ID
      defaultCustomerId: null,
      // 默认报价ID
      defaultQuoteId: null,
      // 报价表单
      quoteForm: {
        quoteId: null,
        customerId: null,
        quoteCode: null,
        remark: null,
        effectiveStartDate: null,
        effectiveEndDate: null,
        quoteDetails: [],
        effectiveDateRange: [],
      },
      // 客户列表数据
      customerOptions: [],
      // 客户 sku 报价列表
      skuQuoteList: [],
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
        price: [
          { required: true, message: "商品单价必须填写" },
          { type: "number", min: 0, message: "请输入正数" },
        ],
      },
    };
  },
  watch: {
    // 监听 effectiveDateRange
    "quoteForm.effectiveDateRange": {
      handler(val) {
        if (val) {
          this.quoteForm.effectiveStartDate = val[0];
          this.quoteForm.effectiveEndDate = val[1];
        } else {
          this.quoteForm.effectiveStartDate = null;
          this.quoteForm.effectiveEndDate = null;
        }
      },
      deep: true,
    },
  },
  computed: {
    isAddMode() {
      return this.$route.query.mode == "add";
    },
    isViewMode() {
      return this.$route.query.mode == "view";
    },
    isCopyMode() {
      return this.$route.query.mode == "copy";
    },
  },
  created() {
    // 从路由获取参数
    const customerIdFromParams = this.$route.params.customerId;
    const quoteIdFromQuery = this.$route.query.quoteId;
    this.defaultCustomerId = customerIdFromParams
      ? parseInt(customerIdFromParams, 10)
      : null;
    this.defaultQuoteId = quoteIdFromQuery
      ? parseInt(quoteIdFromQuery, 10)
      : null;
    // 设置查询参数
    this.quoteForm.customerId = this.defaultCustomerId;
    this.quoteForm.quoteId = this.defaultQuoteId;

    // 初始化数据
    this.getCustomerList();
    this.getQuoteCode();
    // 初始化表单+表格
    this.initSkuQuoteData();
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
    /** 刷新当前报价单号 */
    refreshQuoteCode() {
      let param = { currentCode: this.quoteForm.quoteCode };
      genQuoteCode(param).then((response) => {
        this.quoteForm.quoteCode = response.msg;
      });
    },
    /** 初始化报价表单+明细列表 */
    async initSkuQuoteData() {
      const quoteId = this.quoteForm.quoteId;
      const quoteCode = this.quoteForm.quoteCode;
      let skuQuoteList = [];

      try {
        // 从sku列表初始化报价明细列表
        const param = { customerId: this.quoteForm.customerId };
        const response = await listSku(param);
        const skuList = response.data || [];
        skuQuoteList = skuList.map((item) => ({
          customerId: item.customerId,
          categoryName: item.categoryName,
          quoteId: null,
          skuId: item.id,
          productCode: item.code,
          productName: item.name,
          productUnit: item.unit,
          productSpec: item.spec,
          price: "0.00",
        }));

        if (!quoteId) {
          this.skuQuoteList = skuQuoteList;
          return;
        }

        // 获取报价信息
        const quoteResponse = await getQuote(quoteId);
        const quote = quoteResponse.data || {};
        this.quoteForm = {
          quoteId: quote.id,
          quoteCode: quote.code,
          effectiveDateRange: [
            quote.effectiveStartDate,
            quote.effectiveEndDate,
          ],
          ...quote,
        };

        // 获取报价详情
        const detailResponse = await listQuoteDetail({ quoteId });
        const quoteList = detailResponse.data || [];
        skuQuoteList = skuQuoteList.map((item) => {
          const quoteItem = quoteList.find((q) => q.skuId === item.skuId);
          if (quoteItem) {
            return {
              ...item,
              quoteId: quoteItem.quoteId,
              price: quoteItem.price,
            };
          } else {
            return item;
          }
        });

        // 若当前为复制模式，重置 quoteId & quoteCode
        if (this.isCopyMode) {
          this.defaultQuoteId = null;
          this.quoteForm.quoteId = null;
          this.quoteForm.quoteCode = quoteCode;
          skuQuoteList = skuQuoteList.map((item) => ({
            ...item,
            quoteId: null,
          }));
        }
        this.skuQuoteList = skuQuoteList;

        // 刷新表格状态
        this.$refs.xTable.reloadData(this.skuQuoteList);
      } catch (error) {
        console.error("Error occurred during initialization:", error);
        // 可以选择显示错误消息给用户
      }
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["quoteForm"].validate((valid) => {
        if (valid) {
          // set quoteDetails
          if (this.skuQuoteList.length == 0) {
            this.$modal.msgError("报价明细列表不能为空！");
            return;
          }
          this.quoteForm.quoteDetails = this.skuQuoteList;

          // save or update
          if (this.quoteForm.quoteId != null) {
            updateSkuQuote(this.quoteForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                this.$tab.closeOpenPage(quotePage);
              }
            });
          } else {
            createSkuQuote(this.quoteForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("新增成功");
                this.$tab.closeOpenPage(quotePage);
              }
            });
          }
        }
      });
    },
    /** 返回按钮 */
    close() {
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前报价单有改动，是否确认关闭？")
          .then(() => {
            this.$tab.closeOpenPage(quotePage);
          })
          .catch(() => {});
      } else {
        this.$tab.closeOpenPage(quotePage);
      }
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
    /** vxe表格-过滤商品名称方法 */
    filterProductNameMethod({ option, row }) {
      if (row.productName.indexOf(option.data) > -1) {
        return row.productName;
      }
    },
    /** vxe表格-全局禁用编辑 */
    checkTableActive({ row, column }) {
      return !this.isViewMode;
    },
    /** vxe表格检测是否改动 */
    checkTableUpdted() {
      return this.$refs.xTable.getUpdateRecords().length > 0;
    },
  },
};
</script>

<style scoped>
:deep(.el-card__header) {
  padding-bottom: 0px;
}
:deep(.el-card__body) {
  padding-top: 10px;
}
</style>
