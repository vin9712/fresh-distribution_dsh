<template>
  <div class="app-container">
    <!-- 草稿恢复提示条：列表页检测到未完成订单草稿 -->
    <el-alert
      v-if="availableDrafts.length"
      type="warning"
      :closable="false"
      show-icon
      class="draft-recover-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          检测到 {{ availableDrafts.length }} 个未完成的订单草稿{{ availableDrafts[0].deptName ? '【' + availableDrafts[0].deptName + '】' : '' }}（保存于 {{ formatSavedTime(availableDrafts[0].savedAt) }}）
          <el-button link type="primary" @click="recoverLatestDraft">去恢复</el-button>
          <el-button link @click="discardAllDrafts">全部忽略</el-button>
        </span>
      </template>
    </el-alert>
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
    >
      <el-form-item label="送货单位" prop="customerDeptId">
        <el-cascader
          v-model="selectedCustomerDepts"
          placeholder="请选择送货单位"
          :options="customerDeptOptions"
          @change="handleFormOptionsChanged"
          :props="{ expandTrigger: 'hover' }"
          filterable
          clearable
        />
      </el-form-item>
      <el-form-item label="订单编号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入订单编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单来源" prop="source">
        <el-select
          v-model="queryParams.source"
          placeholder="请选择订单来源"
          clearable
        >
          <el-option
            v-for="dict in dict.type.t_sale_order_source"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="订单类型" prop="type">
        <el-select
          v-model="queryParams.type"
          placeholder="请选择订单类型"
          clearable
        >
          <el-option
            v-for="dict in dict.type.t_sale_order_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="订单状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择订单状态"
          @change="handleQuery"
          clearable
        >
          <el-option
            v-for="dict in dict.type.t_sale_order_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
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
          type="primary"
          plain
          :icon="Plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['order:sale:add']"
          >新增明细</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <vxe-button
          icon="vxe-icon-edit"
          size="mini"
          :disabled="multiple"
          transfer
          placement="bottom"
        >
          <template #default>批量处理</template>
          <template #dropdowns>
            <vxe-button
              mode="button"
              status="success"
              icon="vxe-icon-check"
              :disabled="multiple"
              content="批量确认"
              @click="handleOrderApproval"
            ></vxe-button>
            <vxe-button
              mode="button"
              class="check-order-btn"
              icon="vxe-icon-check"
              :disabled="multiple"
              content="批量验收"
              @click="handleOrderCheck"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="primary"
              icon="vxe-icon-check"
              :disabled="multiple"
              content="批量月结"
              v-hasPermi="['order:sale:settle']"
              @click="handleOrderSettle"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="info"
              icon="vxe-icon-undo"
              :disabled="multiple"
              content="批量还原"
              @click="handleOrderRestore"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="danger"
              icon="vxe-icon-delete"
              :disabled="multiple"
              content="批量删除"
              @click="handleDelete"
            ></vxe-button>
          </template>
        </vxe-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="ShoppingBag"
          size="small"
          @click="handleBuildPurchase"
          >生成采购单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          :icon="Van"
          size="small"
          @click="handleBuildDelivery"
          >生成送货单</el-button
        >
      </el-col>
      <right-toolbar
        v-model:showSearch="showSearch"
        @queryTable="getPageList"
      ></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="saleList"
      @row-dblclick="handleRowDblClick"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="送货单位" align="center" prop="deliveryName" />
      <el-table-column label="订单编号" align="center" prop="code" />
      <el-table-column label="订单来源" align="center" prop="source">
        <template #default="scope">
          <dict-tag
            :options="dict.type.t_sale_order_source"
            :value="scope.row.source"
          />
        </template>
      </el-table-column>
      <el-table-column label="订单类型" align="center" prop="type">
        <template #default="scope">
          <dict-tag
            :options="dict.type.t_sale_order_type"
            :value="scope.row.type"
          />
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="amount" />
      <el-table-column label="订单状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag
            :options="dict.type.t_sale_order_status"
            :value="scope.row.status"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="配送日期"
        align="center"
        prop="deliveryDate"
        width="180"
      >
        <template #default="scope">
          <span>{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="流程单据" align="center" width="110">
        <template #default="scope">
          <el-tooltip
            v-if="scope.row.purchaseOrderCode"
            :content="'采购单：' + scope.row.purchaseOrderCode"
            placement="top"
          >
            <el-icon class="doc-icon doc-purchase"><ShoppingBag /></el-icon>
          </el-tooltip>
          <el-tooltip
            v-if="scope.row.deliveryOrderCode"
            :content="'送货单：' + scope.row.deliveryOrderCode"
            placement="top"
          >
            <el-icon class="doc-icon doc-delivery"><Van /></el-icon>
          </el-tooltip>
          <span
            v-if="!scope.row.purchaseOrderCode && !scope.row.deliveryOrderCode"
            class="doc-empty"
            >—</span
          >
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
      >
        <template #default="scope">
          <el-button
            v-if="scope.row.status == 1"
            size="small"
            link
            :icon="RefreshLeft"
            @click="handleRecall(scope.row)"
            v-hasPermi="['order:sale:recall']"
            >撤回</el-button
          >
          <el-button
            v-if="scope.row.status == 2 || scope.row.status == 3"
            size="small"
            link
            :icon="Operation"
            @click="handleAdjust(scope.row)"
            v-hasPermi="['order:sale:adjust']"
            >调整</el-button
          >
          <el-button
            size="small"
            link
            :icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['order:sale:edit']"
            >修改</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['order:sale:remove']"
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

    <!-- 添加或修改销售订单对话框 -->
    <el-dialog align-center :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="订单编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入订单编号" />
        </el-form-item>
        <el-form-item label="订单来源" prop="source">
          <el-select v-model="form.source" placeholder="请选择订单来源">
            <el-option
              v-for="dict in dict.type.t_sale_order_source"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="订单类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择订单类型">
            <el-option
              v-for="dict in dict.type.t_sale_order_type"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="总金额" prop="amount">
          <el-input v-model="form.amount" placeholder="请输入总金额" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态">
            <el-option
              v-for="dict in dict.type.t_sale_order_status"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="配送日期" prop="deliveryDate">
          <el-date-picker
            clearable
            v-model="form.deliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择配送日期"
          >
          </el-date-picker>
        </el-form-item>
        <el-form-item label="逻辑删除" prop="isDeleted">
          <el-input v-model="form.isDeleted" placeholder="请输入逻辑删除" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 订单调整对话框（配送后加退换） -->
    <el-dialog align-center :title="adjustTitle" v-model="adjustOpen" width="860px" append-to-body>
      <el-form label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="订单编号">
              <span>{{ adjustForm.orderCode }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调整类型">
              <el-radio-group v-model="adjustForm.type">
                <el-radio :value="1">加单</el-radio>
                <el-radio :value="2">退单</el-radio>
                <el-radio :value="3">换货</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调整日期">
              <el-date-picker
                clearable
                v-model="adjustForm.adjustDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择调整日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="调整原因">
              <el-input v-model="adjustForm.reason" type="textarea" placeholder="请输入调整原因" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">现有明细调整（调整数量：正加负退）</el-divider>
      <el-table :data="adjustDetailRows" border>
        <el-table-column label="商品名称" align="center" prop="productName" :show-overflow-tooltip="true" />
        <el-table-column label="规格" align="center" prop="productSpec" :show-overflow-tooltip="true" />
        <el-table-column label="当前数量" align="center" prop="num" width="100" />
        <el-table-column label="调整数量" align="center" width="180">
          <template #default="scope">
            <el-input-number v-model="scope.row.deltaQuantity" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">新增商品行</el-divider>
      <el-button size="small" link :icon="Plus" @click="adjustAddNewRow" class="mb8">新增商品行</el-button>
      <el-table :data="adjustNewRows" border>
        <el-table-column label="商品名称" align="center" min-width="160">
          <template #default="scope">
            <el-input v-model="scope.row.productName" placeholder="商品名称" />
          </template>
        </el-table-column>
        <el-table-column label="单位" align="center" width="90">
          <template #default="scope">
            <el-input v-model="scope.row.productUnit" placeholder="单位" />
          </template>
        </el-table-column>
        <el-table-column label="规格" align="center" min-width="120">
          <template #default="scope">
            <el-input v-model="scope.row.productSpec" placeholder="规格" />
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="130">
          <template #default="scope">
            <el-input-number v-model="scope.row.productPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="数量" align="center" width="130">
          <template #default="scope">
            <el-input-number v-model="scope.row.num" :min="0" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="70">
          <template #default="scope">
            <el-button size="small" link :icon="Delete" @click="adjustRemoveNewRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="adjustSubmitForm">确 定</el-button>
          <el-button @click="adjustOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 生成采购单/送货单抽屉（三步：汇总预览 → 填写信息 → 确认生成） -->
    <el-drawer
      v-model="buildDrawerVisible"
      :title="buildMode === 'purchase' ? '生成采购单' : '生成送货单'"
      size="600px"
      append-to-body
      :destroy-on-close="false"
      @closed="resetBuildDrawer"
    >
      <el-steps
        :active="buildActiveStep"
        align-center
        finish-status="success"
        style="margin-bottom: 18px"
      >
        <el-step title="汇总预览" />
        <el-step title="填写信息" />
        <el-step title="确认生成" />
      </el-steps>

      <!-- 第一步：按品类分组的商品汇总 -->
      <div v-show="buildActiveStep === 0">
        <div class="build-orders-summary">
          <span class="build-orders-label">已选 {{ buildSelectedOrders.length }} 个订单：</span>
          <el-tag
            v-for="o in buildSelectedOrders"
            :key="o.id"
            size="small"
            class="order-code-tag"
            >{{ o.code }}</el-tag
          >
        </div>
        <div v-loading="previewLoading" class="build-preview-body">
          <template v-if="previewData && previewData.groups && previewData.groups.length">
            <div
              v-for="g in previewData.groups"
              :key="g.categoryName"
              class="preview-group"
            >
              <div class="preview-group-header">
                <span class="preview-category">{{ g.categoryName }}</span>
                <span class="preview-group-meta"
                  >{{ g.items.length }} 项 / 合计 ¥{{ g.amount }}</span
                >
              </div>
              <el-table :data="g.items" size="small" border>
                <el-table-column
                  label="商品名称"
                  prop="productName"
                  min-width="120"
                  :show-overflow-tooltip="true"
                />
                <el-table-column
                  label="规格"
                  prop="productSpec"
                  width="80"
                />
                <el-table-column label="单位" prop="productUnit" width="60" />
                <el-table-column label="数量" prop="quantity" width="70" />
                <el-table-column label="单价" prop="price" width="80" />
                <el-table-column label="小计" prop="amount" width="90" />
              </el-table>
            </div>
            <div class="preview-total">
              共 {{ previewData.itemCount }} 行商品，总金额
              <b>¥{{ previewData.totalAmount }}</b>
            </div>
          </template>
          <el-empty
            v-else-if="!previewLoading"
            description="暂无商品明细"
            :image-size="60"
          />
        </div>
      </div>

      <!-- 第二步：填写信息 -->
      <div v-show="buildActiveStep === 1">
        <el-form label-width="90px">
          <template v-if="buildMode === 'purchase'">
            <el-form-item label="供货商">
              <el-input
                v-model="buildForm.supplierName"
                placeholder="请输入供货商名称（可空，确认时后补）"
                clearable
              />
            </el-form-item>
            <el-form-item label="采购员">
              <el-input
                v-model="buildForm.purchaser"
                placeholder="请输入采购员（可空）"
                clearable
              />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="配送日期" required>
              <el-date-picker
                v-model="buildForm.deliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择配送日期"
                style="width: 100%"
              />
            </el-form-item>
            <el-alert
              title="送货单将按 客户+配送点 自动分组生成（多个配送点会生成多张送货单）"
              type="info"
              :closable="false"
              show-icon
            />
          </template>
        </el-form>
      </div>

      <!-- 第三步：确认生成 -->
      <div v-show="buildActiveStep === 2">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="单据类型">
            {{ buildMode === 'purchase' ? '采购单' : '送货单' }}
          </el-descriptions-item>
          <el-descriptions-item label="订单数量">
            {{ previewData.orders.length }} 个
          </el-descriptions-item>
          <el-descriptions-item label="商品行数">
            {{ previewData.itemCount }} 行
          </el-descriptions-item>
          <el-descriptions-item label="总金额">
            ¥{{ previewData.totalAmount }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="buildMode === 'purchase'"
            label="供货商"
          >
            {{ buildForm.supplierName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="buildMode === 'purchase'"
            label="采购员"
          >
            {{ buildForm.purchaser || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-else label="配送日期">
            {{ buildForm.deliveryDate }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          title="同一订单不可重复生成单据，生成后列表行将显示采购/送货图标（悬浮可见单号）"
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 14px"
        />
      </div>

      <template #footer>
        <div class="build-drawer-footer">
          <el-button @click="buildDrawerVisible = false">取 消</el-button>
          <el-button
            v-if="buildActiveStep > 0"
            @click="buildActiveStep--"
            >上一步</el-button
          >
          <el-button
            v-if="buildActiveStep < 2"
            type="primary"
            :disabled="!buildStep2Valid"
            @click="buildActiveStep++"
            >下一步</el-button
          >
          <el-button
            v-else
            type="primary"
            :loading="buildSubmitting"
            @click="confirmBuild"
            >确认生成</el-button
          >
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script>
import {
  pageSaleOrder,
  listSale,
  getSaleOrder,
  delSale,
  addSale,
  updateSale,
  updateOrderStatus,
  generatePreview,
} from "@/api/order/sale";
import { generatePurchaseByOrders } from "@/api/purchase/purchase";
import { generateDeliveryByOrders } from "@/api/order/delivery";
import { listCustomerDept } from "@/api/partner/customerDept";
import { listSaleDetail } from "@/api/order/saleDetail";
import { listDrafts, removeDraft } from "@/utils/saleDraft";
import { createAdjustment } from "@/api/order/adjustment";
import {
  Search,
  Refresh,
  Plus,
  ShoppingBag,
  Van,
  Edit,
  Delete,
  Operation,
} from "@element-plus/icons-vue";

export default {
  name: "Sale",
  dicts: ["t_sale_order_status", "t_sale_order_type", "t_sale_order_source"],
  setup() {
    return { Search, Refresh, Plus, ShoppingBag, Van, Edit, Delete, Operation };
  },
  data() {
    return {
      // 未完成订单草稿（列表页恢复横幅）
      availableDrafts: [],
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 销售订单表格数据
      saleList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: null,
        customerDeptId: null,
        code: null,
        source: null,
        type: null,
        amount: null,
        status: null,
        deliveryDate: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        code: [
          { required: true, message: "订单编号不能为空", trigger: "blur" },
        ],
        source: [
          {
            required: true,
            message: "订单来源：1后台下单,2线上下单不能为空",
            trigger: "change",
          },
        ],
        type: [
          {
            required: true,
            message: "订单类型不能为空",
            trigger: "change",
          },
        ],
        amount: [
          { required: true, message: "总金额不能为空", trigger: "blur" },
        ],
        status: [
          {
            required: true,
            message: "订单状态不能为空",
            trigger: "change",
          },
        ],
        deliveryDate: [
          { required: true, message: "配送日期不能为空", trigger: "blur" },
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" },
        ],
      },
      // 已选择的列表
      formSelectedOptions: [],
      // 已选择的送货单位
      selectedCustomerDepts: [],
      // 送货单位map: <customerDeptId, customerId>
      customerDeptMap: {},
      // 送货单位树列表
      customerDeptOptions: [],
      // 订单调整对话框
      adjustOpen: false,
      adjustTitle: "",
      adjustForm: {
        orderId: null,
        orderCode: null,
        type: 1,
        reason: null,
        adjustDate: null,
      },
      adjustDetailRows: [],
      adjustNewRows: [],
      // 生成采购单/送货单抽屉
      buildDrawerVisible: false,
      buildMode: "purchase", // purchase | delivery
      buildActiveStep: 0,
      buildSelectedOrders: [],
      buildForm: {
        supplierName: null,
        purchaser: null,
        deliveryDate: null,
      },
      previewLoading: false,
      previewData: {
        orders: [],
        groups: [],
        itemCount: 0,
        totalQuantity: 0,
        totalAmount: 0,
      },
      buildSubmitting: false,
    };
  },
  computed: {
    /** 第二步可进入：采购单无需必填；送货单需配送日期 */
    buildStep2Valid() {
      if (this.buildMode === "delivery") {
        return !!this.buildForm.deliveryDate;
      }
      return true;
    },
  },
  created() {
    this.getTreeselect();
    this.getPageList();
    // 检测未完成订单草稿，展示恢复横幅
    this.refreshDrafts();
  },
  methods: {
    /** 刷新草稿列表（新单页恢复横幅用） */
    refreshDrafts() {
      this.availableDrafts = listDrafts();
    },
    /** 去恢复：携带 draft key 跳转录单页，详情页自动恢复 */
    recoverLatestDraft() {
      const draft = this.availableDrafts[0];
      if (!draft) return;
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { draft: draft.key },
      });
    },
    /** 全部忽略：删除所有草稿 */
    discardAllDrafts() {
      this.availableDrafts.forEach((d) => removeDraft(d.key));
      this.availableDrafts = [];
    },
    /** 格式化保存时间 HH:mm */
    formatSavedTime(savedAt) {
      if (!savedAt) return "";
      const d = new Date(savedAt);
      const p = (n) => String(n).padStart(2, "0");
      return `${p(d.getHours())}:${p(d.getMinutes())}`;
    },
    /** 查询销售订单列表 */
    getList() {
      this.loading = true;
      listSale(this.queryParams).then((response) => {
        this.saleList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询销售订单列表 */
    getPageList() {
      this.loading = true;
      pageSaleOrder(this.queryParams).then((response) => {
        this.saleList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        customerId: null,
        customerDeptId: null,
        code: null,
        source: null,
        type: null,
        amount: null,
        status: null,
        deliveryDate: null,
        isDeleted: null,
        version: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null,
      };
      this.resetForm("form");
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
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.formSelectedOptions = selection;
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      // 跳转到新增详情
      this.$router.push({
        path: "/order/sale-detail/index/",
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const orderId = row.id;
      // 跳转到订单详情
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { orderId: orderId },
      });
    },
    /** 批量审核订单 */
    handleOrderApproval() {
      // 校验是否全为制单状态订单
      const valid = this.formSelectedOptions.some((item) => item.status !== 0);
      if (valid) {
        this.$modal.msgError("请选择制单状态的订单");
        return;
      }
      const orderIds = this.formSelectedOptions.map((item) => item.id);
      this.$modal
        .confirm("是否确认审核选中的" + orderIds.length + "条的订单？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 1,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 批量还原订单状态 */
    handleOrderRestore(row) {
      // 获取选中的订单
      const orderList = row && row.status ? [row] : this.formSelectedOptions;
      // 校验是否全为审核状态订单
      const valid = orderList.some((item) => item.status !== 1);
      if (valid) {
        this.$modal.msgError("请选择审核状态的订单");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
      this.$modal
        .confirm("是否确认还原选中的" + orderIds.length + "条订单的状态？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 0,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 批量验收订单状态 */
    handleOrderCheck(row) {
      // 获取选中的订单
      const orderList = row && row.status ? [row] : this.formSelectedOptions;
      // 校验是否全为送货状态的订单
      const valid = orderList.some((item) => item.status !== 2);
      if (valid) {
        this.$modal.msgError("请选择送货状态的订单");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
    },
    /** 批量完成订单状态 */
    handleOrderFinish(row) {
      // 获取选中的订单
      const orderList = row && row.status ? [row] : this.formSelectedOptions;
      // 校验是否全为验收状态的订单
      const valid = orderList.some((item) => item.status !== 3);
      if (valid) {
        this.$modal.msgError("请选择验收状态的订单");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
    },
    /** 批量月结订单（SETTLED，管理员） */
    handleOrderSettle() {
      const orderList = this.formSelectedOptions;
      const valid = orderList.some((item) => item.status !== 3);
      if (valid) {
        this.$modal.msgError("请选择验收状态的订单进行月结");
        return;
      }
      const orderIds = orderList.map((item) => item.id);
      this.$modal
        .confirm("是否确认对选中的" + orderIds.length + "条订单执行月结？")
        .then(function () {
          const params = {
            orderIds: orderIds,
            status: 4,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 行内撤回订单（CONFIRMED→DRAFT，已生成送货单不可撤回由后端校验） */
    handleRecall(row) {
      this.$modal
        .confirm("是否确认撤回订单【" + row.code + "】？")
        .then(function () {
          const params = {
            orderIds: [row.id],
            status: 0,
          };
          return updateOrderStatus(params);
        })
        .then(() => {
          this.handleQuery();
        });
    },
    /** 双击行处理详情 */
    handleRowDblClick(row) {
      // 检查是否有多选
      if (this.ids.length > 1) {
        this.$modal.msgError("当前为多选模式，不可双击查看详情");
        return;
      }

      if (!row) return;
      // 跳转到订单详情
      this.$router.push({
        path: "/order/sale-detail/index/",
        query: { orderId: row.id },
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateSale(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addSale(this.form).then((response) => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getPageList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除销售订单编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSale(ids);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 生成采购单 */
    handleBuildPurchase() {
      const selected = this.formSelectedOptions;
      if (!selected.length) {
        this.$modal.msgWarning("请先勾选要生成采购单的订单");
        return;
      }
      if (selected.some((item) => item.status !== 1)) {
        this.$modal.msgError("请选择审核状态的订单");
        return;
      }
      this.openBuildDrawer("purchase");
    },
    /** 生成送货单 */
    handleBuildDelivery() {
      const selected = this.formSelectedOptions;
      if (!selected.length) {
        this.$modal.msgWarning("请先勾选要生成送货单的订单");
        return;
      }
      if (selected.some((item) => item.status !== 1)) {
        this.$modal.msgError("请选择审核状态的订单");
        return;
      }
      this.openBuildDrawer("delivery");
    },
    /** 打开抽屉并加载预览 */
    openBuildDrawer(mode) {
      this.buildMode = mode;
      this.buildSelectedOrders = [...this.formSelectedOptions];
      this.buildActiveStep = 0;
      this.buildForm = {
        supplierName: null,
        purchaser: null,
        deliveryDate: null,
      };
      this.buildSubmitting = false;
      this.buildDrawerVisible = true;
      this.loadBuildPreview();
    },
    /** 加载汇总预览（按品类分组） */
    loadBuildPreview() {
      this.previewLoading = true;
      const orderIds = this.buildSelectedOrders.map((o) => o.id);
      generatePreview({ orderIds })
        .then((response) => {
          this.previewData = response.data || {
            orders: [],
            groups: [],
            itemCount: 0,
            totalQuantity: 0,
            totalAmount: 0,
          };
          // 默认配送日期 = 订单配送日期
          if (!this.buildForm.deliveryDate && this.buildSelectedOrders[0]) {
            this.buildForm.deliveryDate =
              this.buildSelectedOrders[0].deliveryDate;
          }
        })
        .finally(() => {
          this.previewLoading = false;
        });
    },
    /** 关闭抽屉后重置状态 */
    resetBuildDrawer() {
      this.buildSelectedOrders = [];
      this.previewData = {
        orders: [],
        groups: [],
        itemCount: 0,
        totalQuantity: 0,
        totalAmount: 0,
      };
      this.buildSubmitting = false;
    },
    /** 确认生成 */
    confirmBuild() {
      this.buildSubmitting = true;
      const orderIds = this.buildSelectedOrders.map((o) => o.id);
      const request =
        this.buildMode === "purchase"
          ? generatePurchaseByOrders({
              orderIds,
              supplierName: this.buildForm.supplierName,
              purchaser: this.buildForm.purchaser,
            })
          : generateDeliveryByOrders({
              orderIds,
              deliveryDate: this.buildForm.deliveryDate,
            });
      request
        .then((response) => {
          const codes =
            this.buildMode === "purchase"
              ? response.data && response.data.code
              : (response.data || [])
                  .map((d) => d.code)
                  .join("、");
          this.$modal.msgSuccess("生成成功：" + (codes || ""));
          this.buildDrawerVisible = false;
          this.getPageList();
        })
        .catch(() => {})
        .finally(() => {
          this.buildSubmitting = false;
        });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download(
        "order/sale/export",
        {
          ...this.queryParams,
        },
        `sale_${new Date().getTime()}.xlsx`
      );
    },
    /** 选择送货单位树回调（兼容清空时传入 null） */
    handleFormOptionsChanged(value) {
      const arr = Array.isArray(value) ? value : [];
      const customerDeptId = arr.length ? arr[arr.length - 1] : null;
      this.queryParams.customerDeptId = customerDeptId;
      this.queryParams.customerId = customerDeptId ? this.customerDeptMap[customerDeptId] : null;
      this.handleQuery();
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCustomerDept().then((response) => {
        // init customerDeptMap
        this.customerDeptMap = Object.fromEntries(
          response.data.map(({ id, customerId }) => [id, customerId])
        );

        // init customerDeptOptions
        const treeList = this.handleTree(response.data);
        this.customerDeptOptions = this.transformData(treeList);
      });
    },
    /** 树形列表转换为级联列表 */
    transformData(data) {
      return data.map((item) => {
        const newItem = {
          value: item.id.toString(),
          label: item.name,
        };
        if (Array.isArray(item.children) && item.children.length > 0) {
          newItem.children = this.transformData(item.children);
        }
        return newItem;
      });
    },
    /** 行内调整（配送后加退换） */
    handleAdjust(row) {
      this.adjustForm = {
        orderId: row.id,
        orderCode: row.code,
        type: 1,
        reason: null,
        adjustDate: this.parseTime(new Date(), "{y}-{m}-{d}"),
      };
      this.adjustDetailRows = [];
      this.adjustNewRows = [];
      this.adjustTitle = "订单调整 - " + row.code;
      listSaleDetail({ orderId: row.id }).then((response) => {
        this.adjustDetailRows = (response.data || []).map((item) => ({
          ...item,
          deltaQuantity: null,
        }));
        this.adjustOpen = true;
      });
    },
    /** 新增商品行 */
    adjustAddNewRow() {
      this.adjustNewRows.push({
        productName: null,
        productUnit: null,
        productSpec: null,
        productPrice: null,
        num: null,
      });
    },
    /** 删除新增商品行 */
    adjustRemoveNewRow(index) {
      this.adjustNewRows.splice(index, 1);
    },
    /** 提交订单调整 */
    adjustSubmitForm() {
      if (!this.adjustForm.adjustDate) {
        this.$modal.msgWarning("请选择调整日期");
        return;
      }
      const items = [];
      this.adjustDetailRows.forEach((row) => {
        if (row.deltaQuantity != null && row.deltaQuantity !== 0) {
          items.push({
            orderItemId: row.id,
            deltaQuantity: row.deltaQuantity,
          });
        }
      });
      this.adjustNewRows.forEach((row) => {
        if (row.productName && row.num != null && row.num > 0) {
          items.push({
            orderItemId: null,
            deltaQuantity: row.num,
            skuId: null,
            productName: row.productName,
            productUnit: row.productUnit,
            productSpec: row.productSpec,
            productPrice: row.productPrice,
          });
        }
      });
      if (items.length === 0) {
        this.$modal.msgWarning("请填写调整明细");
        return;
      }
      createAdjustment({
        orderId: this.adjustForm.orderId,
        type: this.adjustForm.type,
        reason: this.adjustForm.reason,
        adjustDate: this.adjustForm.adjustDate,
        items: items,
      }).then(() => {
        this.$modal.msgSuccess("调整成功");
        this.adjustOpen = false;
        this.getPageList();
      }).catch(() => {});
    },
  },
};
</script>

<style lang="scss" scoped>
/* 草稿恢复横幅 */
.draft-recover-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    .el-button + .el-button {
      margin-left: 8px;
    }
  }
}
/* 流程单据图标列 */
.doc-icon {
  font-size: 18px;
  vertical-align: middle;
  cursor: default;
  &.doc-purchase {
    color: #e6a23c;
    margin-right: 4px;
  }
  &.doc-delivery {
    color: #67c23a;
  }
}
.doc-empty {
  color: #c0c4cc;
}
/* 生成单据抽屉 */
.build-orders-summary {
  margin-bottom: 10px;
  .build-orders-label {
    font-size: 13px;
    color: #606266;
  }
  .order-code-tag {
    margin: 0 4px 4px 0;
  }
}
.build-preview-body {
  max-height: 52vh;
  overflow-y: auto;
}
.preview-group {
  margin-bottom: 12px;
  .preview-group-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 10px;
    background: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-bottom: none;
    border-radius: 4px 4px 0 0;
    .preview-category {
      font-weight: 600;
      color: #303133;
    }
    .preview-group-meta {
      font-size: 12px;
      color: #909399;
    }
  }
}
.preview-total {
  text-align: right;
  padding: 8px 4px;
  color: #606266;
  b {
    color: #e6a23c;
  }
}
.build-drawer-footer {
  text-align: right;
}
.check-order-btn {
  background-color: #625ceb;
  color: #fff;
  border: none;
  padding: 10px 20px;
  transition: all 0.3s ease; /* 添加过渡效果 */
}

.check-order-btn:hover {
  background-color: lighten(#625ceb, 15%); /* 悬停时颜色变淡 */
  color: #fff !important;
  cursor: pointer;
}

.check-order-btn:active {
  background-color: darken(#625ceb, 10%); /* 激活时颜色加深 */
  color: #fff; /* 确保文本颜色始终为白色 */
}

.check-order-btn:disabled {
  background-color: lighten(#625ceb, 20%); /* 禁用时背景颜色 */
  color: #fff; /* 禁用时文本颜色 */
  cursor: not-allowed; /* 改变鼠标指针形状 */
  opacity: 0.65; /* 降低透明度以显示禁用状态 */
  pointer-events: none; /* 禁止所有鼠标事件 */
}
</style>
