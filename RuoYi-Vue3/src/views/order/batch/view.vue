<template>
  <div class="app-container batch-view-page">
    <!-- 查询条：客户 + 配送日期（矩阵总表 D-044 / 配货总表 D-027·28 两种口径共用一页） -->
    <el-form :inline="true" size="small" label-width="80px">
      <el-form-item label="客户">
        <el-select v-model="customerId" placeholder="请选择客户" filterable style="width: 220px" @change="onCustomerChange">
          <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="配送日期">
        <el-date-picker
          v-model="deliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择配送日期"
          clearable
          @change="loadView"
        />
      </el-form-item>
      <el-form-item label="口径">
        <el-radio-group v-model="mode" size="small" @change="loadView">
          <el-radio-button value="matrix">矩阵总表（菜品×配送点）</el-radio-button>
          <el-radio-button value="pick">配货总表（不拆价）</el-radio-button>
          <el-radio-button value="point">点单（分配送点 tab）</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" :disabled="!customerId || !deliveryDate" @click="loadView"
          >刷新</el-button
        >
        <el-button :icon="Printer" :disabled="!hasData" @click="handlePrint">{{
          mode === "pick" ? "打印本页" : "打印总单"
        }}</el-button>
        <!-- OA：验收改订单维度（一订单一验），此入口改为按客户批量验收（配送点 tab + 批量验收） -->
        <el-button
          type="success"
          plain
          :icon="CircleCheck"
          :disabled="!customerId || !deliveryDate"
          @click="handleAcceptance"
          >按客户验收</el-button
        >
        <el-button type="warning" plain :icon="Printer" @click="handlePrintManifest">当日打印（全部客户）</el-button>
      </el-form-item>
    </el-form>

    <!-- 当日打印清单抽屉（PT-2：全客户总单+点单批量队列出纸） -->
    <print-manifest-drawer v-model="manifestOpen" :delivery-date="deliveryDate" @printed="onManifestPrinted" />

    <!-- OA：按客户批量验收（配送点 tab 区分，逐单/批量验收；跳转订单页验收模式） -->
    <el-dialog align-center title="按客户验收" v-model="accOpen" width="760px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="批量验收 = 勾选订单按下单数量与金额整单确认（已保存过实收草稿的按草稿提交）；需逐行调整请点「去验收」进订单明细页"
        style="margin-bottom: 10px"
      />
      <el-tabs v-model="accActiveTab">
        <el-tab-pane v-for="g in accGroups" :key="g.key" :name="g.key">
          <template #label>
            {{ g.name }}<span v-if="g.pending > 0" class="acc-tab-pending">（未验收 {{ g.pending }}）</span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <div class="acc-batch-bar">
        <el-checkbox
          :model-value="accCurrentAllSelected"
          :indeterminate="accCurrentIndeterminate"
          :disabled="!accCurrentSelectable.length"
          @change="toggleCurrentTabSelection"
        >本点全选（{{ accCurrentSelectable.length }} 单可验收）</el-checkbox>
        <el-button
          type="primary"
          size="small"
          :disabled="!accCurrentSelected.length"
          :loading="accBatchRunning"
          @click="handleBatchAccept"
        >批量验收（{{ accCurrentSelected.length }}）</el-button>
      </div>
      <el-table
        v-loading="accLoading"
        :data="accCurrentRows"
        size="small"
        max-height="420"
        empty-text="该配送点在此日期没有订单"
      >
        <el-table-column width="46" align="center">
          <template #default="{ row }">
            <el-checkbox v-if="accRowSelectable(row)" v-model="row._selected" />
          </template>
        </el-table-column>
        <el-table-column label="订单编号" prop="code" min-width="150" show-overflow-tooltip />
        <el-table-column label="订单金额" width="100" align="right">
          <template #default="{ row }">{{ row.amount != null ? Number(row.amount).toFixed(2) : "" }}</template>
        </el-table-column>
        <el-table-column label="订单状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="primary" effect="plain">已确认</el-tag>
            <el-tag v-else-if="row.status === 3" type="success">已验收</el-tag>
            <el-tag v-else-if="row.status === 4" type="success" effect="dark">已结算</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="验收状态" width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.accSubmitted">验收单 {{ row.accCode || '—' }}</span>
            <el-tag v-else-if="row.accDraft" type="warning" effect="plain">草稿（已录实收）</el-tag>
            <el-tag v-else type="info" effect="plain">未验收</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goAcceptance(row)">{{
              row.accSubmitted ? "查看验收" : "去验收"
            }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-empty
      v-if="!loading && !hasData"
      :description="
        customerId && deliveryDate
          ? '该客户在此日期没有已确认订单（D-055：送货单=订单的视图，下单即有数据）'
          : '请选择客户与配送日期'
      "
    />

    <div v-show="hasData" ref="printArea" class="batch-print-area">
      <div class="batch-title">
        {{
          mode === "matrix" ? "配送矩阵总表" : mode === "point" ? "配送点单" : "配货总表"
        }}（{{ customerName }} / {{ deliveryDate }}）
        <span class="batch-sub">
          {{
            mode === "matrix"
              ? "行=菜品（品名+规格+单价相同为一行）· 列=配送点 · 格=数量 · 不含价格"
              : mode === "point"
                ? "客户+日期+配送点 的订单明细（含配送后变更标记），可打印点单；验收逐单进行（查询条「验收状态」）"
                : "内部配货·采购参考，不含价格"
          }}
        </span>
      </div>

      <!-- 提示条：恒等式不一致（D-047）/ 无快照实时推导（D-045）/ 历史单无台账（D-051）/ 停用点临时补列（D-053） -->
      <el-alert
        v-if="mode === 'matrix' && matrix.identityOk === false"
        type="error"
        :closable="false"
        show-icon
        class="matrix-alert"
        title="恒等式自检不通过：明细数量 ≠ 各配送点分配量合计，请先到送货单「来源」页核对后再出纸"
      >
        <div v-for="(m, i) in matrix.mismatches" :key="i" class="alert-line">
          {{ m.deliveryCode }} · {{ m.productName }}：明细 {{ m.num }}，各点合计 {{ m.cellSum }}
        </div>
      </el-alert>
      <el-alert
        v-if="mode === 'matrix' && adHocColumns.length"
        type="warning"
        :closable="false"
        show-icon
        class="matrix-alert"
        :title="'以下配送点已停用但当日仍有订单，已临时补列：' + adHocColumns.map((c) => c.name).join('、')"
      />
      <el-alert
        v-if="mode === 'matrix' && matrix.layoutDerived"
        type="info"
        :closable="false"
        show-icon
        class="matrix-alert"
        title="本批次无布局快照，列按当前启用配送点实时推导（重新生成送货单后即定格为快照）"
      />
      <el-alert
        v-if="mode === 'matrix' && matrix.historyFallback"
        type="info"
        :closable="false"
        show-icon
        class="matrix-alert"
        title="历史单无点级分配台账，各配送点列以 — 占位"
      />

      <!-- 矩阵总表：列=配送点快照（含当日无单空列），格=分配量，纸面不打价（D-046） -->
      <template v-if="mode === 'matrix'">
        <div v-if="colBlocks > 1" class="col-block-bar">
          <span>列块（每页 {{ matrix.colsPerPage }} 个点列，超出横向分页）：</span>
          <el-radio-group v-model="colBlock" size="small">
            <el-radio-button v-for="b in colBlocks" :key="b" :value="b">第 {{ b }}/{{ colBlocks }} 块</el-radio-button>
          </el-radio-group>
        </div>
        <el-table v-loading="loading" :data="pagedRows" size="small" border :row-class-name="matrixRowClass">
          <el-table-column label="序号" type="index" width="55" align="center" />
          <el-table-column label="菜品" align="center" min-width="170" :show-overflow-tooltip="true">
            <template #default="scope">
              <span>{{ scope.row.displayProductName }}</span>
              <el-tag v-if="scope.row.docKind === 1" size="small" type="warning" effect="plain" class="row-tag">补</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" prop="spec" width="110" :show-overflow-tooltip="true" />
          <el-table-column label="单位" align="center" prop="unit" width="70" />
          <el-table-column
            v-for="col in blockColumns"
            :key="col.deptId"
            align="center"
            min-width="96"
            :class-name="col.hasData ? '' : 'col-empty'"
          >
            <template #header>
              <div class="col-head">
                <span>{{ col.name }}</span>
                <el-tag v-if="col.adHoc" size="small" type="warning" effect="plain">停</el-tag>
                <div v-if="!col.hasData" class="col-head-sub">当日无单</div>
              </div>
            </template>
            <template #default="scope">
              <span :class="{ 'cell-zero': !isQty(scope.row, col) }">{{ cellText(scope.row, col) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" align="center" width="90">
            <template #default="scope">
              <span>{{ scope.row.remark || "" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合计" align="center" width="90">
            <template #default="scope">
              <b>{{ scope.row.totalQuantity }}</b>
            </template>
          </el-table-column>
        </el-table>
        <div class="batch-footer">
          共 {{ matrix.rows.length }} 行 · 合计 {{ matrix.totalQuantity }} · 配送点 {{ matrix.columns.length }} 个
          <span v-if="matrix.layoutVersion">（布局版本 v{{ matrix.layoutVersion }}）</span>
        </div>
      </template>

      <!-- 配货总表：标准品名 + 总量 + 各点小计折叠，不因价格拆行（D-027/28） -->
      <template v-else-if="mode === 'pick'">
        <el-table v-loading="loading" :data="pickRows" size="small" border>
          <el-table-column type="expand">
            <template #default="scope">
              <div class="dept-subtotal">
                <div v-for="(d, i) in scope.row.depts" :key="i" class="dept-line">
                  <span class="dept-name">{{ d.deptName || "—" }}</span>
                  <span class="dept-qty">{{ d.quantity }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="序号" type="index" width="55" align="center" />
          <el-table-column label="标准品名" align="center" prop="productName" min-width="160" :show-overflow-tooltip="true" />
          <el-table-column label="规格" align="center" prop="spec" width="110" :show-overflow-tooltip="true" />
          <el-table-column label="单位" align="center" prop="unit" width="80" />
          <el-table-column label="总量" align="center" prop="totalQuantity" width="100">
            <template #default="scope">
              <b>{{ scope.row.totalQuantity }}</b>
            </template>
          </el-table-column>
          <el-table-column label="配送点小计" align="center" min-width="220">
            <template #default="scope">
              <el-tag v-for="(d, i) in scope.row.depts" :key="i" size="small" type="info" class="dept-tag">
                {{ (d.deptName || "—") + " " + d.quantity }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="batch-footer">共 {{ pickRows.length }} 个品种 · 合计 {{ pickTotalCount }}</div>
      </template>

      <!-- 点单：客户+日期+配送点 的订单明细行（D-055：含加单/换货/退货标记 + 打印/验收） -->
      <template v-else>
        <el-empty
          v-if="!pointGroups.length && !loading"
          description="该客户在此日期没有已确认订单明细（配送点 tab 按当天实际有单的点展示）"
        />
        <el-tabs v-else v-model="pointTab" class="point-tabs" v-loading="loading" @tab-change="onPointTabChange">
          <el-tab-pane v-for="g in pointGroups" :key="g.deptId" :name="String(g.deptId)">
            <template #label>
              <span class="point-tab-label">
                {{ g.deptName }}
                <el-tag size="small" effect="plain" round type="info">应送 {{ g.totalNum }}</el-tag>
              </span>
            </template>
            <template v-if="String(deptId) === String(g.deptId)">
              <el-table :data="pointRows" size="small" border>
                <el-table-column label="订单号" align="center" prop="orderCode" min-width="130" :show-overflow-tooltip="true" />
                <el-table-column label="商品" align="center" prop="productName" min-width="140" :show-overflow-tooltip="true" />
                <el-table-column label="规格" align="center" prop="productSpec" width="110" :show-overflow-tooltip="true" />
                <el-table-column label="单位" align="center" prop="productUnit" width="70" />
                <el-table-column label="应送" align="center" prop="num" width="90" />
                <el-table-column label="实收" align="center" width="90">
                  <template #default="scope">
                    <span>{{ scope.row.actualNum ?? "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="标记" align="center" width="110">
                  <template #default="scope">
                    <el-tag v-if="scope.row.changeType == 1" size="small" type="warning" effect="plain">加单</el-tag>
                    <el-tag v-else-if="scope.row.changeType == 2" size="small" type="success" effect="plain">换货</el-tag>
                    <el-tag v-else-if="scope.row.changeType == 3" size="small" type="danger" effect="plain">退货</el-tag>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="说明" align="center" prop="changeRemark" min-width="130" :show-overflow-tooltip="true" />
              </el-table>
              <div class="batch-footer">
                共 {{ pointRows.length }} 行（含标记行）· 应送合计 {{ pointTotalNum }}· 实收合计 {{ pointTotalActual }}
                <el-tag
                  :type="pointPrinted ? 'danger' : 'info'"
                  size="small"
                  effect="plain"
                  style="margin-right: 8px"
                  >{{
                    pointPrinted
                      ? "已打印（配送后，改动请走带标记的配送后变更）"
                      : "未打印（配送前，直接改订单即可）"
                  }}</el-tag
                >
                <el-button size="small" type="primary" plain :icon="Printer" @click="handlePointPrint">打印点单</el-button>
              </div>
            </template>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </div>
</template>

<script>
import { batchView, deliveryMatrix, pointViewAllDelivery, getDeliveryPrintState, getPrintManifest } from "@/api/order/delivery";
import { listSale } from "@/api/order/sale";
import { locateAcceptanceByOrder, quickAcceptOrder } from "@/api/acceptance/acceptance";
import { listCustomer } from "@/api/partner/customer";
import { issuePrintTicket } from "@/api/print/ticket";
import { resolvePrintTemplate } from "@/api/print/template";
import PrintManifestDrawer from "./printManifestDrawer.vue";
import { defaultDeliveryDate } from "@/utils/index";
import { Search, Printer, CircleCheck } from "@element-plus/icons-vue";

export default {
  name: "DeliveryBatchView",
  components: { PrintManifestDrawer },
  setup() {
    return { Search, Printer, CircleCheck };
  },
  data() {
    return {
      loading: false,
      customers: [],
      customerId: null,
      deliveryDate: null,
      // matrix=矩阵总表（菜品×配送点，D-044）；pick=配货总表（不拆价，D-027/28）；point=点单（D-055）
      mode: "matrix",
      colBlock: 1,
      matrix: { columns: [], rows: [], mismatches: [] },
      pickRows: [],
      // 点单口径（D-055 收尾：按当天实际有单的配送点分 tab）
      pointPrinted: false,
      pointGroups: [],
      pointTab: "",
      deptId: null,
      pointRows: [],
      // 当日打印清单抽屉（PT-2 批量打印）
      manifestOpen: false,
      // OA：按客户批量验收（配送点 tab + 勾选批量验收）
      accOpen: false,
      accLoading: false,
      accOrders: [],
      accActiveTab: "_all",
      accBatchRunning: false,
    };
  },
  computed: {
    /* ===== OA：按客户批量验收（配送点 tab 分组） ===== */
    /** 按「全部 + 各配送点」分组 tab */
    accGroups() {
      const map = {};
      (this.accOrders || []).forEach((o) => {
        const name = o.customerDeptName || "未分点";
        if (!map[name]) map[name] = { key: "dp_" + name, name, rows: [] };
        map[name].rows.push(o);
      });
      const groups = Object.values(map);
      groups.forEach((g) => {
        g.pending = g.rows.filter((r) => !r.accSubmitted).length;
      });
      return [{ key: "_all", name: "全部", rows: this.accOrders || [], pending: (this.accOrders || []).filter((r) => !r.accSubmitted).length }, ...groups];
    },
    accCurrentGroup() {
      return this.accGroups.find((g) => g.key === this.accActiveTab) || this.accGroups[0];
    },
    accCurrentRows() {
      return (this.accCurrentGroup && this.accCurrentGroup.rows) || [];
    },
    accRowSelectable() {
      return (row) => row.status === 1 && !row.accSubmitted;
    },
    accCurrentSelectable() {
      return this.accCurrentRows.filter((r) => this.accRowSelectable(r));
    },
    accCurrentSelected() {
      return this.accCurrentSelectable.filter((r) => r._selected);
    },
    accCurrentAllSelected() {
      return this.accCurrentSelectable.length > 0 && this.accCurrentSelected.length === this.accCurrentSelectable.length;
    },
    accCurrentIndeterminate() {
      const n = this.accCurrentSelected.length;
      return n > 0 && n < this.accCurrentSelectable.length;
    },
    customerName() {
      const c = this.customers.find((x) => x.id === this.customerId);
      return c ? c.name : "";
    },
    hasData() {
      if (this.mode === "matrix") {
        return (this.matrix.rows || []).length > 0;
      }
      if (this.mode === "point") {
        return this.pointRows.length > 0;
      }
      return this.pickRows.length > 0;
    },
    colBlocks() {
      return Math.max(1, Number(this.matrix.colBlocks || 1));
    },
    blockColumns() {
      const size = Number(this.matrix.colsPerPage || 0) || (this.matrix.columns || []).length;
      const start = (this.colBlock - 1) * size;
      return (this.matrix.columns || []).slice(start, start + size);
    },
    pagedRows() {
      return this.matrix.rows || [];
    },
    adHocColumns() {
      return (this.matrix.columns || []).filter((c) => c.adHoc);
    },
    pickTotalCount() {
      return this.pickRows.reduce((s, r) => s + Number(r.totalQuantity || 0), 0);
    },
    pointTotalNum() {
      return this.pointRows.reduce((s, r) => s + Number(r.num || 0), 0);
    },
    pointTotalActual() {
      return this.pointRows.reduce((s, r) => s + Number(r.actualNum || 0), 0);
    },
  },
  created() {
    // 支持送货单据页/客户抽屉带参跳转
    if (this.$route.query.customerId) {
      this.customerId = Number(this.$route.query.customerId);
    }
    // 配送日期默认：15:00 前=当天，之后=次日（与订单明细口径一致）；带参跳转优先用参数
    this.deliveryDate = this.$route.query.deliveryDate
      ? String(this.$route.query.deliveryDate)
      : defaultDeliveryDate();
    // 打印回执（PT-3）：报表页真实打印后写 localStorage['print_receipt']，本页监听刷新分界标识
    window.addEventListener("storage", this.onPrintReceipt);
    listCustomer().then((response) => {
      this.customers = response.data || [];
      if (this.customerId && this.deliveryDate) {
        this.loadView();
      }
    });
  },
  beforeUnmount() {
    window.removeEventListener("storage", this.onPrintReceipt);
  },
  methods: {
    /** 报表页回执（PT-3）：本客户日的打印分界标识刷新 */
    onPrintReceipt(e) {
      if (!e || e.key !== "print_receipt" || !e.newValue) return;
      try {
        const payload = JSON.parse(e.newValue);
        if (String(payload.customerId || "") === String(this.customerId || "") || !payload.customerId) {
          this.loadPrintState();
        }
      } catch (err) {
        /* 忽略非法回执 */
      }
    },
    /** 客户切换：清空点单 tab，两个条件齐了自动加载 */
    onCustomerChange(customerId) {
      this.deptId = null;
      this.pointGroups = [];
      this.pointTab = "";
      this.pointRows = [];
      this.loadView();
    },
    loadView() {
      if (!this.customerId || !this.deliveryDate) {
        // 自动触发下条件不全属正常过程：静默清空旧数据，展示空态引导
        this.matrix = { columns: [], rows: [], mismatches: [] };
        this.pickRows = [];
        this.pointGroups = [];
        this.pointTab = "";
        this.pointRows = [];
        this.deptId = null;
        return;
      }
      this.loading = true;
      this.colBlock = 1;
      const done = () => (this.loading = false);
      if (this.mode === "matrix") {
        deliveryMatrix(this.customerId, this.deliveryDate)
          .then((response) => {
            this.matrix = response.data || { columns: [], rows: [], mismatches: [] };
            if (!(this.matrix.rows || []).length) {
              this.$modal.msgWarning("该客户在此日期没有已确认订单明细");
            }
          })
          .finally(done);
      } else if (this.mode === "point") {
        this.loadPointView().finally(done);
      } else {
        batchView(this.customerId, this.deliveryDate)
          .then((response) => {
            this.pickRows = response.data || [];
            if (!this.pickRows.length) {
              this.$modal.msgWarning("该客户在此日期没有已确认订单明细");
            }
          })
          .finally(done);
      }
    },
    /** D-055 点单口径：按 客户+日期+点 拉订单明细行（含标记） */
    loadPointView() {
      this.pointGroups = [];
      this.pointTab = "";
      this.deptId = null;
      this.pointRows = [];
      this.pointPrinted = false;
      return pointViewAllDelivery(this.customerId, this.deliveryDate).then((response) => {
        this.pointGroups = response.data || [];
        if (this.pointGroups.length) {
          // 默认选中第一个有单的配送点
          this.onPointTabChange(String(this.pointGroups[0].deptId));
        }
      });
    },
    /** tab 切换：同步当前配送点并加载该点打印分界状态（含程序化选中首个 tab） */
    onPointTabChange(name) {
      const group = this.pointGroups.find((g) => String(g.deptId) === String(name));
      if (!group) {
        this.deptId = null;
        this.pointRows = [];
        return;
      }
      this.pointTab = String(group.deptId);
      this.deptId = group.deptId;
      this.pointRows = group.rows || [];
      this.loadPrintState();
    },
    /** 点单打印（PT-1：后端三级绑定解析模板，替代硬编码；PT-3：开窗不登记，真实打印后由报表页回执登记） */
    async handlePointPrint() {
      if (!this.customerId || !this.deptId || !this.deliveryDate) {
        this.$modal.msgWarning("请先选择客户、配送日期与配送点");
        return;
      }
      const bizKey = `point:${this.customerId}:${this.deptId}:${this.deliveryDate}`;
      const resolved = await resolvePrintTemplate(bizKey).then((r) => r.data).catch(() => null);
      if (!resolved || !resolved.reportViewId) {
        this.$modal.msgWarning((resolved && resolved.warning) || "未找到可用的点单打印模板");
        return;
      }
      issuePrintTicket({ bizKey, templateId: resolved.templateId }).then((res) => {
        const ticket = res.ticket;
        window.open(
          "/jmreport/view/" + resolved.reportViewId
            + "?token=" + ticket + "&ticket=" + ticket
            + "&deliveryOrderId="
            + "&customerId=" + this.customerId
            + "&customerDeptId=" + this.deptId
            + "&deliveryDate=" + this.deliveryDate,
          "_blank"
        );
      });
    },
    /** 查该 客户+日期+点 是否已打印（D-055 打印分界） */
    loadPrintState() {
      if (!this.customerId || !this.deliveryDate || !this.deptId) {
        this.pointPrinted = false;
        return Promise.resolve();
      }
      return getDeliveryPrintState(this.customerId, this.deliveryDate, this.deptId)
        .then((res) => {
          this.pointPrinted = !!(res && res.printed);
        })
        .catch(() => {
          this.pointPrinted = false;
        });
    },
    /** OA：按客户批量验收——展示该客户日逐单状态（配送点 tab），支持勾选批量验收与跳转订单页验收模式（Q2 确认：不再按客户日建单） */
    handleAcceptance() {
      this.accOpen = true;
      this.loadAccOrders();
    },
    loadAccOrders() {
      this.accLoading = true;
      listSale({ customerId: this.customerId, deliveryDate: this.deliveryDate })
        .then(async (resp) => {
          const orders = resp.data || [];
          // 逐单定位验收单（客户日订单数少，N 次轻量查询可接受）
          for (const o of orders) {
            o.accCode = null;
            o.accDraft = false;
            o.accSubmitted = false;
            o._selected = false;
            try {
              const loc = await locateAcceptanceByOrder(o.id);
              const info = loc.data || {};
              if (info.hasAcceptance && info.acceptanceId) {
                o.accCode = info.acceptanceCode;
                if (info.acceptanceStatus === 0) {
                  o.accDraft = true;
                } else {
                  o.accSubmitted = true;
                }
              }
            } catch (e) {
              /* 定位失败按未验收展示 */
            }
          }
          this.accOrders = orders;
          // 默认定位到第一个还有未验收订单的配送点 tab
          const firstPending = this.accGroups.find((g) => g.pending > 0);
          this.accActiveTab = firstPending ? firstPending.key : "_all";
        })
        .catch(() => {
          this.accOrders = [];
        })
        .finally(() => {
          this.accLoading = false;
        });
    },
    toggleCurrentTabSelection(v) {
      this.accCurrentSelectable.forEach((r) => (r._selected = !!v));
    },
    /** 批量验收：逐单调 quick-accept（后端幂等，失败中断并提示已完成数） */
    handleBatchAccept() {
      const targets = this.accCurrentSelected.slice();
      if (!targets.length) return;
      this.$modal
        .confirm(
          `将按下单数量与金额整单确认 ${targets.length} 张订单（已保存过实收草稿的按草稿提交），确认？`
        )
        .then(async () => {
          this.accBatchRunning = true;
          let ok = 0;
          for (const o of targets) {
            try {
              await quickAcceptOrder({ orderId: o.id });
              ok += 1;
              o.accSubmitted = true;
              o.accDraft = false;
              o._selected = false;
            } catch (e) {
              this.$modal.msgError(
                `订单【${o.code}】验收失败：` + ((e && e.message) || "未知错误") +
                  (ok > 0 ? `（已成功 ${ok} 张，后续已中断）` : "")
              );
              break;
            }
          }
          if (ok === targets.length) {
            this.$modal.msgSuccess(`已批量验收 ${ok} 张订单`);
          }
          this.accBatchRunning = false;
        })
        .catch(() => {});
    },
    /** 跳转订单页验收模式（未验收/草稿=可录入；已验收=只读查看） */
    goAcceptance(row) {
      this.accOpen = false;
      // 子路由路径为 /index/，push 父路径会白屏
      this.$router.push({
        path: "/order/sale-detail/index",
        query: { mode: "acceptance", orderId: row.id },
      });
    },
    /** 矩阵格键（s35）：deptId（未启用班次）或 deptId#班次（启用班次），与后端 ShiftCodes.cellKey 同口径 */
    cellKey(col) {
      return col.shiftCode ? `${col.deptId}#${col.shiftCode}` : String(col.deptId);
    },
    /** 格值：null=该点当日无此菜（空格）；历史单无台账打 — */
    cellText(row, col) {
      const qty = (row.cells || {})[this.cellKey(col)];
      if (qty !== undefined && qty !== null) {
        return qty;
      }
      return this.matrix.historyFallback ? "—" : "";
    },
    isQty(row, col) {
      const qty = (row.cells || {})[this.cellKey(col)];
      return qty !== undefined && qty !== null && Number(qty) !== 0;
    },
    /** 恒等式不一致行标红（D-047） */
    matrixRowClass({ row }) {
      return row.identityOk === false ? "row-identity-bad" : "";
    },
    /** 总单打印（PT-1：后端解析模板；PT-3：开窗不登记，真实打印后由报表页回执登记）；配货口径走浏览器打印本页 */
    async handlePrint() {
      if (this.mode === "pick") {
        window.print();
        return;
      }
      if (!this.customerId || !this.deliveryDate) {
        this.$modal.msgWarning("请先选择客户与配送日期");
        return;
      }
      // D-055 视图化：总单主体 = 客户+配送日期（无送货单ID），票据按 bizKey 绑定
      const bizKey = `matrix:${this.customerId}:${this.deliveryDate}`;
      const resolved = await resolvePrintTemplate(bizKey).then((r) => r.data).catch(() => null);
      if (!resolved || !resolved.reportViewId) {
        this.$modal.msgWarning((resolved && resolved.warning) || "未找到可用的总单打印模板");
        return;
      }
      issuePrintTicket({ bizKey, templateId: resolved.templateId }).then((res) => {
        const ticket = res.ticket;
        window.open(
          "/jmreport/view/" + resolved.reportViewId
            + "?token=" + ticket + "&ticket=" + ticket
            + "&deliveryOrderId="
            + "&customerId=" + this.customerId
            + "&deliveryDate=" + this.deliveryDate,
          "_blank"
        );
      });
    },
    /** 当日打印清单抽屉（PT-2 批量打印：全客户总单+点单一次队列出纸） */
    handlePrintManifest() {
      if (!this.deliveryDate) {
        this.$modal.msgWarning("请先选择配送日期");
        return;
      }
      this.manifestOpen = true;
    },
    /** 清单抽屉打印完成回调：刷新点单分界标识 */
    onManifestPrinted() {
      this.loadPrintState();
    },
  },
};
</script>
<style lang="scss" scoped>
/* OA 按客户批量验收 */
.acc-batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.acc-tab-pending {
  color: #e6a23c;
  font-size: 12px;
}
.batch-view-page {
  .batch-print-area {
    max-width: 1100px;
  }
  .batch-title {
    font-size: 17px;
    font-weight: 700;
    margin-bottom: 12px;
    .batch-sub {
      font-size: 12px;
      color: #909399;
      font-weight: 400;
      margin-left: 8px;
    }
  }
  .matrix-alert {
    margin-bottom: 8px;
    .alert-line {
      font-size: 12px;
      line-height: 1.7;
    }
  }
  .col-block-bar {
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;
  }
  .col-head {
    line-height: 1.4;
    .col-head-sub {
      font-size: 11px;
      font-weight: 400;
      color: #c0c4cc;
    }
  }
  .cell-zero {
    color: #c0c4cc;
  }
  .row-tag {
    margin-left: 4px;
  }
  .batch-footer {
    margin-top: 8px;
    text-align: right;
    color: #606266;
    font-size: 13px;
  }
  .dept-tag {
    margin: 0 4px 4px 0;
  }
  .dept-subtotal {
    padding: 6px 16px;
    .dept-line {
      display: flex;
      justify-content: space-between;
      max-width: 320px;
      line-height: 1.8;
      .dept-name {
        color: #606266;
      }
      .dept-qty {
        font-weight: 600;
      }
    }
  }
}

:deep(.row-identity-bad) td.el-table__cell {
  background-color: #fef0f0 !important;
}
:deep(.col-empty) {
  background-color: #fafafa;
}

/* 点单口径：分点 tab（D-055 收尾，按当天实际有单的配送点） */
:deep(.point-tabs) {
  .el-tabs__header {
    margin-bottom: 10px;
  }
  .point-tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;

    .el-tag {
      font-weight: normal;
    }
  }
}
</style>

<!-- 打印态：只保留总表正文（必须非 scoped，否则 body * 会被编译为 body[data-v-xxx] * 而永不匹配） -->
<style>
@media print {
  body * {
    visibility: hidden;
  }
  .batch-print-area,
  .batch-print-area * {
    visibility: visible;
  }
  .batch-print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
  }
}
</style>
