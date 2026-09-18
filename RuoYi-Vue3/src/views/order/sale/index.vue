<template>
  <div class="app-container">
    <!-- 草稿恢复提示条：列表页检测到未完成订单草稿 -->
    <el-alert
      v-if="availableDrafts.length"
      type="warning"
      :closable="false"
      show-icon
      class="draft-recover-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          检测到 {{ availableDrafts.length }} 个未完成的订单草稿{{
            availableDrafts[0].deptName
              ? "【" + availableDrafts[0].deptName + "】"
              : ""
          }}（保存于 {{ formatSavedTime(availableDrafts[0].savedAt) }}）
          <el-button link type="primary" @click="recoverLatestDraft"
            >去恢复</el-button
          >
          <el-button link @click="discardAllDrafts">全部忽略</el-button>
        </span>
      </template>
    </el-alert>
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
    >
      <el-form-item label="送货单位" prop="customerDeptId">
        <el-cascader
          ref="customerDeptCascader"
          v-model="selectedCustomerDepts"
          placeholder="请选择送货单位（支持多选）"
          :options="customerDeptOptions"
          @change="handleFormOptionsChanged"
          :props="{
            expandTrigger: 'hover',
            multiple: true,
            checkOnClickNode: true,
          }"
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          style="width: 300px"
        />
      </el-form-item>
      <el-form-item label="订单编号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入订单编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择订单状态"
          @change="handleQuery"
          clearable
        >
          <el-option
            v-for="dict in dict.type.t_sale_order_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="配送日期" prop="deliveryDate">
        <el-date-picker
          clearable
          v-model="queryParams.deliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择配送日期"
          @change="handleQuery"
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :icon="Search"
          size="small"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button :icon="Refresh" size="small" @click="resetQuery"
          >重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          :icon="Plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['order:sale:add']"
          >新增明细</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <!-- D-066：客户视角（默认）/ 明细视角切换，选择记忆到 localStorage -->
        <el-radio-group
          v-model="listView"
          size="small"
          @change="handleViewChange"
        >
          <el-radio-button value="customer">客户视角</el-radio-button>
          <el-radio-button value="detail">明细视角</el-radio-button>
        </el-radio-group>
      </el-col>
      <right-toolbar
        v-model:showSearch="showSearch"
        @queryTable="getPageList"
      ></right-toolbar>
    </el-row>

    <!-- 单号搜索自动切明细视角的提示（D-066） -->
    <el-alert
      v-if="autoSwitchTip"
      type="info"
      show-icon
      class="view-switch-tip"
      title="已按订单编号自动切换到明细视角（客户视角下按单号筛选语义不直观）"
      @close="autoSwitchTip = false"
    />

    <!-- 批量操作条：勾选订单后浮现，汇聚全部批量动作 -->
    <div v-if="formSelectedOptions.length > 0" class="batch-action-bar">
      <div class="batch-action-info">
        <span class="batch-action-count"
          >已选 <b>{{ formSelectedOptions.length }}</b> 条<template
            v-if="listView === 'customer' && selectedCustomerCount"
          >
            · 覆盖 <b>{{ selectedCustomerCount }}</b> 个客户</template
          ></span
        >
        <el-button link type="primary" :icon="Close" @click="clearSelection"
          >清空选择</el-button
        >
      </div>
      <div class="batch-action-btns">
        <el-button
          v-hasPermi="['order:sale:edit']"
          type="success"
          size="small"
          plain
          :icon="Check"
          @click="handleOrderApproval"
          >批量确认</el-button
        >
        <el-button
          v-hasPermi="['order:sale:settle']"
          type="primary"
          size="small"
          plain
          :icon="Check"
          @click="handleOrderSettle"
          >批量月结</el-button
        >
        <el-button
          v-hasPermi="['order:sale:recall']"
          type="info"
          size="small"
          plain
          :icon="RefreshLeft"
          @click="handleOrderRestore"
          >批量还原</el-button
        >
        <el-button
          v-hasPermi="['order:sale:remove']"
          type="danger"
          size="small"
          plain
          :icon="Delete"
          @click="handleDelete"
          >批量删除</el-button
        >
      </div>
    </div>

    <!-- ============ 客户视角（D-064）：一行 = 客户 + 配送日期 ============ -->
    <el-table
      v-if="listView === 'customer'"
      ref="customerTable"
      v-loading="loading"
      :data="customerList"
      :row-key="customerRowKey"
      :expand-row-keys="expandRowKeys"
      @expand-change="handleCustomerExpand"
      @row-dblclick="handleCustomerRowDblClick"
    >
      <el-table-column type="expand" width="40">
        <template #default="scope">
          <div class="customer-children">
            <div class="children-title">
              <span class="children-title-main"
                >{{ scope.row.orderCount }} 张订单</span
              >
              <span class="children-title-sub"
                >· 双击订单行可进入菜品明细</span
              >
            </div>
            <el-table
              v-loading="scope.row.__loading"
              :data="scope.row.orders || []"
              size="small"
              :row-key="childRowKey"
              class="children-table"
              @row-dblclick="handleRowDblClick"
              @select="
                (selection, childRow) =>
                  handleChildSelect(scope.row, selection, childRow)
              "
              @select-all="(selection) => handleChildSelectAll(scope.row, selection)"
              :ref="(el) => setChildTableRef(scope.row, el)"
            >
              <el-table-column type="selection" width="45" align="center" />
              <el-table-column
                label="订单编号"
                align="center"
                prop="code"
                min-width="165"
              >
                <template #default="c">
                  <el-button
                    link
                    type="primary"
                    class="code-link"
                    @click="handleOpenDetail(c.row)"
                    >{{ c.row.code }}</el-button
                  >
                </template>
              </el-table-column>
              <el-table-column
                label="配送点"
                align="center"
                prop="customerDeptName"
              >
                <template #default="c">
                  <span v-if="c.row.customerDeptName">{{
                    c.row.customerDeptName
                  }}</span>
                  <span v-else class="text-muted">未指定配送点</span>
                </template>
              </el-table-column>
              <!-- 班次列仅在该客户确实有班次数据时出现（无班次客户不占列） -->
              <el-table-column
                v-if="hasShiftInGroup(scope.row)"
                label="班次"
                align="center"
                width="78"
              >
                <template #default="c">
                  <el-tag
                    v-if="c.row.shiftCode"
                    size="small"
                    effect="plain"
                    type="primary"
                    >{{ shiftLabel(c.row.shiftCode) }}</el-tag
                  >
                  <span v-else class="text-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="总金额" align="center" width="100">
                <template #default="c">
                  <span class="amount">{{ formatAmount(c.row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                label="订单状态"
                align="center"
                prop="status"
                width="190"
              >
                <template #default="c">
                  <order-status-cell :row="c.row" />
                </template>
              </el-table-column>
              <el-table-column
                v-if="hasRemarkInGroup(scope.row)"
                label="备注"
                align="center"
                prop="remark"
                :show-overflow-tooltip="true"
              >
                <template #default="c">
                  <span class="text-muted">{{ c.row.remark || "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                align="center"
                class-name="small-padding fixed-width"
                min-width="330"
              >
                <template #default="c">
                  <order-row-actions
                    :row="c.row"
                    @recall="handleRecall"
                    @acceptance="handleGoAcceptance"
                    @revoke-acceptance="handleRevokeAcceptance"
                    @settle="handleSettle"
                    @delivery-change="handleDeliveryChange"
                    @edit="handleUpdate"
                    @adjustment="handleAdjustmentSummary"
                    @delete="handleDelete"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="客户"
        align="center"
        prop="customerName"
        min-width="150"
        :show-overflow-tooltip="true"
      >
        <template #default="scope">
          <span class="cust-name">{{ scope.row.customerName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="配送日期" align="center" width="105">
        <template #default="scope">
          <span class="text-muted">{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="张数 / 点数" align="center" width="110">
        <template #default="scope">
          <b class="num-strong">{{ scope.row.orderCount }}</b
          ><span class="text-muted"> 张</span>
          <template v-if="scope.row.pointCount"
            ><span class="text-muted"> · {{ scope.row.pointCount }} 点</span></template
          >
        </template>
      </el-table-column>
      <el-table-column label="进度" align="center" min-width="230">
        <template #default="scope">
          <!-- 彩色圆点 + 计数：比一排 tag 更安静，且颜色与字典 list_class 一致 -->
          <span class="progress-line">
            <span
              v-for="p in progressParts(scope.row)"
              :key="p.key"
              class="progress-item"
              :class="{ 'is-todo': p.todo }"
            >
              <i class="progress-dot" :style="{ background: p.color }"></i>
              <span class="progress-label">{{ p.label }}</span>
              <b class="progress-count">{{ p.count }}</b>
            </span>
            <!-- D-068：筛选只作用于分组口径，主行仍展示全部状态计数，故显式标注 -->
            <el-tag
              v-if="isFiltering"
              size="small"
              type="danger"
              effect="plain"
              class="filter-tag"
              >筛选中</el-tag
            >
          </span>
        </template>
      </el-table-column>
      <el-table-column label="合计金额" align="center" width="115">
        <template #default="scope">
          <b class="amount">{{ formatAmount(scope.row.totalAmount) }}</b>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        align="center"
        width="190"
        class-name="small-padding fixed-width"
      >
        <template #default="scope">
          <!-- D-067：主行只提供整组动作（作用于该客户当日全部订单），破坏性动作仍逐单 -->
          <el-button
            v-if="scope.row.draftCount > 0"
            v-hasPermi="['order:sale:edit']"
            size="small"
            link
            type="success"
            :icon="Check"
            @click="handleGroupConfirm(scope.row)"
            >确认草稿</el-button
          >
          <el-button
            v-if="scope.row.acceptedCount > 0"
            size="small"
            link
            type="primary"
            :icon="Check"
            @click="handleGroupSettle(scope.row)"
            v-hasPermi="['order:sale:settle']"
            >批量月结</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- ============ 明细视角（原平铺表格，D-066 保留） ============ -->
    <el-table
      v-else
      ref="tableRef"
      v-loading="loading"
      :data="saleList"
      @row-dblclick="handleRowDblClick"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column
        label="客户"
        align="center"
        prop="customerName"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="配送点" align="center" prop="customerDeptName" />
      <el-table-column
        label="订单编号"
        align="center"
        prop="code"
        min-width="170"
      />
      <el-table-column label="总金额" align="center" width="100">
        <template #default="scope">
          <span class="amount">{{ formatAmount(scope.row.amount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="订单状态" align="center" prop="status" width="180">
        <template #default="scope">
          <order-status-cell :row="scope.row" />
        </template>
      </el-table-column>
      <el-table-column
        label="配送日期"
        align="center"
        prop="deliveryDate"
        width="120"
      >
        <template #default="scope">
          <span>{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
      >
        <template #default="scope">
          <order-row-actions
            :row="scope.row"
            @recall="handleRecall"
            @acceptance="handleGoAcceptance"
            @revoke-acceptance="handleRevokeAcceptance"
            @settle="handleSettle"
            @delivery-change="handleDeliveryChange"
            @edit="handleUpdate"
            @adjustment="handleAdjustmentSummary"
            @delete="handleDelete"
          />
        </template>
      </el-table-column>
    </el-table>

    <!-- 月结调整摘要对话框（蓝图 §2「月结调整追溯」：客户+结算月粒度，不改写原订单快照） -->
    <el-dialog
      align-center
      :title="
        adjustmentSummary.orderCode
          ? '月结调整摘要 - ' + adjustmentSummary.orderCode
          : '月结调整摘要'
      "
      v-model="adjustmentSummary.open"
      width="640px"
      append-to-body
    >
      <el-alert
        v-if="!adjustmentSummary.billMonth"
        type="info"
        :closable="false"
        show-icon
        title="该订单尚未验收归月，暂无关联的下月调整单"
      />
      <template v-else>
        <div class="adjustment-summary-tip">
          结算月 <b>{{ adjustmentSummary.billMonth }}</b
          >，该客户该结算月共
          {{ adjustmentSummary.adjustments.length }} 张调整单（含草稿）:
        </div>
        <el-table :data="adjustmentSummary.adjustments" size="small" border>
          <el-table-column
            label="调整单号"
            align="center"
            prop="code"
            min-width="150"
          />
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <el-tag v-if="scope.row.status === 1" size="small" type="success"
                >已提交</el-tag
              >
              <el-tag v-else size="small" type="info">草稿</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="应收调整"
            align="center"
            prop="receivableAmount"
            width="110"
          />
          <el-table-column
            label="成本调整"
            align="center"
            prop="purchaseCostAmount"
            width="110"
          />
          <el-table-column
            label="备注"
            align="center"
            prop="remark"
            :show-overflow-tooltip="true"
          />
        </el-table>
        <div class="adjustment-summary-total">
          合计：应收调整
          <b>{{ adjustmentSummary.receivableTotal }}</b>
          元，采购成本调整
          <b>{{ adjustmentSummary.costTotal }}</b>
          元
        </div>
      </template>
    </el-dialog>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 添加或修改销售订单对话框 -->
    <!-- 订单调整对话框（S14 退役：写入口已下线，配送后真实退货走「配送后变更（退货标记）」，补货走新增销售订单） -->


  </div>
</template>

<script>
import {
  pageSaleOrder,
  pageSaleCustomer,
  listSale,
  delSale,
  updateOrderStatus,
} from "@/api/order/sale";
import { getOrderAdjustmentSummary } from "@/api/order/monthAdjustment";
import { listCustomerDept } from "@/api/partner/customerDept";
import { locateAcceptanceByOrder, revokeAcceptance } from "@/api/acceptance/acceptance";
import { listDrafts, removeDraft } from "@/utils/saleDraft";
import OrderStatusCell from "./orderStatusCell.vue";
import OrderRowActions from "./orderRowActions.vue";
import {
  Search,
  Refresh,
  Plus,
  Check,
  RefreshLeft,
  Close,
  Delete,
} from "@element-plus/icons-vue";

/** 视角记忆键（D-066）：客户视角 customer（默认）/ 明细视角 detail */
const LIST_VIEW_KEY = "sale:listView";
/** 单订单客户自动展开上限（D-065 便利性 vs N+1 请求开销）：页内超过该数时放弃自动展开，只保留用户手动展开 */
const AUTO_EXPAND_LIMIT = 5;

export default {
  name: "Sale",
  components: { OrderStatusCell, OrderRowActions },
  dicts: ["t_sale_order_status", "biz_shift_type"],
  setup() {
    return {
      Search,
      Refresh,
      Plus,
      Check,
      RefreshLeft,
      Close,
      Delete,
    };
  },
  data() {
    return {
      // 未完成订单草稿（列表页恢复横幅）
      availableDrafts: [],
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
      // 总条数（客户视角 = 客户行数；明细视角 = 订单行数）
      total: 0,
      // 销售订单表格数据（明细视角）
      saleList: [],
      // 客户视角分组数据（D-064）
      customerList: [],
      // 客户视角已展开的客户行 key（受控展开，刷新后保持）
      expandRowKeys: [],
      // 视角切换（D-066）
      listView: localStorage.getItem(LIST_VIEW_KEY) === "detail" ? "detail" : "customer",
      // 单号搜索自动切明细视角的提示
      autoSwitchTip: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: null,
        customerDeptId: null,
        customerIds: [],
        customerDeptIds: [],
        code: null,
        amount: null,
        status: null,
        deliveryDate: null,
      },
      // 已选择的列表（订单级）
      formSelectedOptions: [],
      // 勾选台账（订单ID → 行）：跨客户累计，且不随子表卸载丢失（D-067）
      selectedOrderMap: {},
      // 已选择的送货单位
      selectedCustomerDepts: [],
      // 送货单位树列表
      customerDeptOptions: [],
      // 月结调整摘要对话框（蓝图 §2「月结调整追溯」）
      adjustmentSummary: {
        open: false,
        orderCode: null,
        billMonth: null,
        adjustments: [],
        receivableTotal: "0",
        costTotal: "0",
      },
    };
  },
  computed: {
    /** 客户视角：勾选覆盖的客户数（批量条文案） */
    selectedCustomerCount() {
      return new Set(
        Object.values(this.selectedOrderMap).map((o) => o.customerId)
      ).size;
    },
    /** 是否处于筛选态（D-068 主行标「筛选中」）：仅状态/单号筛选会让进度计数"看起来缺状态" */
    isFiltering() {
      const q = this.queryParams;
      const hasStatus =
        q.status !== null && q.status !== undefined && q.status !== "";
      return !!(hasStatus || q.code);
    },
  },
  created() {
    // 子表实例引用（key = 客户行 key），用于展开时回填勾选；非响应式，避免函数 ref 触发重渲染
    this.childTableRefs = Object.create(null);
    this.getTreeselect();
    // 工作台卡片跳转携带的过滤条件（如待验收卡 → status=已配送）
    const routeStatus = this.$route.query.status;
    if (routeStatus !== undefined && routeStatus !== "") {
      this.queryParams.status = routeStatus;
    }
    this.getPageList();
    // 检测未完成订单草稿，展示恢复横幅
    this.refreshDrafts();
    // keep-alive 缓存页：首次 activated 随首次挂载触发，与 created 重复，跳过
    this._skipActivatedQuery = true;
  },
  activated() {
    // 本页被 keep-alive 缓存，created 仅首次生效；每次重新进入（从订单详情返回、
    // 工作台跳转等）都触发自动查询，保证订单状态/金额与最新一致
    if (this._skipActivatedQuery) {
      this._skipActivatedQuery = false;
      return;
    }
    // 路由携带的过滤条件变化时同步（如工作台待验收卡 → status=已配送）
    const routeStatus = this.$route.query.status;
    if (routeStatus !== undefined && routeStatus !== "" && String(this.queryParams.status) !== String(routeStatus)) {
      this.queryParams.status = routeStatus;
    }
    this.getPageList();
    // 录单页可能新增了草稿，重进时刷新恢复横幅
    this.refreshDrafts();
  },
  methods: {
    /** 刷新草稿列表（新单页恢复横幅用） */
    refreshDrafts() {
      this.availableDrafts = listDrafts();
    },
    /** 去恢复：携带 draft key 跳转录单页，详情页自动恢复 */
    recoverLatestDraft() {
      const draft = this.availableDrafts[0];
      if (!draft) return;
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { draft: draft.key },
      });
    },
    /** 全部忽略：删除所有草稿 */
    discardAllDrafts() {
      this.availableDrafts.forEach((d) => removeDraft(d.key));
      this.availableDrafts = [];
    },
    /** 格式化保存时间 HH:mm */
    formatSavedTime(savedAt) {
      if (!savedAt) return "";
      const d = new Date(savedAt);
      const p = (n) => String(n).padStart(2, "0");
      return `${p(d.getHours())}:${p(d.getMinutes())}`;
    },
    /** 查询销售订单列表 */
    getList() {
      this.loading = true;
      listSale(this.queryParams).then((response) => {
        this.saleList = response.data;
        this.loading = false;
      });
    },
    /** 列表刷新分发：客户视角 / 明细视角（所有既有调用方无需感知视角） */
    getPageList() {
      if (this.listView === "customer") {
        return this.getCustomerPageList();
      }
      return this.getDetailPageList();
    },
    /** 分页查询销售订单列表（明细视角，原行为） */
    getDetailPageList() {
      this.loading = true;
      pageSaleOrder(this.queryParams).then((response) => {
        this.saleList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 分页查询客户视角聚合行（D-064：一行 = 客户 + 配送日期，分页单位 = 客户行） */
    getCustomerPageList() {
      this.loading = true;
      // 数据整体重载：勾选台账失效，一并清空（避免用旧 id 批量操作）
      this.selectedOrderMap = {};
      this.syncSelection();
      pageSaleCustomer(this.queryParams)
        .then((response) => {
          this.customerList = (response.rows || []).map((r) => ({
            ...r,
            __key: r.customerId + "_" + r.deliveryDate,
            // 注意：字段名不能用 children——el-table 会把带 children 的行当成树形数据，
            // 触发 "For nested data item, row-key is required"（与展开列渲染路径冲突）
            orders: [],
            __loaded: false,
            __loading: false,
          }));
          this.total = response.total;
          this.loading = false;
          this.$nextTick(() => this.restoreExpandedCustomers());
        })
        .catch(() => {
          this.loading = false;
        });
    },
    /** 重载后恢复展开态：保留用户已展开的客户 + 单订单客户默认自动展开（D-065，页内超过 AUTO_EXPAND_LIMIT 个时放弃，避免每行一个请求的 N+1 开销） */
    restoreExpandedCustomers() {
      const exists = (k) => this.customerList.some((r) => r.__key === k);
      const kept = this.expandRowKeys.filter(exists);
      const singleOrderKeys = this.customerList
        .filter((r) => r.orderCount === 1)
        .map((r) => r.__key);
      const auto = singleOrderKeys.length <= AUTO_EXPAND_LIMIT ? singleOrderKeys : [];
      const keys = Array.from(new Set([...kept, ...auto]));
      this.expandRowKeys = keys;
      keys.forEach((k) => {
        const row = this.customerList.find((r) => r.__key === k);
        if (row) {
          // 单个客户加载失败不阻断其他客户（错误提示由请求拦截器统一给出）
          this.loadCustomerChildren(row).catch(() => {});
        }
      });
    },
    /** 客户行展开/收起：展开即懒加载该客户当日订单（子行复用既有 /order/sale/list） */
    handleCustomerExpand(row, expandedRows) {
      if (!row) return;
      // 受控展开态与内部状态同步，刷新后不丢
      const expanded = Array.isArray(expandedRows)
        ? expandedRows.some((r) => r.__key === row.__key)
        : true;
      if (Array.isArray(expandedRows)) {
        this.expandRowKeys = expandedRows.map((r) => r.__key);
      }
      // 收起时不再发请求（已加载数据与勾选台账都保留）
      if (expanded) {
        this.loadCustomerChildren(row).catch(() => {});
      }
    },
    /** 双击客户行 = 展开/收起（不跳转；菜品明细仍进订单明细页） */
    handleCustomerRowDblClick(row, column, event) {
      if (!row || !this.$refs.customerTable) return;
      // 子表（订单行）内的双击会冒泡上来，不能把客户行收起
      const target = event && event.target;
      if (target && target.closest && target.closest(".customer-children")) {
        return;
      }
      this.$refs.customerTable.toggleRowExpansion(row);
    },
    /** 加载客户行子订单（幂等：已加载直接返回并回填勾选） */
    loadCustomerChildren(row) {
      if (row.__loaded) {
        this.$nextTick(() => this.restoreChildSelection(row));
        return Promise.resolve(row.orders || []);
      }
      row.__loading = true;
      const query = {
        ...this.queryParams,
        // 分组键定位：不受分页参数影响。
        // 注意：/order/sale/list 是无分页全量接口（后端不 startPage），pageSize 在此仅是占位；
        // 整组动作（确认草稿/批量月结）与勾选台账的正确性依赖“子行不截断”，勿改为分页接口
        pageNum: 1,
        pageSize: 200,
        customerId: row.customerId,
        deliveryDate: row.deliveryDate,
      };
      return listSale(query)
        .then((response) => {
          row.orders = response.data || [];
          row.__loaded = true;
          return row.orders;
        })
        .finally(() => {
          row.__loading = false;
          this.$nextTick(() => this.restoreChildSelection(row));
        });
    },
    /** 子表实例登记（函数 ref；卸载时置 null 由 setChildTableRef 处理） */
    setChildTableRef(row, el) {
      if (!row || !row.__key) return;
      if (el) {
        this.childTableRefs[row.__key] = el;
      } else {
        delete this.childTableRefs[row.__key];
      }
    },
    /** 展开时回填勾选（子表卸载会丢 DOM 勾选态，台账仍保留） */
    restoreChildSelection(row) {
      const table = this.childTableRefs[row.__key];
      if (!table || !Array.isArray(row.orders)) return;
      row.orders.forEach((r) => {
        table.toggleRowSelection(r, !!this.selectedOrderMap[r.id]);
      });
    },
    /** 子行勾选（@select）：按行增删台账 */
    handleChildSelect(customerRow, selection, childRow) {
      if (selection.includes(childRow)) {
        this.selectedOrderMap[childRow.id] = childRow;
      } else {
        delete this.selectedOrderMap[childRow.id];
      }
      this.syncSelection();
    },
    /** 子表全选/取消全选（@select-all）：只影响该客户下的订单 */
    handleChildSelectAll(customerRow, selection) {
      if (selection && selection.length) {
        selection.forEach((r) => {
          this.selectedOrderMap[r.id] = r;
        });
      } else {
        (customerRow.orders || []).forEach((r) => {
          delete this.selectedOrderMap[r.id];
        });
      }
      this.syncSelection();
    },
    /** 台账 → 批量条（formSelectedOptions / ids / single / multiple） */
    syncSelection() {
      const list = Object.values(this.selectedOrderMap);
      this.formSelectedOptions = list;
      this.ids = list.map((o) => o.id);
      this.single = list.length !== 1;
      this.multiple = !list.length;
    },
    /** 视角切换（D-066）：记忆 + 重查 + 清空选择与展开 */
    handleViewChange(view) {
      localStorage.setItem(LIST_VIEW_KEY, view);
      this.autoSwitchTip = false;
      this.queryParams.pageNum = 1;
      this.clearSelection();
      this.expandRowKeys = [];
      this.getPageList();
    },
    /** 搜索按钮操作 */
    handleQuery() {
      // D-066：按订单编号搜索时客户视角语义不直观，自动切明细视角
      if (this.queryParams.code && this.listView === "customer") {
        this.listView = "detail";
        this.autoSwitchTip = true;
        this.clearSelection();
        this.expandRowKeys = [];
      }
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      // 清空级联选择器选中值
      this.selectedCustomerDepts = [];
      this.queryParams.customerDeptIds = [];
      this.queryParams.customerIds = [];
      this.handleQuery();
    },
    // 多选框选中数据（明细视角）
    handleSelectionChange(selection) {
      this.formSelectedOptions = selection;
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
      // 台账同步，保证切到客户视角时勾选不丢
      this.selectedOrderMap = {};
      selection.forEach((r) => {
        this.selectedOrderMap[r.id] = r;
      });
    },
    /** 清空表格勾选（两个视角共用） */
    clearSelection() {
      this.selectedOrderMap = {};
      this.syncSelection();
      this.$refs["tableRef"] && this.$refs["tableRef"].clearSelection();
      Object.values(this.childTableRefs).forEach((t) => {
        t && t.clearSelection && t.clearSelection();
      });
    },
    /** 客户行 key（分组唯一键） */
    customerRowKey(row) {
      return row.__key;
    },
    /** 子订单行 key */
    childRowKey(row) {
      return row.id;
    },
    /** 客户行进度分解（彩色圆点 + 计数）：颜色与字典 list_class 一致；todo=需人工跟进的状态 */
    progressParts(row) {
      const defs = [
        { key: "draft", label: "草稿", count: row.draftCount, color: "#909399", todo: true },
        { key: "confirmed", label: "已确认", count: row.confirmedCount, color: "#409eff", todo: false },
        { key: "delivered", label: "已配送", count: row.deliveredCount, color: "#67c23a", todo: true },
        { key: "accepted", label: "已验收", count: row.acceptedCount, color: "#e6a23c", todo: true },
        { key: "settled", label: "已结算", count: row.settledCount, color: "#f56c6c", todo: false },
      ];
      return defs.filter((d) => d.count);
    },
    /** 该客户当日订单里是否真的有班次数据（决定子表是否出班次列） */
    hasShiftInGroup(row) {
      return (row.orders || []).some((o) => !!o.shiftCode);
    },
    /** 该客户当日订单里是否有备注（无备注不出整列，避免一列“—”） */
    hasRemarkInGroup(row) {
      return (row.orders || []).some((o) => !!(o.remark && String(o.remark).trim()));
    },
    /** 班次码 → 字典文案（biz_shift_type） */
    shiftLabel(code) {
      const hit = (this.dict.type.biz_shift_type || []).find((d) => d.value === code);
      return hit ? hit.label : code;
    },
    /** 金额统一两位小数（两视角显示一致） */
    formatAmount(value) {
      if (value === null || value === undefined || value === "") return "0.00";
      const n = Number(value);
      return Number.isFinite(n) ? n.toFixed(2) : String(value);
    },
    /** 打开订单明细页（子表单号点击 / 双击行共用，与明细视角双击行为一致） */
    handleOpenDetail(row) {
      if (!row) return;
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { orderId: row.id },
      });
    },
    /** 新增按钮操作 */
    handleAdd() {
      // 跳转到新增详情
      this.$router.push({
        path: "/order/sale-detail/index/",
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      const orderId = row.id;
      // 跳转到订单详情
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { orderId: orderId },
      });
    },
    /** 批量审核订单 */
    handleOrderApproval() {
      // 校验是否全为制单状态订单
      const valid = this.formSelectedOptions.some((item) => item.status !== 0);
      if (valid) {
        this.$modal.msgError("请选择制单状态的订单");
        return;
      }
      const orderIds = this.formSelectedOptions.map((item) => item.id);
      this.$modal
        .confirm("是否确认审核选中的" + orderIds.length + "条的订单？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 1,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 批量还原订单状态 */
    handleOrderRestore(row) {
      // 获取选中的订单
      const orderList = row && row.status ? [row] : this.formSelectedOptions;
      // 校验是否全为审核状态订单
      const valid = orderList.some((item) => item.status !== 1);
      if (valid) {
        this.$modal.msgError("请选择审核状态的订单");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
      this.$modal
        .confirm("是否确认还原选中的" + orderIds.length + "条订单的状态？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 0,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 去验收（OA：订单维度优先 → 订单页验收模式；历史单回退旧验收页送货单反查链路） */
    /** D-055 配送后变更（加单/换货/退货）：跳订单明细页变更模式（布局同验收，无实收），原订单不变标记附加明细 */
    handleDeliveryChange(row) {
      this.$router.push({
        path: "/order/sale-detail/index",
        query: { mode: "change", orderId: row.id },
      });
    },
    handleGoAcceptance(row) {
      locateAcceptanceByOrder(row.id)
        .then((response) => {
          const info = response.data || {};
          // ① OA 订单维度：跳订单明细页验收模式（orderId 携带，页面自动定位/建草稿）
          if (info.orderView) {
            this.$router.push({
              // 注意：子路由路径为 /index/，直接 push 父路径会因无子路由匹配而白屏
              path: "/order/sale-detail/index",
              query: {
                mode: "acceptance",
                orderId: row.id,
                acceptanceId: info.hasAcceptance ? info.acceptanceId : undefined,
              },
            });
            return;
          }
          // ② 历史回退：送货单反查链路（status=2 历史单，旧验收页）
          if (!info.deliveryId) {
            this.$modal.msgError("该订单尚未进入有效送货单，无法验收");
            return;
          }
          const query = { deliveryId: info.deliveryId, highlightOrder: row.id };
          if (info.hasAcceptance) {
            // 已有验收单：直接跳转定位
            query.acceptanceId = info.acceptanceId;
          } else if (info.deliveryStatus !== 2) {
            this.$modal.msgWarning(
              "该订单所在送货单【" +
                info.deliveryCode +
                "】尚未送达，送达后才能验收"
            );
            return;
          } else {
            // 无验收单：引导创建草稿
            query.create = 1;
          }
          this.$router.push({ path: "/order/acceptance", query });
        })
        .catch(() => {});
    },
    /** 批量完成订单状态 */
    handleOrderFinish(row) {
      // 获取选中的订单
      const orderList = row && row.status ? [row] : this.formSelectedOptions;
      // 校验是否全为验收状态的订单
      const valid = orderList.some((item) => item.status !== 3);
      if (valid) {
        this.$modal.msgError("请选择验收状态的订单");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
    },
    /** 批量月结订单（SETTLED，管理员） */
    handleOrderSettle() {
      const orderList = this.formSelectedOptions;
      const valid = orderList.some((item) => item.status !== 3);
      if (valid) {
        this.$modal.msgError("请选择验收状态的订单进行月结");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
      this.$modal
        .confirm("是否确认对选中的" + orderIds.length + "条订单执行月结？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 4,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /**
     * 客户行「确认草稿」（D-067）：整组确认该客户当日全部草稿订单。
     * 子行可能未加载，故先确保加载再按状态过滤；无草稿则提示不写库。
     */
    handleGroupConfirm(row) {
      this.loadCustomerChildren(row)
        .then(() => {
          const drafts = (row.orders || []).filter((o) => o.status === 0);
          if (!drafts.length) {
            this.$modal.msgError("该客户当日没有草稿状态的订单");
            return null;
          }
          return this.$modal
            .confirm(
              `是否确认【${row.customerName}】${row.deliveryDate} 的 ${drafts.length} 张草稿订单？`
            )
            .then(() =>
              updateOrderStatus({ orderIds: drafts.map((o) => o.id), status: 1 })
            )
            .then(() => {
              this.$modal.msgSuccess("已确认 " + drafts.length + " 张订单");
              this.handleQuery();
            });
        })
        .catch(() => {});
    },
    /**
     * 客户行「批量月结」（D-067）：整组结算该客户当日全部已验收订单。
     * 文案与单行结算保持一致口径（结算后只读，纠错走下月调整单）。
     */
    handleGroupSettle(row) {
      this.loadCustomerChildren(row)
        .then(() => {
          const accepted = (row.orders || []).filter((o) => o.status === 3);
          if (!accepted.length) {
            this.$modal.msgError("该客户当日没有已验收状态的订单");
            return null;
          }
          return this.$modal
            .confirm(
              `对【${row.customerName}】${row.deliveryDate} 的 ${accepted.length} 张已验收订单执行结算？<br/><br/>` +
                `结算后这些订单转为只读（不能再改单/撤回验收/重验收）；` +
                `如需纠错请在「月结调整」中挂下月调整单。`,
              "订单结算",
              { dangerouslyUseHTMLString: true, confirmButtonText: "确认结算" }
            )
            .then(() =>
              updateOrderStatus({ orderIds: accepted.map((o) => o.id), status: 4 })
            )
            .then(() => {
              this.$modal.msgSuccess("已结算 " + accepted.length + " 张订单");
              this.handleQuery();
            });
        })
        .catch(() => {});
    },
    /** 行内撤回订单（CONFIRMED→DRAFT，已生成送货单不可撤回由后端校验） */
    /**
     * 已验收订单「撤回」（撤销验收）：验收收敛订单视角（2026-09-15）——
     * 验收单不再有独立入口，撤回就放在订单行上；先定位该订单的验收单，再填原因撤销。
     * 撤销后订单回到「已确认」（可继续改单/重验收）；已月结则不提供该按钮。
     */
    handleRevokeAcceptance(row) {
      locateAcceptanceByOrder(row.id)
        .then((response) => {
          const info = response.data || {};
          if (!info.hasAcceptance || !info.acceptanceId) {
            this.$modal.msgWarning("未找到该订单的验收单，请刷新后重试");
            return;
          }
          return this.$modal
            .prompt(
              `撤销订单【${row.code}】的验收后，订单回到「<b>已确认</b>」状态，可继续修改或重新验收。` +
                `<br/>撤销前的验收数据会完整备份到审计日志（含明细快照）。`,
              "撤回验收",
              {
                dangerouslyUseHTMLString: true,
                confirmButtonText: "确认撤销",
                inputPlaceholder: "请填写撤销原因（必填，例：现场数量复核有误）",
                inputValidator: (v) => (v && String(v).trim() ? true : "撤销原因不能为空"),
              }
            )
            .then(({ value }) => revokeAcceptance(info.acceptanceId, String(value).trim()))
            .then(() => {
              this.$modal.msgSuccess("验收已撤销，订单回到已确认");
              this.handleQuery();
            });
        })
        .catch(() => {});
    },

    /**
     * 已验收订单「结算」（已验收 → 已结算）：月结后订单只读、实收与金额冻结，
     * 后续纠错走「下月调整单」（不直接改历史）。
     */
    handleSettle(row) {
      this.$modal
        .confirm(
          `对订单【${row.code}】执行结算（已验收 → <b>已结算</b>）？<br/><br/>` +
            `结算后该订单转为只读（不能再改单/撤回验收/重验收）；` +
            `如需纠错请在「月结调整」中挂下月调整单。`,
          "订单结算",
          { dangerouslyUseHTMLString: true, confirmButtonText: "确认结算" }
        )
        .then(() => updateOrderStatus({ orderIds: [row.id], status: 4 }))
        .then(() => {
          this.$modal.msgSuccess("已结算");
          this.handleQuery();
        })
        .catch(() => {});
    },

    /**
     * 撤回订单（CONFIRMED→DRAFT）：
     * 2026-09-14 业务定稿——已确认订单不再提供「修改」，需先撤回才能在草稿态编辑，
     * 故确认文案必须说清「撤回后能干什么 / 会连带影响什么 / 什么情况不能撤回」。
     */
    handleRecall(row) {
      this.$modal
        .confirm(
          `撤回订单【${row.code}】后，订单回到<b>草稿</b>状态，可点「修改」继续编辑（改完需重新确认）。<br/><br/>` +
            `撤回时会自动<b>作废/扣除</b>该订单尚未入库的采购单（共享采购单仅扣除本单部分）；<br/>` +
            `若该订单已进入<b>未作废的送货单</b>、或采购单<b>已入库</b>，则不提供撤回，请先作废对应单据。`,
          "撤回订单",
          { dangerouslyUseHTMLString: true, confirmButtonText: "确认撤回" }
        )
        .then(function () {
          const params = {
            orderIds: [row.id],
            status: 0,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.$modal.msgSuccess("已撤回为草稿，可点「修改」继续编辑");
          this.handleQuery();
        })
        .catch(() => {});
    },
    /** 双击行处理详情 */
    handleRowDblClick(row) {
      // 检查是否有多选
      if (this.ids.length > 1) {
        this.$modal.msgError("当前为多选模式，不可双击查看详情");
        return;
      }

      if (!row) return;
      // 跳转到订单详情
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { orderId: row.id },
      });
    },
    /** 月结调整摘要（蓝图 §2「月结调整追溯」：客户+结算月粒度，订单归月=最近已提交验收单验收月） */
    handleAdjustmentSummary(row) {
      getOrderAdjustmentSummary(row.id)
        .then((response) => {
          const data = response.data || {};
          this.adjustmentSummary = {
            open: true,
            orderCode: row.code,
            billMonth: data.billMonth || null,
            adjustments: data.adjustments || [],
            receivableTotal:
              data.receivableTotal != null ? data.receivableTotal : "0",
            costTotal: data.costTotal != null ? data.costTotal : "0",
          };
        })
        .catch(() => {});
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除销售订单编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSale(ids);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download(
        "order/sale/export",
        {
          ...this.queryParams,
        },
        `sale_${new Date().getTime()}.xlsx`
      );
    },
    /** 选择送货单位树回调（兼容清空时触发）；多选仅按“配送点/送货单位”过滤 */
    handleFormOptionsChanged() {
      // 取“仅勾选到的叶子（配送点）”节点：父级被勾选时会自动展开为全部子叶子，
      // 因此这里用 leafOnly 直接拿到最终应参与过滤的配送点，避免把父级误当“全量”展开。
      const leafNodes = this.$refs.customerDeptCascader
        ? this.$refs.customerDeptCascader.getCheckedNodes(true)
        : [];
      const deptIds = leafNodes
        .map((node) => String(node.value))
        .filter((id) => !!id);
      this.queryParams.customerDeptIds = deptIds;
      // 不再按客户过滤，避免与配送点交叉导致漏筛
      this.queryParams.customerIds = [];
      // 兼容：仅选中一个配送点时设置单个字段
      this.queryParams.customerDeptId =
        deptIds.length === 1 ? deptIds[0] : null;
      this.queryParams.customerId = null;
      this.handleQuery();
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCustomerDept().then((response) => {
        // init customerDeptOptions
        const treeList = this.handleTree(response.data);
        this.customerDeptOptions = this.transformData(treeList);
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
    // 行内调整（S14 退役）：入口已下线，配送后真实退货走「配送后变更（退货标记）」，补货走新增销售订单
  },
};
</script>

<style lang="scss" scoped>
/* 月结调整摘要对话框 */
.adjustment-summary-tip {
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}
.adjustment-summary-total {
  margin-top: 10px;
  text-align: right;
  font-size: 13px;
  color: #606266;
}
/* 批量操作条：勾选订单后浮现 */
.batch-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;

  .batch-action-info {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
    color: #409eff;

    .batch-action-count {
      b {
        color: #f56c6c;
        font-size: 15px;
      }
    }
  }

  .batch-action-btns {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .el-button + .el-button {
      margin-left: 0;
    }
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
/* 单号搜索自动切视角提示 */
.view-switch-tip {
  margin-bottom: 10px;
}
/* 客户视角：主行层级（客户名最重，金额次之，辅助信息最轻） */
.cust-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.num-strong {
  font-size: 15px;
  color: #303133;
}
.amount {
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.text-muted {
  color: #a8abb2;
}

/* 客户视角：展开区（子订单表）—— 底色 + 左侧引导线，建立“客户 > 订单”层级 */
.customer-children {
  padding: 10px 14px 12px;
  margin: 0 10px 6px;
  background: #f7f9fc;
  border-left: 3px solid #c6d9f1;
  border-radius: 0 4px 4px 0;

  .children-title {
    margin-bottom: 8px;
    font-size: 12px;
    line-height: 1;
    color: #909399;

    .children-title-main {
      font-weight: 600;
      color: #606266;
    }

    .children-title-sub {
      margin-left: 4px;
      color: #b1b3b8;
    }
  }

  /* 子表：去边框 + 淡色表头 + 紧凑行高，避免与外层表格“双重表格感” */
  :deep(.children-table) {
    th.el-table__cell {
      background: #eef2f7;
      color: #606266;
      font-size: 12px;
      font-weight: 600;
      padding: 6px 0;
    }
    td.el-table__cell {
      padding: 6px 0;
      border-bottom: 1px solid #f0f2f5;
    }
    /* 子表紧贴外层底色的白底，让订单行浮在客户行之上 */
    .el-table__body-wrapper,
    .el-table__header-wrapper {
      background: #fff;
    }
    &::before {
      display: none;
    }
  }

  .code-link {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    padding: 0;
  }
}

/* 客户视角：进度分布（彩色圆点 + 计数，替代一排 tag） */
.progress-line {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 2px 12px;
  line-height: 1.4;

  .progress-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }

  /* 待人工跟进的状态（草稿/已配送/已验收）文案加深，已确认/已结算退为次要 */
  .progress-item.is-todo .progress-label {
    color: #606266;
  }

  .progress-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .progress-count {
    color: #303133;
    font-variant-numeric: tabular-nums;
  }

  .filter-tag {
    height: 18px;
    padding: 0 5px;
  }
}

/* 级联选择器：点击节点任意位置即可勾选（父级=全选/取消全部子叶子，叶子=勾选/取消） */
:deep(.el-cascader-panel) {
  .el-cascader-node {
    cursor: pointer;
  }
  /* 节点文字 / 展开箭头不拦截点击事件，确保整行可触发勾选 */
  .el-cascader-node__label {
    pointer-events: none;
  }
  .el-cascader-node__postfix {
    pointer-events: none;
  }
}

/* 已选标签与内联搜索框保持单行：
   否则选中多个送货单位时「客户 / 点」标签 + 「+N」标签 + filterable 的搜索框会换行，
   把选择框撑成两行高，与同行的订单编号/来源/类型控件错位（看起来像文案错乱） */
:deep(.el-cascader__tags) {
  flex-wrap: nowrap;
  overflow: hidden;

  .el-tag {
    flex-shrink: 0;
  }

  .el-cascader__search-input {
    min-width: 40px;
  }
}
</style>
