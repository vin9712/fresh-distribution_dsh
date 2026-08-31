<template>
  <el-drawer
    v-model="visible"
    title="选订单 · 生成送货单"
    size="880px"
    append-to-body
    :destroy-on-close="false"
    @open="handleOpen"
  >
    <!-- 筛选条：客户必选（送货单按「客户 + 配送日期」出单，跨客户无意义） -->
    <el-form :model="query" size="small" inline label-width="72px" class="dg-filter">
      <el-form-item label="送货客户" required>
        <el-select
          v-model="query.customerId"
          filterable
          placeholder="必选（只能出同一客户的单）"
          style="width: 190px"
          @change="handleQuery"
        >
          <el-option
            v-for="item in customerOptions"
            :key="item.id"
            :label="item.alias ? item.alias : item.name"
            :value="item.id"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="配送日期">
        <el-date-picker
          v-model="query.deliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="留空=该客户全部日期"
          clearable
          style="width: 150px"
          @change="handleQuery"
        ></el-date-picker>
      </el-form-item>
      <el-form-item label="订单状态">
        <el-select v-model="query.scope" style="width: 150px" @change="handleQuery">
          <el-option label="未进单（默认）" value="unallocated"></el-option>
          <el-option label="仅草稿" value="draft"></el-option>
          <el-option label="仅已确认未进单" value="confirmed"></el-option>
          <el-option label="全部（含已进单）" value="all"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="本地筛选：订单号/送货单位"
          clearable
          style="width: 180px"
        ></el-input>
      </el-form-item>
    </el-form>

    <!-- 补齐语义如实告知（D-025：勾选只决定处理哪些「客户+日期」分组） -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="dg-tip"
      title="勾选只决定处理哪些「客户 + 配送日期」；同组内其他未进单的已确认订单会被一并并入（防漏单），取消勾选不能排除。"
    />

    <vxe-grid
      border
      auto-resize
      max-height="420"
      size="small"
      ref="orderGrid"
      :loading="loading"
      :data="filteredRows"
      :columns="columns"
      :checkbox-config="{ checkMethod: checkMethod, highlight: true }"
      :row-config="{ keyField: 'id', isHover: true }"
      @checkbox-change="handleSelectionChange"
      @checkbox-all="handleSelectionChange"
    >
      <template #status="{ row }">
        <dict-tag :options="t_sale_order_status" :value="row.status" />
      </template>
      <template #allocated="{ row }">
        <el-tag v-if="row.allocated" size="small" type="info" effect="plain">
          {{ row.deliveryOrderCode || "已进单" }}
        </el-tag>
        <span v-else class="dg-muted">—</span>
      </template>
      <template #empty>
        <span class="dg-muted">{{ query.customerId ? "该客户当前没有可出单的订单" : "请先选择送货客户" }}</span>
      </template>
    </vxe-grid>

    <!-- 生成结果：出单后在抽屉内如实展示，不靠 3 秒 toast -->
    <el-alert
      v-if="lastResult"
      type="success"
      :closable="false"
      show-icon
      class="dg-result"
    >
      <template #title>
        <span v-if="lastResult.created.length"
          >已生成送货单：{{ lastResult.created.join("、") }}</span
        >
        <span v-else>没有新单据（该客户当日订单已全部进单，幂等跳过）</span>
      </template>
      <div v-if="lastResult.confirmed.length" class="dg-result-line">
        本次一并确认草稿 {{ lastResult.confirmed.length }} 张：{{ lastResult.confirmed.join("、") }}
      </div>
      <div v-if="lastResult.packaged" class="dg-result-line">
        实际并入订单 {{ lastResult.packaged }} 张（含未被勾选、按规则补齐的遗漏单）
      </div>
      <div v-if="lastResult.skipped.length" class="dg-result-line">
        跳过 {{ lastResult.skipped.length }} 项：{{ lastResult.skipped.join("；") }}
      </div>
    </el-alert>

    <template #footer>
      <div class="dg-footer">
        <div class="dg-footer-summary">
          已选
          <b>{{ selection.length }}</b>
          张 · 合计
          <b>{{ formatAmount(totalAmount) }}</b>
          <span v-if="draftCount" class="dg-draft">
            含草稿 {{ draftCount }} 张（出单时一并确认，确认后订单不可再改）
          </span>
          <span v-else-if="selection.length" class="dg-muted">均为已确认订单</span>
        </div>
        <div>
          <el-button @click="visible = false">关 闭</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!selection.length"
            @click="handleSubmit"
            >确认草稿并生成送货单</el-button
          >
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { listSale } from "@/api/order/sale";
import { generateDeliveryByOrders } from "@/api/order/delivery";

const { proxy } = getCurrentInstance();
const { t_sale_order_status } = proxy.useDict("t_sale_order_status");

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 客户下拉（复用录单页已加载的 customerOptions，避免重复请求）
  customerOptions: { type: Array, default: () => [] },
  // 默认带出的客户（本次会话最后一次保存订单的客户）
  defaultCustomerId: { type: [Number, String], default: null },
  // 默认带出的配送日期；为空时按录单页同一规则（15点前=今天，15点后=明天）
  defaultDeliveryDate: { type: String, default: null },
});
const emit = defineEmits(["update:modelValue", "success"]);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const selection = ref([]);
const lastResult = ref(null);
const orderGrid = ref(null);

const query = reactive({
  customerId: null,
  deliveryDate: null,
  scope: "unallocated",
  keyword: "",
});

const columns = [
  { type: "checkbox", width: 46, align: "center" },
  { field: "code", title: "订单编号", width: 150 },
  { field: "deliveryName", title: "送货单位", minWidth: 150 },
  { field: "deliveryDate", title: "配送日期", width: 110 },
  { field: "amount", title: "金额", width: 96, align: "right", formatter: ({ cellValue }) => formatAmount(cellValue) },
  { field: "status", title: "状态", width: 96, align: "center", slots: { default: "status" } },
  { field: "deliveryOrderCode", title: "已进单", width: 150, slots: { default: "allocated" } },
];

