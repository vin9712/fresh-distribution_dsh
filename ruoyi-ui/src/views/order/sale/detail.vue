<template>
  <div class="app-container">
    <!-- 做单区 -->
    <div class="main-board">
      <el-card>
        <!-- 订单表单 -->
        <div slot="header">
          <span>订单信息</span>
          <el-form ref="orderForm" :model="orderForm" :rules="rules" size="medium" inline label-width="100px">
            <el-form-item label="送货单位" prop="customerDeptId">
              <el-cascader v-model="formSelectedOptions" placeholder="请选择送货单位" :disabled="orderForm.orderId != null"
                :options="customerDeptOptions" @change="handleFormOptionsChanged" :props="{ expandTrigger: 'hover' }"
                filterable clearable />
            </el-form-item>
            <el-form-item label="配送日期" prop="deliveryDate">
              <el-date-picker v-model="orderForm.deliveryDate" format="yyyy-MM-dd" value-format="yyyy-MM-dd"
                placeholder="请选择配送日期" clearable></el-date-picker>
            </el-form-item>
            <el-form-item prop="orderCode">
              <span slot="label">
                订单编号
                <i class="el-icon-refresh" @click="refreshOrderCode" style="cursor: pointer"></i>
              </span>
              <el-input v-model="orderForm.orderCode" placeholder="请输入订单编号" disabled>
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
          <vxe-table border resizable show-footer show-overflow keep-source ref="xTable" height="500" size="small"
            :row-config="{ isHover: true, useKey: true }" :mouse-config="{ selected: true }" :keyboard-config="{
              isArrow: true,
              isDel: true,
              isEnter: true,
              isTab: true,
              isEdit: true,
              isChecked: true,
            }" :footer-method="footerMethod" :edit-rules="validRules" :edit-config="{
  trigger: 'click',
  mode: 'cell',
  beforeEditMethod: checkTableActive,
}" :data="orderDetailList" @cell-mouseenter="cellMouseenterEvent" @cell-mouseleave="cellMouseleaveEvent">
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
            <vxe-column field="productName" title="商品名称" :edit-render="{ name: 'VxeInput', autoselect: true }"
              width="25%">
              <!-- 商品名称+报价详情下拉框 -->
              <template #edit="{ row: parentRow }">
                <vxe-pulldown ref="pulldownRef" popup-class-name="product-name-dropdown" transfer>
                  <template #default>
                    <vxe-input v-model="parentRow.productName" placeholder="请输入商品名称" clearable @keyup="keyupEvent"
                      @focus="focusEvent" @blur="blurEvent({ parentRow, value: $event.value })"
                      @clear="clearEvent(parentRow)"></vxe-input>
                  </template>

                  <template #dropdown>
                    <div class="my-bodydown4">
                      <vxe-grid border auto-resize height="auto" :row-config="{ isHover: true }" :data="pulldownTableData"
                        :columns="tableColumn" @cell-click="
                          cellClickEvent({ parentRow, row: $event.row })
                        ">
                      </vxe-grid>
                    </div>
                  </template>
                </vxe-pulldown>
              </template>
            </vxe-column>
            <vxe-column field="productUnit" title="单位" width="8%"
              :edit-render="{ name: 'input', autoselect: true }"></vxe-column>
            <vxe-column field="num" title="数量" cell-type="number" :formatter="decimalFormatter('num')"
              :edit-render="{ name: 'input', autoselect: true }">
              <template #edit="{ row }">
                <vxe-input v-model="row.num" type="text" @change="calcAmount(row)"></vxe-input>
              </template>
            </vxe-column>
            <vxe-column field="price" title="单价" cell-type="number" :formatter="decimalFormatter('price')"
              :edit-render="{ name: 'input', autoselect: true }">
              <template #edit="{ row }">
                <vxe-input v-model="row.price" type="text" @change="calcAmount(row)"></vxe-input>
              </template>
            </vxe-column>
            <vxe-column field="amount" title="金额"> </vxe-column>
            <vxe-column field="productSpec" title="规格" :edit-render="{ name: 'input', autoselect: true }"></vxe-column>
            <vxe-column field="remark" title="备注" :edit-render="{ name: 'input', autoselect: true }"></vxe-column>
          </vxe-table>
        </div>

        <!-- 底部工具栏 -->
        <el-form label-width="100px">
          <el-form-item style="text-align: center; margin-left: -100px; margin-top: 10px">
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
      <div class="field-box">
        <!-- 最近订单 -->
        <div class="recent-order-tab" v-show="currentTab === 'recentOrder'">
          <el-form size="small" label-width="100px">
            <el-form-item label="订单编号">
              <el-input v-model="recentQuery.orderCode" placeholder="请输入订单编号" />
            </el-form-item>
            <el-form-item label="送货单位">
              <el-input v-model="recentQuery.customerDeptId" placeholder="请选择送货单位" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" size="mini" @click="handleRecentQuery">搜索</el-button>
              <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="recentOrderList">
            <el-table-column label="订单编号" align="center" prop="code" />
            <el-table-column label="配送时间" align="center" prop="deliveryDate" />
            <el-table-column label="客户名称" align="center" prop="customerName" />" />
            <el-table-column label="送货单位" align="center" prop="customerDeptName" />
          </el-table>

          <pagination v-show="total > 0" :total="total" :page.sync="recentQuery.pageNum"
            :limit.sync="recentQuery.pageSize" @pagination="getOrderPageList" />
        </div>
      </div>
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
import { customerListQuoteDetail } from "@/api/product/quoteDetail";
import { listCustomerDept } from "@/api/partner/customerDept";

