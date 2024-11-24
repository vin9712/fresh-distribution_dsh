<template>
  <div class="app-container">
    <!-- 做单区 -->
    <div class="main-board">
      <el-card>
        <!-- 订单表单 -->
        <div slot="header">
          <span>订单信息</span>
          <el-form
            ref="orderForm"
            :model="orderForm"
            :rules="rules"
            size="medium"
            inline
            label-width="100px"
          >
            <el-form-item label="送货单位" prop="customerDeptId">
              <el-select
                v-model="orderForm.customerDeptId"
                placeholder="请选择送货单位"
                :disabled="orderForm.orderId != null"
              >
                <el-option
                  v-for="item in customerDeptOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="配送日期" prop="deliveryDate">
              <el-date-picker
                v-model="orderForm.deliveryDate"
                format="yyyy-MM-dd"
                value-format="yyyy-MM-dd"
                placeholder="请选择配送日期"
                clearable
              ></el-date-picker>
            </el-form-item>
            <el-form-item prop="orderCode">
              <span slot="label">
                订单编号
                <i
                  class="el-icon-refresh"
                  @click="refreshOrderCode"
                  style="cursor: pointer"
                ></i>
              </span>
              <el-input
                v-model="orderForm.orderCode"
                placeholder="请输入订单编号"
                disabled
              >
              </el-input>
            </el-form-item>
            <el-form-item label="订单备注" prop="remark">
              <el-input v-model="orderForm.remark" placeholder="请输入订单备注">
              </el-input>
            </el-form-item>
          </el-form>
        </div>

        <!-- 订单表格 -->
        <div>
          <span>订单表格</span>
          <el-button @click="insertEvent(-1)">新增行</el-button>
          <vxe-table
            border
            show-footer
            show-overflow
            keep-source
            ref="xTable"
            height="400"
            size="small"
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
            :footer-method="footerMethod"
            :edit-rules="validRules"
            :edit-config="{
              trigger: 'click',
              mode: 'cell',
              showUpdateStatus: true,
              beforeEditMethod: checkTableActive,
            }"
            :data="orderDetailList"
          >
            <vxe-column type="seq" width="50"></vxe-column>
            <vxe-column
              field="productName"
              title="商品名称"
              :edit-render="{ name: 'input', autoselect: true }"
              width="25%"
            ></vxe-column>
            <vxe-column
              field="productUnit"
              title="单位"
              width="8%"
              :edit-render="{ name: 'input', autoselect: true }"
            ></vxe-column>
            <vxe-column
              field="num"
              title="数量"
              cell-type="number"
              :formatter="decimalFormatter('num')"
              :edit-render="{ name: 'input', autoselect: true }"
            >
              <template #edit="{ row }">
                <vxe-input
                  v-model="row.num"
                  type="text"
                  @change="calcAmount(row)"
                ></vxe-input>
              </template>
            </vxe-column>
            <vxe-column
              field="price"
              title="单价"
              cell-type="number"
              :formatter="decimalFormatter('price')"
              :edit-render="{ name: 'input', autoselect: true }"
            >
              <template #edit="{ row }">
                <vxe-input
                  v-model="row.price"
                  type="text"
                  @change="calcAmount(row)"
                ></vxe-input>
              </template>
            </vxe-column>
            <vxe-column field="amount" title="金额"> </vxe-column>
            <vxe-column
              field="productSpec"
              title="规格"
              :edit-render="{ name: 'input', autoselect: true }"
            ></vxe-column>
            <vxe-column
              field="remark"
              title="备注"
              :edit-render="{ name: 'input', autoselect: true }"
            ></vxe-column>
          </vxe-table>
        </div>

        <!-- 底部工具栏 -->
        <el-form label-width="100px">
          <el-form-item
            style="text-align: center; margin-left: -100px; margin-top: 10px"
          >
            <el-button type="primary" @click="submitForm()">保存</el-button>
            <el-button @click="close()">返回</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 选单区 -->
    <div class="right-board">
      <el-tabs v-model="currentTab" class="center-tabs">
        <el-tab-pane label="最近订单" name="recentOrder" />
        <el-tab-pane label="标签b" name="tabB" />
      </el-tabs>
      <div class="field-box"></div>
    </div>
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

import XEUtils from "xe-utils";
import Sortable from "sortablejs";
const orderPage = { path: "/order/sale" };

