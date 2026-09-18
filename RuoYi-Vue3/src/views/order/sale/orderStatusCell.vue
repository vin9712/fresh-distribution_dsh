<template>
  <span class="order-status-cell">
    <dict-tag
      class="order-status-dict"
      :options="dict.type.t_sale_order_status"
      :value="row.status"
    />
    <el-tooltip
      v-if="row.status == 1 && !row.allocated"
      content="已确认订单不可直接修改：如需改动请先点「撤回」回到草稿"
      placement="top"
    >
      <el-tag size="small" type="success" effect="plain" class="recallable-tag"
        >可撤回</el-tag
      >
    </el-tooltip>
    <!-- 流程单据（原独立列并入状态列）：采购单/送货单号 tooltip -->
    <el-tooltip
      v-if="row.purchaseOrderCode"
      :content="'采购单：' + row.purchaseOrderCode"
      placement="top"
    >
      <el-icon class="doc-icon doc-purchase"><ShoppingBag /></el-icon>
    </el-tooltip>
    <el-tooltip
      v-if="row.deliveryOrderCode"
      :content="
        '送货单：' +
        row.deliveryOrderCode +
        (row.deliveryOrderVoided ? '（已作废，仅作留痕；撤回不再被它阻塞）' : '')
      "
      placement="top"
    >
      <el-icon
        class="doc-icon"
        :class="row.deliveryOrderVoided ? 'doc-voided' : 'doc-delivery'"
        ><Van
      /></el-icon>
    </el-tooltip>
  </span>
</template>

<script>
import { ShoppingBag, Van } from "@element-plus/icons-vue";

/**
 * 销售订单状态单元格（D-065 抽出）：明细视角主表与客户视角子行共用，
 * 保证两个视角的状态/单据图标口径完全一致。
 */
export default {
  name: "OrderStatusCell",
  dicts: ["t_sale_order_status"],
  props: {
    row: { type: Object, required: true },
  },
  setup() {
    return { ShoppingBag, Van };
  },
};
</script>

<style lang="scss" scoped>
/* 状态单元格：状态 tag + 「可撤回」/单据图标同行排列（而不是各占一行） */
.order-status-cell {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

/* DictTag 根节点是 block div，会把后续标记挤到下一行；
   这里只在本单元格内把它改为 inline-block（不动全局 DictTag 组件） */
.order-status-dict {
  display: inline-block;
}

/* 流程单据图标（并入订单状态列） */
.doc-icon {
  font-size: 16px;
  vertical-align: middle;
  cursor: default;
  &.doc-purchase {
    color: #e6a23c;
  }
  &.doc-delivery {
    color: #67c23a;
  }
  /* 已作废送货单：保留引用但灰显（仅留痕，不再阻塞撤回） */
  &.doc-voided {
    color: #c0c4cc;
    text-decoration: line-through;
  }
}
/* 可撤回标识（间距由 .order-status-cell 的 gap 提供） */
.recallable-tag {
  margin: 0;
}
</style>
