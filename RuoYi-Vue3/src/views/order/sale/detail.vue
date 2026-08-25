<template>
  <div class="app-container">
    <!-- 草稿恢复提示条（新单页，检测到未完成草稿时显示） -->
    <el-alert
      v-if="showDraftBanner && availableDrafts.length"
      type="warning"
      :closable="false"
      show-icon
      class="draft-recover-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          检测到未完成的订单草稿{{ availableDrafts[0].deptName ? '【' + availableDrafts[0].deptName + '】' : '' }}（保存于 {{ formatSavedAt(availableDrafts[0].savedAt) }}）
          <el-button link type="primary" @click="restoreLatestDraft">点击恢复</el-button>
          <el-button link @click="dismissDraftBanner">忽略</el-button>
        </span>
      </template>
    </el-alert>
    <el-row :gutter="10">
      <!-- 做单区 -->
      <el-col :span="16">
        <el-card class="order-card">
          <!-- 订单表单 -->
          <template #header>
            <span class="order-header-title">订单信息</span>
            <span
              v-if="draftStatusText"
              class="draft-status-tag"
              :class="draftStatusType"
              >{{ draftStatusText }}</span
            >
            <el-form
              ref="orderForm"
              :model="orderForm"
              :rules="rules"
              size="small"
              inline
              label-width="100px"
            >
              <el-form-item prop="customerDeptId">
                <template #label>
                  送货单位
                  <el-tooltip
                    content="查看或新增明细时不可修改"
                    placement="top"
                  >
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
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
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择配送日期"
                  clearable
                ></el-date-picker>
              </el-form-item>
              <el-form-item prop="orderCode">
                <template #label>
                  订单编号
                  <el-icon
                    @click="refreshOrderCode"
                    style="cursor: pointer"
                  >
                    <Refresh />
                  </el-icon>
                </template>
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
          </template>

          <!-- 订单表格 -->
          <div class="order-table-container" :style="{ height: tableHeight }">
            <vxe-table
              border
              :column-config="{ resizable: true }"
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
                    <el-icon><Rank /></el-icon>
                  </span>
                  <!-- 增加 -->
                  <span @click="throttledAddRow(rowIndex)">
                    <el-icon><Plus /></el-icon>
                  </span>
                  <!-- 减少 -->
                  <span @click="handleRemoveRow(row)">
                    <el-icon><Minus /></el-icon>
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
                          <!-- 商品名称列：关键词高亮 + 来源标签 -->
                          <template #productNameCell="{ row }">
                            <span class="prod-name-cell">
                              <span v-html="highlightKeyword(row.productName)"></span>
                              <el-tag
                                v-if="row.sourceTag"
                                size="small"
                                :type="row.sourceTag === '临时' ? 'info' : 'warning'"
                                class="prod-source-tag"
                                >{{ row.sourceTag }}</el-tag
                              >
                            </span>
                          </template>
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
                :edit-render="{ name: '$input', autoselect: true }"
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
                :edit-render="{ name: '$input', autoselect: true }"
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
                :edit-render="{ name: '$input', autoselect: true }"
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
                :edit-render="{ name: '$input', autoselect: true }"
              ></vxe-column>
              <vxe-column
                field="remark"
                title="备注"
                :edit-render="{ name: '$input', autoselect: true }"
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
          <template #header>
            <el-tabs v-model="rightTab" class="right-tabs" stretch>
              <el-tab-pane label="常用" name="frequent" />
              <el-tab-pane label="最近" name="recent" />
            </el-tabs>
          </template>
          <!-- 常用商品面板：近30天下单频率 Top，点击即插入明细 -->
          <div
            v-show="rightTab === 'frequent'"
            class="frequent-panel"
            :style="{ height: recentTableHeight }"
          >
            <div v-if="!orderForm.customerId" class="frequent-empty">
              <el-empty description="请先选择送货单位" :image-size="60" />
            </div>
            <div v-else-if="!frequentList.length" class="frequent-empty">
              <el-empty
                description="暂无常用商品（按近30天下单频率统计）"
                :image-size="60"
              />
            </div>
            <div v-else class="frequent-list">
              <div
                v-for="(item, idx) in frequentList"
                :key="item.skuId"
                class="frequent-item"
                @click="addFrequentProduct(item)"
              >
                <span class="frequent-index">{{ idx + 1 }}</span>
                <span class="frequent-name"
                  >{{ item.productName
                  }}<em v-if="item.productSpec" class="frequent-spec"
                    >（{{ item.productSpec }}）</em
                  ></span
                >
                <span class="frequent-unit">{{ item.productUnit }}</span>
                <span class="frequent-count">×{{ item.orderCount }}</span>
              </div>
            </div>
          </div>
          <!-- 最近订单 -->
          <div v-show="rightTab === 'recent'">
            <!-- 最近订单表单 -->
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
                    :icon="Search"
                    type="primary"
                    @click="handleRecentQuery"
                    circle
                    title="搜索"
                  ></el-button>
                  <el-button
                    :icon="Refresh"
                    @click="resetQuery"
                    circle
                    title="重置"
                    style="margin-left: 10px"
                  ></el-button>
                </el-col>
              </el-row>
            </el-form>
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
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { pageSaleOrder, getSaleOrder, genOrderCode, createSaleOrder, updateSaleOrder, recentSaleOrder } from "@/api/order/sale";
import { listSaleDetail, frequentSaleDetail } from "@/api/order/saleDetail";
import { listDrafts, saveDraft, removeDraft, restoreDraft } from "@/utils/saleDraft";
import { listCustomerSku } from "@/api/product/customerSku";
import { listTemp } from "@/api/product/temp";
import { queryPrice } from "@/api/price/query";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { QuestionFilled, Refresh, Rank, Plus, Minus, Search } from "@element-plus/icons-vue";

