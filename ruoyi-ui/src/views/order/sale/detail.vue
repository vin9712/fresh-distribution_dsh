<template>
  <div class="app-container">
    <el-row :gutter="10">
      <!-- 做单区 -->
      <el-col :span="16">
        <el-card class="order-card">
          <!-- 订单表单 -->
          <div slot="header">
            <span>订单信息</span>
            <el-form
              ref="orderForm"
              :model="orderForm"
              :rules="rules"
              size="small"
              inline
              label-width="100px"
            >
              <el-form-item prop="customerDeptId">
                <span slot="label">
                  送货单位
                  <el-tooltip
                    content="查看或新增明细时不可修改"
                    placement="top"
                  >
                    <i class="el-icon-question"></i>
                  </el-tooltip>
                </span>
                <el-cascader
                  v-model="selectedCustomerDepts"
                  placeholder="请选择送货单位"
                  :disabled="customerDeptDisabled"
                  :options="customerDeptOptions"
                  @change="handleFormOptionsChanged"
                  :props="{ expandTrigger: 'hover' }"
                  filterable
                />
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
                <el-input
                  v-model="orderForm.remark"
                  placeholder="请输入订单备注"
                >
                </el-input>
              </el-form-item>
              <el-form-item label="自动新增">
                <el-switch v-model="isContinueAdd" />
              </el-form-item>
            </el-form>
          </div>

          <!-- 订单表格 -->
          <div class="order-table-container" :style="{ height: tableHeight }">
            <vxe-table
              border
              resizable
              show-footer
              show-overflow
              keep-source
              ref="xTable"
              size="small"
              class="order-table"
              :height="tableInnerHeight"
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
                  <span @click="throttledAddRow(rowIndex)">
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
                :edit-render="{ name: 'VxeInput', autoselect: true }"
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
                        @keyup="keyupProductNameEvent"
                        @focus="focusProductNameEvent"
                        @blur="
                          blurProductNameEvent({
                            parentRow,
                            value: $event.value,
                          })
                        "
                        @clear="clearProductNameEvent(parentRow)"
                      ></vxe-input>
                    </template>

                    <template #dropdown>
                      <div class="product-dropdown-planel">
                        <vxe-grid
                          border
                          auto-resize
                          height="auto"
                          :row-config="{ isHover: true }"
                          :data="pulldownTableData"
                          :columns="pulldownTableColumn"
                          @cell-click="
                            pulldownCellClickEvent({
                              parentRow,
                              row: $event.row,
                            })
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
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.productUnit"
                    type="text"
                    @change="changedProductUnitEvent(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
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
                field="productPrice"
                title="单价"
                cell-type="number"
                :formatter="decimalFormatter('productPrice')"
                :edit-render="{ name: 'input', autoselect: true }"
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.productPrice"
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
          <el-form class="order-footer" label-width="100px">
            <el-form-item
              style="text-align: center; margin-left: -100px; margin-top: 10px"
            >
              <el-button @click="resetOrderForm()">重置</el-button>
              <el-button type="primary" @click="submitForm()">保存</el-button>
              <el-button @click="close()">返回</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 选单区 -->
      <el-col :span="8">
        <el-card class="recent-order-card">
          <!-- 最近订单表单 -->
          <div slot="header" class="recent-order-card-header">
            <span>最近订单</span>
            <el-form
              :model="recentQuery"
              size="small"
              ref="recentOrderForm"
              label-width="80px"
            >
              <!-- 第一行：天数选择器和客户选择器 -->
              <el-row :gutter="20">
                <el-col :span="9">
                  <el-form-item label="查询天数" prop="days">
                    <el-select
                      v-model="recentQuery.days"
                      placeholder="请选择查询天数"
                      @change="handleRecentQuery"
                      style="width: 100%"
                    >
                      <el-option label="1天" :value="1"></el-option>
                      <el-option label="3天" :value="3"></el-option>
                      <el-option label="7天" :value="7"></el-option>
                      <el-option label="14天" :value="14"></el-option>
                      <el-option label="30天" :value="30"></el-option>
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="15">
                  <el-form-item label="送货客户" prop="customerId">
                    <el-select
                      v-model="recentQuery.customerId"
                      @change="handleRecentQuery"
                      filterable
                      clearable
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in customerOptions"
                        :key="item.id"
                        :label="item.alias ? item.alias : item.name"
                        :value="item.id"
                      ></el-option>
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 第二行：搜索词输入框和按钮 -->
              <el-row :gutter="20" style="display: flex; align-items: center">
                <el-col :span="18">
                  <el-form-item
                    label="搜索词"
                    prop="keyword"
                    style="margin-bottom: 0"
                  >
                    <el-input
                      v-model="recentQuery.keyword"
                      placeholder="请输入订单编号/送货单位"
                      clearable
                      style="width: 100%"
                    ></el-input>
                  </el-form-item>
                </el-col>
                <el-col
                  :span="6"
                  style="display: flex; justify-content: flex-end"
                >
                  <el-button
                    icon="el-icon-search"
                    type="primary"
                    @click="handleRecentQuery"
                    circle
                    title="搜索"
                  ></el-button>
                  <el-button
                    icon="el-icon-refresh"
                    @click="resetQuery"
                    circle
                    title="重置"
                    style="margin-left: 10px"
                  ></el-button>
                </el-col>
              </el-row>
            </el-form>
          </div>
          <!-- 最近订单列表 -->
          <div
            class="recent-order-table-container"
            :style="{ height: recentTableHeight }"
          >
            <vxe-grid
              border
              auto-resize
              ref="recentOrderTable"
              size="small"
              height="auto"
              :row-config="{ isHover: true, isCurrent: true }"
              :data="recentOrderList"
              :columns="recentTableColumns"
              @current-change="handleRecentOrderRowChange"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  pageSaleOrder,
  getSaleOrder,
  genOrderCode,
  createSaleOrder,
  updateSaleOrder,
  recentSaleOrder,
} from "@/api/order/sale";
import { listSaleDetail } from "@/api/order/saleDetail";
import { customerListQuoteDetail } from "@/api/product/quoteDetail";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { throttle, deepCloneWithoutFields, deepEqual } from "@/utils";

