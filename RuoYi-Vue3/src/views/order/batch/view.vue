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
        />
      </el-form-item>
      <el-form-item label="口径">
        <el-radio-group v-model="mode" size="small" @change="loadView">
          <el-radio-button value="matrix">矩阵总表（菜品×配送点）</el-radio-button>
          <el-radio-button value="pick">配货总表（不拆价）</el-radio-button>
          <el-radio-button value="point">点单（按配送点）</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="mode === 'point'" label="配送点">
        <el-select v-model="deptId" placeholder="请选择配送点" filterable clearable style="width: 190px">
          <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" :disabled="!customerId || !deliveryDate" @click="loadView"
          >查询</el-button
        >
        <el-button :icon="Printer" :disabled="!hasData" @click="handlePrint">{{
          mode === "pick" ? "打印本页" : "打印总单"
        }}</el-button>
      </el-form-item>
    </el-form>

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
        {{ mode === "matrix" ? "配送矩阵总表" : "配送总表" }}（{{ customerName }} / {{ deliveryDate }}）
        <span class="batch-sub">
          {{ mode === "matrix" ? "行=菜品（品名+规格+单价相同为一行）· 列=配送点 · 格=数量 · 不含价格" : "内部配货·采购参考，不含价格" }}
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
        <el-alert
          v-if="!deptId"
          type="info"
          :closable="false"
          show-icon
          class="matrix-alert"
          title="请选择配送点查看该点的点单明细（订单号 + 应送 + 变更标记）"
        />
        <el-table v-loading="loading" :data="pointRows" size="small" border>
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
            v-if="deptId"
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
          <el-button v-if="deptId" size="small" type="primary" plain :icon="Printer" @click="handlePointPrint">打印点单</el-button>
          <el-button v-if="deptId" size="small" type="warning" plain @click="handleAcceptance">生成验收单</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { batchView, deliveryMatrix, pointViewDelivery, markDeliveryPrinted, getDeliveryPrintState } from "@/api/order/delivery";
import { createAcceptanceByPoint } from "@/api/acceptance/acceptance";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { issuePrintTicket } from "@/api/print/ticket";
import { Search, Printer } from "@element-plus/icons-vue";

// 总单通用模板（s18：客户总单矩阵，司机对单 A4）——所有客户共用
const TOTAL_MATRIX_TEMPLATE_ID = "2599000000000000001";
// 点单模板（FLAT，全局默认）
const POINT_FLAT_TEMPLATE_ID = "2099000000000000001";