import XEUtils from "xe-utils";
import Sortable from "sortablejs";
const orderPage = { path: "/order/sale" };

/**
 * @description: 节流
 * @param {*} delay
 * @param {*} fn
 * @return {*}
 */
function throttle(delay, fn) {
  let firstTime = true
  let timer = null
  return function () {
    const args = [].slice.apply(arguments)
    if (firstTime) {
      fn.apply(this, args)
      firstTime = false
      return
    }
    if (timer) return
    timer = setTimeout(() => {
      fn.apply(this, args)
      clearTimeout(timer)
      timer = null
    }, delay)
  }
}

/** 深拷贝含忽略字段 */
function deepCloneWithoutFields(obj, ignoreFields = []) {
  // 兼容旧浏览器（structuredClone 为 Chrome 98+ API），订单明细为纯 JSON 数据，JSON 克隆等效。
  // 注意：Vue3 响应式对象是 Proxy，structuredClone 会抛 DataCloneError，需回退 JSON。
  let clone
  try {
    clone = typeof structuredClone === 'function' ? structuredClone(obj) : JSON.parse(JSON.stringify(obj))
  } catch (e) {
    clone = JSON.parse(JSON.stringify(obj))
  }

  function removeFields(o) {
    if (Array.isArray(o)) {
      o.forEach(item => removeFields(item));
    } else if (typeof o === 'object' && o !== null) {
      ignoreFields.forEach(field => delete o[field]);
      Object.values(o).forEach(removeFields);
    }
  }

  removeFields(clone);
  return clone;
}

/** 深对象对比(含注释) */
function deepEqual(obj1, obj2, path = '') {
  if (obj1 === obj2) return true; // 相同引用或基本类型相等

  // 检查是否为对象或 null
  if (typeof obj1 !== 'object' || obj1 === null || typeof obj2 !== 'object' || obj2 === null) {
    console.log(`Mismatch at ${path}: Types differ.`, { obj1, obj2 });
    return false;
  }

  const keys1 = Object.keys(obj1);
  const keys2 = Object.keys(obj2);

  // 比较键的数量
  if (keys1.length !== keys2.length) {
    console.log(`Mismatch at ${path}: Key lengths differ.`, { keys1, keys2 });
    return false;
  }

  // 比较每个键的值
  for (let key of keys1) {
    const newPath = path ? `${path}.${key}` : key;

    // 检查键是否存在
    if (!keys2.includes(key)) {
      console.log(`Mismatch at ${newPath}: Key does not exist in obj2.`);
      return false;
    }

    // 深度递归比较
    if (!deepEqual(obj1[key], obj2[key], newPath)) {
      console.log(`Mismatch at ${newPath}: Values do not match.`);
      return false;
    }
  }

  return true;
}

