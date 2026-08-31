<template>
  <div class="gp-list">
    <!-- 汇总条：先给结论（几个客户、几张单、多少钱），再看明细 -->
    <div class="gp-summary">
      <div class="gp-summary-item">
        配送日期<b>{{ preview.deliveryDate || "—" }}</b>
      </div>
      <div class="gp-summary-item">
        待生成客户<b>{{ nullish(preview.customerCount) }}</b>
      </div>
      <div class="gp-summary-item">
        待并入订单<b>{{ nullish(preview.sourceOrderCount) }}</b>
      </div>
      <div class="gp-summary-item gp-strong">
        预计生成送货单<b>{{ nullish(preview.expectedDeliveryCount) }}</b> 张
      </div>
      <div class="gp-summary-item">
        预计明细行<b>{{ nullish(preview.expectedDetailCount) }}</b>
      </div>
      <div class="gp-summary-item">
        金额合计<b>{{ formatAmount(preview.totalAmount) }}</b>
      </div>
      <slot name="actions" />
    </div>

    <!-- 影响面提示：作废重建 / 补充单 / 未确认草稿 -->
    <el-alert
      v-if="preview.rebuildCount"
      type="warning"
      :closable="false"
      show-icon
      class="gp-tip"
      :title="
        '其中 ' +
        preview.rebuildCount +
        ' 个客户当日已有【未打印】送货单：生成时将先作废原单（释放来源分配），再按当日全部已确认订单重建，原纸面单号作废失效。'
      "
    />
    <el-alert
      v-if="preview.supplementCount"
      type="warning"
      :closable="false"
      show-icon
      class="gp-tip"
      :title="
        '其中 ' +
        preview.supplementCount +
        ' 个客户当日送货单已打印/已送达：原单不动，遗漏订单另出【补充单】（列表带补充单标记）。'
      "
    />
    <el-alert
      v-if="preview.draftOrderCount"
      type="info"
      :closable="false"
      show-icon
      class="gp-tip"
      :title="draftTip"
    />

    <!-- 客户维度待生成清单：一行 = 一个客户 -->
    <el-table
      v-loading="loading"
      :data="preview.customers || []"
      size="small"
      border
      max-height="420"
      row-key="customerId"
    >
      <el-table-column type="expand">
        <template #default="scope">
          <div class="gp-expand">
            <div class="gp-expand-title">
              待并入销售订单（{{ scope.row.orders.length }} 张 · 合计 {{ formatAmount(scope.row.totalAmount) }}）
            </div>
            <el-table :data="scope.row.orders" size="small" border>
              <el-table-column label="订单编号" prop="code" min-width="150" />
              <el-table-column label="配送点" min-width="140">
                <template #default="s">
                  <span v-if="s.row.customerDeptName">{{ s.row.customerDeptName }}</span>
                  <span v-else class="gp-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="配送日期" width="110" align="center">
                <template #default="s">{{ dateText(s.row.deliveryDate) }}</template>
              </el-table-column>
              <el-table-column label="明细行" prop="itemCount" width="80" align="center" />
              <el-table-column label="金额" width="110" align="right">
                <template #default="s">{{ formatAmount(s.row.amount) }}</template>
              </el-table-column>
              <el-table-column label="进单情况" width="170" align="center">
                <template #default="s">
                  <el-tag v-if="s.row.rebuildCovered" size="small" type="warning" effect="plain">
                    原单已含·随重建并入
                  </el-tag>
                  <el-tag v-else size="small" type="success" effect="plain">未进单·本次并入</el-tag>
                </template>
              </el-table-column>
            </el-table>

            <template v-if="scope.row.existingOrders.length">
              <div class="gp-expand-title">
                当日既有有效送货单（{{ scope.row.existingOrders.length }} 张）· {{ scope.row.actionTip }}
              </div>
              <el-table :data="scope.row.existingOrders" size="small" border>
                <el-table-column label="送货单编号" min-width="170">
                  <template #default="s">
                    <span>{{ s.row.code }}</span>
                    <el-tag v-if="s.row.docKind === 1" size="small" type="warning" effect="plain" class="gp-tag">
                      补充单
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="配送点" min-width="140">
                  <template #default="s">
                    <span v-if="s.row.deliveryPointName">{{ s.row.deliveryPointName }}</span>
                    <span v-else class="gp-muted">跨点总单/未指定</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100" align="center" prop="statusDesc" />
                <el-table-column label="打印次数" width="90" align="center" prop="printCount" />
              </el-table>
            </template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="160">
        <template #default="scope">
          <span>{{ scope.row.customerName || "客户ID=" + scope.row.customerId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="组单策略" width="160" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.scopeType === 'CUSTOMER_DATE' ? 'primary' : 'info'" size="small" effect="plain">
            {{ scope.row.scopeDesc }}
          </el-tag>
          <div class="gp-sub">{{ scope.row.mergeSameItem ? "同商品合并成行" : "一订单行一行" }}</div>
          <el-tooltip v-if="scope.row.scopeSourceDesc" :content="scope.row.scopeSourceDesc" placement="top">
            <div class="gp-sub">
              来源：{{ scope.row.scopeSource === "BATCH_SNAPSHOT" ? "当日批次快照" : "客户配置" }}
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="处理方式" width="110" align="center">
        <template #default="scope">
          <el-tag :type="actionTagType(scope.row.action)" size="small" effect="dark">
            {{ scope.row.actionDesc }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="待并入订单" width="100" align="center">
        <template #default="scope">{{ (scope.row.orders || []).length }} 张</template>
      </el-table-column>
      <el-table-column label="预计生成" width="100" align="center">
        <template #default="scope">
          <b>{{ scope.row.expectedDeliveryCount }}</b> 张
        </template>
      </el-table-column>
      <el-table-column label="明细行" width="80" align="center" prop="expectedDetailCount" />
      <el-table-column label="金额" width="110" align="right">
        <template #default="scope">{{ formatAmount(scope.row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="scope">
          <span class="gp-muted">{{ scope.row.actionTip }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="gp-muted">{{ emptyText }}</span>
      </template>
    </el-table>
  </div>
</template>

<script setup>
/**
 * 送货单「待生成清单」客户维度视图（纯展示，无副作用）。
 * 送货单页生成确认抽屉与客户管理页送货单抽屉共用，保证两处口径一致。
 * 数据源：GET /order/delivery/group-preview（与统一生成服务同源判定，只读）。
 */
const props = defineProps({
  /** DeliveryGeneratePreviewVO */
  preview: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  /** 客户维度入口时的空态文案区分 */
  singleCustomer: { type: Boolean, default: false },
});

const draftTip = computed(() => {
  const preview = props.preview || {};
  const codes = preview.draftOrderCodes || [];
  const more = preview.draftOrderCount > codes.length ? " …" : "";
  return (
    "另有 " +
    preview.draftOrderCount +
    " 张草稿订单未确认，不会进入本次生成" +
    (codes.length ? "（" + codes.join("、") + more + "）" : "") +
    "；需要出单请到销售订单页勾选后生成（出单时会一并确认草稿）。"
  );
});

const emptyText = computed(() => {
  if (!(props.preview || {}).deliveryDate) return "请先选择配送日期";
  return props.singleCustomer
    ? "该客户当日订单均已生成送货单，无需再生成（幂等）"
    : "当日订单均已生成送货单，无需再生成（幂等）";
});

function nullish(val) {
  return val === null || val === undefined ? "—" : val;
}
function formatAmount(val) {
  const num = Number(val);
  return Number.isNaN(num) ? "0.00" : num.toFixed(2);
}
function dateText(val) {
  return val ? String(val).substring(0, 10) : "—";
}
function actionTagType(action) {
  if (action === "REBUILD") return "warning";
  if (action === "SUPPLEMENT") return "danger";
  return "success";
}
</script>

<style scoped>
.gp-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
}
.gp-summary-item b {
  color: var(--el-color-primary);
  margin-left: 4px;
}
.gp-strong b {
  font-size: 15px;
}
.gp-tip {
  margin-bottom: 6px;
}
.gp-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.gp-muted {
  color: var(--el-text-color-secondary);
}
.gp-tag {
  margin-left: 4px;
}
.gp-expand {
  padding: 8px 24px 12px;
}
.gp-expand-title {
  font-size: 12px;
  font-weight: bold;
  margin: 6px 0;
}
</style>
