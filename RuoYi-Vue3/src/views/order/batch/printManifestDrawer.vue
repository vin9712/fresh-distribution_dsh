<template>
  <el-drawer
    :model-value="modelValue"
    title="当日打印清单（全部客户）"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <div v-loading="loading" class="manifest-wrap">
      <div class="manifest-toolbar">
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="配送日期"
          style="width: 150px"
          @change="loadManifest"
        />
        <el-checkbox v-model="onlyUnprinted" style="margin-left: 12px">只看未打印</el-checkbox>
        <el-button size="small" :disabled="printing" @click="checkAllUnprinted">全选未打印</el-button>
        <el-button size="small" type="primary" :loading="printing" :disabled="!checkedKeys.length" @click="startQueue">
          {{ printing ? "打印中…" : `开始打印（${checkedKeys.length}）` }}
        </el-button>
        <el-button v-if="printing" size="small" type="danger" plain @click="abortQueue">中止</el-button>
      </div>

      <el-alert
        v-if="summary"
        :title="summary"
        :type="queueDone ? 'success' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 8px"
      />

      <el-empty v-if="!loading && !manifest.length" description="该日期没有应打单据（无已确认订单）" />

      <div v-for="c in visibleManifest" :key="c.customerId" class="cust-block">
        <div class="cust-head">
          <el-checkbox
            :model-value="isCustomerAllChecked(c)"
            :indeterminate="isCustomerIndeterminate(c)"
            :disabled="printing"
            @change="(v) => toggleCustomer(c, v)"
          />
          <b>{{ c.customerName }}</b>
          <el-tag v-if="c.matrixPrinted" size="small" type="danger" effect="plain">总单已打印</el-tag>
        </div>
        <!-- 总单行 -->
        <div class="job-row">
          <el-checkbox
            :model-value="checkedKeys.includes(c.matrixBizKey)"
            :disabled="printing"
            @change="(v) => toggleJob(c.matrixBizKey, v)"
          />
          <span class="job-name">总单（矩阵）</span>
          <el-tag :type="jobState(c.matrixBizKey).type" size="small" effect="plain">{{ jobState(c.matrixBizKey).text }}</el-tag>
          <el-tag size="small" :type="c.matrixPrinted ? 'danger' : 'info'" effect="plain">
            {{ c.matrixPrinted ? "已打印" : "未打印" }}
          </el-tag>
        </div>
        <!-- 点单行 -->
        <div v-for="p in c.points" :key="p.bizKey" class="job-row job-sub">
          <el-checkbox
            :model-value="checkedKeys.includes(p.bizKey)"
            :disabled="printing"
            @change="(v) => toggleJob(p.bizKey, v)"
          />
          <span class="job-name">{{ p.deptName }}点单</span>
          <span class="job-qty">应送 {{ p.totalNum }}</span>
          <el-tag :type="jobState(p.bizKey).type" size="small" effect="plain">{{ jobState(p.bizKey).text }}</el-tag>
          <el-tag size="small" :type="p.printed ? 'danger' : 'info'" effect="plain">
            {{ p.printed ? "已打印" : "未打印" }}
          </el-tag>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { getPrintManifest, getDeliveryPrintState } from "@/api/order/delivery";
import { issuePrintTicket } from "@/api/print/ticket";
import { resolvePrintTemplate } from "@/api/print/template";

// 回执事件 localStorage 键（报表页 print-annotation.js 真实打印后写入，本抽屉监听 storage 事件自动推进）
const RECEIPT_KEY = "print_receipt";
// 单张等待回执超时（ms）
const RECEIPT_TIMEOUT = 45000;

