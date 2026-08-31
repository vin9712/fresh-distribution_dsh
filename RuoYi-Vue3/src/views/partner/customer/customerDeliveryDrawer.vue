<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="1040px"
    append-to-body
    :destroy-on-close="false"
    @open="handleOpen"
  >
    <!-- 客户维度：送货单口径（D-038/D-040/D-041） -->
    <el-descriptions :column="2" border size="small" class="cd-scope">
      <el-descriptions-item label="客户">
        {{ customerLabel }}
      </el-descriptions-item>
      <el-descriptions-item label="配送日期">
        <el-date-picker
          v-model="deliveryDate"
          type="date"
          size="small"
          value-format="YYYY-MM-DD"
          placeholder="选择配送日期"
          :clearable="false"
          style="width: 150px"
          @change="loadAll"
        />
      </el-descriptions-item>
      <el-descriptions-item label="客户配置">
        <el-tag :type="scopeTagType(customer.docScopeType)" size="small" effect="plain">
          {{ scopeText(customer.docScopeType) }}
        </el-tag>
        <span class="cd-muted">
          · 相同商品{{ customer.docMergeSameItem === false ? "不合并（一订单行一行）" : "合并成行（不同价必拆行）" }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="当日生效策略">
        <template v-if="effective">
          <el-tag :type="scopeTagType(effective.scopeType)" size="small" effect="plain">
            {{ effective.scopeDesc }}
          </el-tag>
          <span class="cd-muted">
            · 来源：{{ effective.scopeSource === "BATCH_SNAPSHOT" ? "当日批次快照（已锁定）" : "客户配置（尚未建批次）" }}
          </span>
          <div class="cd-hint">{{ effective.scopeSourceDesc }}</div>
        </template>
        <span v-else class="cd-muted">
          {{ loaded ? "当日无待生成订单，未涉及策略取值" : "加载中…" }}
        </span>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 待生成清单（客户维度，与送货单页同一视图与口径） -->
    <div class="cd-section-title">待生成清单</div>
    <generate-preview-list :preview="preview" :loading="loading" single-customer />

    <!-- 当日送货单（既有有效单 + 作废单一并列出，便于客户视角核对） -->
    <div class="cd-section-title">
      当日送货单
      <el-button link type="primary" size="small" @click="gotoDeliveryPage">到送货单页查看</el-button>
    </div>
    <el-table v-loading="listLoading" :data="deliveryList" size="small" border max-height="260">
      <el-table-column label="送货单编号" min-width="190">
        <template #default="scope">
          <span>{{ scope.row.code }}</span>
          <el-tag v-if="scope.row.docKind === 1" size="small" type="warning" effect="plain" class="cd-tag">补充单</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配送点" min-width="140">
        <template #default="scope">
          <span v-if="scope.row.customerDeptName">{{ scope.row.customerDeptName }}</span>
          <el-tag v-else-if="scope.row.scopeType === 'CUSTOMER_DATE'" size="small" type="info" effect="plain">跨点总单</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <dict-tag :options="t_delivery_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="打印次数" width="90" align="center" prop="printCount" />
      <el-table-column label="备注" min-width="140" prop="remark" :show-overflow-tooltip="true" />
      <template #empty>
        <span class="cd-muted">该客户当日尚无送货单（未生成或已全部作废）</span>
      </template>
    </el-table>

    <template #footer>
      <div class="cd-footer">
        <div class="cd-footer-hint">
          生成/补单按「客户 + 配送日期」幂等补齐全部遗漏订单（不能只出勾选的部分）；未打印原单会被作废重建，已打印则另出补充单。
        </div>
        <div>
          <el-button @click="visible = false">关 闭</el-button>
          <el-button
            v-hasPermi="['order:delivery:add']"
            type="primary"
            :loading="submitting"
            :disabled="!deliveryDate || !expectedCount"
            @click="handleGenerate"
          >
            生成 / 补单（预计 {{ expectedCount || 0 }} 张）
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import GeneratePreviewList from "@/views/order/delivery/generatePreviewList.vue";
import { groupPreview, listDelivery, generateDeliveryForCustomer } from "@/api/order/delivery";

const { proxy } = getCurrentInstance();
const { t_delivery_order_status } = proxy.useDict("t_delivery_order_status");

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 客户行（id/name/alias/docScopeType/docMergeSameItem） */
  customer: { type: Object, default: () => ({}) },
});
const emit = defineEmits(["update:modelValue"]);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const loading = ref(false);
const listLoading = ref(false);
const submitting = ref(false);
const loaded = ref(false);
const preview = ref({});
const deliveryList = ref([]);
const deliveryDate = ref(today());