export default {
  name: "SaleDetail",
  data() {
    return {
      // 当前激活 tab
      currentTab: "recentOrder",
      // 送货单位下拉选项
      customerDeptOptions: [],
      // 订单明细列表
      orderDetailList: [],
      // 订单表单
      orderForm: {},
      // 订单校验
      rules: {
        orderCode: [
          { required: true, message: "订单编号不能为空", trigger: "blur" },
        ],
        customerDeptId: [
          { required: true, message: "送货单位不能为空", trigger: "blur" },
        ],
        deliveryDate: [
          { required: true, message: "送货日期不能为空", trigger: "blur" },
        ],
      },
      // 订单明细表格校验
      validRules: {
        productName: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        productUnit: [
          { required: true, message: "商品单位不能为空", trigger: "blur" },
        ],
        num: [{ required: true, message: "商品数量不能为空", trigger: "blur" }],
        price: [
          { required: true, message: "商品单价不能为空", trigger: "blur" },
        ],
      },
    };
  },
  mounted() {
    // 组件挂载完成后添加一行
    this.insertEvent(-1);
  },
  created() {},
  methods: {
    /** 默认追加一行到表格 */
    async insertEvent(row) {
      const $table = this.$refs.xTable;
      const record = { productUnit: "斤" };
      await $table.insertAt(record, row, { isInsert: false });
    },
    /** 刷新订单编号 */
    refreshOrderCode() {},
    /** 保存订单信息 */
    submitForm() {},
    /** 返回按钮 */
    close() {
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认关闭？")
          .then(() => {
            this.$tab.closeOpenPage(orderPage);
          })
          .catch(() => {});
      } else {
        this.$tab.closeOpenPage(orderPage);
      }
    },
    /** 格式化小数类型 */
    decimalFormatter(key) {
      return ({ row }) => {
        let value = XEUtils.toNumber(row[key]);
        let formatValue = XEUtils.commafy(value, {
          digits: 2,
        });
        if (formatValue <= 0) {
          formatValue = "0.00";
        }
        // 将格式化后的值赋值回去
        row[key] = formatValue;
        return formatValue;
      };
    },
    /** 计算商品小计 */
    calcAmount(row) {
      if (!row) return;
      let price = XEUtils.toNumber(row.price);
      let num = XEUtils.toNumber(row.num);
      let formatAmount = XEUtils.commafy(price * num, {
        digits: 2,
      });
      row.amount = formatAmount;
      return formatAmount;
    },
    /** 表尾合计方法 */
    sumNum(list, field) {
      let count = 0;
      if (list && list.length) {
        list.forEach((item) => {
          const value = XEUtils.toNumber(item[field]);
          if (!isNaN(value)) {
            count += value;
          }
        });
      }
      return count;
    },
    sumNumWithGroup(list, field, groupField) {
      if (!list || !list.length) {
        return "";
      }

      const groupedData = XEUtils.groupBy(list, groupField);
      const results = [];

      for (const [groupKey, groupItems] of Object.entries(groupedData)) {
        let count = 0;
        groupItems.forEach((item) => {
          const value = XEUtils.toNumber(item[field]);
          if (!isNaN(value)) {
            count += value;
          }
        });
        results.push(`${count}${groupKey}`);
      }

      return results.join(" + ");
    },
    /** 表尾渲染方法 */
    footerMethod({ columns, data }) {
      return [
        columns.map((column, columnIndex) => {
          if (columnIndex === 0) {
            return "合计";
          }
          if (column.property === "num") {
            return this.sumNum(data, "num");
          } else if (column.property === "amount") {
            return this.sumNum(data, "amount");
          }
          return "";
        }),
      ];
    },
    /** vxe表格检测是否改动 */
    checkTableUpdted() {
      return this.$refs.xTable.getUpdateRecords().length > 0;
    },
    /** vxe表格-过滤商品名称方法 */
    filterProductNameMethod({ option, row }) {
      if (row.productName.indexOf(option.data) > -1) {
        return row.productName;
      }
    },
    /** vxe表格-全局禁用编辑 */
    checkTableActive({ row, column }) {
      return true;
    },
  },
};
</script>

<style lang="scss" scoped>
.main-board {
  height: 100vh;
  width: auto;
  margin: 0 350px 0 0;
  box-sizing: border-box;
  background-color: coral;
}

.right-board {
  width: 350px;
  position: absolute;
  right: 0;
  top: 0;
  padding-top: 3px;
  .field-box {
    position: relative;
    height: calc(100vh - 42px);
    box-sizing: border-box;
    overflow: hidden;
    background-color: cornflowerblue;
  }
  .el-scrollbar {
    height: 100%;
  }
}

.center-tabs {
  .el-tabs__header {
    margin-bottom: 0 !important;
  }
  .el-tabs__item {
    width: 50%;
    text-align: center;
  }
  .el-tabs__nav {
    width: 100%;
  }
}
.right-scrollbar {
  .el-scrollbar__view {
    padding: 12px 18px 15px 15px;
  }
}
</style>
