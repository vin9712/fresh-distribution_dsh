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
      <el-form-item label="采购单号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入采购单号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="采购日期" prop="dateRange">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择状态"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="dict in dict.type.t_purchase_order_status"
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
          v-hasPermi="['purchase:add']"
          >新增</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          :icon="Cpu"
          size="small"
          @click="handleGenerate"
          v-hasPermi="['purchase:add']"
          >生成采购单</el-button
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
          v-hasPermi="['purchase:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="purchaseList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="采购单号" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="采购日期" align="center" prop="orderDate" width="120" />
      <el-table-column label="来源" align="center" prop="sourceType" width="90">
        <template #default="scope">
          <span>{{ scope.row.sourceType === 1 ? '自动' : '手工' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商名称" align="center" prop="supplierName" :show-overflow-tooltip="true" />
      <el-table-column label="采购员" align="center" prop="purchaser" :show-overflow-tooltip="true" />
      <el-table-column label="采购总额" align="center" prop="totalAmount" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="dict.type.t_purchase_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="260">
        <template #default="scope">
          <el-button
            size="small"
            link
            :icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['purchase:edit']"
            >修改</el-button
          >
          <el-button
            size="small"
            link
            :icon="Check"
            @click="handleConfirm(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['purchase:edit']"
            >确认</el-button
          >
          <el-button
            size="small"
            link
            :icon="Select"
            @click="handleStockIn(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['purchase:edit']"
            >入库</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['purchase:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改采购单对话框 -->
    <el-dialog align-center :title="title" v-model="open" width="920px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="采购日期" prop="orderDate">
              <el-date-picker
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择采购日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">采购明细</el-divider>
        <el-table :data="form.items" size="small" border>
          <el-table-column label="SKU" align="center" width="220">
            <template #default="scope">
              <el-select
                v-model="scope.row.skuId"
                filterable
                clearable
                placeholder="选择SKU"
                style="width: 100%"
                @change="handleSkuChange(scope.row)"
              >
                <el-option
                  v-for="sku in skuOptions"
                  :key="sku.id"
                  :label="sku.name"
                  :value="sku.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="商品名称" align="center" min-width="140">
            <template #default="scope">
              <el-input v-model="scope.row.productName" placeholder="商品名称" />
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.productSpec" placeholder="规格" />
            </template>
          </el-table-column>
          <el-table-column label="单位" align="center" width="90">
            <template #default="scope">
              <el-input v-model="scope.row.productUnit" placeholder="单位" />
            </template>
          </el-table-column>
          <el-table-column label="数量" align="center" width="140">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantity"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%"
                @change="calcRowSubtotal(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="单价" align="center" width="140">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.unitPrice"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%"
                @change="calcRowSubtotal(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" align="center" width="110">
            <template #default="scope">
              <span>{{ scope.row.subtotal }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="70">
            <template #default="scope">
              <el-button
                size="small"
                link
                :icon="Delete"
                @click="removeItem(scope.$index)"
              ></el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 8px; display: flex; justify-content: space-between; align-items: center;">
          <el-button type="primary" plain size="small" :icon="Plus" @click="addItem">添加明细</el-button>
          <span>总金额：¥ {{ totalAmount }}</span>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  listPurchase,
  getPurchase,
  getPurchaseItems,
  generatePurchase,
  addPurchase,
  updatePurchase,
  confirmPurchase,
  stockInPurchase,
  delPurchase,
} from "@/api/purchase/purchase";
import { listSku } from "@/api/product/sku";
import { Search, Refresh, Plus, Delete, Edit, Cpu, Check, Select } from "@element-plus/icons-vue";

export default {
  name: "Purchase",
  dicts: ["t_purchase_order_status"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, Cpu, Check, Select };
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 采购单表格数据
      purchaseList: [],
      // SKU 选项
      skuOptions: [],
      // 采购日期范围
      dateRange: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        code: null,
        status: null,
        sourceType: null,
        beginOrderDate: null,
        endOrderDate: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        orderDate: [
          { required: true, message: "采购日期不能为空", trigger: "change" },
        ],
      },
    };
  },
  computed: {
    totalAmount() {
      return (this.form.items || []).reduce(
        (sum, item) => sum + (Number(item.subtotal) || 0),
        0
      ).toFixed(2);
    },
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询采购单列表 */
    getList() {
      this.loading = true;
      this.queryParams.beginOrderDate = this.dateRange && this.dateRange.length === 2 ? this.dateRange[0] : null;
      this.queryParams.endOrderDate = this.dateRange && this.dateRange.length === 2 ? this.dateRange[1] : null;
      listPurchase(this.queryParams).then((response) => {
        this.purchaseList = response.data || [];
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
        orderDate: this.getTomorrow(),
        supplierName: null,
        remark: null,
        items: [],
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.getSkuList();
      this.open = true;
      this.title = "新增采购单";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      this.getSkuList();
      getPurchase(row.id).then((response) => {
        this.form = response.data;
        return getPurchaseItems(row.id);
      }).then((response) => {
        this.form.items = response.data || [];
        this.open = true;
        this.title = "修改采购单";
      });
    },
    /** 生成采购单按钮操作（明日配送订单） */
    handleGenerate() {
      this.$modal
        .confirm("生成明日配送订单的采购单？")
        .then(() => {
          return generatePurchase({ orderDate: this.getTomorrow() });
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("生成成功");
        })
        .catch(() => {});
    },
    /** 确认按钮操作 */
    handleConfirm(row) {
      this.$modal
        .confirm('是否确认采购单【' + row.code + '】？')
        .then(() => {
          return confirmPurchase(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("确认成功");
        })
        .catch(() => {});
    },
    /** 入库按钮操作 */
    handleStockIn(row) {
      this.$modal
        .confirm('是否确认采购单【' + row.code + '】入库？')
        .then(() => {
          return stockInPurchase(row.id);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("入库成功");
        })
        .catch(() => {});
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal
        .confirm('是否确认删除采购单编号为"' + ids + '"的数据项？')
        .then(() => {
          return delPurchase(ids);
        })
        .then(() => {
          this.getList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.items == null || this.form.items.length === 0) {
            this.$modal.msgError("请至少添加一条采购明细");
            return;
          }
          if (this.form.id != null) {
            updatePurchase(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addPurchase(this.form).then((response) => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 查询 SKU 列表 */
    getSkuList() {
      listSku().then((response) => {
        this.skuOptions = response.data || [];
      });
    },
    /** SKU 选择变更：回填商品名称/规格/单位/单价 */
    handleSkuChange(row) {
      const sku = this.skuOptions.find((s) => s.id === row.skuId);
      if (sku) {
        row.productName = sku.name;
        row.productSpec = sku.spec;
        row.productUnit = sku.unit;
        if (!row.unitPrice) {
          row.unitPrice = sku.salePrice;
        }
      }
      this.calcRowSubtotal(row);
    },
    /** 计算行小计 */
    calcRowSubtotal(row) {
      const quantity = Number(row.quantity) || 0;
      const unitPrice = Number(row.unitPrice) || 0;
      row.subtotal = Number((quantity * unitPrice).toFixed(2));
    },
    /** 添加明细行 */
    addItem() {
      this.form.items = this.form.items || [];
      this.form.items.push({
        skuId: null,
        productName: null,
        productSpec: null,
        productUnit: null,
        quantity: 1,
        unitPrice: 0,
        subtotal: 0,
      });
    },
    /** 删除明细行 */
    removeItem(index) {
      this.form.items.splice(index, 1);
    },
    /** 计算明日日期 yyyy-MM-dd */
    getTomorrow() {
      const d = new Date();
      d.setDate(d.getDate() + 1);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return y + "-" + m + "-" + day;
    },
  },
};
</script>