const customerLabel = computed(() => {
  const c = props.customer || {};
  return c.alias ? c.alias : c.name || "—";
});
const drawerTitle = computed(() => "客户送货单 · " + customerLabel.value);
const effective = computed(() => (preview.value.customers || [])[0] || null);
const expectedCount = computed(() => preview.value.expectedDeliveryCount || 0);

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function scopeText(val) {
  return val === "CUSTOMER_DATE" ? "跨点总单（按客户日合并）" : "每配送点一张单";
}
function scopeTagType(val) {
  return val === "CUSTOMER_DATE" ? "primary" : "info";
}

function handleOpen() {
  if (!deliveryDate.value) {
    deliveryDate.value = today();
  }
  loadAll();
}

/** 预览清单 + 当日既有单一并刷新（两者共同构成客户视角的送货单现状） */
function loadAll() {
  loadPreview();
  loadDeliveryList();
}

function loadPreview() {
  if (!props.customer.id || !deliveryDate.value) {
    preview.value = {};
    return;
  }
  loading.value = true;
  groupPreview({ customerId: props.customer.id, deliveryDate: deliveryDate.value })
    .then((response) => {
      preview.value = response.data || {};
    })
    .catch(() => {
      preview.value = {};
    })
    .finally(() => {
      loading.value = false;
      loaded.value = true;
    });
}

function loadDeliveryList() {
  if (!props.customer.id || !deliveryDate.value) {
    deliveryList.value = [];
    return;
  }
  listLoading.value = true;
  listDelivery({ customerId: props.customer.id, deliveryDate: deliveryDate.value })
    .then((response) => {
      deliveryList.value = response.data || [];
    })
    .catch(() => {
      deliveryList.value = [];
    })
    .finally(() => {
      listLoading.value = false;
    });
}

/** 确认生成：清单已在上方展示，这里只二次确认影响面（作废重建/补充单）后调统一生成服务 */
function handleGenerate() {
  const cust = (preview.value.customers || [])[0];
  const impact = cust
    ? cust.actionDesc + "：" + cust.actionTip
    : "当日无待生成订单，生成将幂等跳过";
  proxy
    .$modal.confirm(
      `将为「${customerLabel.value}」${deliveryDate.value} 生成送货单。\n` +
        `预计生成 ${expectedCount.value} 张 · 待并入订单 ${(cust && cust.orders ? cust.orders.length : 0)} 张 · 金额 ${
          cust && cust.totalAmount != null ? Number(cust.totalAmount).toFixed(2) : "0.00"
        }。\n处理方式：${impact}\n是否确认？`
    )
    .then(() => {
      submitting.value = true;
      return generateDeliveryForCustomer(props.customer.id, deliveryDate.value);
    })
    .then((response) => {
      const result = response.data || {};
      const created = (result.createdOrders || []).map((o) => o.code).filter(Boolean);
      const skipped = result.skippedReasons || [];
      if (created.length) {
        proxy.$modal.msgSuccess("已生成 " + created.length + " 张送货单：" + created.join("、"));
      } else if (skipped.length) {
        proxy.$modal.msg(skipped.join("；"));
      } else {
        proxy.$modal.msg("没有新单据（当日订单已全部进单）");
      }
      loadAll();
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false;
    });
}

function gotoDeliveryPage() {
  visible.value = false;
  proxy.$router.push({ path: "/order/delivery", query: { deliveryDate: deliveryDate.value } });
}
</script>

<style scoped>
.cd-scope {
  margin-bottom: 12px;
}
.cd-section-title {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: bold;
}
.cd-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.cd-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.cd-tag {
  margin-left: 4px;
}
.cd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.cd-footer-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
