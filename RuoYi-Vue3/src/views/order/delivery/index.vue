<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
    >
      <el-form-item label="送货单编号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入送货单编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="dict in dict.type.t_delivery_order_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="范围" prop="scopeType">
        <el-select v-model="queryParams.scopeType" placeholder="组单范围" clearable>
          <el-option label="客户日总单" value="CUSTOMER_DATE" />
          <el-option label="每点一单" value="DELIVERY_POINT_DATE" />
        </el-select>
      </el-form-item>
      <el-form-item label="配送日期" prop="deliveryDate">
        <el-date-picker
          clearable
          v-model="queryParams.deliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择配送日期"
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :icon="Search"
          size="small"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button :icon="Refresh" size="small" @click="resetQuery"
          >重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-date-picker
          v-model="generateDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择配送日期"
          size="small"
          style="width: 150px"
        />
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          :icon="Plus"
          size="small"
          @click="handleGenerate"
          v-hasPermi="['order:delivery:add']"
          >生成送货单</el-button
        >
        <span class="generate-hint">先出待生成清单（按客户），确认后才写单</span>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Download"
          size="small"
          @click="handleExport"
          v-hasPermi="['order:delivery:export']"
          >导出</el-button
        >
      </el-col>
      <right-toolbar
        v-model:showSearch="showSearch"
        @queryTable="getPageList"
      ></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="deliveryList"
      :row-class-name="reminderRowClass"
    >
      <el-table-column label="送货单编号" align="center" width="210">
        <template #default="scope">
          <span>{{ scope.row.code }}</span>
          <el-tag
            v-if="scope.row.docKind === 1"
            size="small"
            type="warning"
            effect="plain"
            class="code-tag"
            >补充单</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="客户" align="center" prop="customerName" />
      <el-table-column label="配送点" align="center" prop="customerDeptName">
        <template #default="scope">
          <span v-if="scope.row.customerDeptName">{{ scope.row.customerDeptName }}</span>
          <el-tag v-else-if="scope.row.scopeType === 'CUSTOMER_DATE'" size="small" type="info" effect="plain"
            >跨点总单</el-tag
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column
        label="配送日期"
        align="center"
        prop="deliveryDate"
        width="120"
      >
        <template #default="scope">
          <span>{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <div class="status-cell">
            <el-tooltip
              v-if="scope.row.status === 3 && scope.row.voidReason"
              :content="'作废原因：' + scope.row.voidReason"
              placement="top"
            >
              <span><dict-tag
                :options="dict.type.t_delivery_order_status"
                :value="scope.row.status"
              /></span>
            </el-tooltip>
            <dict-tag
              v-else
              :options="dict.type.t_delivery_order_status"
              :value="scope.row.status"
            />
            <!-- W0-3.2：已送达未验收行追加提醒标记（红=当天11:30后/过期，黄=打印满2h），不取代原状态 -->
            <el-tooltip
              v-if="scope.row.status === 2 && scope.row.reminderLevel"
              :content="'待验收提醒：' + (scope.row.reminderReason || '超时未验收')"
              placement="top"
            >
              <span
                class="reminder-tag"
                :class="scope.row.reminderLevel === 2 ? 'reminder-tag--red' : 'reminder-tag--yellow'"
                >待验收</span
              >
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="打印次数" align="center" prop="printCount" width="90" />
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
        width="300"
      >
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="View"
            @click="handleDetail(scope.row)"
            v-hasPermi="['order:delivery:query']"
            >明细</el-button
          >
          <el-button
            size="small"
            link
            :icon="Document"
            @click="handleSources(scope.row)"
            v-hasPermi="['order:delivery:query']"
            >来源</el-button
          >
          <el-button
            size="small"
            link
            :icon="Printer"
            :disabled="scope.row.status === 2 || scope.row.status === 3"
            @click="handlePrint(scope.row)"
            v-hasPermi="['order:delivery:print']"
            >打印</el-button
          >
          <el-button
            v-if="scope.row.status === 0 || scope.row.status === 1"
            size="small"
            link
            :icon="Van"
            @click="handleDeliver(scope.row)"
            v-hasPermi="['order:delivery:deliver']"
            >送达</el-button
          >
          <el-button
            v-if="scope.row.status === 0 || scope.row.status === 1"
            size="small"
            link
            type="danger"
            :icon="CircleClose"
            @click="handleVoid(scope.row)"
            v-hasPermi="['order:delivery:void']"
            >作废</el-button
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

    <!-- 送货单明细对话框（按商品合并行） -->
    <el-dialog align-center :title="detailTitle" v-model="detailOpen" width="760px" append-to-body>
      <el-table :data="detailList" size="small" border>
        <el-table-column label="商品名称" align="center" prop="productName" />
        <el-table-column label="单位" align="center" prop="productUnit" width="80" />
        <el-table-column label="规格" align="center" prop="productSpec" width="140" />
        <el-table-column label="送货数量" align="center" prop="num" width="100" />
        <el-table-column label="单价" align="center" prop="price" width="100" />
        <el-table-column label="小计" align="center" prop="amount" width="120" />
      </el-table>
      <div class="detail-total">合计：{{ detailTotal }}</div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 来源明细对话框（S14：聚合行 + 展开来源订单/配送点/分配量；历史单显示"—历史数据—"） -->
    <el-dialog align-center :title="sourcesTitle" v-model="sourcesOpen" width="860px" append-to-body>
      <el-table :data="sourcesList" size="small" border row-key="deliveryDetailId">
        <el-table-column type="expand">
          <template #default="scope">
            <div class="sources-expand">
              <template v-if="scope.row.sources && scope.row.sources.length">
                <el-table :data="scope.row.sources" size="small" border>
                  <el-table-column label="来源订单号" align="center" prop="orderCode" min-width="130" />
                  <el-table-column label="配送点" align="center" prop="customerDeptName" min-width="120" />
                  <el-table-column label="分配数量" align="center" prop="allocatedQuantity" width="100" />
                  <el-table-column label="下单单价" align="center" prop="unitPrice" width="100" />
                </el-table>
              </template>
              <span v-else class="sources-empty">—历史数据—（该单生成于来源台账上线前，无来源明细）</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品名称" align="center" prop="productName" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" width="120" />
        <el-table-column label="单位" align="center" prop="productUnit" width="70" />
        <el-table-column label="送货数量" align="center" prop="num" width="100" />
        <el-table-column label="单价" align="center" prop="price" width="90" />
        <el-table-column label="小计" align="center" prop="amount" width="110" />
        <el-table-column label="来源行数" align="center" width="90">
          <template #default="scope">
            <span v-if="scope.row.sources && scope.row.sources.length">{{ scope.row.sources.length }}</span>
            <span v-else class="sources-empty">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="sourcesOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 标记送达对话框：未打印（PENDING）单送达必选免纸原因（D-018） -->
    <el-dialog align-center title="标记送达" v-model="deliverOpen" width="480px" append-to-body>
      <el-form ref="deliverFormRef" :model="deliverForm" :rules="deliverRules" label-width="90px">
        <el-form-item label="送货单">
          <span>{{ deliverRow.code }}</span>
        </el-form-item>
        <template v-if="deliverRow.status === 0">
          <el-form-item label="免纸原因" prop="reasonCode">
            <el-select v-model="deliverForm.reasonCode" placeholder="该单未打印，请选择免纸送达原因" style="width: 100%">
              <el-option
                v-for="d in dict.type.delivery_no_print_reason"
                :key="d.value"
                :label="d.label"
                :value="d.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="deliverForm.reasonCode === 'other'"
            label="补充说明"
            prop="remark"
          >
            <el-input v-model="deliverForm.remark" type="textarea" placeholder="选择“其他”时必填" />
          </el-form-item>
          <div class="dialog-hint">未打印单送达必须登记免纸原因；已打印单送达无需登记。</div>
        </template>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="已打印单标记送达，无需登记原因；仅来源台账命中的订单会进入已配送状态"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="deliverSubmitting" @click="submitDeliver">确认送达</el-button>
          <el-button @click="deliverOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 作废对话框：原因必填（字典），other 必填说明；可选作废后立即重新生成 -->
    <el-dialog align-center title="作废送货单" v-model="voidOpen" width="500px" append-to-body>
      <el-form ref="voidFormRef" :model="voidForm" :rules="voidRules" label-width="90px">
        <el-form-item label="送货单">
          <span>{{ voidRow.code }}</span>
        </el-form-item>
        <el-form-item label="作废原因" prop="reasonCode">
          <el-select v-model="voidForm.reasonCode" placeholder="请选择作废原因" style="width: 100%">
            <el-option
              v-for="d in dict.type.delivery_void_reason"
              :key="d.value"
              :label="d.label"
              :value="d.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="voidForm.reasonCode === 'other'"
          label="补充说明"
          prop="reasonNote"
        >
          <el-input v-model="voidForm.reasonNote" type="textarea" placeholder="选择“其他”时必填" />
        </el-form-item>
        <el-form-item label="重新生成">
          <el-checkbox v-model="voidForm.regenerate">作废后立即按客户+日期重新生成（遗漏订单补齐）</el-checkbox>
        </el-form-item>
        <div class="dialog-hint">
          作废后来源分配即释放，订单回到"未进送货单"状态（可正常编辑/撤回）；已提交验收或来源订单已结算时后端将拒绝。
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="danger" :loading="voidSubmitting" @click="submitVoid">确认作废</el-button>
          <el-button @click="voidOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 生成送货单·待生成清单确认（D-039 三步式：选日期 → 客户维度清单 → 确认） -->
    <generate-preview-drawer
      v-model="generatePreviewOpen"
      :delivery-date="generateDate"
      @success="getPageList"
    />

    <!-- 打印对话框（W0-4.4：强制预览→确认→打印→回执；失败不增加成功打印次数） -->
    <el-dialog align-center title="打印送货单" v-model="printOpen" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="送货单">
          <span>{{ printRow.code }}</span>
        </el-form-item>
        <el-form-item label="打印模板">
          <el-radio-group v-model="printChosenRecordId">
            <div v-for="t in printTemplates" :key="t.id" style="display: block; margin-bottom: 6px">
              <el-radio :value="t.id">
                {{ t.name }}（联数 {{ t.copies }}）
                <el-tag v-if="t.id === printInfo.templateRecordId" size="small" type="success" style="margin-left: 4px">默认</el-tag>
              </el-radio>
            </div>
          </el-radio-group>
          <div class="dialog-hint">可切换为其他已发布模板，仅对本次打印生效；份数可在打印窗口调整。</div>
        </el-form-item>
        <el-form-item label="打印步骤">
          <el-steps :active="printPreviewed ? 1 : 0" simple style="width: 100%">
            <el-step title="预览确认" />
            <el-step title="正式打印" />
            <el-step title="结果回执" />
          </el-steps>
        </el-form-item>
        <div class="dialog-hint" style="padding: 0 12px 8px">
          正式打印前必须预览（系统记录预览留痕）；打印失败不会记录打印次数，可修复后重试。
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="warning" plain @click="openPrintPreview">打开预览</el-button>
          <el-button type="primary" :disabled="!printPreviewed" @click="confirmPrint">确认打印</el-button>
          <el-button @click="printOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageDelivery,
  listDeliveryDetail,
  listDeliverySources,
  generateDeliveryForCustomer,
  printDelivery,
  printInfoDelivery,
  deliveredDelivery,
  voidDelivery,
} from "@/api/order/delivery";
import GeneratePreviewDrawer from "./generatePreviewDrawer.vue";
import { issuePrintTicket } from "@/api/print/ticket";
import { listPrintTemplate, recordPrintPreview } from "@/api/print/template";
import {
  Search,
  Refresh,
  Plus,
  Download,
  Printer,
  Van,
  View,
  Document,
  CircleClose,
} from "@element-plus/icons-vue";