export default {
  name: "PrintManifestDrawer",
  props: {
    modelValue: { type: Boolean, default: false },
    deliveryDate: { type: String, default: null },
  },
  emits: ["update:modelValue", "printed"],
  data() {
    return {
      loading: false,
      date: null,
      manifest: [],
      onlyUnprinted: true,
      checkedKeys: [],
      // 队列状态：bizKey → idle|waiting|printed|skipped|timeout|error
      jobStates: {},
      printing: false,
      queueDone: false,
      queueAborted: false,
    };
  },
  computed: {
    visibleManifest() {
      if (!this.onlyUnprinted) return this.manifest;
      return this.manifest.filter(
        (c) => !c.matrixPrinted || (c.points || []).some((p) => !p.printed)
      );
    },
    summary() {
      if (!this.manifest.length) return "";
      const total = this.manifest.reduce((s, c) => s + 1 + (c.points || []).length, 0);
      const printed = this.manifest.reduce(
        (s, c) => s + (c.matrixPrinted ? 1 : 0) + (c.points || []).filter((p) => p.printed).length,
        0
      );
      if (this.queueDone) {
        const ok = Object.values(this.jobStates).filter((s) => s === "printed").length;
        const skip = Object.values(this.jobStates).filter((s) => s === "skipped" || s === "timeout").length;
        return `队列完成：成功 ${ok} · 跳过/超时 ${skip}`;
      }
      return `当日共 ${total} 张：已打印 ${printed} · 未打印 ${total - printed}`;
    },
  },
  beforeUnmount() {
    window.removeEventListener("storage", this.onStorageReceipt);
  },
  methods: {
    onOpen() {
      this.date = this.deliveryDate;
      this.resetQueue();
      this.loadManifest();
      window.addEventListener("storage", this.onStorageReceipt);
    },
    resetQueue() {
      this.checkedKeys = [];
      this.jobStates = {};
      this.printing = false;
      this.queueDone = false;
      this.queueAborted = false;
    },
    loadManifest() {
      if (!this.date) return;
      this.loading = true;
      getPrintManifest(this.date)
        .then((res) => {
          this.manifest = res.data || [];
          this.checkAllUnprinted();
        })
        .finally(() => (this.loading = false));
    },
    checkAllUnprinted() {
      const keys = [];
      this.manifest.forEach((c) => {
        if (!c.matrixPrinted) keys.push(c.matrixBizKey);
        (c.points || []).forEach((p) => {
          if (!p.printed) keys.push(p.bizKey);
        });
      });
      this.checkedKeys = keys;
    },
    toggleJob(key, checked) {
      if (checked) {
        if (!this.checkedKeys.includes(key)) this.checkedKeys.push(key);
      } else {
        this.checkedKeys = this.checkedKeys.filter((k) => k !== key);
      }
    },
    toggleCustomer(c, checked) {
      const keys = [c.matrixBizKey, ...(c.points || []).map((p) => p.bizKey)];
      if (checked) {
        keys.forEach((k) => {
          if (!this.checkedKeys.includes(k)) this.checkedKeys.push(k);
        });
      } else {
        this.checkedKeys = this.checkedKeys.filter((k) => !keys.includes(k));
      }
    },
    isCustomerAllChecked(c) {
      const keys = [c.matrixBizKey, ...(c.points || []).map((p) => p.bizKey)];
      return keys.every((k) => this.checkedKeys.includes(k));
    },
    isCustomerIndeterminate(c) {
      const keys = [c.matrixBizKey, ...(c.points || []).map((p) => p.bizKey)];
      const n = keys.filter((k) => this.checkedKeys.includes(k)).length;
      return n > 0 && n < keys.length;
    },
    jobState(key) {
      const map = {
        idle: { text: "待处理", type: "info" },
        waiting: { text: "等待打印…", type: "warning" },
        printed: { text: "✓ 已出纸", type: "success" },
        skipped: { text: "已跳过", type: "info" },
        timeout: { text: "未确认", type: "warning" },
        error: { text: "失败", type: "danger" },
      };
      return map[this.jobStates[key] || "idle"];
    },
    /** 队列主循环（PT-2）：resolve 模板 → 签发票据 → 开窗 → 等回执 → 下一张 */
    async startQueue() {
      if (!this.checkedKeys.length || this.printing) return;
      this.printing = true;
      this.queueDone = false;
      this.queueAborted = false;
      const queue = [...this.checkedKeys];
      for (const bizKey of queue) {
        if (this.queueAborted) break;
        this.jobStates[bizKey] = "waiting";
        try {
          await this.printOne(bizKey);
        } catch (e) {
          this.jobStates[bizKey] = "error";
        }
      }
      this.printing = false;
      this.queueDone = true;
      // 队列结束：重拉清单刷新打印分界展示 + 通知父页
      this.loadManifest();
      this.$emit("printed");
    },
    abortQueue() {
      this.queueAborted = true;
    },
    async printOne(bizKey) {
      const resolved = await resolvePrintTemplate(bizKey).then((r) => r.data);
      if (!resolved || !resolved.reportViewId) {
        this.$modal.msgWarning((resolved && resolved.warning) || `未找到模板：${bizKey}`);
        this.jobStates[bizKey] = "error";
        return;
      }
      const ticket = await issuePrintTicket({ bizKey, templateId: resolved.templateId }).then((r) => r.ticket);
      window.open(this.buildReportUrl(bizKey, resolved.reportViewId, ticket), "_blank");
      // 等回执（storage 事件）或超时；resolve 后清除监听
      await new Promise((resolve) => {
        const timer = setTimeout(() => {
          if (this.jobStates[bizKey] === "waiting") this.jobStates[bizKey] = "timeout";
          window.removeEventListener(RECEIPT_EVENT, onReceipt);
          resolve();
        }, RECEIPT_TIMEOUT);
        const onReceipt = (event) => {
          if (event.detail && event.detail.bizKey === bizKey) {
            clearTimeout(timer);
            this.jobStates[bizKey] = "printed";
            window.removeEventListener(RECEIPT_EVENT, onReceipt);
            resolve();
          }
        };
        window.addEventListener(RECEIPT_EVENT, onReceipt);
      });
    },
    /** 报表页回执转发：print-annotation.js 同页派发自定义事件兜底（同页场景）+ storage 跨页场景 */
    onStorageReceipt(e) {
      if (e.key !== RECEIPT_KEY || !e.newValue) return;
      try {
        const payload = JSON.parse(e.newValue);
        window.dispatchEvent(new CustomEvent(RECEIPT_EVENT, { detail: payload }));
      } catch (err) {
        /* 忽略非法回执 */
      }
    },
    buildReportUrl(bizKey, reportViewId, ticket) {
      const parts = bizKey.split(":");
      let url = "/jmreport/view/" + reportViewId + "?token=" + ticket + "&ticket=" + ticket + "&deliveryOrderId=";
      if (parts[0] === "matrix") {
        url += "&customerId=" + parts[1] + "&deliveryDate=" + parts[2];
      } else {
        url += "&customerId=" + parts[1] + "&customerDeptId=" + parts[2] + "&deliveryDate=" + parts[3];
      }
      return url;
    },
  },
};

const RECEIPT_EVENT = "print-receipt-event";
</script>

<style lang="scss" scoped>
.manifest-wrap {
  padding: 0 4px;
}
.manifest-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cust-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 10px;

  .cust-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
}
.job-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0 3px 8px;

  &.job-sub {
    padding-left: 28px;
  }
  .job-name {
    min-width: 120px;
  }
  .job-qty {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
