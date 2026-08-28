<template>
  <div class="app-container">
    <!-- 送货单生成异常高优告警（S14/Q36/D-037：定时窗口失败/部分失败时置顶提示） -->
    <el-alert
      v-if="jobAlert"
      type="error"
      :closable="false"
      show-icon
      class="job-alert"
    >
      <template #title>
        <span class="job-alert-title">
          {{ jobAlertText }}
          <el-button link type="primary" @click="goTo({ path: '/order/delivery' })">去送货单页检查</el-button>
        </span>
      </template>
    </el-alert>
    <el-row :gutter="16">
      <el-col
        v-for="item in cards"
        :key="item.key"
        :xs="12"
        :sm="8"
        :md="4"
      >
        <el-card class="workbench-card" shadow="hover" @click="goTo(item)">
          <div class="card-icon" :style="{ backgroundColor: item.color }">
            <el-icon :size="26" color="#fff"><component :is="item.icon" /></el-icon>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div class="card-count">{{ summary[item.key] || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getWorkbenchSummary, getJobAlert } from "@/api/workbench";
import {
  EditPen,
  Checked,
  ShoppingCart,
  Printer,
  Box,
  RefreshLeft,
} from "@element-plus/icons-vue";

export default {
  name: "Workbench",
  components: { EditPen, Checked, ShoppingCart, Printer, Box, RefreshLeft },
  setup() {
    return {};
  },
  data() {
    return {
      loading: false,
      summary: {},
      // 最近一次送货单生成任务异常记录（null = 无告警）
      jobAlert: null,
      cards: [
        { key: "draftOrders", title: "待录/待确认订单", icon: "EditPen", color: "#409eff", path: "/order/sale" },
        { key: "pendingPurchase", title: "待生成采购单", icon: "ShoppingCart", color: "#67c23a", path: "/order/sale" },
        { key: "pendingPrint", title: "待打印送货单", icon: "Printer", color: "#e6a23c", path: "/order/delivery" },
        { key: "pendingAcceptance", title: "待验收", icon: "Box", color: "#f56c6c", path: "/order/acceptance" },
        { key: "pendingAdjust", title: "待处理加退换", icon: "RefreshLeft", color: "#909399", path: "/order/sale" },
      ],
    };
  },
  created() {
    this.getSummary();
    this.refreshJobAlert();
  },
  methods: {
    getSummary() {
      this.loading = true;
      getWorkbenchSummary()
        .then((response) => {
          this.summary = response.data || {};
          this.loading = false;
        })
        .catch(() => {
          this.loading = false;
        });
    },
    /** 拉取最近一次 DELIVERY_GENERATE 运行结果（失败/部分失败→高优告警） */
    refreshJobAlert() {
      getJobAlert()
        .then((response) => {
          this.jobAlert = response.data || null;
        })
        .catch(() => {});
    },
    jobAlertText() {
      const a = this.jobAlert || {};
      const kind = a.status === 2 ? "部分失败（存在遗漏订单）" : "失败";
      const warn = a.warningCount ? "，遗漏订单 " + a.warningCount + " 个" : "";
      return (
        "送货单定时生成" + kind + "：业务日期 " + (a.bizDate || "—") + warn +
        (a.message ? "（" + a.message + "）" : "")
      );
    },
    goTo(item) {
      // query：可选过滤条件（如待验收卡 → 订单页并筛选「已配送」）
      this.$router.push({ path: item.path, query: item.query });
    },
  },
};
</script>

<style lang="scss" scoped>
.job-alert {
  margin-bottom: 16px;
  .job-alert-title {
    font-weight: 600;
    .el-button {
      margin-left: 8px;
    }
  }
}
.workbench-card {
  cursor: pointer;
  margin-bottom: 16px;
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
  }
  .card-icon {
    width: 56px;
    height: 56px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 16px;
    flex-shrink: 0;
  }
  .card-body {
    .card-title {
      font-size: 14px;
      color: #909399;
      margin-bottom: 6px;
    }
    .card-count {
      font-size: 28px;
      font-weight: bold;
      color: #303133;
    }
  }
}
</style>
