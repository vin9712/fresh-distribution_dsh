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

    <!-- S2-2.1 日结待办链：已确认订单 → 批量采购 → 送货打印 → 送达登记 → 独立验收 -->
    <el-card class="chain-card" shadow="never">
      <template #header>
        <div class="chain-header">
          <span class="chain-title">日结待办链</span>
          <span class="chain-subtitle">从已确认订单到独立验收的当日闭环，点击节点进入对应队列</span>
        </div>
      </template>
      <div class="todo-chain">
        <template v-for="(node, idx) in chainNodes" :key="node.key">
          <div
            class="chain-node"
            :class="{ 'chain-node--done': nodeCount(node) === 0 }"
            @click="goTo(node)"
          >
            <div class="chain-node-badge" :style="{ backgroundColor: node.color }">
              {{ nodeCount(node) }}
            </div>
            <div class="chain-node-name">{{ node.title }}</div>
            <div class="chain-node-desc">{{ nodeDesc(node) }}</div>
          </div>
          <div v-if="idx < chainNodes.length - 1" class="chain-arrow">→</div>
        </template>
      </div>
    </el-card>

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
      // S2-2.1 日结待办链：按业务时序串联五个阶段
      chainNodes: [
        {
          key: "chainConfirmed",
          title: "已确认订单",
          color: "#409eff",
          path: "/order/sale",
          query: { status: 1 },
          descFn: (s) =>
            `共 ${s.confirmedOrders || 0} 单，明日配送待生成采购 ${s.pendingPurchase || 0} 单`,
        },
        {
          key: "chainPurchase",
          title: "批量采购",
          color: "#67c23a",
          path: "/purchase",
          descFn: (s) =>
            `待确认 ${s.purchaseDraft || 0} 单，到货待确认成本 ${s.purchasePendingCost || 0} 单`,
        },
        {
          key: "chainPrint",
          title: "送货打印",
          color: "#e6a23c",
          path: "/order/delivery",
          query: { status: 0 },
          descFn: (s) => `待打印送货单 ${s.pendingPrint || 0} 张`,
        },
        {
          key: "chainDelivered",
          title: "送达登记",
          color: "#f89898",
          path: "/order/delivery",
          query: { status: 1 },
          descFn: (s) => `已打印待登记送达 ${s.pendingMarkDelivered || 0} 张`,
        },
        {
          key: "chainAcceptance",
          title: "独立验收",
          color: "#f56c6c",
          path: "/order/acceptance",
          descFn: (s) => `已送达待验收 ${s.pendingAcceptance || 0} 张`,
        },
      ],
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
    /** 链节点主计数：采购节点取 待确认+待成本 之和，其余取对应字段 */
    nodeCount(node) {
      const s = this.summary;
      if (node.key === "chainPurchase") {
        return (s.purchaseDraft || 0) + (s.purchasePendingCost || 0);
      }
      return s[node.key.replace("chain", "pending")] || 0;
    },
    nodeDesc(node) {
      return node.descFn ? node.descFn(this.summary) : "";
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
/* S2-2.1 日结待办链 */
.chain-card {
  margin-bottom: 16px;
  .chain-header {
    display: flex;
    align-items: baseline;
    gap: 10px;
    .chain-title {
      font-size: 15px;
      font-weight: 600;
    }
    .chain-subtitle {
      font-size: 12px;
      color: #909399;
    }
  }
  .todo-chain {
    display: flex;
    align-items: stretch;
    flex-wrap: wrap;
    gap: 8px;
  }
  .chain-node {
    flex: 1;
    min-width: 150px;
    text-align: center;
    padding: 10px 8px;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    &:hover {
      border-color: #409eff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
    }
    /* 该阶段无待办：降灰提示已清空 */
    &.chain-node--done {
      opacity: 0.55;
    }
    .chain-node-badge {
      display: inline-block;
      min-width: 36px;
      padding: 2px 10px;
      border-radius: 12px;
      color: #fff;
      font-weight: bold;
      font-size: 15px;
    }
    .chain-node-name {
      margin-top: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
    .chain-node-desc {
      margin-top: 4px;
      font-size: 12px;
      color: #909399;
      line-height: 1.4;
    }
  }
  .chain-arrow {
    align-self: center;
    font-size: 18px;
    color: #c0c4cc;
    padding: 0 2px;
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
