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
      <el-form-item label="客户" prop="customerId">
        <el-select v-model="queryParams.customerId" placeholder="请选择客户" filterable clearable style="width: 190px">
          <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
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
      :data="batchList"
      :row-key="rowKey"
      :row-class-name="batchReminderClass"
      @expand-change="handleBatchExpand"
    >
      <!-- D-043：批次两级视图，主行=客户+配送日期=批次，展开看该批次下送货单 -->
      <el-table-column type="expand" width="40">
        <template #default="scope">
          <div class="batch-children">
            <div class="children-title">
              {{ scope.row.customerName }} / {{ scope.row.deliveryDate }} 批次下 {{ scope.row.docCount }} 张送货单
              （已打 {{ scope.row.printedCount }} · 待打 {{ scope.row.pendingCount }} · 已送 {{ scope.row.deliveredCount }}
              <template v-if="scope.row.voidedCount"> · 已作废 {{ scope.row.voidedCount }}</template>）
            </div>
            <el-table
              v-loading="scope.row.__loading"
              :data="scope.row.children || []"
              size="small"
              border
              :row-class-name="reminderRowClass"
            >
              <el-table-column label="送货单编号" align="center" width="200">
                <template #default="c">
                  <span>{{ c.row.code }}</span>
                  <el-tag
                    v-if="c.row.docKind === 1"
                    size="small"
                    type="warning"
                    effect="plain"
                    class="code-tag"
                    >补充单</el-tag
                  >
                </template>
              </el-table-column>
              <el-table-column label="配送点" align="center">
                <template #default="c">
                  <span v-if="c.row.customerDeptName">{{ c.row.customerDeptName }}</span>
                  <el-tag v-else-if="c.row.scopeType === 'CUSTOMER_DATE'" size="small" type="info" effect="plain"
                    >跨点总单</el-tag
                  >
                  <span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" align="center" prop="status" width="110">
                <template #default="c">
                  <div class="status-cell">
                    <el-tooltip
                      v-if="c.row.status === 3 && c.row.voidReason"
                      :content="'作废原因：' + c.row.voidReason"
                      placement="top"
                    >
                      <span><dict-tag
                        :options="dict.type.t_delivery_order_status"
                        :value="c.row.status"
                      /></span>
                    </el-tooltip>
                    <dict-tag
                      v-else
                      :options="dict.type.t_delivery_order_status"
                      :value="c.row.status"
                    />
                    <el-tooltip
                      v-if="c.row.status === 2 && c.row.reminderLevel"
                      :content="'待验收提醒：' + (c.row.reminderReason || '超时未验收')"
                      placement="top"
                    >
                      <span
                        class="reminder-tag"
                        :class="c.row.reminderLevel === 2 ? 'reminder-tag--red' : 'reminder-tag--yellow'"
                        >待验收</span
                      >
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="打印次数" align="center" prop="printCount" width="80" />
              <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
              <el-table-column
                label="操作"
                align="center"
                class-name="small-padding fixed-width"
                width="300"
              >
                <template #default="c">
                  <el-button
                    size="small"
                    link
                    :icon="View"
                    @click="handleDetail(c.row)"
                    v-hasPermi="['order:delivery:query']"
                    >明细</el-button
                  >
                  <el-button
                    size="small"
                    link
                    :icon="Document"
                    @click="handleSources(c.row)"
                    v-hasPermi="['order:delivery:query']"
                    >来源</el-button
                  >
                  <el-button
                    size="small"
                    link
                    :icon="Printer"
                    :disabled="c.row.status === 2 || c.row.status === 3"
                    @click="handlePrint(c.row)"
                    v-hasPermi="['order:delivery:print']"
                    >打印</el-button
                  >
                  <el-button
                    v-if="c.row.status === 0 || c.row.status === 1"
                    size="small"
                    link
                    :icon="Van"
                    @click="handleDeliver(c.row)"
                    v-hasPermi="['order:delivery:deliver']"
                    >送达</el-button
                  >
                  <el-button
                    v-if="c.row.status === 0 || c.row.status === 1"
                    size="small"
                    link
                    type="danger"
                    :icon="CircleClose"
                    @click="handleVoid(c.row)"
                    v-hasPermi="['order:delivery:void']"
                    >作废</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户" align="center" prop="customerName" min-width="140" />
      <el-table-column label="配送日期" align="center" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="形态" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.printForm === 'MATRIX'" size="small" type="info" effect="plain">总单·矩阵</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">每点一单</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="张数" align="center" width="70">
        <template #default="scope">
          <b>{{ scope.row.docCount }}</b>
          <el-tag v-if="scope.row.supplementCount" size="small" type="warning" effect="plain" class="code-tag">
            补{{ scope.row.supplementCount }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="待打/已打/已送" align="center" width="130">
        <template #default="scope">
          <span class="batch-status-count">
            待打 {{ scope.row.pendingCount }} · 已打 {{ scope.row.printedCount }} · 已送
            {{ scope.row.deliveredCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="合计数量" align="center" width="100">
        <template #default="scope">
          <b>{{ scope.row.totalQuantity }}</b>
        </template>
      </el-table-column>
      <el-table-column label="合计金额" align="center" width="100">
        <template #default="scope">
          {{ scope.row.totalAmount }}
        </template>
      </el-table-column>
      <el-table-column label="待验收" align="center" width="90">
        <template #default="scope">
          <el-tag
            v-if="scope.row.maxReminderLevel === 2"
            size="small"
            type="danger"
            effect="plain"
            >待验收</el-tag
          >
          <el-tag
            v-else-if="scope.row.maxReminderLevel === 1"
            size="small"
            type="warning"
            effect="plain"
            >待验收</el-tag
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="总表" align="center" width="120">
        <template #default="scope">
          <el-button
            v-if="scope.row.printForm === 'MATRIX'"
            size="small"
            link
            type="primary"
            :icon="Printer"
            @click="handleMatrixPrint(scope.row)"
            v-hasPermi="['order:delivery:print']"
            >总表</el-button
          >
          <span v-else>—</span>
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
          <span style="margin-left: 8px; color: #909399">作废仅释放历史单据；D-055 后送货单为订单视图，无需重新生成</span>
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
  listDelivery,
  batchPageDelivery,
  listDeliveryDetail,
  listDeliverySources,
  printDelivery,
  printInfoDelivery,
  printCandidatesDelivery,
  deliveredDelivery,
  voidDelivery,
} from "@/api/order/delivery";
import { listCustomer } from "@/api/partner/customer";
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
  components: {},
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
      // 批次聚合主行（D-043：一行=客户+配送日期=批次）
      batchList: [],
      // 送货单表格数据
      deliveryList: [],
      // 客户下拉（D-043 筛选补齐）
      customerOptions: [],
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
      voidForm: { reasonCode: null, reasonNote: null },
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
      // P1/D-048：候选模板是否命中「全局默认」（用于打印对话框告警）
      printMatchGlobalDefault: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        customerId: null,
        status: null,
        scopeType: null,
        deliveryDate: null,
      },
    };
  },
  created() {
    // 客户下拉（D-043 筛选）
    listCustomer().then((response) => {
      this.customerOptions = response.data || [];
    });
    // S2-2.1 待办链跳转支持：/order/delivery?status=0/1 预置状态筛选（保持字符串与字典值匹配）
    const routeStatus = this.$route.query.status;
    if (routeStatus !== undefined && routeStatus !== null && routeStatus !== "") {
      this.queryParams.status = String(routeStatus);
    }
    // 客户管理页「送货单」抽屉跳转带日期：列表筛选与生成日期同步预置
    const routeDate = this.$route.query.deliveryDate;
    if (routeDate) {
      this.queryParams.deliveryDate = String(routeDate);
    }
    this.getPageList();
  },
  methods: {
    /** 批次主行唯一键（客户+日期组合，稳定展开状态） */
    rowKey(row) {
      return (row.customerId || 0) + "-" + (row.deliveryDate || "");
    },
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
    /** 分页查询送货单列表（D-043：批次聚合主行，一行=客户+配送日期） */
    getPageList() {
      this.loading = true;
      batchPageDelivery(this.queryParams).then((response) => {
        this.batchList = (response.rows || []).map((r) => ({
          ...r,
          children: r.children || [],
          __loaded: false,
          __loading: false,
        }));
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 批次主行展开事件：加载该批次子送货单 */
    handleBatchExpand(row, expandedRows) {
      if (!row) {
        return;
      }
      if (row.__loaded) {
        return;
      }
      row.__loading = true;
      this.loadBatchChildren(row).finally(() => {
        row.__loading = false;
      });
    },
    /** 批次总表打印：矩阵形态批次跳转客户日总表矩阵页（含总表预览/打印） */
    handleMatrixPrint(row) {
      this.$router.push({
        path: "/order/batch",
        query: { customerId: row.customerId, deliveryDate: row.deliveryDate },
      });
    },
    /** 展开批次子行：按 客户+配送日期 拉该批次下全部送货单（复用 listDelivery） */
    loadBatchChildren(row) {
      // 已加载过则不重复
      if (row.__loaded) {
        row.children = row.children || [];
        return Promise.resolve(row.children);
      }
      const q = {
        pageNum: 1,
        pageSize: 100,
        customerId: row.customerId,
        deliveryDate: row.deliveryDate,
      };
      return listDelivery(q).then((response) => {
        row.children = response.data || [];
        row.__loaded = true;
        return row.children;
      });
    },
    /** 批次主行是否命中提醒（红色/黄色），驱动主行标色 */
    batchReminderClass({ row }) {
      if (row.maxReminderLevel === 2) {
        return "row-reminder-red";
      }
      if (row.maxReminderLevel === 1) {
        return "row-reminder-yellow";
      }
      return "";
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
    /** 可切换模板（P1/D-048）：后端出候选（同印刷形态、按绑定层级排序，替代前端复刻过滤）；命中全局默认时告警 */
    loadPrintTemplates() {
      printCandidatesDelivery(this.printRow.id)
        .then((response) => {
          const vo = response.data || {};
          const list = vo.templates || [];
          // 兑底：解析模板不在候选（如历史数据形态不匹配）仍可选
          const info = this.printInfo;
          if (info.templateRecordId && !list.some((t) => t.id === info.templateRecordId)) {
            list.unshift({
              id: info.templateRecordId,
              name: info.templateName,
              content: info.templateId,
              copies: info.copies,
            });
          }
          this.printTemplates = list;
          this.matchGlobalDefault = Boolean(vo.matchGlobalDefault);
        })
        .catch(() => {});
    },
    chosenPrintTemplate() {
      return this.printTemplates.find((t) => t.id === this.printChosenRecordId);
    },
    /** 打印视图 URL 参数：token/ticket 双传（W0-4.1 票据），customerId/deliveryDate 留空占位（历史单证主体） */
    printParams(row, ticket) {
      return (
        "?token=" + ticket + "&ticket=" + ticket
        + "&deliveryOrderId=" + row.id
        + "&customerId=" + (row.customerId || "")
        + "&deliveryDate=" + (row.deliveryDate || "")
        + "&customerDeptId=" + (row.deliveryPointId || "")
        + "&colBlock=1"
      );
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
          "/jmreport/view/" + template.content + printParams(this.printRow, res.ticket),
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
          "/jmreport/view/" + template.content + printParams(row, res.ticket),
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
      this.voidForm = { reasonCode: null, reasonNote: null };
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
.batch-children {
  padding: 6px 16px 8px 24px;
}
.children-title {
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}
.batch-status-count {
  font-size: 12px;
  color: #606266;
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
