<template>
  <div class="app-container batch-view-page">
    <!-- 查询条：客户 + 配送日期（客户日总表 = 采购配货来源之一，D-027/28） -->
    <el-form :inline="true" size="small" label-width="80px">
      <el-form-item label="客户">
        <el-select v-model="customerId" placeholder="请选择客户" filterable style="width: 220px">
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
      <el-form-item>
        <el-button type="primary" :icon="Search" :disabled="!customerId || !deliveryDate" @click="loadView"
          >查询</el-button
        >
        <el-button :icon="Printer" :disabled="!rows.length" @click="handlePrint">打印</el-button>
      </el-form-item>
    </el-form>

    <el-empty
      v-if="!loading && !rows.length"
      :description="customerId && deliveryDate ? '该客户在此日期没有有效送货单（未生成或已全部作废）' : '请选择客户与配送日期'"
    />

    <!-- 总表正文：标准品名 + 总量 + 各点小计折叠，无价格（D-028） -->
    <div v-show="rows.length" ref="printArea" class="batch-print-area">
      <div class="batch-title">
        配送总表（{{ customerName }} / {{ deliveryDate }}）
        <span class="batch-sub">内部配货·采购参考，不含价格</span>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" border>
        <el-table-column type="expand">
          <template #default="scope">
            <div class="dept-subtotal">
              <div v-for="(d, i) in scope.row.depts" :key="i" class="dept-line">
                <span class="dept-name">{{ d.deptName || '—' }}</span>
                <span class="dept-qty">{{ d.quantity }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="标准品名" align="center" prop="productName" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="总量" align="center" prop="totalQuantity" width="120">
          <template #default="scope">
            <b>{{ scope.row.totalQuantity }}</b>
          </template>
        </el-table-column>
        <el-table-column label="配送点小计" align="center" min-width="220">
          <template #default="scope">
            <el-tag
              v-for="(d, i) in scope.row.depts"
              :key="i"
              size="small"
              type="info"
              class="dept-tag"
              >{{ (d.deptName || '—') + ' ' + d.quantity }}</el-tag
            >
          </template>
        </el-table-column>
      </el-table>
      <div class="batch-footer">
        共 {{ rows.length }} 个品种 · 合计 {{ totalCount }}
      </div>
    </div>
  </div>
</template>

<script>
import { batchView } from "@/api/order/delivery";
import { listCustomer } from "@/api/partner/customer";
import { Search, Printer } from "@element-plus/icons-vue";

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
      rows: [],
    };
  },
  computed: {
    customerName() {
      const c = this.customers.find((x) => x.id === this.customerId);
      return c ? c.name : "";
    },
    totalCount() {
      return this.rows.reduce((s, r) => s + Number(r.totalQuantity || 0), 0);
    },
  },
  created() {
    listCustomer().then((response) => {
      this.customers = response.data || [];
    });
  },
  methods: {
    loadView() {
      if (!this.customerId || !this.deliveryDate) {
        this.$modal.msgWarning("请选择客户与配送日期");
        return;
      }
      this.loading = true;
      batchView(this.customerId, this.deliveryDate)
        .then((response) => {
          this.rows = response.data || [];
          if (!this.rows.length) {
            this.$modal.msgWarning("该客户在此日期没有有效送货单数据");
          }
        })
        .finally(() => {
          this.loading = false;
        });
    },
    /** 打印（内部总表：仅品名+数量，D-028 不含价格） */
    handlePrint() {
      window.print();
    },
  },
};
</script>

<style lang="scss" scoped>
.batch-view-page {
  .batch-print-area {
    max-width: 900px;
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