export default {
  name: "DeliveryBatchView",
  setup() {
    return { Search, Printer };
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
      // 点单口径（D-055）
      pointPrinted: false,
      depts: [],
      deptId: null,
      pointRows: [],
    };
  },
  computed: {
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
    if (this.$route.query.deliveryDate) {
      this.deliveryDate = String(this.$route.query.deliveryDate);
    }
    listCustomer().then((response) => {
      this.customers = response.data || [];
      if (this.customerId && this.deliveryDate) {
        this.loadView();
      }
    });
    // 路由带客户时预拉该客户配送点（点单口径）
    if (this.customerId) {
      this.loadDepts();
    }
  },
  methods: {
    /** 客户切换：刷新该客户下属配送点（点单口径只显示本客户） */
    onCustomerChange(customerId) {
      this.deptId = null;
      this.pointRows = [];
      if (customerId) {
        this.loadDepts();
      } else {
        this.depts = [];
      }
    },
    /** 拉选中客户的下属配送点（排除父级单位 parent_id=0） */
    loadDepts() {
      listCustomerDept({ customerId: this.customerId }).then((response) => {
        this.depts = (response.data || []).filter((d) => d.parentId && d.parentId !== 0);
      });
    },
    loadView() {
      if (!this.customerId || !this.deliveryDate) {
        this.$modal.msgWarning("请选择客户与配送日期");
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
      if (!this.deptId) {
        this.pointRows = [];
        this.pointPrinted = false;
        return Promise.resolve();
      }
      this.loadPrintState();
      return pointViewDelivery(this.customerId, this.deptId, this.deliveryDate).then((response) => {
        this.pointRows = response.data || [];
      });
    },
    /** 点单打印（FLAT 通用模板，D-055：按 客户+日期+点 实时取数） */
    handlePointPrint() {
      if (!this.customerId || !this.deptId || !this.deliveryDate) {
        this.$modal.msgWarning("请先选择客户、配送日期与配送点");
        return;
      }
      const bizKey = `point:${this.customerId}:${this.deptId}:${this.deliveryDate}`;
      issuePrintTicket({ bizKey, templateId: POINT_FLAT_TEMPLATE_ID }).then((res) => {
        const ticket = res.ticket;
        window.open(
          "/jmreport/view/" + POINT_FLAT_TEMPLATE_ID
            + "?token=" + ticket + "&ticket=" + ticket
            + "&deliveryOrderId="
            + "&customerId=" + this.customerId
            + "&customerDeptId=" + this.deptId
            + "&deliveryDate=" + this.deliveryDate,
          "_blank"
        );
        // 打印分界登记（D-055）：点单按 客户+日期+点 记，已打印=配送后
        markDeliveryPrinted({
          customerId: this.customerId,
          deliveryDate: this.deliveryDate,
          customerDeptId: this.deptId,
          templateId: POINT_FLAT_TEMPLATE_ID,
        })
          .then(() => this.loadPrintState())
          .catch(() => {});
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
    /** D-055 生成验收单（客户+日期+点，应送行=订单明细） */
    handleAcceptance() {
      this.$modal
        .confirm("将为该 客户+配送日期+配送点 生成验收单草稿（应送行=订单明细，含加单/换货/退货标记），确认？")
        .then(() => {
          createAcceptanceByPoint({
            customerId: this.customerId,
            customerDeptId: this.deptId,
            deliveryDate: this.deliveryDate,
          }).then((resp) => {
            this.$modal.msgSuccess("验收单已生成：" + (resp.data?.code || ""));
            this.$router.push({
              path: "/order/acceptance",
              query: { id: resp.data?.id },
            });
          });
        });
    },
    /** 格值：null=该点当日无此菜（空格）；历史单无台账打 — */
    cellText(row, col) {
      const qty = (row.cells || {})[col.deptId];
      if (qty !== undefined && qty !== null) {
        return qty;
      }
      return this.matrix.historyFallback ? "—" : "";
    },
    isQty(row, col) {
      const qty = (row.cells || {})[col.deptId];
      return qty !== undefined && qty !== null && Number(qty) !== 0;
    },
    /** 恒等式不一致行标红（D-047） */
    matrixRowClass({ row }) {
      return row.identityOk === false ? "row-identity-bad" : "";
    },
    /** 总单打印：矩阵口径走 JimuReport 总单模板（D-055 按 客户+日期 实时取数）；配货口径走浏览器打印本页 */
    handlePrint() {
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
      issuePrintTicket({ bizKey, templateId: TOTAL_MATRIX_TEMPLATE_ID }).then((res) => {
        const ticket = res.ticket;
        window.open(
          "/jmreport/view/" + TOTAL_MATRIX_TEMPLATE_ID
            + "?token=" + ticket + "&ticket=" + ticket
            + "&deliveryOrderId="
            + "&customerId=" + this.customerId
            + "&deliveryDate=" + this.deliveryDate,
          "_blank"
        );
        // 打印分界登记（D-055）：总单打印不拆点，customerDeptId 传空
        markDeliveryPrinted({
          customerId: this.customerId,
          deliveryDate: this.deliveryDate,
          templateId: TOTAL_MATRIX_TEMPLATE_ID,
        })
          .then(() => {
            this.pointPrinted = false;
            this.loadPrintState();
          })
          .catch(() => {});
      });
    },
  },
};
</script>
<style lang="scss" scoped>
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

/* 打印态：只保留总表正文 */
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
