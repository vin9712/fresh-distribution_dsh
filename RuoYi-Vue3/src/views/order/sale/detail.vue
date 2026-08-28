<template>
  <div class="app-container">
    <!-- 浏览模式横幅：查看历史订单时常驻「返回我的新单」入口 -->
    <el-alert
      v-if="browseMode"
      type="info"
      :closable="false"
      show-icon
      class="browse-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          浏览模式：正在查看历史订单【{{ browsedOrder ? browsedOrder.code : '' }}】，数据只读、自动保存已暂停
          <el-button type="primary" size="small" @click="exitBrowseMode">← 返回我的新单</el-button>
        </span>
      </template>
    </el-alert>
    <!-- 配送/验收冻结提示条：已进入配送流程的订单明细为生成时快照，不可改单（S14/G6 护栏） -->
    <el-alert
      v-if="frozenBannerVisible"
      type="info"
      :closable="false"
      show-icon
      class="browse-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          该订单已进入配送/验收流程，明细为生成送货单时的冻结快照；配送后的真实退货请使用退货单，补货请新增销售订单
        </span>
      </template>
    </el-alert>
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
              <el-form-item label="送货单位" prop="customerDeptId">
                <div class="dept-cascader-line">
                  <el-cascader
                    ref="deptCascader"
                    v-model="selectedCustomerDepts"
                    placeholder="请选择送货单位"
                    :disabled="customerDeptLocked || browseMode"
                    :options="customerDeptOptions"
                    @change="handleFormOptionsChanged"
                    :props="{ expandTrigger: 'hover' }"
                    filterable
                  />
                  <!-- 整体锁定后的重开新单入口：清明细、重取价、重新分配编号 -->
                  <el-button
                    v-if="showReopenNewBtn"
                    link
                    type="danger"
                    class="reopen-new-btn"
                    @click="reopenNewOrder"
                    >重开新单</el-button
                  >
                </div>
              </el-form-item>
              <el-form-item label="配送日期" prop="deliveryDate">
                <el-date-picker
                  v-model="orderForm.deliveryDate"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择配送日期"
                  clearable
                  :disabled="orderInfoReadonly"
                ></el-date-picker>
              </el-form-item>
              <el-form-item prop="orderCode">
                <template #label>
                  订单编号
                  <el-icon
                    v-if="!orderInfoReadonly"
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
                  :disabled="orderInfoReadonly"
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
              <!-- 操作列（浏览/验收态隐藏增删拖拽） -->
              <vxe-column v-if="canEditOrder" field="operate" width="63">
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
                        @keyup="keyupProductNameEvent({ parentRow, value: $event.value })"
                        @focus="focusProductNameEvent({ parentRow, value: $event.value })"
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
                :class-name="priceCellClass"
                :edit-render="{ name: '$input', autoselect: true }"
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.productPrice"
                    type="text"
                    @change="handlePriceChange(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
              <vxe-column field="amount" title="金额"> </vxe-column>
              <!-- 实收区（只读镜像）：实收数据归验收单（C1），此处仅展示验收提交同步的 actual_* 镜像值 -->
              <vxe-column
                v-if="showActualColumns"
                field="actualNum"
                title="实收数量"
                cell-type="number"
                width="100"
                :formatter="decimalFormatter('actualNum')"
              />
              <vxe-column
                v-if="showActualColumns"
                field="lossReason"
                title="差异原因"
                width="120"
              >
                <template #default="{ row }">
                  <dict-tag
                    v-if="row.lossReason"
                    :options="dict.type.biz_loss_reason"
                    :value="row.lossReason"
                  />
                </template>
              </vxe-column>
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
              <el-button v-if="canEditOrder" @click="resetOrderForm()">重置</el-button>
              <el-button v-if="canEditOrder" type="primary" @click="submitForm()">保存</el-button>
              <el-button
                v-if="canEditOrder"
                type="success"
                plain
                :icon="Van"
                @click="handleFinishGenerate()"
                v-hasPermi="['order:delivery:generateCustomer']"
                >本客户订单已录完·出送货单</el-button
              >
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
                      v-model="recentQuery.recentDays"
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
            <!-- 浏览模式工具条：复制为新单（仅底层录入工作区为空时可用） -->
            <div v-if="browseMode" class="copy-as-new-bar">
              <el-tooltip
                :disabled="canCopyAsNew"
                content="请先完成并提交当前订单，再复制历史订单"
                placement="top"
              >
                <span>
                  <el-button
                    type="warning"
                    size="small"
                    :disabled="!canCopyAsNew"
                    @click="copyAsNew"
                    >复制为新单</el-button
                  >
                </span>
              </el-tooltip>
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
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { pageSaleOrder, getSaleOrder, genOrderCode, createSaleOrder, updateSaleOrder, recentSaleOrder } from "@/api/order/sale";
import { listSaleDetail, frequentSaleDetail } from "@/api/order/saleDetail";
import { listDrafts, saveDraft, removeDraft, restoreDraft, syncDraftToServer, fetchDraftFromServer, removeDraftFromServer } from "@/utils/saleDraft";
import { listCustomerSku } from "@/api/product/customerSku";
import { listTemp } from "@/api/product/temp";
import { queryPrice } from "@/api/price/query";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { Refresh, Rank, Plus, Minus, Search, Van } from "@element-plus/icons-vue";
import { generateDeliveryForCustomer } from "@/api/order/delivery";

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
  // 损耗原因固定字典（验收实收差异行必选）
  dicts: ["biz_loss_reason"],
  setup() {
    return { Refresh, Rank, Plus, Minus, Search, Van };
  },
  data() {
    return {
      // 默认订单id
      defaultOrderId: null,
      // 当前加载订单的状态（null=新单；2=已配送进入验收态；3+只读）
      orderStatus: null,
      // 浏览模式：查看历史订单（收起/恢复模型，不覆盖录入工作区）
      browseMode: false,
      browsedOrder: null,
      // 浏览前收起的录入工作区快照
      stashedWorkspace: null,
      stashWasEmpty: true,
      // 明细区门禁提示节流标记
      _gateWarnAt: 0,
      // 最近订单查询条件
      recentQuery: {
        customerId: null,
        keyword: null,
        recentDays: 3,
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
    /* ========== 录单页交互细化（客户锁定 / 浏览模式 / 验收态） ========== */
    /** 客户+配送点整体锁定：编辑已有单、浏览态；或新单已录入商品后（未录商品前允许直接改选配送点） */
    customerDeptLocked() {
      if (this.browseMode || !!this.orderForm.orderId) return true;
      return !!this.orderForm.customerDeptId && this.hasDetailContent;
    },
    /** 重开新单入口：新单录入中且已因录入商品而锁定 */
    showReopenNewBtn() {
      return !this.browseMode && !this.orderForm.orderId && !!this.orderForm.customerDeptId && this.hasDetailContent;
    },
    /** 是否处于可编辑的录单态（非浏览、非只读）；实收编辑已下线（C1：数据归验收单） */
    canEditOrder() {
      return !this.browseMode && !this.viewOnlyMode;
    },
    /** 订单信息是否只读（浏览/只读查看模式下，表头信息不可修改） */
    orderInfoReadonly() {
      return !this.canEditOrder;
    },
    /** 只读查看：已验收/已结算订单 */
    viewOnlyMode() {
      return !this.browseMode && !!this.orderForm.orderId && this.orderStatus != null && this.orderStatus >= 3;
    },
    /** 冻结提示条：已配送及之后状态的订单，明细为生成送货单时的快照 */
    frozenBannerVisible() {
      return (
        !this.browseMode &&
        !!this.orderForm.orderId &&
        this.orderStatus != null &&
        this.orderStatus >= 2
      );
    },
    /** 实收列展示：订单状态≥已配送 */
    showActualColumns() {
      return (
        (!this.browseMode && !!this.orderForm.orderId && this.orderStatus != null && this.orderStatus >= 2) ||
        (this.browseMode && !!this.browsedOrder && Number(this.browsedOrder.status) >= 2)
      );
    },
    /** 有效商品行：商品名+单位齐全 且 数量>0（页面默认空白行不算） */
    hasValidProductRow() {
      return (this.orderDetailList || []).some(
        (row) => row.productName && row.productUnit && XEUtils.toNumber(row.num) > 0
      );
    },
    /** 明细已录入内容：任一行有商品/数量/单价/备注（默认空白行不算），用于配送点锁定判定 */
    hasDetailContent() {
      return (this.orderDetailList || []).some(
        (row) =>
          row.productName ||
          row.remark ||
          XEUtils.toNumber(row.num) > 0 ||
          XEUtils.toNumber(row.productPrice) > 0
      );
    },
    /** 空工作区判定（§3.6）：没有有效商品行 或 没有选配送点，任一满足即视为空 */
    isWorkspaceEmpty() {
      return !this.hasValidProductRow || !this.orderForm.customerDeptId;
    },
    /** 复制为新单准入：浏览模式下底层录入工作区为空，且不是从已有单进入 */
    canCopyAsNew() {
      return this.browseMode && this.stashWasEmpty && !this.defaultOrderId;
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
    "orderForm.customerDeptId"() {
      // 送货单位变化：常用商品按新配送点白名单重新过滤
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
      // 重置浏览模式与订单状态标记
      this.browseMode = false;
      this.browsedOrder = null;
      this.stashedWorkspace = null;
      this.orderStatus = null;
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
            // 记录状态：2=已配送（验收态），≥3 只读
            this.orderStatus = orderData.status != null ? Number(orderData.status) : null;
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
                const row = {
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
                // 实收列：已配送及之后状态展示
                if (item.actualNum !== null && item.actualNum !== undefined) {
                  row.actualNum = XEUtils.commafy(item.actualNum, { digits: 2 });
                }
                return row;
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
    /** 刷新订单编号（补传 currentCode：相同则保持，被占用才 +1，避免浪费号段） */
    refreshOrderCode() {
      genOrderCode({ refresh: true, currentCode: this.orderForm.orderCode || undefined }).then(
        (response) => {
          this.orderForm.orderCode = response.msg;
        }
      );
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

          // 校验订单总金额：为 0 不允许保存
          const totalAmount = this.orderForm.orderDetails.reduce(
            (sum, item) => sum + (XEUtils.toNumber(item.amount) || 0),
            0
          );
          if (totalAmount <= 0) {
            this.$modal.msgWarning(
              "订单金额为 0，不允许保存，请检查商品单价或数量"
            );
            return;
          }

          // save or update order
          if (this.orderForm.orderId) {
            updateSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                // 已保存订单：清除对应草稿（本地 + 后端）
                removeDraft("saleDraft:order:" + this.orderForm.orderId);
                removeDraftFromServer(this.orderForm.orderId, this.orderForm.customerDeptId);
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
                // 新单已保存：清除对应草稿（本地 + 后端）
                removeDraft("saleDraft:new:" + this.orderForm.customerDeptId);
                removeDraftFromServer(null, this.orderForm.customerDeptId);
                this.refreshDraftBanner();
                const orderId = this.isContinueAdd ? null : response.data.id;
                this.initOrderDetailPage(orderId);
              }
            });
          }
        }
      });
    },
    /**
     * 本客户订单已录完·出送货单（S14 §6.1 入口①，手工生成为主路径）：
     * 按当前订单的 客户+配送日期 调统一生成服务，幂等补齐全部已确认订单（D-025）。 
     */
    handleFinishGenerate() {
      if (!this.orderForm.customerId || !this.orderForm.deliveryDate) {
        this.$modal.msgWarning("请先选择送货单位与配送日期");
        return;
      }
      if (!this.orderForm.orderId) {
        this.$modal.msgWarning("当前订单尚未保存，请先保存订单再出送货单");
        return;
      }
      if (this.isOrderDirty) {
        this.$modal.msgWarning("当前订单有未保存的改动，请先保存");
        return;
      }
      this.$modal
        .confirm(
          "将按【客户 + 配送日期 " + this.orderForm.deliveryDate + "】生成/补齐送货单：" +
            "已生成过的订单自动幂等跳过；未打印的既有单将被作废重建，已打印的走补充单。是否继续？"
        )
        .then(() => generateDeliveryForCustomer(this.orderForm.customerId, this.orderForm.deliveryDate))
        .then((response) => {
          const data = response.data || {};
          const created = (data.createdOrders || []).map((d) => d.code).filter(Boolean);
          const skipped = data.skippedReasons || [];
          let msg = "送货单生成完成";
          if (created.length) msg += "：新出单 " + created.join("、");
          if (skipped.length) msg += "；跳过 " + skipped.length + " 项（幂等）";
          if (!created.length && !skipped.length) msg += "：无遗漏订单，未产生新单据";
          this.$modal.msgSuccess(msg);
        })
        .catch(() => {});
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
          } else if (column.property === "actualNum") {
            return this.sumNum(data, "actualNum");
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
    /** vxe表格-编辑门禁：未选送货单位禁录；浏览/只读全禁（实收编辑已下线 C1） */
    checkTableActive({ row, column }) {
      // 浏览模式与已验收/已结算订单：全部只读
      if (this.browseMode || this.viewOnlyMode) {
        return false;
      }
      // 录单态明细区门禁：未选定客户(+配送点)前禁止编辑，引导先选送货单位
      if (!this.orderForm.customerDeptId) {
        const now = Date.now();
        if (now - this._gateWarnAt > 2000) {
          this._gateWarnAt = now;
          this.$modal.msgWarning("请先选择送货单位");
        }
        return false;
      }
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
    /** 添加行（明细区门禁：浏览态禁加行；未选送货单位时仅保留默认空行，禁止加行） */
    handleAddRow(rowIndex) {
      if (this.browseMode && (this.orderDetailList || []).length >= 1) return;
      if (!this.orderForm.customerDeptId && (this.orderDetailList || []).length >= 1) {
        const now = Date.now();
        if (now - this._gateWarnAt > 2000) {
          this._gateWarnAt = now;
          this.$modal.msgWarning("请先选择送货单位");
        }
        return;
      }
      if (!this.orderDetailList) {
        this.orderDetailList = [];
      }
      const newRecord = {
        productUnit: "斤",
        num: "0.00",
        productPrice: "0.00",
        amount: "0.00",
        refPrice: null,
        priceChanged: false,
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
      // 浏览态只读，禁止删行
      if (this.browseMode) return;
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
    focusProductNameEvent({ parentRow, value }) {
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        this.initPulldownData(value, parentRow);
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件（服务端按关键字检索，防抖） */
    keyupProductNameEvent({ parentRow, value }) {
      this.initPulldownData(value, parentRow);
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

      // 重设列表（过滤掉其它行已添加的 SKU）
      this._pulldownExcludeRow = parentRow;
      this.pulldownTableData = this.filterAddedSku(this.skuQuoteDetails);
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
        parentRow.priceChanged = false;
        parentRow.skuId = row.skuId;
        parentRow.isTemp = row.isTemp ? 1 : 0;

        if (row.isTemp) {
          // 临时商品：使用默认单价，不参与取价（以默认单价作为参考价）
          parentRow.refPrice = row.price;
          this.calcAmount(parentRow);
        } else {
          // 正式SKU：两层取价（客户报价 > 客户模板），未命中提示人工填写。
          // 参考价待取价引擎返回后再设置，避免用展示价误触发改价提醒。
          parentRow.refPrice = null;
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
            // 记录取价引擎参考价，用于改价提醒比对
            parentRow.refPrice = result.price;
            parentRow.priceChanged = false;
            this.calcAmount(parentRow);
          } else {
            // 未命中有效报价：清空价格，并重置参考价，避免用初始展示价误触发改价提醒
            parentRow.productPrice = "";
            parentRow.amount = "0.00";
            parentRow.refPrice = null;
            parentRow.priceChanged = false;
            this.$modal.msgWarning(
              "商品【" + parentRow.productName + "】无有效报价，请人工填写价格"
            );
          }
        })
        .catch(() => {
          // 取价失败不阻塞录单，保留报价明细默认价
        });
    },
    /** 单价变更：计算金额 + 与当期参考价比对，不一致则单元格提醒 */
    handlePriceChange(row) {
      this.calcAmount(row);
      const price = XEUtils.toNumber(row.productPrice);
      if (row.refPrice == null) {
        row.priceChanged = false;
        return;
      }
      const ref = XEUtils.toNumber(row.refPrice);
      const changed = Math.abs(price - ref) > 0.005;
      if (changed && !row.priceChanged) {
        this.$modal.msgWarning(
          "商品【" +
            row.productName +
            "】单价 " +
            price.toFixed(2) +
            " 与当期报价 " +
            ref.toFixed(2) +
            " 不一致，请确认"
        );
      }
      row.priceChanged = changed;
    },
    /** 单价与参考价不一致的单元格高亮 */
    priceCellClass({ row }) {
      return row.priceChanged ? "col-price-changed" : "";
    },
    /** 商品名称下拉容器-初始化数据（服务端按关键字检索客户商品池+临时商品） */
    initPulldownData(value, excludeRow) {
      // 记录关键词供下拉单元格高亮
      this.pulldownKeyword = value || "";
      // 记录当前编辑行，用于过滤「已在其它行添加」的 SKU
      this._pulldownExcludeRow = excludeRow || null;
      if (this._pulldownTimer) {
        clearTimeout(this._pulldownTimer);
      }
      this._pulldownTimer = setTimeout(() => {
        this.getSkuQuoteDetailList(value || "").then(() => {
          this.pulldownTableData = this.filterAddedSku(this.skuQuoteDetails);
        });
      }, 200);
    },
    /** 行或候选的 SKU 标识键（有 skuId 用 sku，临时商品用名称） */
    skuRowKey(row) {
      if (!row) return null;
      if (row.skuId != null && row.skuId !== "") return "sku:" + row.skuId;
      return row.productName ? "name:" + row.productName : null;
    },
    /** 过滤掉已在订单明细其它行添加过的 SKU，候选列表不重复展示 */
    filterAddedSku(list) {
      const excludeRow = this._pulldownExcludeRow || null;
      const added = new Set();
      (this.orderDetailList || []).forEach((row) => {
        if (row === excludeRow) return;
        const key = this.skuRowKey(row);
        if (key) added.add(key);
      });
      // 当前行自身已有商品始终保留（避免编辑本行时把当前商品也过滤掉）
      const selfKey = this.skuRowKey(excludeRow);
      return (list || []).filter((c) => {
        const key = this.skuRowKey(c);
        if (!key) return true;
        if (selfKey && key === selfKey) return true;
        return !added.has(key);
      });
    },
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      // Element Plus 的 togglePopperVisible 在 disabled 状态下会早退（无法关面板），
      // 而选定送货单位后组件立即锁定，故必须在置值锁定前先手动收起下拉面板
      const cascader = this.$refs.deptCascader;
      if (cascader && cascader.togglePopperVisible) {
        cascader.togglePopperVisible(false);
      }
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
    /** 更新送货单位下拉选择器状态（客户+配送点整体锁定的兼容入口） */
    updateCustomerDeptStatus() {
      // 整体锁定规则由 computed customerDeptLocked 控制，此处仅同步旧字段
      this.customerDeptDisabled =
        !!this.orderForm.orderId || !!this.orderForm.customerDeptId;
    },
    /* ========== 浏览模式（收起/恢复模型） ========== */
    /** 最近订单行点击：非空工作区先确认收起，再进入浏览模式 */
    handleRecentOrderRowChange(event) {
      if (!event || !event.row) return;
      const orderId = event.row.id;
      if (this.browseMode) {
        // 已在浏览中：直接切换目标订单
        this.loadBrowseOrder(orderId);
        return;
      }
      const workspaceEmpty = this.isWorkspaceEmpty;
      const proceed = () => {
        this.enterBrowseMode(orderId, workspaceEmpty);
      };
      if (!workspaceEmpty) {
        this.$modal
          .confirm("当前正在录入的新单将暂时收起，进入浏览模式？")
          .then(proceed)
          .catch(() => {
            this.$refs.recentOrderTable.clearCurrentRow();
          });
      } else {
        proceed();
      }
    },
    /** 收起当前工作区并载入历史订单浏览 */
    enterBrowseMode(orderId, wasEmpty) {
      this.stashCurrentWorkspace(wasEmpty);
      this.loadBrowseOrder(orderId);
    },
    /** 快照当前录入工作区（表头+明细+级联选中），供返回时原样恢复 */
    stashCurrentWorkspace(wasEmpty) {
      this.stashWasEmpty = !!wasEmpty;
      this.stashedWorkspace = {
        orderForm: JSON.parse(JSON.stringify(this.orderForm)),
        orderDetailList: this.deepCloneOrderDetailList(this.orderDetailList),
        originalOrderDetailList: this.deepCloneOrderDetailList(
          this.originalOrderDetailList
        ),
        selectedCustomerDepts: [...(this.selectedCustomerDepts || [])],
        originalOrderForm: this.originalOrderForm
          ? JSON.parse(JSON.stringify(this.originalOrderForm))
          : null,
        orderStatus: this.orderStatus,
      };
    },
    /** 载入历史订单到只读浏览态 */
    loadBrowseOrder(orderId) {
      getSaleOrder(orderId).then((response) => {
        const orderData = response.data;
        this.browsedOrder = orderData;
        // 更新订单信息（表头）到当前浏览的历史订单，否则上方表单不会跟随刷新
        this.orderForm = {
          ...orderData,
          orderId: orderData.id,
          orderCode: orderData.code,
        };
        this.orderStatus =
          orderData.status != null ? Number(orderData.status) : null;
        this.customerDeptDisabled = true;
        this.selectedCustomerDepts = this.orderForm.customerDeptId
          ? this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              this.orderForm.customerDeptId.toString()
            )
          : [];
        this.snapshotOriginalOrderForm();
      });
      listSaleDetail({ orderId }).then((response) => {
        const details = (response.data || []).map((item) => ({
          ...item,
          productPrice: XEUtils.commafy(item.productPrice, { digits: 2 }),
          num: XEUtils.commafy(item.num, { digits: 2 }),
          amount: XEUtils.commafy(item.expectAmount, { digits: 2 }),
          actualNum:
            item.actualNum !== null && item.actualNum !== undefined
              ? XEUtils.commafy(item.actualNum, { digits: 2 })
              : item.actualNum,
        }));
        // 直接替换展示列表；浏览态自动保存已暂停，不污染草稿
        this.orderDetailList = details;
        this.originalOrderDetailList = this.deepCloneOrderDetailList(details);
      });
      this.browseMode = true;
    },
    /** 返回我的新单：原样恢复收起的录入工作区 */
    exitBrowseMode() {
      const stash = this.stashedWorkspace;
      if (stash) {
        this.orderForm = stash.orderForm;
        this.orderDetailList = stash.orderDetailList.map((r) => ({ ...r }));
        this.originalOrderDetailList = stash.originalOrderDetailList;
        this.selectedCustomerDepts = stash.selectedCustomerDepts;
        this.originalOrderForm = stash.originalOrderForm;
        this.orderStatus = stash.orderStatus;
      }
      this.browseMode = false;
      this.browsedOrder = null;
      this.stashedWorkspace = null;
      // 清除右侧最近订单的当前行高亮
      this.$nextTick(() => {
        if (this.$refs.recentOrderTable) {
          this.$refs.recentOrderTable.clearCurrentRow();
        }
      });
    },
    /* ========== 复制为新单 ========== */
    /** 历史订单 → 新单：商品+数量灌入；单价按当前执行价逐行重取；日期/编号重新分配 */
    copyAsNew() {
      if (!this.canCopyAsNew || !this.browsedOrder) return;
      const sourceOrderId = this.browsedOrder.id;
      // 同步订单信息中的送货单位（客户 + 配送点）
      const sourceCustomerId = this.browsedOrder.customerId;
      const sourceDeptId = this.browsedOrder.customerDeptId;
      listSaleDetail({ orderId: sourceOrderId }).then((response) => {
        const sourceRows = (response.data || []).filter(
          (d) => d.isDeleted === false || d.isDeleted === 0 || d.isDeleted == null
        );
        if (!sourceRows.length) {
          this.$modal.msgWarning("该历史订单没有有效商品行");
          return;
        }
        // 重置为新单工作区
        this.browseMode = false;
        this.browsedOrder = null;
        this.stashedWorkspace = null;
        this.orderForm = {
          orderId: null,
          orderCode: null,
          customerId: sourceCustomerId,
          customerDeptId: sourceDeptId,
          deliveryDate: null,
          remark: "",
        };
        // 同步送货单位：构造级联选中路径（有配送点用配送点，否则用客户）
        this.selectedCustomerDepts = sourceDeptId
          ? this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              sourceDeptId.toString()
            )
          : sourceCustomerId
            ? this.fillWithParentCustomerDeptId(
                this.customerDeptOptions,
                sourceCustomerId.toString()
              )
            : [];
        // 送货日期重算：15 点前=今天，之后=明天
        const today = new Date();
        const tomorrow = new Date(today);
        tomorrow.setDate(today.getDate() + 1);
        const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
        this.orderForm.deliveryDate = today.getHours() < 15 ? fmt(today) : fmt(tomorrow);
        // 订单号按 §3.2 重新分配
        genOrderCode({ refresh: false }).then((res) => {
          this.orderForm.orderCode = res.msg;
        });
        // 商品+数量灌入，单价清零待取价引擎回填
        this.orderDetailList = sourceRows.map((d) => ({
          productName: d.productName,
          productUnit: d.productUnit || "斤",
          productSpec: d.productSpec || "",
          skuId: d.skuId,
          isTemp: 0,
          num: XEUtils.commafy(d.num, { digits: 2 }),
          productPrice: "0.00",
          refPrice: null,
          priceChanged: false,
          amount: "0.00",
          remark: "",
        }));
        this.originalOrderDetailList = this.deepCloneOrderDetailList(
          this.orderDetailList
        );
        this.snapshotOriginalOrderForm();
        this.draftStatusText = "";
        // 刷新商品池与常用商品，使下拉/报价按复制的客户+配送点生效
        this.getSkuQuoteDetailList();
        this.loadFrequentProducts();
        this.$modal.msgSuccess("已复制商品清单，单价正在按当前执行价重新获取");
        // 逐行按 配送点覆盖价 > 客户报价 > 报价模板 重取价
        this.$nextTick(() => {
          this.orderDetailList.forEach((row) => {
            if (row.skuId) {
              this.applyPriceQuery(row);
            }
          });
        });
      });
    },
    /* ========== 重开新单（客户+配送点整体换选入口） ========== */
    reopenNewOrder() {
      const deptName = this.deptNameOf(this.orderForm.customerDeptId);
      this.$modal
        .confirm(
          "重开新单将清空当前明细并重新分配订单编号" +
            (deptName ? "（当前送货单位【" + deptName + "】）" : "") +
          "，是否继续？"
        )
        .then(() => {
          this.initOrderDetailPage(null);
          this.$modal.msgSuccess("已重开新单，请重新选择送货单位");
        })
        .catch(() => {});
    },

    /* ========== 草稿自动保存（Phase 1.1） ========== */
    /** 进入页面时检查草稿：路由带 draft key 则自动恢复，否则新单页显示恢复横幅 */
    checkDraftOnEnter() {
      const draftKey = this.$route.query.draft;
      if (draftKey) {
        const draft = restoreDraft(draftKey);
        // 后端为主：同 key 后端草稿更新时间更新则优先
        fetchDraftFromServer(draft.orderId, draft.customerDeptId).then((serverDraft) => {
          let target = draft;
          if (serverDraft) {
            const serverAt = new Date(serverDraft.savedAt).getTime();
            const localAt = new Date(draft.savedAt).getTime();
            if (serverAt > localAt + 1000) {
              target = { key: draft.key, ...serverDraft };
            }
          }
          if (target && !this.defaultOrderId) {
            this.restoreDraftIntoForm(target);
          } else {
            this.$modal.msgWarning("草稿不存在或已过期");
          }
        });
        return;
      }
      // 无指定草稿时，若后端有本配送点的新单草稿也提示恢复（换电脑场景）
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
    /** 将草稿恢复到表单：校验客户/配送点仍存在，恢复后清除本地+后端草稿 */
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
      removeDraftFromServer(draft.orderId, draft.customerDeptId);
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
    /** 数据变更后路由：浏览态暂停；录单态走草稿双写（实收自动保存已下线 C1） */
    scheduleDraftSave() {
      if (this.browseMode) return; // 浏览模式暂停草稿写入，防止浏览动作污染新单草稿
      if (!this.isOrderDirty) return;
      this.draftStatusText = "草稿保存中...";
      this.draftStatusType = "saving";
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this._draftTimer = setTimeout(() => {
        this.saveDraftIfMeaningful();
      }, 5000);
    },
    /** 保存草稿（有实际内容才保存；后端为主 + localStorage 兜底断网场景） */
    saveDraftIfMeaningful() {
      if (!this.isOrderDirty || this.browseMode) return;
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
      const payload = {
        orderId: this.orderForm.orderId,
        customerId: this.orderForm.customerId,
        customerDeptId: deptId,
        deptName: this.deptNameOf(deptId),
        orderCode: this.orderForm.orderCode,
        deliveryDate: this.orderForm.deliveryDate,
        remark: this.orderForm.remark,
        details: this.deepCloneOrderDetailList(this.orderDetailList),
        savedAt: new Date().toISOString(),
      };
      // 双写：localStorage 兜底断网，后端为主（换电脑可恢复）
      saveDraft(payload);
      syncDraftToServer(payload);
      this.draftStatusText = `已于 ${this.formatSavedAt(new Date())} 自动保存`;
      this.draftStatusType = "saved";
    },
    /** 页面刷新/关闭前立即保存 */
    handleBeforeUnload() {
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this.saveDraftIfMeaningful();
    },

    /* ========== 常用商品面板（Phase 1.2） ========== */
    /** 加载近30天下单频率 Top20（当前客户+当前配送点白名单） */
    loadFrequentProducts() {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.frequentList = [];
        return;
      }
      frequentSaleDetail({
        customerId,
        days: 30,
        limit: 20,
        deliveryPointId: this.orderForm.customerDeptId || undefined,
      }).then((response) => {
        this.frequentList = response.data || [];
      });
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
      row.refPrice = pool ? pool.price : null;
      row.priceChanged = false;
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
/* 客户+配送点整体锁定的级联行 */
.dept-cascader-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.reopen-new-btn {
  padding-left: 4px;
}
/* 浏览模式横幅 */
.browse-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    display: inline-flex;
    align-items: center;
    gap: 10px;
  }
}
/* 复制为新单工具条 */
.copy-as-new-bar {
  margin-bottom: 8px;
  text-align: right;
}

/* 改价提醒：单价与当期报价不一致的单元格高亮 + "改"角标 */
:deep(.col-price-changed) {
  background-color: #fdf6ec !important;
}
:deep(.col-price-changed .vxe-cell)::after {
  content: "改";
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 12px;
  line-height: 18px;
  color: #e6a23c;
  border: 1px solid #e6a23c;
  border-radius: 3px;
}
</style>
