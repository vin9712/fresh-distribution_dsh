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
          <vxe-table
            border
            resizable
            show-footer
            show-overflow
            keep-source
            ref="xTable"
            height="500"
            size="small"
            :row-config="{ isHover: true, useKey: true }"
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
              beforeEditMethod: checkTableActive,
            }"
            :data="orderDetailList"
            @cell-mouseenter="cellMouseenterEvent"
            @cell-mouseleave="cellMouseleaveEvent"
          >
            <!-- 操作列 -->
            <vxe-column field="operate" width="63">
              <template #default="{ row, rowIndex }">
                <!-- 拖动 -->
                <span v-if="currentHoverRow === row" class="drag-btn">
                  <i class="el-icon-rank"></i>
                </span>
                <!-- 增加 -->
                <span @click="handleAddRow(rowIndex)">
                  <i class="el-icon-plus"></i>
                </span>
                <!-- 减少 -->
                <span @click="handleRemoveRow(row)">
                  <i class="el-icon-minus"></i>
                </span>
              </template>
            </vxe-column>

            <vxe-column type="seq" width="50"></vxe-column>
            <vxe-column
              field="productName"
              title="商品名称"
              :edit-render="{ autoselect: true }"
              width="25%"
            >
              <!-- 商品名称+报价详情下拉框 -->
              <template #edit="{ row: parentRow }">
                <vxe-pulldown
                  ref="pulldownRef"
                  popup-class-name="product-name-dropdown"
                  transfer
                >
                  <template #default>
                    <vxe-input
                      v-model="parentRow.productName"
                      placeholder="请输入商品名称"
                      clearable
                      @keyup="keyupEvent"
                      @focus="focusEvent"
                      @change="changeEvent({ parentRow, value: $event.value })"
                      @clear="clearEvent(parentRow)"
                    ></vxe-input>
                  </template>

                  <template #dropdown>
                    <div class="my-bodydown4">
                      <vxe-grid
                        border
                        auto-resize
                        height="auto"
                        :row-config="{ isHover: true }"
                        :data="tableData"
                        :columns="tableColumn"
                        @cell-click="
                          cellClickEvent({ parentRow, row: $event.row })
                        "
                      >
                      </vxe-grid>
                    </div>
                  </template>
                </vxe-pulldown>
              </template>
            </vxe-column>
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
      // 行拖拽
      sortableX: null,
      // 商品报价明细
      skuQuoteDetails: [],
      tableData: [],
      tableColumn: [
        { field: "productName", title: "商品名称" },
        { field: "productUnit", title: "单位" },
        { field: "productSpec", title: "规格" },
      ],
      currentHoverRow: null,
    };
  },
  mounted() {
    // 组件挂载完成后添加一行
    this.handleAddRow();
    this.rowDrop();
  },
  beforeDestroy() {
    if (this.sortableX) {
      this.sortableX.destroy();
    }
  },
  created() {
    // 初始化数据
    this.getSkuQuoteDetailList();
  },
  methods: {
    /** 获取当前客户的报价明细列表 */
    getSkuQuoteDetailList() {
      // todo 模拟数据
      this.skuQuoteDetails = [
        {
          productName: "商品1",
          productUnit: "个",
          num: 10,
          price: 100,
          amount: 1000,
          productSpec: "规格1",
          remark: "备注1",
        },
        {
          productName: "商品2",
          productUnit: "个",
          num: 20,
          price: 200,
          amount: 4000,
          productSpec: "规格2",
          remark: "备注2",
        },
        {
          productName: "商品3",
          productUnit: "个",
          num: 30,
          price: 300,
          amount: 9000,
          productSpec: "规格3",
          remark: "备注3",
        },
        {
          productName: "商品31",
          productUnit: "个",
          num: 31,
          price: 301,
          amount: 9001,
          productSpec: "规格31",
          remark: "备注31",
        },
      ];
      // 根据示例生成 tableColumns
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
        if (!row || typeof row[key] === "undefined") {
          return "0.00";
        }
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
    /** 行拖拽 */
    rowDrop() {
      const xTable = this.$refs.xTable;
      this.sortableX = Sortable.create(
        xTable.$el.querySelector(".body--wrapper>.vxe-table--body tbody"),
        {
          handle: ".drag-btn",
          onEnd: ({ newIndex, oldIndex }) => {
            const currRow = this.orderDetailList.splice(oldIndex, 1)[0];
            this.orderDetailList.splice(newIndex, 0, currRow);
            const newArr = this.orderDetailList.slice(0);
            this.orderDetailList = [];
            // 重新赋值
            this.$nextTick(() => {
              this.orderDetailList = newArr;
            });
          },
        }
      );
    },
    /** 添加行 */
    handleAddRow(rowIndex) {
      if (!this.orderDetailList) {
        this.orderDetailList = [];
      }
      const newRecord = {
        productUnit: "斤",
        num: "0.00",
        price: "0.00",
        amount: "0.00",
      };

      const index =
        rowIndex == null || rowIndex === -1
          ? this.orderDetailList.length
          : rowIndex + 1;
      this.orderDetailList.splice(index, 0, newRecord);
    },
    /** 减少行 */
    handleRemoveRow(row) {
      if (!row) return;
      const index = this.orderDetailList.indexOf(row);
      this.$confirm("确定要删除第【" + (index + 1) + "】行数据吗?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          const length = this.orderDetailList.length;
          this.orderDetailList.splice(index, 1);
          // 防止全部删完了
          if (length <= 1) {
            this.handleAddRow();
          }
        })
        .catch(() => {});
    },
    /** 鼠标进入悬浮单元格事件 */
    cellMouseenterEvent({ row, rowIndex, column }) {
      if (this.currentHoverRow && this.currentHoverRow === row) return;
      this.currentHoverRow = this.$refs.xTable.hoverRow;
    },
    /** 鼠标离开悬浮单元格事件 */
    cellMouseleaveEvent({ row, rowIndex, column }) {
      if (
        this.currentHoverRow &&
        this.currentHoverRow === row &&
        rowIndex !== 0 &&
        rowIndex !== this.orderDetailList.length - 1
      )
        return;

      // reset hover row
      this.currentHoverRow = null;
    },
    /** 商品名称输入框-聚焦事件 */
    focusEvent({ value }) {
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        if (typeof value === "undefined") {
          this.tableData = this.skuQuoteDetails;
        }
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件 */
    keyupEvent({ value }) {
      if (value) {
        this.tableData = this.skuQuoteDetails.filter(
          (row) => row.productName.indexOf(value) > -1
        );
      } else {
        this.tableData = this.skuQuoteDetails;
      }
    },
    /** 商品名称输入框-值变更事件 */
    changeEvent({ parentRow, value }) {
      console.log("changeEvent parentRow", parentRow, "value", value);
    },
    /** 商品名称输入框-清除按钮事件 */
    clearEvent(parentRow) {
      if (!parentRow) return;

      // 重设已选项
      parentRow.productName = "";
      parentRow.productUnit = "";
      parentRow.productSpec = "";
      parentRow.num = "0.00";
      parentRow.price = "0.00";
      parentRow.amount = "0.00";

      // 重设列表
      this.tableData = this.skuQuoteDetails;
    },
    /** 商品名称下拉容器-选中元素事件 */
    cellClickEvent({ parentRow, row }) {
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        // 设置选中的 skuQuote 到订单详情
        parentRow.productName = row.productName;
        parentRow.productUnit = row.productUnit;
        parentRow.productSpec = row.productSpec;
        $pulldown.hidePanel();
      }
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

.drag-btn {
  cursor: move;
  font-size: 12px;
}

.product-name-dropdown {
  background-color: #fff;
  box-shadow: 0 0 6px 2px rgba(0, 0, 0, 0.1);
  .my-bodydown4 {
    width: 600px;
    height: 300px;
  }

  .my-footdown4 {
    border-top: 1px solid #e8eaec;
  }
}
</style>