export default {
  name: "Delivery",
  components: { GeneratePreviewDrawer },
  dicts: ["t_delivery_order_status", "delivery_no_print_reason", "delivery_void_reason"],
  setup() {
    return { Search, Refresh, Plus, Download, Printer, Van, View, Document, CircleClose };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 送货单表格数据
      deliveryList: [],
      // 生成送货单的配送日期
      generateDate: null,
      // 待生成清单确认抽屉（D-039）
      generatePreviewOpen: false,
      // 明细对话框
      detailOpen: false,
      detailTitle: "",
      detailList: [],
      detailTotal: "0.00",
      // 来源明细对话框
      sourcesOpen: false,
      sourcesTitle: "",
      sourcesList: [],
      // 送达对话框
      deliverOpen: false,
      deliverSubmitting: false,
      deliverRow: {},
      deliverForm: { reasonCode: null, remark: null },
      deliverRules: {
        reasonCode: [{ required: true, message: "免纸送达原因不能为空", trigger: "change" }],
        remark: [{ required: true, message: "选择“其他”时必须填写说明", trigger: "blur" }],
      },
      // 作废对话框
      voidOpen: false,
      voidSubmitting: false,
      voidRow: {},
      voidForm: { reasonCode: null, reasonNote: null, regenerate: false },
      voidRules: {
        reasonCode: [{ required: true, message: "作废原因不能为空", trigger: "change" }],
        reasonNote: [{ required: true, message: "选择“其他”时必须填写说明", trigger: "blur" }],
      },
      // 打印对话框（W0-4.4：强制预览→确认→打印→回执）
      printOpen: false,
      printRow: {},
      printInfo: {},
      printTemplates: [],
      printChosenRecordId: null,
      printPreviewed: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
        scopeType: null,
        deliveryDate: null,
      },
    };
  },
  created() {
    // S2-2.1 待办链跳转支持：/order/delivery?status=0/1 预置状态筛选（保持字符串与字典值匹配）
    const routeStatus = this.$route.query.status;
    if (routeStatus !== undefined && routeStatus !== null && routeStatus !== "") {
      this.queryParams.status = String(routeStatus);
    }
    // 客户管理页「送货单」抽屉跳转带日期：列表筛选与生成日期同步预置
    const routeDate = this.$route.query.deliveryDate;
    if (routeDate) {
      this.queryParams.deliveryDate = String(routeDate);
      this.generateDate = String(routeDate);
    }
    this.getPageList();
  },
  methods: {
    /** W0-3.2：已送达未验收行按提醒级别标行色 */
    reminderRowClass({ row }) {
      if (row.status === 2 && row.reminderLevel === 2) {
        return "row-reminder-red";
      }
      if (row.status === 2 && row.reminderLevel === 1) {
        return "row-reminder-yellow";
      }
      return "";
    },
    /** 分页查询送货单列表 */
    getPageList() {
      this.loading = true;
      pageDelivery(this.queryParams).then((response) => {
        this.deliveryList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    /**
     * 生成送货单（D-039 三步式）：先拉客户维度待生成清单预览，确认后才调统一生成服务。
     * 幂等与三态分支（跨点总单/每点一单、作废重建 D-022、补充单 D-023）在预览里逐客户如实展示。
     */
    handleGenerate() {
      if (!this.generateDate) {
        this.$modal.msgWarning("请先选择配送日期");
        return;
      }
      this.generatePreviewOpen = true;
    },
    /** 查看明细 */
    handleDetail(row) {
      listDeliveryDetail(row.id).then((response) => {
        this.detailList = response.data || [];
        this.detailTitle = "送货单明细 - " + row.code;
        this.detailOpen = true;
        const total = this.detailList.reduce((sum, item) => {
          return sum + (Number(item.amount) || 0);
        }, 0);
        this.detailTotal = total.toFixed(2);
      });
    },
    /** 来源明细（聚合行 + 展开的来源订单/配送点/分配量） */
    handleSources(row) {
      listDeliverySources(row.id).then((response) => {
        this.sourcesList = response.data || [];
        this.sourcesTitle = "来源明细 - " + row.code;
        this.sourcesOpen = true;
      });
    },
    /** 打印（W0-4.4 闭环）：解析模板 → 强制预览（留痕）→ 确认 → 打开打印视图 → 回执（成功才计次） */
    handlePrint(row) {
      printInfoDelivery(row.id)
        .then((response) => {
          this.printRow = row;
          this.printInfo = response.data;
          this.printChosenRecordId = response.data.templateRecordId;
          this.printPreviewed = false;
          this.printOpen = true;
          this.loadPrintTemplates();
        })
        .catch(() => {});
    },
    /** 可切换模板：已发布送货单模板，前端按三级绑定过滤（与后端 selectBindTemplate 同口径） */
    loadPrintTemplates() {
      listPrintTemplate({ type: 0, status: 2 })
        .then((response) => {
          const info = this.printInfo;
          const list = (response.data || []).filter(
            (t) =>
              (t.bindType === 1 && t.customerId === info.customerId && t.deliveryPointId === info.deliveryPointId) ||
              (t.bindType === 2 && t.customerId === info.customerId) ||
              (t.bindType === 3 && t.isDefault === "1")
          );
          // 兑底：解析模板不在过滤结果中（如历史数据）仍可选
          if (info.templateRecordId && !list.some((t) => t.id === info.templateRecordId)) {
            list.unshift({
              id: info.templateRecordId,
              name: info.templateName,
              content: info.templateId,
              copies: info.copies,
            });
          }
          this.printTemplates = list;
        })
        .catch(() => {});
    },
    chosenPrintTemplate() {
      return this.printTemplates.find((t) => t.id === this.printChosenRecordId);
    },
    /** 打开预览窗口并记录预览留痕（W0-4.4：每次正式打印前必须有预览记录） */
    openPrintPreview() {
      const template = this.chosenPrintTemplate();
      if (!template) {
        this.$modal.msgWarning("请先选择打印模板");
        return;
      }
      issuePrintTicket({ deliveryOrderId: this.printRow.id, templateId: template.content }).then((res) => {
        window.open(
          "/jmreport/view/" + template.content + "?token=" + res.ticket + "&deliveryOrderId=" + this.printRow.id,
          "_blank"
        );
        // 预览留痕（模板主键 + 送货单）
        recordPrintPreview(template.id, this.printRow.id).catch(() => {});
        this.printPreviewed = true;
        this.$modal.msgSuccess("预览已打开，请核对版式与数据后点「确认打印」");
      });
    },
    /** 确认打印：打开打印视图，回执确认后成功才记录打印次数（失败不计数） */
    confirmPrint() {
      const template = this.chosenPrintTemplate();
      const row = this.printRow;
      if (!template) {
        this.$modal.msgWarning("请先选择打印模板");
        return;
      }
      issuePrintTicket({ deliveryOrderId: row.id, templateId: template.content }).then((res) => {
        this.printOpen = false;
        window.open(
          "/jmreport/view/" + template.content + "?token=" + res.ticket + "&deliveryOrderId=" + row.id,
          "_blank"
        );
        this.$modal
          .confirm(
            "请在打印窗口完成打印。\n本次打印是否成功？\n· 成功：记录打印次数并推进单据状态\n· 失败：不记录次数，可修复后重新打印"
          )
          .then(() => printDelivery(row.id))
          .then((printResp) => {
            this.$modal.msgSuccess("打印成功已记录，当前打印次数 " + printResp.data.printCount);
            this.getPageList();
          })
          .catch(() => {
            this.$modal.msgWarning("本次打印未记录次数（失败或放弃），请排查后重新打印");
          });
      });
    },
    /** 标记送达：PENDING 弹免纸原因（D-018），PRINTED 直接确认 */
    handleDeliver(row) {
      this.deliverRow = row;
      this.deliverForm = { reasonCode: null, remark: null };
      this.deliverOpen = true;
      this.$nextTick(() => {
        this.$refs["deliverFormRef"] && this.$refs["deliverFormRef"].clearValidate();
      });
    },
    /** 提交送达 */
    submitDeliver() {
      if (this.deliverRow.status === 0) {
        this.$refs["deliverFormRef"].validate((valid) => {
          if (!valid) return;
          this.doDeliver({
            noPrint: {
              reasonCode: this.deliverForm.reasonCode,
              remark: this.deliverForm.remark || null,
            },
          });
        });
      } else {
        this.doDeliver(undefined);
      }
    },
    doDeliver(body) {
      this.deliverSubmitting = true;
      deliveredDelivery(this.deliverRow.id, body)
        .then(() => {
          this.$modal.msgSuccess("已标记送达");
          this.deliverOpen = false;
          this.getPageList();
        })
        .catch(() => {})
        .finally(() => {
          this.deliverSubmitting = false;
        });
    },
    /** 作废送货单（PENDING/PRINTED；原因必填；可选作废后重新生成） */
    handleVoid(row) {
      this.voidRow = row;
      this.voidForm = { reasonCode: null, reasonNote: null, regenerate: false };
      this.voidOpen = true;
      this.$nextTick(() => {
        this.$refs["voidFormRef"] && this.$refs["voidFormRef"].clearValidate();
      });
    },
    /** 提交作废 */
    submitVoid() {
      this.$refs["voidFormRef"].validate((valid) => {
        if (!valid) return;
        this.voidSubmitting = true;
        voidDelivery(this.voidRow.id, {
          reasonCode: this.voidForm.reasonCode,
          reasonNote: this.voidForm.reasonNote || null,
        })
          .then(() => {
            if (this.voidForm.regenerate) {
              return generateDeliveryForCustomer(
                this.voidRow.customerId,
                this.voidRow.deliveryDate
              ).then((resp) => {
                const result = resp.data || {};
                const created = result.createdOrders || [];
                this.$modal.msgSuccess(
                  "已作废并重新生成 " + created.length + " 张送货单（原纸面单号已失效）"
                );
              });
            }
            this.$modal.msgSuccess("已作废，来源订单已释放");
          })
          .then(() => {
            this.voidOpen = false;
            this.getPageList();
          })
          .catch(() => {})
          .finally(() => {
            this.voidSubmitting = false;
          });
      });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download(
        "order/delivery/export",
        {
          ...this.queryParams,
        },
        `delivery_${new Date().getTime()}.xlsx`
      );
    },
  },
};
</script>

<style scoped>
.detail-total {
  margin-top: 12px;
  text-align: right;
  font-weight: bold;
}
.code-tag {
  margin-left: 4px;
}
.sources-expand {
  padding: 6px 24px;
}
.sources-empty {
  color: #c0c4cc;
  font-size: 12px;
}
.dialog-hint {
  margin: 4px 0 0 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.generate-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
/* W0-3.2 待验收提醒行标色（el-table row-class-name） */
:deep(.row-reminder-red) td.el-table__cell {
  background-color: #fef0f0 !important;
}
:deep(.row-reminder-yellow) td.el-table__cell {
  background-color: #fdf6ec !important;
}
.reminder-tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}
/* 状态标签 + 提醒标签竖排，避免撑破状态列 */
.status-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.status-cell .reminder-tag {
  cursor: default;
}
.reminder-tag--red {
  background-color: #f56c6c;
}
.reminder-tag--yellow {
  background-color: #e6a23c;
}
</style>
