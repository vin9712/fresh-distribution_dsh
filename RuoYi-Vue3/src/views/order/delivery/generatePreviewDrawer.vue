<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="1040px"
    append-to-body
    :destroy-on-close="false"
    :close-on-click-modal="false"
    @open="loadPreview"
  >
    <generate-preview-list :preview="preview" :loading="loading" :single-customer="!!customerId">
      <template #actions>
        <div class="gp-summary-actions">
          <el-button link type="primary" :icon="Refresh" :loading="loading" @click="loadPreview()">
            刷新清单
          </el-button>
        </div>
      </template>
    </generate-preview-list>

    <!-- 生成结果：在抽屉内如实回显，不靠 3 秒 toast -->
    <el-alert
      v-if="lastResult"
      :type="lastResult.created.length ? 'success' : 'info'"
      :closable="false"
      show-icon
      class="gp-result"
    >
      <template #title>
        <span v-if="lastResult.created.length">已生成送货单：{{ lastResult.created.join("、") }}</span>
        <span v-else>没有新单据（当日订单已全部进单，幂等跳过）</span>
      </template>
      <div v-if="lastResult.skipped.length" class="gp-result-line">
        幂等跳过 {{ lastResult.skipped.length }} 项：{{ lastResult.skipped.join("；") }}
      </div>
      <div class="gp-result-line">清单已按最新数据重新推演，可继续核对其他客户。</div>
    </el-alert>

    <template #footer>
      <div class="gp-footer">
        <div class="gp-footer-hint">
          预览与生成同源判定（只读推演，不落库）；确认后仍按幂等规则补齐，期间新确认的订单会一并进单。
        </div>
        <div>
          <el-button @click="visible = false">取 消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="loading || !expectedCount"
            @click="handleConfirm"
          >
            确认生成（预计 {{ expectedCount || 0 }} 张）
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { Refresh } from "@element-plus/icons-vue";
import GeneratePreviewList from "./generatePreviewList.vue";
import {
  groupPreview,
  generateDelivery,
  generateDeliveryForCustomer,
} from "@/api/order/delivery";

const { proxy } = getCurrentInstance();

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 配送日期（必填，yyyy-MM-dd） */
  deliveryDate: { type: String, default: null },
  /** 客户维度入口：传值=只预览/生成该客户（客户管理页「送货单」） */
  customerId: { type: [Number, String], default: null },
  /** 客户名（仅用于标题展示） */
  customerName: { type: String, default: null },
});
const emit = defineEmits(["update:modelValue", "success"]);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const loading = ref(false);
const submitting = ref(false);
const preview = ref({});
const lastResult = ref(null);

const expectedCount = computed(() => preview.value.expectedDeliveryCount || 0);
const drawerTitle = computed(() =>
  props.customerId
    ? "送货单 · 待生成清单确认（" + (props.customerName || "该客户") + "）"
    : "生成送货单 · 待生成清单确认"
);

/** 拉取待生成清单（只读预览，与生成同源判定）；keepResult=true 用于生成后刷新时保留结果回显 */
function loadPreview(keepResult) {
  if (!keepResult) {
    lastResult.value = null;
  }
  if (!props.deliveryDate) {
    preview.value = {};
    return;
  }
  loading.value = true;
  const params = { deliveryDate: props.deliveryDate };
  if (props.customerId) {
    params.customerId = props.customerId;
  }
  groupPreview(params)
    .then((response) => {
      preview.value = response.data || {};
    })
    .catch(() => {
      preview.value = {};
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 确认生成：按日期（当日全部客户）或按客户+日期（客户维度补单），均走统一生成服务（幂等） */
function handleConfirm() {
  if (!props.deliveryDate) {
    proxy.$modal.msgWarning("请先选择配送日期");
    return;
  }
  submitting.value = true;
  const req = props.customerId
    ? generateDeliveryForCustomer(props.customerId, props.deliveryDate)
    : generateDelivery(props.deliveryDate);
  req
    .then((response) => {
      const result = response.data || {};
      const created = (result.createdOrders || []).map((o) => o.code).filter(Boolean);
      lastResult.value = { created, skipped: result.skippedReasons || [] };
      if (created.length) {
        proxy.$modal.msgSuccess("已生成 " + created.length + " 张送货单");
      }
      emit("success", result);
      // 生成后重新推演剩余遗漏（如并发新确认的订单），结果回显保留
      loadPreview(true);
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false;
    });
}

defineExpose({ loadPreview });
</script>

<style scoped>
.gp-summary-actions {
  margin-left: auto;
}
.gp-result {
  margin-top: 10px;
}
.gp-result-line {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
}
.gp-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.gp-footer-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
