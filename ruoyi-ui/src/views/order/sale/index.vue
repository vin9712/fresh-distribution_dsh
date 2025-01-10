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
          @keyup.enter.native="handleQuery"
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
          value-format="yyyy-MM-dd"
          placeholder="请选择配送日期"
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          icon="el-icon-search"
          size="mini"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery"
          >重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['order:sale:add']"
          >新增明细</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <vxe-button
          icon="el-icon-edit"
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
              icon="el-icon-s-check"
              :disabled="multiple"
              content="批量审核"
              @click="handleOrderApproval"
            ></vxe-button>
            <vxe-button
              mode="button"
              class="check-order-btn"
              icon="el-icon-check"
              :disabled="multiple"
              content="批量验收"
              @click="handleOrderCheck"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="primary"
              icon="el-icon-finished"
              :disabled="multiple"
              content="批量完成"
              @click="handleOrderFinish"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="info"
              icon="el-icon-refresh-left"
              :disabled="multiple"
              content="批量还原"
              @click="handleOrderRestore"
            ></vxe-button>
            <vxe-button
              mode="button"
              status="danger"
              icon="el-icon-delete"
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
          icon="el-icon-shopping-bag-2"
          size="mini"
          @click="handleBuildPurchase"
          >生成采购单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-truck"
          size="mini"
          @click="handleBuildDelivery"
          >生成送货单</el-button
        >
      </el-col>
      <right-toolbar
        :showSearch.sync="showSearch"
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
        <template slot-scope="scope">
          <dict-tag
            :options="dict.type.t_sale_order_source"
            :value="scope.row.source"
          />
        </template>
      </el-table-column>
      <el-table-column label="订单类型" align="center" prop="type">
        <template slot-scope="scope">
          <dict-tag
            :options="dict.type.t_sale_order_type"
            :value="scope.row.type"
          />
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="amount" />
      <el-table-column label="订单状态" align="center" prop="status">
        <template slot-scope="scope">
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
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
      >
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['order:sale:edit']"
            >修改</el-button
          >
          <el-button
            v-if="
              scope.row.status === 1 ||
              scope.row.status === 2 ||
              scope.row.status === 3
            "
            size="mini"
            type="text"
            icon="el-icon-printer"
            @click="handlePrint(scope.row)"
            >打印</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
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
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getPageList"
    />

    <!-- 添加或修改销售订单对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
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
            value-format="yyyy-MM-dd"
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
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 打印预览窗口 -->
    <Preview ref="printPreiew" />
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
  deliveryPrintTemplate,
  deliveryPrintData,
} from "@/api/order/sale";
import { listCustomerDept } from "@/api/partner/customerDept";
import { listTask } from "@/api/print/task";

import Preview from "@/components/PrintDesigner/preview.vue";
import { disAutoConnect, hiprint } from "@sv-print/hiprint";
import "sv-print/dist/style.css"; // sv-print 样式

export default {
  name: "Sale",
  components: { Preview },
  dicts: ["t_sale_order_status", "t_sale_order_type", "t_sale_order_source"],
  data() {
    return {
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
    };
  },
  mounted() {
    // 取消自动连接
    disAutoConnect();
  },
  created() {
    this.getTreeselect();
    this.getPageList();
  },
  methods: {
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
    /** 打印按钮操作 */
    async handlePrint(row) {
      console.log("handlePrint row", row);

      const orderId = row.id;
      try {
        // 获取打印模板
        const deliveryTemplateResponse = await deliveryPrintTemplate({
          orderId: orderId,
        });
        const template = deliveryTemplateResponse.data || {};
        if (!template) {
          this.$modal.msgError("获取打印模板失败");
          return;
        }

        const deliveryPrintDataResponse = await deliveryPrintData({
          orderId: orderId,
          templateId: template.id,
        });
        const printObject = deliveryPrintDataResponse.data || {};
        if (!printObject) {
          this.$modal.msgError("获取打印数据失败");
        }

        let panel = JSON.parse(printObject.template);
        let printData = printObject.data || {};
        console.log("printData", printData);

        // 构造打印模板
        let hiprintTemplate = new hiprint.PrintTemplate({
          template: panel,
        });

        // 打开预览窗口
        this.$refs.printPreiew.show(
          hiprintTemplate,
          printData,
          orderId,
          template.id
        );

        // todo 点击打印时，创建打印任务
      } catch (error) {
        console.error("handlePrint Error: ", error);
      }
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
      // todo 使用 el-drawer 来加载已审核的订单，用于生成采购单

      // 校验是否全为审核状态订单
      const valid = this.formSelectedOptions.some((item) => item.status !== 1);
      if (valid) {
        this.$modal.msgError("请选择审核状态的订单");
        return;
      }
      const orderIds = this.formSelectedOptions.map((item) => item.id);
    },
    /** 生成送货单 */
    handleBuildDelivery() {},
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
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      const customerDeptId = value[value.length - 1];
      this.queryParams.customerDeptId = customerDeptId;
      this.queryParams.customerId = this.customerDeptMap[customerDeptId];
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
  },
};
</script>

<style lang="scss" scoped>
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
