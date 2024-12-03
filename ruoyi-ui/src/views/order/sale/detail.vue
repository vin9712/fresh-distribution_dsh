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
            <el-form-item prop="customerDeptId">
              <span slot="label">
                送货单位
                <el-tooltip
                  content="查看模式或有新增明细时不可修改"
                  placement="top"
                >
                  <i class="el-icon-question"></i>
                </el-tooltip>
              </span>
              <el-cascader
                v-model="formSelectedOptions"
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
                        blurProductNameEvent({ parentRow, value: $event.value })
                      "
                      @clear="clearProductNameEvent(parentRow)"
                    ></vxe-input>
                  </template>

                  <template #dropdown>
                    <div class="my-bodydown4">
                      <vxe-grid
                        border
                        auto-resize
                        height="auto"
                        :row-config="{ isHover: true }"
                        :data="pulldownTableData"
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
      <div class="field-box">
        <!-- 最近订单 -->
        <div class="recent-order-tab" v-show="currentTab === 'recentOrder'">
          <el-form :model="recentQuery" size="small" label-width="100px">
            <el-form-item label="订单编号">
              <el-input
                v-model="recentQuery.orderCode"
                placeholder="请输入订单编号"
              />
            </el-form-item>
            <el-form-item label="送货单位">
              <el-input
                v-model="recentQuery.customerDeptId"
                placeholder="请选择送货单位"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                icon="el-icon-search"
                size="mini"
                @click="handleRecentQuery"
                >搜索</el-button
              >
              <el-button icon="el-icon-refresh" size="mini" @click="resetQuery"
                >重置</el-button
              >
            </el-form-item>
          </el-form>
          <el-table :data="recentOrderList">
            <el-table-column label="订单编号" align="center" prop="code" />
            <el-table-column
              label="配送时间"
              align="center"
              prop="deliveryDate"
            />
            <el-table-column
              label="客户名称"
              align="center"
              prop="customerName"
            />" />
            <el-table-column
              label="送货单位"
              align="center"
              prop="customerDeptName"
            />
          </el-table>

          <pagination
            v-show="total > 0"
            :total="total"
            :page.sync="recentQuery.pageNum"
            :limit.sync="recentQuery.pageSize"
            @pagination="getOrderPageList"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {
  getSaleOrder,
  genOrderCode,
  createSaleOrder,
  updateSaleOrder,
} from "@/api/order/sale";
import { listSaleDetail } from "@/api/order/saleDetail";
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
      // 送货单位是否禁用
      customerDeptDisabled: false,
      // 订单明细列表
      orderDetailList: [],
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
      tableColumn: [
        { field: "productName", title: "商品名称" },
        { field: "productUnit", title: "单位" },
        { field: "productSpec", title: "规格" },
        { field: "remark", title: "备注" },
      ],
      // 当前鼠标悬停的行
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
    this.orderForm.orderId = this.defaultOrderId;
    this.orderForm.customerId = this.defaultCustomerId;
    this.orderForm.customerDeptId = this.defaultCustomerDeptId;

    // 初始化送货单位下拉列表
    this.customerDeptDisabled = this.orderForm.orderId != null;

    // 初始化数据
    this.getTreeselect();
    this.initOrderDetailPage();
    this.getSkuQuoteDetailList();
  },
  methods: {
    /** 获取最近订单列表 */
    getOrderPageList() {},
    /** 查询最近订单列表 */
    handleRecentQuery() {},
    /** 重置查询条件 */
    resetQuery() {},
    /** 获取当前客户的报价明细列表 */
    async getSkuQuoteDetailList() {
      const param = {
        customerId: this.orderForm.customerId || this.defaultCustomerId,
      };
      const detailResponse = await customerListQuoteDetail(param);
      this.skuQuoteDetails = detailResponse.data || [];
    },
    /** 初始化订单明细页 */
    initOrderDetailPage(orderId) {
      // 初始化页面
      if (orderId) {
        // 获取当前 order 信息
        getSaleOrder(orderId).then((response) => {
          const orderData = response.data;
          this.orderForm = {
            ...orderData,
            orderId: orderData.id,
            orderCode: orderData.code,
          };
          // todo init orderDetailList
        });
      } else {
        genOrderCode()
          .then((response) => {
            // 初始化订单编号
            this.orderForm.orderCode = response.msg;
          })
          .then(() => {
            // 初始化送货日期
            const today = new Date();
            const nowHour = today.getHours();
            const tomorrow = new Date(today);
            tomorrow.setDate(today.getDate() + 1);

            // 若当前时间小于15点，则送货时间为今天，否则为明天
            const deliveryDate = nowHour < 15 ? today : tomorrow;
            this.orderForm.deliveryDate = deliveryDate;
          });
      }
    },
    /** 获取当前订单编号 */
    getOrderCode() {},
    /** 刷新订单编号 */
    refreshOrderCode() {
      let param = { currentCode: this.orderForm.orderCode };
      genOrderCode(param).then((response) => {
        this.orderForm.orderCode = response.msg;
      });
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
          this.orderForm.orderDetails = this.orderDetailList;

          // save or update order
          if (this.orderForm.orderId) {
            updateSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                this.initOrderDetailPage(this.orderForm.orderId);
              }
            });
          } else {
            createSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("新增成功");
                const orderId = response.data.id;
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
            this.handleAddRow();
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
    cellClickEvent({ parentRow, row }) {
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
      this.handleAddRow();

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
          // 构造级联选择器选中的数据
          this.formSelectedOptions = this.fillWithParentCustomerDeptId(
            this.customerDeptOptions,
            this.orderForm.customerDeptId.toString()
          );
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
      if (orderId != null) {
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
