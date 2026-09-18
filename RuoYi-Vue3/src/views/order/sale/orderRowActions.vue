<template>
  <span class="order-row-actions">
    <el-button
      v-if="row.status == 1 && !row.allocated"
      size="small"
      link
      :icon="RefreshLeft"
      @click="$emit('recall', row)"
      v-hasPermi="['order:sale:recall']"
      >撤回</el-button
    >
    <el-tooltip
      v-if="row.status == 1 && row.allocated"
      content="已生成送货单，请先作废对应送货单再撤回"
      placement="top"
    >
      <span class="op-disabled-tip">撤回</span>
    </el-tooltip>
    <el-button
      v-if="row.status == 1 || row.status == 2"
      size="small"
      link
      type="primary"
      :icon="Box"
      @click="$emit('acceptance', row)"
      v-hasPermi="['acceptance:query']"
      >去验收</el-button
    >
    <el-button
      v-if="row.status == 3 || row.status == 4"
      size="small"
      link
      :icon="View"
      @click="$emit('acceptance', row)"
      v-hasPermi="['acceptance:query']"
      >查看验收</el-button
    >
    <el-button
      v-if="row.status == 3"
      size="small"
      link
      type="warning"
      :icon="RefreshLeft"
      @click="$emit('revoke-acceptance', row)"
      v-hasPermi="['acceptance:revoke']"
      >撤回</el-button
    >
    <el-button
      v-if="row.status == 3"
      size="small"
      link
      type="success"
      :icon="Check"
      @click="$emit('settle', row)"
      v-hasPermi="['order:sale:settle']"
      >结算</el-button
    >
    <el-button
      v-if="row.status == 1"
      size="small"
      link
      type="danger"
      :icon="CircleClose"
      @click="$emit('delivery-change', row)"
      v-hasPermi="['order:sale:edit']"
      >配送后变更</el-button
    >
    <el-button
      v-if="row.status == 0"
      size="small"
      link
      :icon="Edit"
      @click="$emit('edit', row)"
      v-hasPermi="['order:sale:edit']"
      >修改</el-button
    >
    <el-button
      v-if="row.status >= 2"
      size="small"
      link
      type="warning"
      :icon="TrendCharts"
      @click="$emit('adjustment', row)"
      v-hasPermi="['order:sale:list']"
      >调整摘要</el-button
    >
    <el-button
      v-if="row.status == 0"
      size="small"
      link
      :icon="Delete"
      @click="$emit('delete', row)"
      v-hasPermi="['order:sale:remove']"
      >删除</el-button
    >
  </span>
</template>

<script>
import {
  RefreshLeft,
  Box,
  View,
  Check,
  CircleClose,
  Edit,
  TrendCharts,
  Delete,
} from "@element-plus/icons-vue";

/**
 * 销售订单行操作（D-065 抽出）：明细视角主表与客户视角子行共用。
 *
 * <p>只负责「按状态+权限显示按钮」，动作全部 emit 给列表页既有 handler，
 * 保证两个视角的操作语义、权限点、二次确认文案零漂移。</p>
 */
export default {
  name: "OrderRowActions",
  props: {
    row: { type: Object, required: true },
  },
  emits: [
    "recall",
    "acceptance",
    "revoke-acceptance",
    "settle",
    "delivery-change",
    "edit",
    "adjustment",
    "delete",
  ],
  setup() {
    return {
      RefreshLeft,
      Box,
      View,
      Check,
      CircleClose,
      Edit,
      TrendCharts,
      Delete,
    };
  },
};
</script>

<style lang="scss" scoped>
/* 撤回禁用提示（已生成送货单，不可撤回） */
.op-disabled-tip {
  display: inline-block;
  font-size: 12px;
  color: #c0c4cc;
  cursor: not-allowed;
  margin: 0 12px;
}
</style>
