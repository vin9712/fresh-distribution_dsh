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
      <el-table-column label="送货单编号" align="center" prop="code" width="200" />
      <el-table-column label="客户" align="center" prop="customerName" />
      <el-table-column label="配送点" align="center" prop="customerDeptName" />
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
          <dict-tag
            :options="dict.type.t_delivery_order_status"
            :value="scope.row.status"
          />
        </template>
      </el-table-column>
      <el-table-column label="打印次数" align="center" prop="printCount" width="90" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
        width="260"
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
            :icon="Printer"
            :disabled="scope.row.status === 2"
            @click="handlePrint(scope.row)"
            v-hasPermi="['order:delivery:print']"
            >打印</el-button
          >
          <el-button
            size="small"
            link
            :icon="Van"
            :disabled="scope.row.status === 2"
            @click="handleDeliver(scope.row)"
            v-hasPermi="['order:delivery:deliver']"
            >送达</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['order:delivery:remove']"
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

    <!-- 送货单明细对话框（按商品合并行） -->
    <el-dialog :title="detailTitle" v-model="detailOpen" width="760px" append-to-body>
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
  </div>
</template>

<script>
import {
  pageDelivery,
  listDeliveryDetail,
  generateDelivery,
  printDelivery,
  printInfoDelivery,
  deliverDelivery,
  delDelivery,
} from "@/api/order/delivery";
import { getToken } from "@/utils/auth";
import { Search, Refresh, Plus, Delete, Download, Printer, Van, View } from "@element-plus/icons-vue";

export default {
  name: "Delivery",
  dicts: ["t_delivery_order_status"],
  setup() {
    return { Search, Refresh, Plus, Delete, Download, Printer, Van, View };
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
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        code: null,
        status: null,
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
    /** 生成送货单 */
    handleGenerate() {
      const deliveryDate = this.generateDate;
      if (!deliveryDate) {
        this.$modal.msgWarning("请先选择配送日期");
        return;
      }
      this.$modal
        .confirm("将为配送日期 " + deliveryDate + " 生成送货单（仅汇总已确认订单，按客户+配送点分组、明细按商品合并）？")
        .then(() => {
          return generateDelivery(deliveryDate);
        })
        .then((response) => {
          const created = response.data || [];
          this.$modal.msgSuccess("已生成 " + created.length + " 张送货单");
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
              const token = getToken();
              window.open(
                "/jmreport/view/" + info.templateId + "?token=" + token + "&deliveryOrderId=" + row.id,
                "_blank"
              );
              this.getPageList();
            })
            .catch(() => {});
        })
        .catch(() => {});
    },
    /** 标记送达 */
    handleDeliver(row) {
      this.$modal
        .confirm("确认送货单编号为 " + row.code + " 已送达？（同组已确认订单将进入已配送状态）")
        .then(() => {
          return deliverDelivery(row.id);
        })
        .then(() => {
          this.$modal.msgSuccess("已标记送达");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      this.$modal
        .confirm('是否确认删除送货单编号为"' + row.code + '"的数据项？')
        .then(function () {
          return delDelivery(row.id);
        })
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
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
</style>
