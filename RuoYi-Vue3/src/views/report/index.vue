<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="card">
      <!-- ============ 销售日报 ============ -->
      <el-tab-pane label="销售日报" name="daily">
        <el-form size="small" :inline="true" label-width="80px">
          <el-form-item label="配送日期">
            <el-date-picker
              v-model="dailyForm.deliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择配送日期"
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" size="small" @click="loadDaily">查询</el-button>
            <el-button
              type="warning"
              plain
              :icon="Download"
              size="small"
              @click="exportDaily"
              v-hasPermi="['report:export']"
              >导出</el-button
            >
          </el-form-item>
        </el-form>

        <el-table v-loading="dailyLoading" :data="dailyGroups" border>
          <el-table-column type="expand">
            <template #default="scope">
              <el-table :data="scope.row.items" size="small" border>
                <el-table-column label="商品名称" align="center" prop="productName" />
                <el-table-column label="规格" align="center" prop="productSpec" width="140" />
                <el-table-column label="单位" align="center" prop="productUnit" width="70" />
                <el-table-column label="实收数量" align="center" prop="actualQuantity" width="100" />
                <el-table-column label="损耗" align="center" width="100">
                  <template #default="s">
                    <span :class="{ 'loss-negative': Number(s.row.lossQuantity) < 0 }">{{ s.row.lossQuantity }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单价" align="center" prop="unitPrice" width="90" />
                <el-table-column label="实收金额" align="center" prop="actualAmount" width="110" />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column label="客户" align="center" prop="customerName" />
          <el-table-column label="配送点" align="center" prop="deliveryPointName" />
          <el-table-column label="实收金额" align="center" prop="totalActualAmount" width="130" />
          <el-table-column label="损耗数量" align="center" prop="totalLossQuantity" width="110" />
          <el-table-column label="损耗金额" align="center" prop="totalLossAmount" width="110" />
        </el-table>
        <div v-if="dailyLoaded && dailyGroups.length === 0" class="empty-tip">该配送日期暂无已提交验收数据</div>
      </el-tab-pane>

      <!-- ============ 客户对账单 ============ -->
      <el-tab-pane label="客户对账单" name="statement">
        <el-form size="small" :inline="true" label-width="70px">
          <el-form-item label="客户">
            <el-select
              v-model="statementForm.customerId"
              placeholder="请选择客户"
              filterable
              clearable
              style="width: 180px"
            >
              <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker
              v-model="statementForm.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" size="small" @click="loadStatement">查询</el-button>
            <el-button
              type="warning"
              plain
              :icon="Download"
              size="small"
              @click="exportStatement"
              v-hasPermi="['report:export']"
              >导出</el-button
            >
          </el-form-item>
        </el-form>

        <div v-if="statement && statement.acceptances" class="statement-summary">
          <el-tag size="small">期间合计：{{ statement.totalAmount }}</el-tag>
          <span class="summary-hint">（共 {{ statement.acceptances.length }} 张验收单，为结算依据）</span>
        </div>

        <el-table v-loading="statementLoading" :data="statement ? statement.acceptances : []" border>
          <el-table-column type="expand">
            <template #default="scope">
              <el-table :data="scope.row.items" size="small" border>
                <el-table-column label="商品名称" align="center" prop="productName" />
                <el-table-column label="规格" align="center" prop="productSpec" width="140" />
                <el-table-column label="单位" align="center" prop="productUnit" width="70" />
                <el-table-column label="实收数量" align="center" prop="actualQuantity" width="100" />
                <el-table-column label="单价" align="center" prop="unitPrice" width="90" />
                <el-table-column label="实收金额" align="center" prop="actualAmount" width="110" />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column label="验收单号" align="center" prop="code" />
          <el-table-column label="验收日期" align="center" prop="acceptDate" width="120" />
          <el-table-column label="送货单号" align="center" prop="deliveryCode" />
          <el-table-column label="验收单金额" align="center" prop="totalAmount" width="130" />
        </el-table>
        <div v-if="statementLoaded && statement && statement.acceptances.length === 0" class="empty-tip">
          该期间暂无已提交验收数据
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { dailySale, customerStatement } from "@/api/report/report";
import { listCustomer } from "@/api/partner/customer";
import { Search, Download } from "@element-plus/icons-vue";

export default {
  name: "Report",
  setup() {
    return { Search, Download };
  },
  data() {
    return {
      activeTab: "daily",
      // 销售日报
      dailyForm: { deliveryDate: null },
      dailyGroups: [],
      dailyLoading: false,
      dailyLoaded: false,
      // 客户对账单
      statementForm: { customerId: null, dateRange: [] },
      statement: null,
      statementLoading: false,
      statementLoaded: false,
      customerOptions: [],
    };
  },
  created() {
    this.dailyForm.deliveryDate = this.parseTime(new Date(), "{y}-{m}-{d}");
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
    this.statementForm.dateRange = [
      this.parseTime(firstDay, "{y}-{m}-{d}"),
      this.parseTime(now, "{y}-{m}-{d}"),
    ];
    listCustomer().then((response) => {
      this.customerOptions = response.data || [];
    });
    this.loadDaily();
  },
  methods: {
    loadDaily() {
      if (!this.dailyForm.deliveryDate) {
        this.$modal.msgWarning("请选择配送日期");
        return;
      }
      this.dailyLoading = true;
      dailySale(this.dailyForm.deliveryDate)
        .then((response) => {
          this.dailyGroups = response.data || [];
          this.dailyLoaded = true;
          this.dailyLoading = false;
        })
        .catch(() => {
          this.dailyLoading = false;
        });
    },
    exportDaily() {
      if (!this.dailyForm.deliveryDate) {
        this.$modal.msgWarning("请选择配送日期");
        return;
      }
      this.download(
        "report/dailySale/export",
        { deliveryDate: this.dailyForm.deliveryDate },
        `销售日报_${this.dailyForm.deliveryDate}.xlsx`
      );
    },
    loadStatement() {
      if (!this.statementForm.customerId) {
        this.$modal.msgWarning("请选择客户");
        return;
      }
      const range = this.statementForm.dateRange;
      if (!range || range.length !== 2) {
        this.$modal.msgWarning("请选择日期范围");
        return;
      }
      this.statementLoading = true;
      customerStatement({
        customerId: this.statementForm.customerId,
        beginDate: range[0],
        endDate: range[1],
      })
        .then((response) => {
          this.statement = response.data;
          this.statementLoaded = true;
          this.statementLoading = false;
        })
        .catch(() => {
          this.statementLoading = false;
        });
    },
    exportStatement() {
      if (!this.statementForm.customerId) {
        this.$modal.msgWarning("请选择客户");
        return;
      }
      const range = this.statementForm.dateRange;
      if (!range || range.length !== 2) {
        this.$modal.msgWarning("请选择日期范围");
        return;
      }
      this.download(
        "report/customerStatement/export",
        {
          customerId: this.statementForm.customerId,
          beginDate: range[0],
          endDate: range[1],
        },
        `客户对账单_${this.statementForm.customerId}_${range[0]}_${range[1]}.xlsx`
      );
    },
  },
};
</script>

<style scoped>
.empty-tip {
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}
.statement-summary {
  margin: 8px 0;
  display: flex;
  align-items: center;
}
.summary-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.loss-negative {
  color: #f56c6c;
  font-weight: bold;
}
</style>
