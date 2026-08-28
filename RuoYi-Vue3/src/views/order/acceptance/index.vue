<template>
  <div class="app-container">
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
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
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
          @click="handleAdd"
          v-hasPermi="['acceptance:add']"
          >新增验收单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          :icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['acceptance:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="acceptanceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="验收单号" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="送货单号" align="center" prop="deliveryCode" :show-overflow-tooltip="true" />
      <el-table-column label="客户" align="center" prop="customerName" />
      <el-table-column label="配送点" align="center" prop="customerDeptName" />
      <el-table-column label="验收日期" align="center" prop="acceptDate" width="120" />
      <el-table-column label="验收总额" align="center" prop="totalAmount" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="dict.type.t_acceptance_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="250">
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
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 新增验收单：选择已送达送货单（一单一验） -->
    <el-dialog align-center :title="addTitle" v-model="addOpen" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="送货单">
          <el-select v-model="addForm.deliveryOrderId" placeholder="请选择已送达的送货单" filterable style="width: 100%">
            <el-option
              v-for="d in deliveryOptions"
              :key="d.id"
              :label="d.code + '（' + (d.customerName || d.customerId) + ' / ' + d.customerDeptName + ' / ' + d.deliveryDate + '）'"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <div class="hint">仅列出"已送达"且未生成过验收单的送货单（一单一验）。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAddForm">确 定</el-button>
          <el-button @click="addOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 撤销验收弹窗（S14/D-014：SUBMITTED→DRAFT，原因必填，来源订单回退已配送，完整审计快照） -->
    <el-dialog align-center title="撤销验收单" v-model="revokeOpen" width="480px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="撤销后验收单回到草稿、来源订单回退「已配送」；撤销前将保留完整审计快照，任一来源订单已月结则无法撤销"
        style="margin-bottom: 12px"
      />
      <el-form ref="revokeFormRef" :model="revokeForm" :rules="revokeRules" label-width="80px">
        <el-form-item label="撤销原因" prop="reason">
          <el-input
            v-model="revokeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入撤销原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="revoking" @click="submitRevoke">确认撤销</el-button>
          <el-button @click="revokeOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 验收明细对话框（查看/录入共用；A类总单按「商品组→各点行」分组展示，S14 §八） -->
    <el-dialog align-center :title="detailTitle" v-model="detailOpen" width="1080px" append-to-body class="acceptance-dialog">
      <template #header>
        <div class="detail-dialog-header">
          <span class="detail-dialog-title">{{ detailTitle }}</span>
          <el-button
            v-if="editMode && detailList.length"
            type="primary"
            plain
            size="small"
            :icon="Iphone"
            @click="toggleMobileMode"
            >{{ mobileMode ? '退出移动模式' : '移动模式' }}</el-button
          >
        </div>
      </template>

      <!-- 订单定位提示（highlightOrder：来自订单页「去验收」跳转） -->
      <el-alert
        v-if="highlightOrder && highlightOrderCodes.length"
        type="warning"
        :closable="true"
        show-icon
        class="highlight-banner"
        :title="'黄色高亮行 = 来源订单 ' + highlightOrderCodes.join('、') + ' 贡献的明细'"
      />

      <!-- 标准模式（商品分组录入/查看） -->
      <template v-if="!mobileMode">
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

        <div class="group-scroll">
          <div
            v-for="(group, gi) in productGroups"
            :key="gi"
            class="product-group"
            :class="{ 'product-group-highlight': group.highlight }"
          >
            <!-- 组头：商品汇总（只读） -->
            <div class="group-header">
              <span class="group-name">
                <el-icon v-if="group.highlight" class="group-flag"><Aim /></el-icon>
                {{ group.productName }}
              </span>
              <el-tag v-if="group.productSpec" size="small" type="info">{{ group.productSpec }}</el-tag>
              <span class="group-unit">{{ group.productUnit }}</span>
              <span class="group-meta">
                应送 <b>{{ group.deliveredTotal }}</b>
                <template v-if="editMode">· 实收 <b>{{ group.actualTotal }}</b></template>
                · 差异
                <b :class="diffClass(group.diffTotal)">{{ group.diffTotal }}</b>
              </span>
            </div>
            <!-- 成员：各配送点行（录入时实收/原因可编辑） -->
            <el-table :data="group.items" size="small" border :row-class-name="rowHighlightClass">
              <el-table-column
                v-if="hasDeptInfo"
                label="配送点"
                align="center"
                prop="customerDeptName"
                width="120"
                :show-overflow-tooltip="true"
              />
              <el-table-column label="送货数量" align="center" prop="deliveredQuantity" width="90" />
              <el-table-column label="实收数量" align="center" width="140">
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
              <el-table-column label="单价" align="center" prop="unitPrice" width="80" />
              <el-table-column label="验收差异" align="center" width="90">
                <template #default="scope">
                  <span :class="diffClass(diffOf(scope.row))">{{ diffOf(scope.row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="差异原因（双向必填）" align="center" min-width="170">
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
              <el-table-column label="来源对照" align="center" min-width="180">
                <template #default="scope">
                  <template v-if="scope.row.sources && scope.row.sources.length">
                    <div
                      v-for="(s, si) in scope.row.sources"
                      :key="si"
                      class="source-line"
                      :class="{ 'source-line-hit': isHighlightSource(s) }"
                    >
                      <span class="source-code">{{ s.orderCode || s.saleOrderId }}</span>
                      <span class="source-qty">{{ s.allocatedQuantity }}×{{ s.unitPrice }}</span>
                    </div>
                  </template>
                  <span v-else class="reason-none">—历史数据—</span>
                </template>
              </el-table-column>
              <el-table-column label="实收金额" align="center" width="100">
                <template #default="scope">{{ amountOf(scope.row).toFixed(2) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
        <div class="detail-total">合计：{{ totalAmount.toFixed(2) }}</div>
      </template>

      <!-- 移动模式：仅商品名称+实收数量大输入，每屏 3 条，扫码枪连续扫码定位并累加 -->
      <template v-else>
        <div class="mobile-scan-bar">
          <el-input
            ref="scanInput"
            v-model="scanCode"
            placeholder="扫码枪扫码定位商品（回车确认，支持 码*数量）"
            clearable
            size="large"
            @keyup.enter="handleScan"
          >
            <template #append>
              <el-button :icon="Search" @click="handleScan">定位</el-button>
            </template>
          </el-input>
        </div>
        <div class="mobile-list">
          <div
            v-for="item in mobilePageItems"
            :key="item.id"
            class="mobile-card"
            :class="{
              'mobile-card-active': item.id === mobileActiveId,
              'mobile-card-ok': Number(item.actualQuantity) >= Number(item.deliveredQuantity),
            }"
          >
            <div class="mobile-card-name">
              <span class="mobile-card-name-text">{{ item.productName }}</span>
              <el-tag v-if="item.customerDeptName" size="small" type="info">{{ item.customerDeptName }}</el-tag>
              <el-tag v-if="item.productSpec" size="small" type="info">{{ item.productSpec }}</el-tag>
              <span class="mobile-card-unit">{{ item.productUnit }}</span>
            </div>
            <div class="mobile-card-qty">
              <span class="mobile-qty-label">实收</span>
              <el-input-number
                v-model="item.actualQuantity"
                :min="0"
                :precision="2"
                :controls="false"
                class="mobile-qty-input"
                size="large"
              />
              <span class="mobile-qty-unit">{{ item.productUnit }}</span>
              <span class="mobile-qty-deliver">送货 {{ item.deliveredQuantity }}</span>
            </div>
          </div>
          <div v-if="!mobilePageItems.length" class="mobile-empty">本页暂无商品</div>
        </div>
        <div class="mobile-pager">
          <el-button size="small" :icon="ArrowLeft" :disabled="mobilePage <= 0" @click="mobilePage--">上一页</el-button>
          <span class="mobile-page-info">{{ mobilePage + 1 }} / {{ mobileTotalPages }}</span>
          <el-button size="small" :disabled="mobilePage >= mobileTotalPages - 1" @click="mobilePage++">下一页</el-button>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="editMode" type="primary" @click="submitEditForm">保 存</el-button>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageAcceptance,
  listAcceptanceItems,
  createAcceptance,
  updateAcceptance,
  submitAcceptance,
  revokeAcceptance,
  delAcceptance,
} from "@/api/acceptance/acceptance";
import { listDelivery } from "@/api/order/delivery";
import { Search, Refresh, Plus, Delete, Edit, Check, View, Iphone, ArrowLeft, RefreshLeft, Aim } from "@element-plus/icons-vue";

export default {
  name: "Acceptance",
  dicts: ["t_acceptance_status"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Check, View, Iphone, ArrowLeft, RefreshLeft, Aim };
  },
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      acceptanceList: [],
      // 选中数组
      ids: [],
      multiple: true,
      // 新增对话框
      addOpen: false,
      addTitle: "",
      addForm: { deliveryOrderId: null },
      deliveryOptions: [],
      // 撤销验收弹窗（S14/D-014）
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
      // 订单定位高亮（订单页「去验收」跳转携带）
      highlightOrder: null,
      highlightOrderCodes: [],
      // 移动模式（验收录入，仅改布局不改保存逻辑）
      mobileMode: false,
      mobilePage: 0,
      mobilePageSize: 3,
      scanCode: "",
      mobileActiveId: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
      },
    };
  },
  computed: {
    totalAmount() {
      return this.detailList.reduce((sum, row) => sum + this.amountOf(row), 0);
    },
    /** 明细是否带配送点信息（A类点级展开/B/C类回填都有；历史单可能为空） */
    hasDeptInfo() {
      return this.detailList.some((row) => row.customerDeptId != null);
    },
    /** 商品分组（S14 §八：组头=商品汇总只读，成员=各点行可编辑实收） */
    productGroups() {
      const map = new Map();
      this.detailList.forEach((row) => {
        const key = (row.skuId || row.productName) + "|" + (row.productSpec || "");
        if (!map.has(key)) {
          map.set(key, {
            productName: row.productName,
            productSpec: row.productSpec,
            productUnit: row.productUnit,
            items: [],
            highlight: false,
          });
        }
        const group = map.get(key);
        group.items.push(row);
        if (this.isHighlightRow(row)) {
          group.highlight = true;
        }
      });
      return Array.from(map.values()).map((g) => ({
        ...g,
        deliveredTotal: g.items.reduce((s, r) => s + Number(r.deliveredQuantity || 0), 0),
        actualTotal: g.items.reduce((s, r) => s + Number(r.actualQuantity || 0), 0),
        diffTotal: g.items.reduce((s, r) => s + this.diffOf(r), 0),
      }));
    },
    /** 移动模式总页数 */
    mobileTotalPages() {
      return Math.max(1, Math.ceil(this.detailList.length / this.mobilePageSize));
    },
    /** 当前移动模式页显示的商品（每屏 3 条） */
    mobilePageItems() {
      const start = this.mobilePage * this.mobilePageSize;
      return this.detailList.slice(start, start + this.mobilePageSize);
    },
  },
  created() {
    this.getPageList();
    // 订单页「去验收」跳转：acceptanceId 直接打开 / create=1 引导建草稿（S14 §6.1）
    const { acceptanceId, deliveryId, create, highlightOrder } = this.$route.query;
    if (highlightOrder) {
      this.highlightOrder = Number(highlightOrder);
    }
    if (acceptanceId) {
      this.openById(Number(acceptanceId), highlightOrder ? "去验收" : undefined);
    } else if (create && deliveryId) {
      this.createForDelivery(Number(deliveryId), true);
    }
  },
  methods: {
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
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
    },
    /** 按验收单ID直接打开（订单页「去验收」跳转定位） */
    openById(id, titleSuffix) {
      getAcceptance(id).then((response) => {
        const acc = response.data || {};
        if (acc.status === 0) {
          this.handleEdit(acc, titleSuffix);
        } else {
          this.handleView(acc, titleSuffix);
        }
      });
    },
    /** 为送货单创建验收草稿并直接进入录入（无验收单引导创建） */
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
    /** 新增：选择已送达送货单 */
    handleAdd() {
      this.addForm = { deliveryOrderId: null };
      this.addTitle = "新增验收单";
      listDelivery({ status: 2 }).then((response) => {
        this.deliveryOptions = response.data || [];
        if (this.deliveryOptions.length === 0) {
          this.$modal.msgWarning("暂无已送达的送货单");
          return;
        }
        this.addOpen = true;
      });
    },
    submitAddForm() {
      if (!this.addForm.deliveryOrderId) {
        this.$modal.msgWarning("请选择送货单");
        return;
      }
      this.createForDelivery(this.addForm.deliveryOrderId).then(() => {
        this.addOpen = false;
        this.getPageList();
      });
    },
    /** 撤销验收（S14/D-014：仅已提交可撤销，原因必填，任一来源订单 SETTLED 由后端拒绝） */
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
            this.$modal.msgSuccess("验收单【" + this.revokeForm.code + "】已撤销，来源订单回退「已配送」");
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
      this.detailTitle = "验收单明细 - " + row.code + (titleSuffix ? "（" + titleSuffix + "）" : "");
      this.loadDetail(row.id);
    },
    /** 录入（草稿可编辑） */
    handleEdit(row, titleSuffix) {
      this.editMode = true;
      this.currentAcceptanceId = row.id;
      this.detailTitle = "验收录入 - " + row.code + (titleSuffix ? "（" + titleSuffix + "）" : "");
      this.editForm = { acceptDate: row.acceptDate, remark: row.remark };
      this.loadDetail(row.id);
    },
    loadDetail(id) {
      listAcceptanceItems(id).then((response) => {
        this.detailList = (response.data || []).map((item) => ({
          ...item,
          lossReason: item.lossReason || null,
        }));
        // 定位订单号 → 来源订单号文本（highlightOrder 提示条用）
        const codes = [];
        this.detailList.forEach((row) => {
          (row.sources || []).forEach((s) => {
            if (this.isHighlightSource(s) && s.orderCode && !codes.includes(s.orderCode)) {
              codes.push(s.orderCode);
            }
          });
        });
        this.highlightOrderCodes = codes;
        this.resetMobileState();
        this.detailOpen = true;
      });
    },
    /** 高亮判定：来源对照包含定位订单（highlightOrder） */
    isHighlightSource(source) {
      return this.highlightOrder != null && Number(source.saleOrderId) === this.highlightOrder;
    },
    isHighlightRow(row) {
      return (row.sources || []).some((s) => this.isHighlightSource(s));
    },
    rowHighlightClass({ row }) {
      return this.isHighlightRow(row) ? "row-highlight-order" : "";
    },
    diffOf(row) {
      return Math.round((Number(row.actualQuantity || 0) - Number(row.deliveredQuantity || 0)) * 100) / 100;
    },
    /** 差异配色：负=短收红、正=超收橙（S14 §八） */
    diffClass(value) {
      if (Number(value) < 0) return "diff-shortfall";
      if (Number(value) > 0) return "diff-overage";
      return "";
    },
    amountOf(row) {
      return Number(row.actualQuantity || 0) * Number(row.unitPrice || 0);
    },
    /** 重置移动模式状态 */
    resetMobileState() {
      this.mobileMode = false;
      this.mobilePage = 0;
      this.scanCode = "";
      this.mobileActiveId = null;
    },
    /** 切换移动模式 */
    toggleMobileMode() {
      this.mobileMode = !this.mobileMode;
      this.mobilePage = 0;
      this.scanCode = "";
      this.mobileActiveId = null;
      if (this.mobileMode) {
        this.$nextTick(() => this.focusScanInput());
      }
    },
    /** 聚焦扫码输入框（保持连续扫码） */
    focusScanInput() {
      const inp = this.$refs.scanInput;
      if (inp && inp.focus) {
        inp.focus();
      }
    },
    /**
     * 扫码处理：按商品编码/SKU ID/名称定位商品，并累加实收数量。
     * 支持 "码*数量"（如 S00000003*5），默认每次 +1。
     */
    handleScan() {
      const raw = String(this.scanCode || "").trim();
      if (!raw) return;
      let code = raw;
      let qty = 1;
      const starIdx = raw.indexOf("*");
      if (starIdx > -1) {
        code = raw.slice(0, starIdx).trim();
        const q = parseFloat(raw.slice(starIdx + 1));
        if (!isNaN(q) && q > 0) qty = q;
      }
      const idx = this.detailList.findIndex(
        (item) =>
          item.skuCode === code ||
          String(item.skuId) === code ||
          item.productName === code
      );
      if (idx === -1) {
        this.$modal.msgWarning("未找到商品：" + code);
        return;
      }
      const item = this.detailList[idx];
      item.actualQuantity =
        Math.round((Number(item.actualQuantity || 0) + qty) * 100) / 100;
      // 跳转到该商品所在页并高亮
      this.mobilePage = Math.floor(idx / this.mobilePageSize);
      this.mobileActiveId = item.id;
      // 清空输入并重新聚焦（连续扫码）
      this.scanCode = "";
      this.$nextTick(() => this.focusScanInput());
    },
    /** 保存录入（差异≠0 必填原因前端预检，后端双向校验兜底） */
    submitEditForm() {
      const missing = this.detailList.filter(
        (row) => this.diffOf(row) !== 0 && !String(row.lossReason || "").trim()
      );
      if (missing.length) {
        const name = missing[0].productName + (missing[0].customerDeptName ? "（" + missing[0].customerDeptName + "）" : "");
        this.$modal.msgError(
          (this.diffOf(missing[0]) < 0 ? "短收差异必须填写原因：" : "超收差异必须填写原因：") + name
        );
        return;
      }
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
    /** 提交 */
    handleSubmit(row) {
      this.$modal
        .confirm("确认提交验收单【" + row.code + "】？（来源订单进入已验收，验收总额作为结算依据；提交后如需修改请走「撤销」）")
        .then(() => submitAcceptance(row.id))
        .then(() => {
          this.$modal.msgSuccess("提交成功");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 删除 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除验收单编号为"' + (row.code || ids) + '"的数据项？')
        .then(() => delAcceptance(ids))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
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
/* 差异配色：负=短收红、正=超收橙（S14 §八） */
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

/* 商品分组（S14 §八：组头汇总只读 / 成员点行可编辑） */
.group-scroll {
  max-height: 56vh;
  overflow-y: auto;
}
.product-group {
  margin-bottom: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.product-group-highlight {
  border-color: #e6a23c;
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.35);
}
.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
.group-name {
  font-weight: 600;
  color: #303133;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.group-flag {
  color: #e6a23c;
}
.group-unit {
  color: #909399;
  font-size: 12px;
}
.group-meta {
  margin-left: auto;
  font-size: 12px;
  color: #606266;
}
.source-line {
  line-height: 1.5;
  text-align: left;
}
.source-line-hit {
  color: #e6a23c;
  font-weight: 600;
}
.source-code {
  margin-right: 6px;
}
.source-qty {
  color: #909399;
}
/* 高亮订单行 */
:deep(.row-highlight-order) {
  background: #fdf6ec;
}
/* 订单定位提示 */
.highlight-banner {
  margin-bottom: 10px;
}

/* 验收明细对话框头部：标题 + 移动模式切换 */
.detail-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.detail-dialog-title {
  font-weight: 600;
}

/* 移动模式 */
.mobile-scan-bar {
  margin-bottom: 12px;
}
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fafafa;
  transition: box-shadow 0.2s, border-color 0.2s, background-color 0.2s;
}
.mobile-card-active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  background: #ecf5ff;
}
.mobile-card-ok {
  border-color: #67c23a;
}
.mobile-card-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 10px;
}
.mobile-card-name-text {
  word-break: break-all;
}
.mobile-card-unit {
  color: #909399;
  font-weight: 400;
  font-size: 13px;
}
.mobile-card-qty {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mobile-qty-label {
  font-size: 14px;
  color: #606266;
}
.mobile-qty-input {
  flex: 1;
  max-width: 220px;
}
.mobile-qty-unit {
  color: #909399;
}
.mobile-qty-deliver {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}
.mobile-empty {
  text-align: center;
  color: #909399;
  padding: 20px 0;
}
.mobile-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}
.mobile-page-info {
  color: #606266;
}
</style>