export default {
  name: "SaleDetail",
  setup() {
    return { QuestionFilled, Refresh, Rank, Plus, Minus, Search };
  },
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
        {
          field: "productName",
          title: "商品名称",
          minWidth: 200,
          slots: { default: "productNameCell" },
        },
        { field: "productUnit", title: "单位" },
        { field: "price", title: "单价" },
        { field: "productSpec", title: "规格" },
        { field: "remark", title: "备注" },
      ],
      // 当前鼠标悬停的行
      currentHoverRow: null,
      // 右侧面板当前 tab（frequent=常用 / recent=最近）
      rightTab: "recent",
      // 常用商品列表（近30天下单频率 Top）
      frequentList: [],
      // 草稿自动保存状态文案 / 类型（saving/saved/idle）
      draftStatusText: "",
      draftStatusType: "",
      // 可恢复的草稿列表（新单页提示恢复）
      availableDrafts: [],
      // 是否显示草稿恢复横幅
      showDraftBanner: false,
      // 商品下拉检索关键词（高亮用）
      pulldownKeyword: "",
      // 表单初始快照（判定是否脏）
      originalOrderForm: null,
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

    // 页面刷新/关闭前立即保存草稿（配合 5s 防抖自动保存）
    window.addEventListener("beforeunload", this.handleBeforeUnload);
    // 全局搜索 Jump 前立即落盘草稿（Phase 3 联动）
    window.addEventListener("sale-draft-flush", this.handleBeforeUnload);
  },
  beforeUnmount() {
    window.removeEventListener("beforeunload", this.handleBeforeUnload);
    window.removeEventListener("sale-draft-flush", this.handleBeforeUnload);
    if (this._draftTimer) clearTimeout(this._draftTimer);
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
    // 订单是否有未保存改动（明细对比 + 表单字段对比）
    isOrderDirty() {
      if (this.checkTableUpdted()) return true;
      if (!this.originalOrderForm) return false;
      const { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark } = this.orderForm;
      const cur = { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark };
      return JSON.stringify(cur) !== JSON.stringify(this.originalOrderForm);
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

    // 草稿：列表页跳转（query.draft）自动恢复；新单页展示恢复横幅
    this.checkDraftOnEnter();
  },
  watch: {
    orderForm: {
      handler() {
        this.scheduleDraftSave();
      },
      deep: true,
    },
    orderDetailList: {
      handler() {
        this.scheduleDraftSave();
      },
      deep: true,
    },
    "orderForm.customerId"(val) {
      this.loadFrequentProducts();
    },
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
    /** 获取当前客户的商品选项列表（客户商品池按配送点白名单过滤+临时商品） */
    async getSkuQuoteDetailList(keyword) {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.skuQuoteDetails = [];
        return;
      }
      const [poolRes, tempRes] = await Promise.all([
        listCustomerSku({
          customerId,
          deliveryPointId: this.orderForm.customerDeptId || undefined,
          keyword: keyword || undefined,
        }),
        listTemp({ customerId, name: keyword || undefined }),
      ]);
      // 客户商品池：已按配送点白名单过滤（通用池∪本点专属），别名/展示价取自池条目，交易价仍走取价引擎
      const pool = (poolRes.data || []).map((item) => ({
        skuId: item.skuId,
        productName: item.alias || item.skuName,
        productUnit: item.skuUnit,
        productSpec: item.skuSpecName || "",
        productMnemonicCode: item.skuMnemonicCode || "",
        price: item.priceOverride != null ? item.priceOverride : item.skuSalePrice,
        isTemp: false,
        sourceTag: item.alias && item.alias !== item.skuName ? "别名" : "",
        remark: "",
      }));
      // 临时商品：全局 + 客户专用（未转正）
      const temps = (tempRes.data || []).map((item) => ({
        skuId: null,
        productName: item.name,
        productUnit: item.unit || "斤",
        productSpec: item.spec || "",
        productMnemonicCode: "",
        price: item.defaultPrice,
        isTemp: true,
        sourceTag: "临时",
        remark: item.remark || "",
      }));
      this.skuQuoteDetails = [...pool, ...temps].map((item) => ({
        ...item,
        price: XEUtils.commafy(item.price == null ? 0 : item.price, { digits: 2 }),
      }));
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
              this.snapshotOriginalOrderForm();
              this.draftStatusText = "";

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
        genOrderCode({ refresh: true })
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
            const formatDay = (d) => {
              const y = d.getFullYear();
              const m = String(d.getMonth() + 1).padStart(2, "0");
              const day = String(d.getDate()).padStart(2, "0");
              return `${y}-${m}-${day}`;
            };

            // 若当前时间小于15点，则送货时间为今天，否则为明天
            const deliveryDate = nowHour < 15 ? formatDay(today) : formatDay(tomorrow);
            this.orderForm.deliveryDate = deliveryDate;

            // 初始化订单表格
            this.orderDetailList = [];
            this.handleAddRow();
            this.originalOrderDetailList = this.deepCloneOrderDetailList(
              this.orderDetailList
            );
            this.snapshotOriginalOrderForm();
            this.draftStatusText = "";

            // 初始化下拉列表
            this.getSkuQuoteDetailList();
          });
      }
      // 初始化最近订单列表
      this.handleRecentQuery();
    },
    /** 刷新订单编号 */
    refreshOrderCode() {
      genOrderCode({ refresh: true }).then((response) => {
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

          // save or update order
          if (this.orderForm.orderId) {
            updateSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                // 已保存订单：清除对应草稿
                removeDraft("saleDraft:order:" + this.orderForm.orderId);
                this.refreshDraftBanner();
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
                // 新单已保存：清除对应草稿
                removeDraft("saleDraft:new:" + this.orderForm.customerDeptId);
                this.refreshDraftBanner();
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
      // vxe-table v4 中 body 结构与 v3 不同：.vxe-table--body 不再是 .body--wrapper 的直接子元素，改用后代选择器
      const tbody = xTable.$el.querySelector(".body--wrapper .vxe-table--body tbody");
      if (!tbody) {
        return;
      }
      this.sortableX = Sortable.create(
        tbody,
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
        this.initPulldownData("");
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件（服务端按关键字检索，防抖） */
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
        // 设置选中的商品到订单详情
        parentRow.productName = row.productName;
        parentRow.productUnit = row.productUnit;
        parentRow.productSpec = row.productSpec;
        parentRow.productPrice = row.price;
        parentRow.skuId = row.skuId;
        parentRow.isTemp = row.isTemp ? 1 : 0;

        if (row.isTemp) {
          // 临时商品：使用默认单价，不参与取价
          this.calcAmount(parentRow);
        } else {
          // 正式SKU：两层取价（客户报价 > 客户模板），未命中提示人工填写
          this.applyPriceQuery(parentRow);
        }

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
    /** 取价：选中商品后按 客户报价>客户模板 取当期有效价；未命中清空价格并提示人工填写 */
    applyPriceQuery(parentRow) {
      const params = {
        customerId: this.orderForm.customerId,
        deliveryPointId: this.orderForm.customerDeptId,
        skuId: parentRow.skuId,
        deliveryDate: this.orderForm.deliveryDate,
      };
      if (!params.customerId || !params.skuId || !params.deliveryDate) {
        return;
      }
      queryPrice(params)
        .then((response) => {
          const result = response.data || {};
          if (result.price != null) {
            parentRow.productPrice = result.price;
            this.calcAmount(parentRow);
          } else {
            parentRow.productPrice = "";
            parentRow.amount = "0.00";
            this.$modal.msgWarning(
              "商品【" + parentRow.productName + "】无有效报价，请人工填写价格"
            );
          }
        })
        .catch(() => {
          // 取价失败不阻塞录单，保留报价明细默认价
        });
    },
    /** 商品名称下拉容器-初始化数据（服务端按关键字检索客户商品池+临时商品） */
    initPulldownData(value) {
      // 记录关键词供下拉单元格高亮
      this.pulldownKeyword = value || "";
      if (this._pulldownTimer) {
        clearTimeout(this._pulldownTimer);
      }
      this._pulldownTimer = setTimeout(() => {
        this.getSkuQuoteDetailList(value || "").then(() => {
          this.pulldownTableData = this.skuQuoteDetails;
        });
      }, 200);
    },
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      // init table item
      this.orderDetailList = [];
      this.throttledAddRow();

      // init customerDeptId（兼容清空时传入 null）
      const arr = Array.isArray(value) ? value : [];
      const customerDeptId = arr.length ? arr[arr.length - 1] : null;
      this.orderForm.customerDeptId = customerDeptId;
      this.orderForm.customerId = customerDeptId ? this.customerDeptMap[customerDeptId] : null;

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

    /* ========== 草稿自动保存（Phase 1.1） ========== */
    /** 进入页面时检查草稿：路由带 draft key 则自动恢复，否则新单页显示恢复横幅 */
    checkDraftOnEnter() {
      const draftKey = this.$route.query.draft;
      if (draftKey) {
        const draft = restoreDraft(draftKey);
        if (draft && !this.defaultOrderId) {
          this.restoreDraftIntoForm(draft);
        } else {
          this.$modal.msgWarning("草稿不存在或已过期");
        }
        return;
      }
      this.refreshDraftBanner();
    },
    /** 刷新草稿横幅（新单页且有未过期草稿时显示） */
    refreshDraftBanner() {
      this.availableDrafts = listDrafts();
      this.showDraftBanner =
        !this.defaultOrderId && this.availableDrafts.length > 0;
    },
    /** 恢复最新一条草稿 */
    restoreLatestDraft() {
      if (this.availableDrafts.length) {
        this.restoreDraftIntoForm(this.availableDrafts[0]);
      }
    },
    /** 忽略草稿（仅隐藏横幅，不删除数据） */
    dismissDraftBanner() {
      this.showDraftBanner = false;
    },
    /** 将草稿恢复到表单：校验客户/配送点仍存在，恢复后删除草稿 */
    restoreDraftIntoForm(draft) {
      const deptId = draft.customerDeptId;
      // 客户配送点树已加载时校验存在性
      if (
        deptId &&
        Object.keys(this.customerDeptMap).length &&
        !this.customerDeptMap[deptId]
      ) {
        this.$modal.msgWarning("草稿对应的送货单位已不存在，草稿已失效");
        removeDraft(draft.key);
        this.refreshDraftBanner();
        return;
      }
      this.orderForm = {
        orderId: draft.orderId || null,
        orderCode: draft.orderCode || null,
        customerId: draft.customerId || null,
        customerDeptId: draft.customerDeptId || null,
        deliveryDate: draft.deliveryDate || null,
        remark: draft.remark || null,
      };
      this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
        this.customerDeptOptions,
        deptId ? String(deptId) : ""
      );
      this.orderDetailList = (draft.details || []).map((row) => ({ ...row }));
      this.originalOrderDetailList = this.deepCloneOrderDetailList(
        this.orderDetailList
      );
      this.snapshotOriginalOrderForm();
      this.customerDeptDisabled = true;
      removeDraft(draft.key);
      this.refreshDraftBanner();
      this.getSkuQuoteDetailList();
      this.$modal.msgSuccess("已恢复草稿，请核对后保存");
    },
    /** 记录表单初始快照（不含 orderDetails，用于脏判定） */
    snapshotOriginalOrderForm() {
      const { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark } = this.orderForm;
      this.originalOrderForm = {
        orderId,
        orderCode,
        customerId,
        customerDeptId,
        deliveryDate,
        remark,
      };
    },
    /** 按配送点ID查名称（级联树） */
    deptNameOf(deptId) {
      if (!deptId) return "";
      let name = "";
      const find = (nodes) => {
        for (const n of nodes) {
          if (String(n.value) === String(deptId)) {
            name = n.label;
            return true;
          }
          if (n.children && find(n.children)) return true;
        }
        return false;
      };
      find(this.customerDeptOptions);
      return name;
    },
    /** 格式化保存时间 HH:mm */
    formatSavedAt(savedAt) {
      if (!savedAt) return "";
      const d = new Date(savedAt);
      const p = (n) => String(n).padStart(2, "0");
      return `${p(d.getHours())}:${p(d.getMinutes())}`;
    },
    /** 数据变更后 5s 防抖触发保存（配合 beforeunload 兜底） */
    scheduleDraftSave() {
      if (!this.isOrderDirty) return;
      this.draftStatusText = "草稿保存中...";
      this.draftStatusType = "saving";
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this._draftTimer = setTimeout(() => {
        this.saveDraftIfMeaningful();
      }, 5000);
    },
    /** 保存草稿（有实际内容才保存；恢复/重置后自动清理状态） */
    saveDraftIfMeaningful() {
      if (!this.isOrderDirty) return;
      const deptId = this.orderForm.customerDeptId;
      const hasContent = (this.orderDetailList || []).some(
        (row) =>
          row.productName &&
          (XEUtils.toNumber(row.num) > 0 || row.skuId)
      );
      if (!deptId || !hasContent) {
        this.draftStatusText = "未保存";
        this.draftStatusType = "idle";
        return;
      }
      saveDraft({
        orderId: this.orderForm.orderId,
        customerId: this.orderForm.customerId,
        customerDeptId: deptId,
        deptName: this.deptNameOf(deptId),
        orderCode: this.orderForm.orderCode,
        deliveryDate: this.orderForm.deliveryDate,
        remark: this.orderForm.remark,
        details: this.deepCloneOrderDetailList(this.orderDetailList),
      });
      this.draftStatusText = `已于 ${this.formatSavedAt(new Date())} 自动保存`;
      this.draftStatusType = "saved";
    },
    /** 页面刷新/关闭前立即保存 */
    handleBeforeUnload() {
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this.saveDraftIfMeaningful();
    },

    /* ========== 常用商品面板（Phase 1.2） ========== */
    /** 加载近30天下单频率 Top20（当前客户） */
    loadFrequentProducts() {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.frequentList = [];
        return;
      }
      frequentSaleDetail({ customerId, days: 30, limit: 20 }).then(
        (response) => {
          this.frequentList = response.data || [];
        }
      );
    },
    /** 点击常用商品：插入明细行（末尾）→ 取价 → 光标落数量列 */
    addFrequentProduct(item) {
      this.handleAddRow(-1);
      const row = this.orderDetailList[this.orderDetailList.length - 1];
      row.productName = item.productName;
      row.productUnit = item.productUnit || "斤";
      row.productSpec = item.productSpec || "";
      row.skuId = item.skuId;
      row.isTemp = 0;
      row.remark = "";
      // 带出客户商品池展示价（若有），交易价仍走取价引擎
      const pool = this.skuQuoteDetails.find((q) => q.skuId === item.skuId);
      row.productPrice = pool ? pool.price : "0.00";
      row.amount = "0.00";
      this.applyPriceQuery(row);
      // 光标跳至数量列
      this.$nextTick(() => {
        this.$refs.xTable.setEditCell(row, "num");
      });
      // 末尾追加空行，保持录单流
      this.handleAddRow(-1);
    },

    /* ========== 下拉关键词高亮（Phase 1.3） ========== */
    escapeHtml(s) {
      return String(s == null ? "" : s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
    },
    /** 匹配关键词包一层高亮 span（先转义再高亮，防 XSS） */
    highlightKeyword(name) {
      const raw = String(name == null ? "" : name);
      const kw = String(this.pulldownKeyword || "").trim();
      if (!kw) return this.escapeHtml(raw);
      const lower = raw.toLowerCase();
      const kl = kw.toLowerCase();
      let out = "";
      let idx = 0;
      let pos = lower.indexOf(kl);
      while (pos !== -1) {
        out += this.escapeHtml(raw.slice(idx, pos));
        out +=
          '<span class="kw-hl">' +
          this.escapeHtml(raw.slice(pos, pos + kw.length)) +
          "</span>";
        idx = pos + kw.length;
        pos = lower.indexOf(kl, idx);
      }
      out += this.escapeHtml(raw.slice(idx));
      return out;
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

/* 草稿恢复横幅 */
.draft-recover-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    .el-button + .el-button {
      margin-left: 8px;
    }
  }
}

/* 订单头部草稿状态标签 */
.order-header-title {
  margin-right: 10px;
}
.draft-status-tag {
  display: inline-block;
  padding: 0 8px;
  font-size: 12px;
  line-height: 20px;
  border-radius: 3px;
  margin-right: 10px;
  vertical-align: middle;
  &.saving {
    color: #909399;
    background: #f4f4f5;
  }
  &.saved {
    color: #67c23a;
    background: #f0f9eb;
  }
  &.idle {
    color: #e6a23c;
    background: #fdf6ec;
  }
}

/* 右侧选单区 Tabs + 常用面板 */
.right-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
  :deep(.el-tabs__nav-wrap::after) {
    height: 0;
  }
}
.frequent-panel {
  overflow-y: auto;
  .frequent-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }
  .frequent-list {
    padding: 4px 0;
    .frequent-item {
      display: flex;
      align-items: center;
      padding: 6px 10px;
      cursor: pointer;
      border-bottom: 1px dashed #ebeef5;
      &:hover {
        background: #f5f7fa;
      }
      .frequent-index {
        width: 22px;
        color: #c0c4cc;
        font-size: 12px;
        flex-shrink: 0;
      }
      .frequent-name {
        flex: 1;
        font-size: 13px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        .frequent-spec {
          color: #909399;
          font-style: normal;
        }
      }
      .frequent-unit {
        margin-left: 8px;
        color: #909399;
        font-size: 12px;
        flex-shrink: 0;
      }
      .frequent-count {
        margin-left: 8px;
        color: #409eff;
        font-size: 12px;
        flex-shrink: 0;
      }
    }
  }
}

/* 下拉商品名称：关键词高亮 + 来源标签 */
.prod-name-cell {
  .kw-hl {
    color: #f56c6c;
    font-weight: 600;
  }
  .prod-source-tag {
    margin-left: 6px;
  }
}
</style>