/** 录单页同款配送日期默认值：15点前=今天，15点后=明天 */
function defaultDeliveryDateByRule() {
  const now = new Date();
  const target = new Date(now);
  if (now.getHours() >= 15) {
    target.setDate(target.getDate() + 1);
  }
  const y = target.getFullYear();
  const m = String(target.getMonth() + 1).padStart(2, "0");
  const d = String(target.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function formatAmount(val) {
  const num = Number(val);
  return Number.isNaN(num) ? "0.00" : num.toFixed(2);
}

/** 已进有效送货单的订单不可再选（all 视图下会显示出来，但勾选无意义） */
function checkMethod({ row }) {
  return !row.allocated;
}

/** 状态口径：草稿=0 / 已确认=1；未进单 = 草稿 或 已确认且未进单 */
function matchScope(row) {
  if (query.scope === "all") return true;
  if (query.scope === "draft") return row.status === 0;
  if (query.scope === "confirmed") return row.status === 1 && !row.allocated;
  return (row.status === 0 || row.status === 1) && !row.allocated;
}

const filteredRows = computed(() => {
  const kw = (query.keyword || "").trim().toLowerCase();
  return rows.value.filter((row) => {
    if (!matchScope(row)) return false;
    if (!kw) return true;
    return (
      String(row.code || "").toLowerCase().includes(kw) ||
      String(row.deliveryName || "").toLowerCase().includes(kw)
    );
  });
});

const totalAmount = computed(() =>
  selection.value.reduce((sum, row) => sum + (Number(row.amount) || 0), 0)
);

const draftCount = computed(
  () => selection.value.filter((row) => row.status === 0).length
);

function handleSelectionChange() {
  selection.value = orderGrid.value ? orderGrid.value.getCheckboxRecords() : [];
}

function clearSelection() {
  selection.value = [];
  if (orderGrid.value) orderGrid.value.clearCheckboxRow();
}

function handleOpen() {
  lastResult.value = null;
  clearSelection();
  if (!query.customerId) {
    query.customerId = props.defaultCustomerId
      ? Number(props.defaultCustomerId)
      : null;
  }
  if (!query.deliveryDate) {
    query.deliveryDate =
      props.defaultDeliveryDate || defaultDeliveryDateByRule();
  }
  handleQuery();
}

function handleQuery() {
  clearSelection();
  lastResult.value = null;
  if (!query.customerId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  const params = { customerId: query.customerId };
  if (query.deliveryDate) {
    params.deliveryDate = query.deliveryDate;
  }
  // 「仅草稿 / 仅已确认」可下推到后端；其余状态组合由前端过滤
  if (query.scope === "draft") {
    params.status = 0;
  } else if (query.scope === "confirmed") {
    params.status = 1;
  }
  listSale(params)
    .then((response) => {
      rows.value = response.data || [];
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  query.customerId = props.defaultCustomerId ? Number(props.defaultCustomerId) : null;
  query.deliveryDate = props.defaultDeliveryDate || defaultDeliveryDateByRule();
  query.scope = "unallocated";
  query.keyword = "";
  handleQuery();
}

function handleSubmit() {
  if (!query.customerId) {
    proxy.$modal.msgError("请先选择送货客户");
    return;
  }
  if (!selection.value.length) {
    proxy.$modal.msgError("请至少勾选一张订单");
    return;
  }
  // 兜底：同客户约束（列表已按客户筛，正常不会触发；防脏数据/并发换客户）
  const customers = new Set(selection.value.map((row) => row.customerId));
  if (customers.size > 1) {
    proxy.$modal.msgError("一次只能生成同一客户的送货单，请只勾选同一个客户的订单");
    return;
  }
  const orderIds = selection.value.map((row) => row.id);
  const drafts = draftCount.value;
  const tip = drafts
    ? `将确认 ${drafts} 张草稿并生成送货单。确认后订单不可再修改；同客户同配送日期下其他未进单的已确认订单会被一并并入。是否继续？`
    : `将按勾选订单生成/补齐送货单，同客户同配送日期下其他未进单的已确认订单会被一并并入。是否继续？`;

  proxy.$modal
    .confirm(tip)
    .then(() => {
      submitting.value = true;
      // 不传 deliveryDate：让后端按各单自身配送日期分组，支持一次勾选跨多天
      return generateDeliveryByOrders({ orderIds, confirmDrafts: true });
    })
    .then((response) => {
      const data = response.data || {};
      lastResult.value = {
        created: (data.createdOrders || []).map((o) => o.code).filter(Boolean),
        confirmed: data.confirmedOrderCodes || [],
        skipped: data.skippedReasons || [],
        packaged: (data.missedOrders || []).length,
      };
      clearSelection();
      handleQuery();
      emit("success", data);
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false;
    });
}

defineExpose({ resetQuery });
</script>

<style scoped>
.dg-filter {
  margin-bottom: 4px;
}
.dg-filter :deep(.el-form-item) {
  margin-bottom: 8px;
  margin-right: 12px;
}
.dg-tip {
  margin-bottom: 8px;
}
.dg-result {
  margin-top: 10px;
}
.dg-result-line {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
}
.dg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dg-footer-summary {
  font-size: 13px;
  line-height: 1.6;
}
.dg-footer-summary b {
  color: var(--el-color-primary);
  padding: 0 2px;
}
.dg-draft {
  color: var(--el-color-warning);
  margin-left: 6px;
}
.dg-muted {
  color: var(--el-text-color-secondary);
}
</style>