import XEUtils from "xe-utils";
import Sortable from "sortablejs";
const orderPage = { path: "/order/sale" };

export default {
  name: "SaleDetail",
  data() {
    return {
      // 默认订单id
      defaultOrderId: null,
      // 最近订单查询条件
      recentQuery: {
        customerId: null,
        keyword: null,
        days: 3,
      },
      // 最近订单表格列
      recentTableColumns: [
        { field: "code", title: "订单编号" },
        { field: "deliveryDate", title: "配送时间" },
        { field: "deliveryName", title: "送货单位" },
        { field: "remark", title: "备注" },
      ],
      // 最近订单列表
      recentOrderList: [],
      // 客户列表数据
      customerOptions: [],
      // 已选择的送货单位
      selectedCustomerDepts: [],
      // 送货单位map: <customerDeptId, customerId>
      customerDeptMap: {},
      // 送货单位下拉选项
      customerDeptOptions: [],
      // 送货单位是否禁用
      customerDeptDisabled: false,
      // 订单明细列表
      orderDetailList: [],
      // 原订单明细列表，用于对比
      originalOrderDetailList: [],
      // 订单表单
      orderForm: {
        orderId: null,
        orderCode: null,
        customerId: null,
        customerDeptId: null,
        deliveryDate: null,
        remark: null,
        orderDetails: [],
      },
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
        productPrice: [
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
      pulldownTableColumn: [
        { field: "productName", title: "商品名称" },
        { field: "productUnit", title: "单位" },
        { field: "price", title: "单价" },
        { field: "productSpec", title: "规格" },
        { field: "remark", title: "备注" },
      ],
      // 当前鼠标悬停的行
      currentHoverRow: null,
      // 每行的大致高度，单位为像素
      rowHeight: 40,
      // 最大显示行数
      maxRows: 15,
      // 是否继续添加订单
      isContinueAdd: true,
    };
  },
  mounted() {
    // 对添加行事件的加入节流处理, 150毫秒内多次触发只会执行一次
    this.throttledAddRow = throttle(150, this.handleAddRow.bind(this));

    // 组件挂载完成后添加一行
    this.throttledAddRow();
    this.rowDrop();
  },
  beforeDestroy() {
    if (this.sortableX) {
      this.sortableX.destroy();
    }
  },
  computed: {
    // 计算表格容器的高度
    tableHeight() {
      const headerFooterHeight = 120; // 头部和底部的高度总和，可以根据实际情况调整
      const calculatedHeight = this.maxRows * this.rowHeight + "px";
      const viewportHeight = `calc(100vh - ${headerFooterHeight}px)`;

      // 返回较小的那个值作为表格容器的最大高度
      return `min(${calculatedHeight}, ${viewportHeight})`;
    },
    // 计算最近订单表格容器的高度
    recentTableHeight() {
      const headerFooterHeight = 120; // 头部和底部的高度总和，可以根据实际情况调整
      const calculatedHeight = 17 * this.rowHeight + "px";
      const viewportHeight = `calc(100vh - ${headerFooterHeight}px)`;

      // 返回较小的那个值作为表格容器的最大高度
      return `min(${calculatedHeight}, ${viewportHeight})`;
    },
    // 计算表格内部的高度
    tableInnerHeight() {
      // 固定15行的高度
      return `${this.maxRows * this.rowHeight}px`;
    },
  },
  created() {
    // 从路由获取参数
    const orderIdFromParams = this.$route.query.orderId;
    this.defaultOrderId = orderIdFromParams
      ? parseInt(orderIdFromParams, 10)
      : null;

    // 初始化数据
    this.getTreeselect();
    this.getCustomerList();
    this.initOrderDetailPage(this.defaultOrderId);
  },
  methods: {
    /** 查询最近订单列表 */
    handleRecentQuery() {
      recentSaleOrder(this.recentQuery).then((response) => {
        this.recentOrderList = response.data;
      });
    },
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
      });
    },
    /** 重置查询条件 */
    resetQuery() {
      this.resetForm("recentOrderForm");
      this.handleRecentQuery();
    },
    /** 获取当前客户的报价明细列表 */
    async getSkuQuoteDetailList() {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.skuQuoteDetails = [];
        return;
      }
      const detailResponse = await customerListQuoteDetail({ customerId });
      const skuQuoteDetails = detailResponse.data || [];
      this.skuQuoteDetails = skuQuoteDetails.map((item) => {
        return {
          ...item,
          price: XEUtils.commafy(item.price, {
            digits: 2,
          }),
        };
      });
    },
    /** 初始化订单明细页 */
    initOrderDetailPage(orderId) {
      if (orderId) {
        // 获取当前 order 信息
        getSaleOrder(orderId)
          .then((response) => {
            const orderData = response.data;
            this.orderForm = {
              ...orderData,
              orderId: orderData.id,
              orderCode: orderData.code,
            };
            // 初始化送货单位下拉列表
            this.customerDeptDisabled = true;
            this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              this.orderForm.customerDeptId.toString()
            );
          })
          .then(() => {
            // 初始化订单表格
            listSaleDetail({ orderId: orderId }).then((response) => {
              const responseOrderDetails = response.data.map((item) => {
                return {
                  ...item,
                  productPrice: XEUtils.commafy(item.productPrice, {
                    digits: 2,
                  }),
                  num: XEUtils.commafy(item.num, {
                    digits: 2,
                  }),
                  amount: XEUtils.commafy(item.expectAmount, {
                    digits: 2,
                  }),
                };
              });
              this.orderDetailList =
                this.deepCloneOrderDetailList(responseOrderDetails);
              this.originalOrderDetailList =
                this.deepCloneOrderDetailList(responseOrderDetails);

              // 初始化下拉列表
              this.getSkuQuoteDetailList();
            });
          });
      } else {
        // 初始化 orderForm
        this.orderForm = {
          orderId: null,
          orderCode: null,
          customerId: null,
          customerDeptId: null,
          deliveryDate: null,
          remark: null,
        };
        genOrderCode()
          .then((response) => {
            // 初始化订单编号
            this.orderForm.orderCode = response.msg;
          })
          .then(() => {
            // 初始化送货单位下拉列表
            this.customerDeptDisabled = false;
            this.selectedCustomerDepts = [];

            // 初始化送货日期
            const today = new Date();
            const nowHour = today.getHours();
            const tomorrow = new Date(today);
            tomorrow.setDate(today.getDate() + 1);

            // 若当前时间小于15点，则送货时间为今天，否则为明天
            const deliveryDate = nowHour < 15 ? today : tomorrow;
            this.orderForm.deliveryDate = deliveryDate;

            // 初始化订单表格
            this.orderDetailList = [];
            this.handleAddRow();
            this.originalOrderDetailList = this.deepCloneOrderDetailList(
              this.orderDetailList
            );

            // 初始化下拉列表
            this.getSkuQuoteDetailList();
          });
      }
      // 初始化最近订单列表
      this.handleRecentQuery();
    },
    /** 刷新订单编号 */
    refreshOrderCode() {
      let param = { currentCode: this.orderForm.orderCode };
      genOrderCode(param).then((response) => {
        this.orderForm.orderCode = response.msg;
      });
    },
    /** 重置订单表单 */
    resetOrderForm() {
      const orderId = this.isContinueAdd ? null : this.orderForm.orderId;
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认重置？")
          .then(() => {
            this.initOrderDetailPage(orderId);
          })
          .catch(() => {});
      } else {
        this.initOrderDetailPage(orderId);
      }
    },
    /** 保存订单信息 */
    submitForm() {
      this.$refs["orderForm"].validate((valid) => {
        if (valid) {
          // set orderDetails
          if (this.orderDetailList.length == 0) {
            this.$modal.msgError("订单明细列表不能为空！");
            return;
          }
          // 去除空行，保留 productName, productUnit 都不为空，num > 0 的数据
          this.orderForm.orderDetails = this.orderDetailList.filter((item) => {
            // 判断是否为有效的商品数量
            let productNum = XEUtils.toNumber(item.num);
            const isValidNum =
              !isNaN(productNum) && productNum && productNum > 0;
            return item.productName && item.productUnit && isValidNum;
          });

          // 格式化金额
          this.orderForm.orderDetails = this.orderForm.orderDetails.map(
            (item) => {
              return {
                ...item,
                num: XEUtils.toNumber(item.num, /,/g, ".", 0),
                productPrice: XEUtils.toNumber(item.productPrice, /,/g, ".", 0),
                amount: XEUtils.toNumber(item.amount, /,/g, ".", 0),
              };
            }
          );

          // 订单总金额为 0 时不允许提交
          const totalAmount = this.orderForm.orderDetails.reduce(
            (sum, item) => sum + (Number(item.amount) || 0),
            0
          );
          if (totalAmount <= 0) {
            this.$modal.msgError("订单金额为 0，不允许提交订单，请检查订单明细！");
            return;
          }

          // save or update order
          if (this.orderForm.orderId) {
            updateSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                const orderId = this.isContinueAdd
                  ? null
                  : this.orderForm.orderId;
                this.initOrderDetailPage(orderId);
              }
            });
          } else {
            createSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("新增成功");
                const orderId = this.isContinueAdd ? null : response.data.id;
                this.initOrderDetailPage(orderId);
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
        if (formatValue <= 0 || formatValue < 0.01) {
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
      let price = XEUtils.toNumber(row.productPrice);
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
      const oldOrderDetails = this.deepCloneOrderDetailList(
        this.originalOrderDetailList
      );
      const orderDetails = this.deepCloneOrderDetailList(this.orderDetailList);
      return !deepEqual(oldOrderDetails, orderDetails);
    },
    /** 深拷贝订单明细列表 */
    deepCloneOrderDetailList(orderDetails) {
      if (!orderDetails) return [];
      return deepCloneWithoutFields(orderDetails, ["_X_ROW_KEY"]);
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
        productPrice: "0.00",
        amount: "0.00",
      };

      const index =
        rowIndex == null || rowIndex === -1
          ? this.orderDetailList.length
          : rowIndex + 1;
      this.orderDetailList.splice(index, 0, newRecord);

      // check & update customerDept cascader status
      this.updateCustomerDeptStatus();
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
            this.throttledAddRow();
          }
          // check & update customerDept cascader status
          this.updateCustomerDeptStatus();
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
    focusProductNameEvent({ value }) {
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        this.initPulldownData(value);
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件 */
    keyupProductNameEvent({ value }) {
      this.initPulldownData(value);
    },
    /** 商品名称输入框-值变更事件 */
    blurProductNameEvent({ parentRow, value }) {
      if (!parentRow) return;

      const quoteItem = this.pulldownTableData.find(
        (q) => q.productName === value
      );
      if (quoteItem) {
        parentRow.skuId = quoteItem.skuId;
        parentRow.productUnit = quoteItem.productUnit;
        parentRow.productSpec = quoteItem.productSpec;
        parentRow.remark = quoteItem.remark;
      } else {
        parentRow.skuId = null;
        parentRow.productUnit = "斤";
        parentRow.productSpec = "";
        parentRow.remark = "";
      }

      // 若当前为最后一行且当前商品名称不为空，新增一行
      const isLastRow =
        this.orderDetailList.length - 1 ===
        this.orderDetailList.indexOf(parentRow);
      const isValidRow = !XEUtils.isEmpty(parentRow.productName);
      if (isLastRow && isValidRow) {
        this.throttledAddRow(-1);
      }
    },
    /** 商品名称输入框-清除按钮事件 */
    clearProductNameEvent(parentRow) {
      if (!parentRow) return;

      // 重设已选项
      parentRow.skuId = null;
      parentRow.productName = "";
      parentRow.productUnit = "";
      parentRow.productSpec = "";
      parentRow.remark = "";
      parentRow.num = "0.00";
      parentRow.productPrice = "0.00";
      parentRow.amount = "0.00";

      // 重设列表
      this.pulldownTableData = this.skuQuoteDetails;
    },
    /** 商品名称下拉容器-选中元素事件 */
    pulldownCellClickEvent({ parentRow, row }) {
      const $table = this.$refs.xTable;
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        // 设置选中的 skuQuote 到订单详情
        parentRow.productName = row.productName;
        parentRow.productUnit = row.productUnit;
        parentRow.productSpec = row.productSpec;
        parentRow.productPrice = row.price;
        parentRow.skuId = row.skuId;

        // 聚焦到数量单元格
        $table.setEditCell(parentRow, "num");

        // 若当前为最后一行，则新增一行
        const isLastRow =
          this.orderDetailList.length - 1 ===
          this.orderDetailList.indexOf(parentRow);
        if (isLastRow) {
          this.throttledAddRow(-1);
        }
      }
    },
    /** 商品名称下拉容器-初始化数据 */
    initPulldownData(value) {
      if (value) {
        // 'i' 标志表示不区分大小写
        const regex = new RegExp(value, "i");
        this.pulldownTableData = this.skuQuoteDetails.filter(
          (row) =>
            regex.test(row.productName) || regex.test(row.productMnemonicCode)
        );
      } else {
        this.pulldownTableData = this.skuQuoteDetails;
      }
    },
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      // init table item
      this.orderDetailList = [];
      this.throttledAddRow();

      // init customerDeptId
      const customerDeptId = value[value.length - 1];
      this.orderForm.customerDeptId = customerDeptId;
      this.orderForm.customerId = this.customerDeptMap[customerDeptId];

      // init skuQuoteDetails
      this.getSkuQuoteDetailList();
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCustomerDept()
        .then((response) => {
          // init customerDeptMap
          this.customerDeptMap = Object.fromEntries(
            response.data.map(({ id, customerId }) => [id, customerId])
          );

          // init customerDeptOptions
          const treeList = this.handleTree(response.data);
          this.customerDeptOptions = this.transformData(treeList);
        })
        .then(() => {
          this.customerDeptDisabled = false;
          // 构造级联选择器选中的数据
          const customerDeptId = this.orderForm.customerDeptId;
          if (customerDeptId) {
            this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              customerDeptId.toString()
            );
          }
        });
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
    /** 商品单位变更事件 */
    changedProductUnitEvent(row) {
      const skuQuote = this.skuQuoteDetails.find(
        (item) =>
          item.productName === row.productName &&
          item.productUnit === row.productUnit &&
          item.productSpec === row.productSpec
      );

      // 重设 skuId
      if (skuQuote) {
        row.skuId = skuQuote.skuId;
      } else {
        row.skuId = null;
      }
    },
    /** 更新送货单位下拉选择器状态 */
    updateCustomerDeptStatus() {
      const orderId = this.orderForm.orderId;
      if (orderId) {
        this.customerDeptDisabled = true;
        return;
      }

      // 只有 orderDetailList 为空或只有一条数据时才为 false
      if (this.orderDetailList.length > 1) {
        this.customerDeptDisabled = true;
      } else {
        this.customerDeptDisabled = false;
      }
    },
    /** 最近订单行变更事件 */
    handleRecentOrderRowChange(event) {
      if (!event || !event.row) return;
      const orderId = event.row.id;
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认变更？")
          .then(() => {
            this.initOrderDetailPage(orderId);
          })
          .catch(() => {
            // 取消选中当前行
            this.$refs.recentOrderTable.clearCurrentRow();
          });
      } else {
        this.initOrderDetailPage(orderId);
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.order-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.order-header,
.order-footer {
  flex-shrink: 0; /* 确保头部和底部不被压缩 */
}

.order-table-container {
  flex-grow: 1; /* 让表格区域占据剩余的所有空间 */
  overflow-y: auto; /* 如果内容超出容器高度，允许滚动 */
}

.order-table {
  width: 100%;
  height: 100%;
}

.recent-order-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.recent-order-table-container {
  flex-grow: 1; /* 让表格区域占据剩余的所有空间 */
  overflow-y: auto; /* 如果内容超出容器高度，允许滚动 */
}

.drag-btn {
  cursor: move;
  font-size: 12px;
}

.product-name-dropdown {
  background-color: #fff;
  box-shadow: 0 0 6px 2px rgba(0, 0, 0, 0.1);

  .product-dropdown-planel {
    width: 600px;
    height: 300px;
  }

  .my-footdown4 {
    border-top: 1px solid #e8eaec;
  }
}
</style>
