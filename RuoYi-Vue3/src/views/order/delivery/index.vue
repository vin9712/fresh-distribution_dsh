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
  </div>
</template>

<script>
import {
  pageDelivery,
  listDeliveryDetail,
  listDeliverySources,
  generateDelivery,
  generateDeliveryForCustomer,
  printDelivery,
  printInfoDelivery,
  deliveredDelivery,
  voidDelivery,
} from "@/api/order/delivery";
import { issuePrintTicket } from "@/api/print/ticket";
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
    this.getPageList();
  },
  methods: {
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
    /** 生成送货单（统一生成服务：幂等，按客户组单策略三态分支） */
    handleGenerate() {
      const deliveryDate = this.generateDate;
      if (!deliveryDate) {
        this.$modal.msgWarning("请先选择配送日期");
        return;
      }
      this.$modal
        .confirm(
          "将为配送日期 " +
            deliveryDate +
            " 生成送货单（按各客户的组单策略：跨点总单/每点一单；已生成过的订单自动幂等跳过，遗漏订单按补单规则补齐）？"
        )
        .then(() => {
          return generateDelivery(deliveryDate);
        })
        .then((response) => {
          const result = response.data || {};
          const created = result.createdOrders || result || [];
          const skipped = result.skippedReasons || [];
          this.$modal.msgSuccess(
            "已生成 " + created.length + " 张送货单" +
              (skipped.length ? "，" + skipped.length + " 个客户幂等跳过" : "")
          );
          this.getPageList();
        })
        .catch(() => {});
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
    /** 打印：解析三级绑定模板 → 记录打印次数 → 打开 JimuReport 打印视图 */
    handlePrint(row) {
      printInfoDelivery(row.id)
        .then((response) => {
          const info = response.data;
          this.$modal
            .confirm(
              "按模板【" + info.templateName + "】打印（联数 " + info.copies + " 份），确认后记录打印次数并打开打印视图？"
            )
            .then(() => printDelivery(row.id))
            .then((printResp) => {
              this.$modal.msgSuccess("已记录打印，当前打印次数 " + printResp.data.printCount);
              // W0-4.1：URL 不再携带长期 JWT，改签发短时一次性打印票据
              return issuePrintTicket({ deliveryOrderId: row.id, templateId: info.templateId }).then((res) => {
                window.open(
                  "/jmreport/view/" + info.templateId + "?token=" + res.ticket + "&deliveryOrderId=" + row.id,
                  "_blank"
                );
                this.getPageList();
              });
            })
            .catch(() => {});
        })
        .catch(() => {});
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
</style>
