<template>
  <div class="app-container">
    <!-- 验收单台账：订单维度=只读（写入口在订单页）；历史送货单维度=可维护（补建/录入/提交/撤销/删除） -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="新订单验收：订单列表「去验收」→ 订单明细页验收模式；本页查看/打印全部验收单，历史送货单维度的验收单可在此补建/录入/提交/撤销/删除"
      style="margin-bottom: 10px"
    />

    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="验收单号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入验收单号"
          clearable
          style="width: 170px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerId">
        <el-select
          v-model="queryParams.customerId"
          placeholder="请选择客户"
          filterable
          clearable
          style="width: 190px"
        >
          <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="验收日期">
        <el-date-picker
          v-model="acceptRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 230px"
          @change="handleDateRange"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 130px">
          <el-option
            v-for="dict in dict.type.t_acceptance_status"
            :key="dict.value"
            :label="dict.label"
            :value="parseInt(dict.value)"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          :icon="Plus"
          size="small"
          @click="handleAddLegacy"
          v-hasPermi="['acceptance:add']"
          >历史单补建</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="acceptanceList">
      <el-table-column label="验收单号" align="center" prop="code" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="订单号" align="center" prop="saleOrderCode" width="150" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.saleOrderCode || "—" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="送货单号" align="center" prop="deliveryCode" width="150" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.deliveryCode || "—" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户" align="center" prop="customerName" :show-overflow-tooltip="true" />
      <el-table-column label="配送点" align="center" prop="customerDeptName">
        <template #default="scope">
          <span>{{ scope.row.customerDeptName || "—" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="验收日期" align="center" prop="acceptDate" width="110" />
      <el-table-column label="验收总额" align="center" prop="totalAmount" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="dict.type.t_acceptance_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240">
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['acceptance:query']"
            >明细</el-button
          >
          <el-button
            size="small"
            link
            :icon="Printer"
            @click="handlePrint(scope.row)"
            v-hasPermi="['acceptance:query']"
            >打印</el-button
          >
          <!-- 历史维度（无 sale_order_id）：保留维护入口（s29 设计：查看/撤销/补建继续可用） -->
          <template v-if="isLegacyRow(scope.row)">
            <el-button
              size="small"
              link
              :icon="Edit"
              v-if="scope.row.status === 0"
              @click="handleEdit(scope.row)"
              v-hasPermi="['acceptance:edit']"
              >录入</el-button
            >
            <el-button
              size="small"
              link
              :icon="Check"
              v-if="scope.row.status === 0"
              @click="handleSubmit(scope.row)"
              v-hasPermi="['acceptance:submit']"
              >提交</el-button
            >
            <el-button
              size="small"
              link
              :icon="RefreshLeft"
              v-if="scope.row.status === 1"
              @click="handleRevoke(scope.row)"
              v-hasPermi="['acceptance:revoke']"
              >撤销</el-button
            >
            <el-button
              size="small"
              link
              :icon="Delete"
              v-if="scope.row.status === 0"
              @click="handleDelete(scope.row)"
              v-hasPermi="['acceptance:remove']"
              >删除</el-button
            >
          </template>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 历史单补建（送货单维度，@Deprecated 接口，仅历史日期维护用） -->
    <el-dialog align-center title="历史单补建验收（送货单维度）" v-model="legacyOpen" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="送货单" required>
          <el-select v-model="legacyForm.deliveryOrderId" placeholder="请选择已送达的送货单" filterable style="width: 100%">
            <el-option
              v-for="d in deliveryOptions"
              :key="d.id"
              :label="(d.code || '') + ' / ' + (d.customerName || '') + ' / ' + (d.deliveryDate || '')"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <div class="hint">仅用于 D-055 之前的历史送货单补建；新订单请在订单列表「去验收」中验收。</div>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitLegacyForm">确 定</el-button>
        <el-button @click="legacyOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 撤销验收单 -->
    <el-dialog align-center title="撤销验收单" v-model="revokeOpen" width="480px" append-to-body>
      <el-form ref="revokeFormRef" :model="revokeForm" :rules="revokeRules" label-width="90px">
        <el-form-item label="验收单号">
          <span>{{ revokeForm.code }}</span>
        </el-form-item>
        <el-form-item label="撤销原因" prop="reason">
          <el-input v-model="revokeForm.reason" type="textarea" :rows="3" placeholder="请输入撤销原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="revoking" @click="submitRevoke">确认撤销</el-button>
        <el-button @click="revokeOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 验收明细对话框（查看/历史单录入共用） -->
    <el-dialog
      align-center
      :title="detailTitle"
      v-model="detailOpen"
      width="1080px"
      append-to-body
      class="acceptance-dialog"
    >
      <!-- 订单定位提示（highlightOrder：来自订单页「去验收」跳转） -->
      <el-alert
        v-if="highlightOrder && highlightOrderCodes.length"
        type="warning"
        :closable="true"
        show-icon
        class="highlight-banner"
        :title="'黄色高亮行 = 来源订单 ' + highlightOrderCodes.join('、') + ' 贡献的明细'"
      />

      <el-form v-if="editMode" label-width="90px" :inline="true" size="small">
        <el-form-item label="验收日期">
          <el-date-picker
            v-model="editForm.acceptDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择验收日期"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="备注" style="width: 240px" />
        </el-form-item>
      </el-form>

      <el-table :data="detailList" size="small" border :row-class-name="rowHighlightClass">
        <el-table-column label="订单号" align="center" prop="orderCode" min-width="120" :show-overflow-tooltip="true">
          <template #default="scope">
            <span>{{ scope.row.orderCode || "—历史数据—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配送点" align="center" prop="customerDeptName" width="110" :show-overflow-tooltip="true">
          <template #default="scope">
            <span>{{ scope.row.customerDeptName || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品" align="center" min-width="150" :show-overflow-tooltip="true">
          <template #default="scope">
            <span>{{ scope.row.productName }}</span>
            <el-tag v-if="scope.row.changeType == 1" size="small" type="warning" effect="plain" class="row-tag">加单</el-tag>
            <el-tag v-else-if="scope.row.changeType == 2" size="small" type="success" effect="plain" class="row-tag">换货</el-tag>
            <el-tag v-else-if="scope.row.changeType == 3" size="small" type="danger" effect="plain" class="row-tag">退货</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格" align="center" prop="productSpec" width="100" :show-overflow-tooltip="true" />
        <el-table-column label="单位" align="center" prop="productUnit" width="65" />
        <el-table-column label="应送" align="center" prop="deliveredQuantity" width="80" />
        <el-table-column label="实收" align="center" width="130">
          <template #default="scope">
            <el-input-number
              v-if="editMode"
              v-model="scope.row.actualQuantity"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
            <span v-else>{{ scope.row.actualQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" prop="unitPrice" width="75" />
        <el-table-column label="差异" align="center" width="80">
          <template #default="scope">
            <span :class="diffClass(diffOf(scope.row))">{{ diffOf(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异原因" align="center" min-width="160">
          <template #default="scope">
            <template v-if="editMode">
              <el-input
                v-if="diffOf(scope.row) !== 0"
                v-model="scope.row.lossReason"
                :placeholder="diffOf(scope.row) < 0 ? '短收差异必填原因' : '超收差异必填原因'"
              />
              <span v-else class="reason-none">—</span>
            </template>
            <template v-else>
              <el-tag v-if="scope.row.reasonType === 1" size="small" type="danger" class="reason-tag">短收</el-tag>
              <el-tag v-else-if="scope.row.reasonType === 2" size="small" type="warning" class="reason-tag">超收</el-tag>
              <span>{{ scope.row.lossReason }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="实收金额" align="center" width="95">
          <template #default="scope">{{ amountOf(scope.row).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
      <div class="detail-total">合计：{{ totalAmount.toFixed(2) }}</div>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="editMode" type="primary" @click="submitEditForm">保 存</el-button>
          <el-button :icon="Printer" :disabled="!detailList.length" @click="doPrint()">打 印</el-button>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 打印区（@media print 只保留此区域；内容与明细弹窗一致） -->
    <div v-show="detailOpen && detailList.length" class="acceptance-print-area">
      <h2 class="print-title">验收单</h2>
      <div class="print-meta">
        <span>验收单号：{{ printRow.code || "—" }}</span>
        <span>客户：{{ printRow.customerName || "—" }}</span>
        <span>配送点：{{ printRow.customerDeptName || "—" }}</span>
      </div>
      <div class="print-meta">
        <span>订单号：{{ printRow.saleOrderCode || "—" }}</span>
        <span>验收日期：{{ printRow.acceptDate || "—" }}</span>
        <span>状态：{{ statusText(printRow.status) }}</span>
      </div>
      <table class="print-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>配送点</th>
            <th>商品</th>
            <th>规格</th>
            <th>单位</th>
            <th>应送</th>
            <th>实收</th>
            <th>单价</th>
            <th>差异</th>
            <th>差异原因</th>
            <th>实收金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in detailList" :key="row.id">
            <td>{{ row.orderCode || "—" }}</td>
            <td>{{ row.customerDeptName || "—" }}</td>
            <td>
              {{ row.productName }}<span v-if="row.changeType == 1">（加单）</span
              ><span v-else-if="row.changeType == 2">（换货）</span
              ><span v-else-if="row.changeType == 3">（退货）</span>
            </td>
            <td>{{ row.productSpec || "" }}</td>
            <td>{{ row.productUnit || "" }}</td>
            <td class="num">{{ row.deliveredQuantity }}</td>
            <td class="num">{{ row.actualQuantity }}</td>
            <td class="num">{{ row.unitPrice }}</td>
            <td class="num">{{ diffOf(row) }}</td>
            <td>{{ row.lossReason || "" }}</td>
            <td class="num">{{ amountOf(row).toFixed(2) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="10" class="num">合计</td>
            <td class="num">{{ totalAmount.toFixed(2) }}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  </div>
</template>

<script>
import {
  pageAcceptance,
  listAcceptanceItems,
  getAcceptance,
  createAcceptance,
  updateAcceptance,
  submitAcceptance,
  revokeAcceptance,
  delAcceptance,
} from "@/api/acceptance/acceptance";
import { listDelivery } from "@/api/order/delivery";
import { listCustomer } from "@/api/partner/customer";
import {
  Search,
  Refresh,
  Plus,
  Delete,
  Edit,
  Check,
  View,
  Printer,
  RefreshLeft,
} from "@element-plus/icons-vue";

export default {
  name: "Acceptance",
  dicts: ["t_acceptance_status"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Check, View, Printer, RefreshLeft };
  },
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      acceptanceList: [],
      customers: [],
      acceptRange: [],
      // 历史单补建对话框（送货单维度，仅历史单使用）
      legacyOpen: false,
      legacyForm: { deliveryOrderId: null },
      deliveryOptions: [],
      // 撤销验收弹窗
      revokeOpen: false,
      revokeForm: { id: null, code: null, reason: null },
      revokeRules: {
        reason: [{ required: true, message: "撤销原因不能为空", trigger: "blur" }],
      },
      revoking: false,
      // 明细对话框
      detailOpen: false,
      detailTitle: "",
      editMode: false,
      editForm: { acceptDate: null, remark: null },
      detailList: [],
      currentAcceptanceId: null,
      printRow: {},
      // 订单定位高亮（订单页「去验收」跳转携带）
      highlightOrder: null,
      highlightOrderCodes: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
        customerId: null,
        beginAcceptDate: null,
        endAcceptDate: null,
      },
    };
  },
  computed: {
    totalAmount() {
      return this.detailList.reduce((sum, row) => sum + this.amountOf(row), 0);
    },
  },
  created() {
    this.getPageList();
    listCustomer().then((response) => {
      this.customers = response.data || [];
    });
    // 跳转承接：① 订单页历史回退（deliveryId 引导补建 / acceptanceId 直开）；② 高亮定位
    const { acceptanceId, deliveryId, create, highlightOrder, highlightCode } = this.$route.query;
    if (highlightOrder) {
      this.highlightOrder = Number(highlightOrder);
    }
    if (highlightCode) {
      this.highlightOrderCodes = [String(highlightCode)];
    }
    if (acceptanceId) {
      this.openById(Number(acceptanceId), highlightOrder ? "去验收" : undefined);
    } else if (create && deliveryId) {
      this.createForDelivery(Number(deliveryId), true);
    }
  },
  methods: {
    /**
     * 历史维度（可维护）：后端只有 createForDelivery/createByCustomerDate 两条历史路径
     * 不写 sale_order_id；OA 订单维度 insertOrderAcceptance 必写 saleOrderId。
     * 故用 saleOrderId == null 判定（不要用 deliveryOrderId：那依赖
     * 「OA 验收单永远不写 delivery_order_id」的隐含约定，将来 OA 若关联送货单会静默失效）。
     */
    isLegacyRow(row) {
      return row != null && row.saleOrderId == null;
    },
    getPageList() {
      this.loading = true;
      pageAcceptance(this.queryParams).then((response) => {
        this.acceptanceList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    resetQuery() {
      this.acceptRange = [];
      this.queryParams.beginAcceptDate = null;
      this.queryParams.endAcceptDate = null;
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleDateRange(range) {
      this.queryParams.beginAcceptDate = range && range.length ? range[0] : null;
      this.queryParams.endAcceptDate = range && range.length ? range[1] : null;
    },
    /** 按验收单ID直接打开（订单页「去验收」历史回退）：历史草稿进录入，其余只读 */
    openById(id, titleSuffix) {
      getAcceptance(id).then((response) => {
        const acc = response.data || {};
        if (acc.status === 0 && this.isLegacyRow(acc)) {
          this.handleEdit(acc, titleSuffix);
        } else {
          this.handleView(acc, titleSuffix);
        }
      });
    },
    /** 为历史送货单创建验收草稿并直接进入录入（无验收单时引导补建） */
    createForDelivery(deliveryId, silent) {
      return createAcceptance({ deliveryOrderId: deliveryId })
        .then((response) => {
          const acc = response.data || {};
          this.$modal.msgSuccess("验收单【" + (acc.code || "") + "】已生成，请录入实收数量");
          this.handleEdit(acc, "去验收");
        })
        .catch(() => {
          if (!silent) this.getPageList();
        });
    },
    /** 历史单补建（送货单维度，@Deprecated 接口，仅历史日期维护用） */
    handleAddLegacy() {
      this.legacyForm = { deliveryOrderId: null };
      listDelivery({ status: 2 }).then((response) => {
        this.deliveryOptions = response.data || [];
        if (this.deliveryOptions.length === 0) {
          this.$modal.msgWarning("暂无已送达的历史送货单");
          return;
        }
        this.legacyOpen = true;
      });
    },
    submitLegacyForm() {
      if (!this.legacyForm.deliveryOrderId) {
        this.$modal.msgWarning("请选择送货单");
        return;
      }
      this.createForDelivery(this.legacyForm.deliveryOrderId).then(() => {
        this.legacyOpen = false;
        this.getPageList();
      });
    },
    /** 撤销验收（仅已提交可撤销，原因必填，任一来源订单 SETTLED 由后端拒绝） */
    handleRevoke(row) {
      this.revokeForm = { id: row.id, code: row.code, reason: null };
      this.revokeOpen = true;
      this.$nextTick(() => this.$refs.revokeFormRef && this.$refs.revokeFormRef.clearValidate());
    },
    submitRevoke() {
      this.$refs.revokeFormRef.validate((valid) => {
        if (!valid) return;
        this.revoking = true;
        revokeAcceptance(this.revokeForm.id, this.revokeForm.reason)
          .then(() => {
            this.$modal.msgSuccess("验收单【" + this.revokeForm.code + "】已撤销");
            this.revokeOpen = false;
            this.getPageList();
          })
          .catch(() => {})
          .finally(() => {
            this.revoking = false;
          });
      });
    },
    /** 查看明细（只读） */
    handleView(row, titleSuffix) {
      this.editMode = false;
      this.currentAcceptanceId = row.id;
      this.printRow = row || {};
      this.detailTitle = "验收单明细 - " + row.code + (titleSuffix ? "（" + titleSuffix + "）" : "");
      return this.loadDetail(row.id);
    },
    /** 录入（仅历史维度草稿可编辑） */
    handleEdit(row, titleSuffix) {
      this.editMode = true;
      this.currentAcceptanceId = row.id;
      this.printRow = row || {};
      this.detailTitle = "验收录入 - " + row.code + (titleSuffix ? "（" + titleSuffix + "）" : "");
      this.editForm = { acceptDate: row.acceptDate, remark: row.remark };
      return this.loadDetail(row.id);
    },
    loadDetail(id) {
      return listAcceptanceItems(id).then((response) => {
        this.detailList = (response.data || []).map((item) => ({
          ...item,
          lossReason: item.lossReason || null,
        }));
        // 定位订单高亮（新路径行直带 orderCode；历史路径从来源对照提取）
        if (!this.highlightOrderCodes.length) {
          const codes = [];
          this.detailList.forEach((row2) => {
            (row2.sources || []).forEach((s) => {
              if (this.isHighlightSource(s) && s.orderCode && !codes.includes(s.orderCode)) {
                codes.push(s.orderCode);
              }
            });
          });
          this.highlightOrderCodes = codes;
        }
        this.detailOpen = true;
      });
    },
    /** 行内「打印」：先加载明细再打印 */
    handlePrint(row) {
      this.handleView(row).then(() => {
        this.$nextTick(() => this.doPrint());
      });
    },
    /** 浏览器打印（@media print 只保留 .acceptance-print-area） */
    doPrint() {
      if (!this.detailList.length) return;
      window.print();
    },
    /** 保存录入（仅历史维度草稿；差异原因选填，后端重算金额与类型） */
    submitEditForm() {
      const items = this.detailList.map((row) => ({
        id: row.id,
        actualQuantity: row.actualQuantity,
        lossReason: this.diffOf(row) === 0 ? null : row.lossReason,
      }));
      updateAcceptance({
        id: this.currentAcceptanceId,
        acceptDate: this.editForm.acceptDate,
        remark: this.editForm.remark,
        items: items,
      })
        .then(() => {
          this.$modal.msgSuccess("保存成功");
          this.detailOpen = false;
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 提交（仅历史维度草稿） */
    handleSubmit(row) {
      this.$modal
        .confirm("确认提交验收单【" + row.code + "】？（提交后如需修改请走「撤销」）")
        .then(() => submitAcceptance(row.id))
        .then(() => {
          this.$modal.msgSuccess("提交成功");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 删除（仅历史维度草稿） */
    handleDelete(row) {
      this.$modal
        .confirm('是否确认删除验收单编号为"' + row.code + '"的数据项？')
        .then(() => delAcceptance(row.id))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 高亮判定：来源对照包含定位订单（highlightOrder，历史单） */
    isHighlightSource(source) {
      return this.highlightOrder != null && Number(source.saleOrderId) === this.highlightOrder;
    },
    isHighlightRow(row) {
      if (row.orderCode && this.highlightOrderCodes.includes(row.orderCode)) {
        return true;
      }
      return (row.sources || []).some((s) => this.isHighlightSource(s));
    },
    rowHighlightClass({ row }) {
      return this.isHighlightRow(row) ? "row-highlight-order" : "";
    },
    diffOf(row) {
      return Math.round((Number(row.actualQuantity || 0) - Number(row.deliveredQuantity || 0)) * 100) / 100;
    },
    /** 差异配色：负=短收红、正=超收橙 */
    diffClass(value) {
      if (Number(value) < 0) return "diff-shortfall";
      if (Number(value) > 0) return "diff-overage";
      return "";
    },
    amountOf(row) {
      return Number(row.actualQuantity || 0) * Number(row.unitPrice || 0);
    },
    statusText(status) {
      const hit = (this.dict.type.t_acceptance_status || []).find((d) => Number(d.value) === Number(status));
      return hit ? hit.label : status;
    },
  },
};
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
.detail-total {
  margin-top: 12px;
  text-align: right;
  font-weight: bold;
}
.diff-shortfall {
  color: #f56c6c;
  font-weight: bold;
}
.diff-overage {
  color: #e6a23c;
  font-weight: bold;
}
.reason-none {
  color: #c0c4cc;
}
.reason-tag {
  margin-right: 4px;
}
.highlight-banner {
  margin-bottom: 10px;
}
:deep(.row-highlight-order) {
  background: #fdf6ec;
}
</style>

<!-- 打印样式：必须非 scoped，否则 body * 会被编译为 body[data-v-xxx] *（永不匹配） -->
<style>
.acceptance-print-area {
  display: none;
}
@media print {
  body * {
    visibility: hidden;
  }
  .acceptance-print-area,
  .acceptance-print-area * {
    visibility: visible;
  }
  .acceptance-print-area {
    display: block;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
  }
  .acceptance-print-area .print-title {
    text-align: center;
    margin: 0 0 10px;
  }
  .acceptance-print-area .print-meta {
    display: flex;
    gap: 24px;
    font-size: 13px;
    margin-bottom: 4px;
  }
  .acceptance-print-area .print-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
  }
  .acceptance-print-area .print-table th,
  .acceptance-print-area .print-table td {
    border: 1px solid #333;
    padding: 3px 5px;
    text-align: center;
  }
  .acceptance-print-area .print-table .num {
    text-align: right;
  }
}
</style>