import XEUtils from "xe-utils";
import Sortable from "sortablejs";
const orderPage = { path: "/order/sale" };

export default {
  name: "SaleDetail",
  data() {
    return {
      // 当前激活 tab
      currentTab: "recentOrder",
      // 总条数
      total: 0,
      // 默认客户id
      defaultCustomerId: null,
      defaultCustomerDeptId: null,
      defaultOrderId: null,
      // 最近订单查询条件
      recentQuery: {
        pageNum: 1,
        pageSize: 10,
        orderCode: null,
        customerDeptId: null,
      },
      // 最近订单列表
      recentOrderList: [],
      // 已选择的列表
      formSelectedOptions: [],
      // 送货单位map: <customerDeptId, customerId>
      customerDeptMap: {},
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
      // 下拉表格数据
      pulldownTableData: [],
      // 下拉表格列配置
      tableColumn: [
        { field: "productName", title: "商品名称" },
        { field: "productUnit", title: "单位" },
        { field: "productSpec", title: "规格" },
      ],
      // 当前鼠标悬停的行
      currentHoverRow: null,
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
    // 从路由获取参数
    const customerDeptIdFromParams = this.$route.params.customerDeptId;
    const customerIdFromParams = this.$route.query.customerId;
    const orderIdFromParams = this.$route.query.orderId;
    this.defaultCustomerId = customerIdFromParams
      ? parseInt(customerIdFromParams, 10)
      : null;
    this.defaultCustomerDeptId = customerDeptIdFromParams
      ? parseInt(customerDeptIdFromParams, 10)
      : null;
    this.defaultOrderId = orderIdFromParams
      ? parseInt(orderIdFromParams, 10)
      : null;

    // 设置表单参数
    this.orderForm.customerId = this.defaultCustomerId;
    this.orderForm.customerDeptId = this.defaultCustomerDeptId;

    // 初始化数据
    this.getTreeselect();
    this.getSkuQuoteDetailList();
  },
  methods: {
    /** 获取最近订单列表 */
    getOrderPageList() {

    },
    /** 查询最近订单列表 */
    handleRecentQuery() { },
    /** 重置查询条件 */
    resetQuery() { },
    /** 获取当前客户的报价明细列表 */
    async getSkuQuoteDetailList() {
      const param = {
        customerId: this.defaultCustomerId
      }
      const detailResponse = await customerListQuoteDetail(param);
      const quoteList = detailResponse.data || [];
      console.log('quoteList', quoteList);
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
        {
          productName: "商品4",
          productUnit: "个",
          num: 40,
          price: 400,
          amount: 16000,
          productSpec: "规格4",
          remark: "备注4",
        },
        {
          productName: "商品5",
          productUnit: "个",
          num: 50,
          price: 500,
          amount: 25000,
          productSpec: "规格5",
          remark: "备注5",
        },
        {
          productName: "商品6",
          productUnit: "个",
          num: 60,
          price: 600,
          amount: 36000,
          productSpec: "规格6",
          remark: "备注6",
        },
        {
          productName: "商品7",
          productUnit: "个",
          num: 70,
          price: 700,
          amount: 49000,
          productSpec: "规格7",
          remark: "备注7",
        },
        {
          productName: "商品8",
          productUnit: "个",
          num: 80,
          price: 800,
          amount: 64000,
          productSpec: "规格8",
          remark: "备注8",
        },
        {
          productName: "商品9",
          productUnit: "个",
          num: 90,
          price: 900,
          amount: 81000,
          productSpec: "规格9",
          remark: "备注9",
        },
        {
          productName: "商品10",
          productUnit: "个",
          num: 100,
          price: 1000,
          amount: 100000,
          productSpec: "规格10",
          remark: "备注10",
        },
        {
          productName: "商品11",
          productUnit: "个",
          num: 110,
          price: 1100,
          amount: 121000,
          productSpec: "规格11",
          remark: "备注11",
        },
        {
          productName: "商品12",
          productUnit: "个",
          num: 120,
          price: 1200,
          amount: 144000,
          productSpec: "规格12",
          remark: "备注12",
        },
        {
          productName: "商品13",
          productUnit: "个",
          num: 130,
          price: 1300,
          amount: 169000,
          productSpec: "规格13",
          remark: "备注13",
        },
        {
          productName: "商品14",
          productUnit: "个",
          num: 140,
          price: 1400,
          amount: 196000,
          productSpec: "规格14",
          remark: "备注14",
        },
        {
          productName: "商品15",
          productUnit: "个",
          num: 150,
          price: 1500,
          amount: 225000,
          productSpec: "规格15",
          remark: "备注15",
        },
        {
          productName: "商品16",
          productUnit: "个",
          num: 160,
          price: 1600,
          amount: 256000,
          productSpec: "规格16",
          remark: "备注16",
        },
        {
          productName: "商品17",
          productUnit: "个",
          num: 170,
          price: 1700,
          amount: 289000,
          productSpec: "规格17",
          remark: "备注17",
        },
        {
          productName: "商品18",
          productUnit: "个",
          num: 180,
          price: 1800,
          amount: 324000,
          productSpec: "规格18",
          remark: "备注18",
        },
        {
          productName: "商品19",
          productUnit: "个",
          num: 190,
          price: 1900,
          amount: 361000,
          productSpec: "规格19",
          remark: "备注19",
        },
        {
          productName: "商品20",
          productUnit: "个",
          num: 200,
          price: 2000,
          amount: 400000,
          productSpec: "规格20",
          remark: "备注20",
        },
        {
          productName: "商品21",
          productUnit: "个",
          num: 210,
          price: 2100,
          amount: 441000,
          productSpec: "规格21",
          remark: "备注21",
        },
        {
          productName: "商品22",
          productUnit: "个",
          num: 220,
          price: 2200,
          amount: 484000,
          productSpec: "规格22",
          remark: "备注22",
        },
        {
          productName: "商品23",
          productUnit: "个",
          num: 230,
          price: 2300,
          amount: 529000,
          productSpec: "规格23",
          remark: "备注23",
        },
        {
          productName: "商品24",
          productUnit: "个",
          num: 240,
          price: 2400,
          amount: 576000,
          productSpec: "规格24",
          remark: "备注24",
        },
        {
          productName: "商品25",
          productUnit: "个",
          num: 250,
          price: 2500,
          amount: 625000,
          productSpec: "规格25",
          remark: "备注25",
        },
        {
          productName: "商品26",
          productUnit: "个",
          num: 260,
          price: 2600,
          amount: 676000,
          productSpec: "规格26",
          remark: "备注26",
        },
        {
          productName: "商品27",
          productUnit: "个",
          num: 270,
          price: 2700,
          amount: 729000,
          productSpec: "规格27",
          remark: "备注27",
        },
        {
          productName: "商品28",
          productUnit: "个",
          num: 280,
          price: 2800,
          amount: 784000,
          productSpec: "规格28",
          remark: "备注28",
        },
        {
          productName: "商品29",
          productUnit: "个",
          num: 290,
          price: 2900,
          amount: 841000,
          productSpec: "规格29",
          remark: "备注29",
        },
        {
          productName: "商品30",
          productUnit: "个",
          num: 300,
          price: 3000,
          amount: 900000,
          productSpec: "规格30",
          remark: "备注30",
        },
        {
          productName: "商品31",
          productUnit: "个",
          num: 310,
          price: 3100,
          amount: 961000,
          productSpec: "规格31",
          remark: "备注31",
        },
        {
          productName: "商品32",
          productUnit: "个",
          num: 320,
          price: 3200,
          amount: 1024000,
          productSpec: "规格32",
          remark: "备注32",
        },
        {
          productName: "商品33",
          productUnit: "个",
          num: 330,
          price: 3300,
          amount: 1093000,
          productSpec: "规格33",
          remark: "备注33",
        },
        {
          productName: "商品34",
          productUnit: "个",
          num: 340,
          price: 3400,
          amount: 1164000,
          productSpec: "规格34",
          remark: "备注34",
        },
        {
          productName: "商品35",
          productUnit: "个",
          num: 350,
          price: 3500,
          amount: 1235000,
          productSpec: "规格35",
          remark: "备注35",
        },
        {
          productName: "商品36",
          productUnit: "个",
          num: 360,
          price: 3600,
          amount: 1306000,
          productSpec: "规格36",
          remark: "备注36",
        },
        {
          productName: "商品37",
          productUnit: "个",
          num: 370,
          price: 3700,
          amount: 1379000,
          productSpec: "规格37",
          remark: "备注37",
        },
        {
          productName: "商品38",
          productUnit: "个",
          num: 380,
          price: 3800,
          amount: 1452000,
          productSpec: "规格38",
          remark: "备注38",
        },
        {
          productName: "商品39",
          productUnit: "个",
          num: 390,
          price: 3900,
          amount: 1529000,
          productSpec: "规格39",
          remark: "备注39",
        },
        {
          productName: "商品40",
          productUnit: "个",
          num: 400,
          price: 4000,
          amount: 1600000,
          productSpec: "规格40",
          remark: "备注40",
        },
        {
          productName: "商品41",
          productUnit: "个",
          num: 410,
          price: 4100,
          amount: 1681000,
          productSpec: "规格41",
          remark: "备注41",
        },
        {
          productName: "商品42",
          productUnit: "个",
          num: 420,
          price: 4200,
          amount: 1764000,
          productSpec: "规格42",
          remark: "备注42",
        },
        {
          productName: "商品43",
          productUnit: "个",
          num: 430,
          price: 4300,
          amount: 1849000,
          productSpec: "规格43",
          remark: "备注43",
        },
        {
          productName: "商品44",
          productUnit: "个",
          num: 440,
          price: 4400,
          amount: 1936000,
          productSpec: "规格44",
          remark: "备注44",
        },
        {
          productName: "商品45",
          productUnit: "个",
          num: 450,
          price: 4500,
          amount: 2025000,
          productSpec: "规格45",
          remark: "备注45",
        },
        {
          productName: "商品46",
          productUnit: "个",
          num: 460,
          price: 4600,
          amount: 2116000,
          productSpec: "规格46",
          remark: "备注46",
        },
        {
          productName: "商品47",
          productUnit: "个",
          num: 470,
          price: 4700,
          amount: 2207000,
          productSpec: "规格47",
          remark: "备注47",
        },
        {
          productName: "商品48",
          productUnit: "个",
          num: 480,
          price: 4800,
          amount: 2298000,
          productSpec: "规格48",
          remark: "备注48",
        },
        {
          productName: "商品49",
          productUnit: "个",
          num: 490,
          price: 4900,
          amount: 2389000,
          productSpec: "规格49",
          remark: "备注49",
        },
        {
          productName: "商品50",
          productUnit: "个",
          num: 500,
          price: 5000,
          amount: 2480000,
          productSpec: "规格50",
          remark: "备注50",
        },
      ];
      // 根据示例生成 tableColumns
    },
    /** 刷新订单编号 */
    refreshOrderCode() { },
    /** 保存订单信息 */
    submitForm() {
      console.log('this.orderForm', this.orderForm);
      this.$refs["orderForm"].validate((valid) => {
        if (valid) {

        }
      });
    },
    /** 返回按钮 */
    close() {
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认关闭？")
          .then(() => {
            this.$tab.closeOpenPage(orderPage);
          })
          .catch(() => { });
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
        .catch(() => { });
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
        this.initPulldownData(value);
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件 */
    keyupEvent({ value }) {
      this.initPulldownData(value);
    },
    /** 商品名称输入框-值变更事件 */
    blurEvent({ parentRow, value }) {
      if (!parentRow) return;

      const quoteItem = this.pulldownTableData.find(
        (q) => q.productName === value
      );
      if (quoteItem) {
        parentRow.skuId = quoteItem.skuId;
        parentRow.productUnit = quoteItem.productUnit;
        parentRow.productSpec = quoteItem.productSpec;
      } else {
        parentRow.skuId = null;
        parentRow.productUnit = "斤";
        parentRow.productUnit = "";
        parentRow.productSpec = "";
      }
    },
    /** 商品名称输入框-清除按钮事件 */
    clearEvent(parentRow) {
      if (!parentRow) return;

      // 重设已选项
      parentRow.skuId = null;
      parentRow.productName = "";
      parentRow.productUnit = "";
      parentRow.productSpec = "";
      parentRow.num = "0.00";
      parentRow.price = "0.00";
      parentRow.amount = "0.00";

      // 重设列表
      this.pulldownTableData = this.skuQuoteDetails;
    },
    /** 商品名称下拉容器-选中元素事件 */
    cellClickEvent({ parentRow, row }) {
      const $table = this.$refs.xTable;
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        // 设置选中的 skuQuote 到订单详情
        parentRow.productName = row.productName;
        parentRow.productUnit = row.productUnit;
        parentRow.productSpec = row.productSpec;
        parentRow.skuId = 1;

        // 聚焦到数量单元格
        $table.setEditCell(parentRow, "num");
      }
    },
    /** 商品名称下拉容器-初始化数据 */
    initPulldownData(value) {
      if (value) {
        this.pulldownTableData = this.skuQuoteDetails.filter(
          (row) => row.productName.indexOf(value) > -1
        );
      } else {
        this.pulldownTableData = this.skuQuoteDetails;
      }
    },
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      const customerDeptId = value[value.length - 1]
      this.orderForm.customerDeptId = customerDeptId;
      this.orderForm.customerId = this.customerDeptMap[customerDeptId];
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCustomerDept().then((response) => {
        // init customerDeptMap
        this.customerDeptMap = response.data.reduce((map, item) => {
          map[item.id] = item.customerId;
          return map;
        });

        // init customerDeptOptions
        const treeList = this.handleTree(response.data);
        this.customerDeptOptions = this.transformData(treeList);
      }).then(() => {
        // 构造级联选择器选中的数据
        this.formSelectedOptions = this.fillWithParentCustomerDeptId(
          this.customerDeptOptions,
          this.orderForm.customerDeptId.toString()
        );
      })
    },
    /** 树形列表转换为级联列表 */
    transformData(data) {
      return data.map((item) => {
        const newItem = {
          value: item.id.toString(),
          label: item.name,
        };
        if (Array.isArray(item.children) && item.children.length > 0) {
          newItem.children = this.transformData(item.children);
        }
        return newItem;
      });
    },
    /** 根据 id 构造父节点列表，并添加自身 */
    fillWithParentCustomerDeptId(list, id) {
      if (!id) return [];
      function getParents(nodes, targetId, path = []) {
        for (const node of nodes) {
          path.push(node.value);
          if (
            node.value === targetId ||
            (node.children && getParents(node.children, targetId, path))
          ) {
            return path;
          }
          path.pop();
        }
        return null;
      }

      return getParents(list, id) || [];
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
