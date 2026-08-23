<template>
  <div class="app-container">
    <el-card>
      <!-- 报价表单 -->
      <template #header>
        <span>报价信息</span>
        <el-form
          ref="quoteForm"
          :model="quoteForm"
          :rules="rules"
          size="default"
          inline
          label-width="100px"
          :disabled="isViewMode"
        >
          <el-form-item label="报价客户" prop="customerId">
            <el-select
              v-model="quoteForm.customerId"
              placeholder="请选择报价客户"
              disabled
            >
              <el-option
                v-for="item in customerOptions"
                :key="item.id"
                :label="item.alias ? item.alias : item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="生效时间" prop="effectiveDateRange">
            <el-date-picker
              type="daterange"
              v-model="quoteForm.effectiveDateRange"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              clearable
            ></el-date-picker>
          </el-form-item>
          <el-form-item prop="quoteCode">
            <template #label>
              报价编号
              <el-icon
                v-if="isAddMode || isCopyMode"
                @click="refreshQuoteCode"
                style="cursor: pointer"
                ><Refresh
              /></el-icon>
            </template>
            <el-input
              v-model="quoteForm.quoteCode"
              placeholder="请输入报价编号"
              disabled
            >
            </el-input>
          </el-form-item>
          <el-form-item label="报价备注" prop="remark">
            <el-input v-model="quoteForm.remark" placeholder="请输入报价备注">
            </el-input>
          </el-form-item>
        </el-form>
      </template>

      <!-- 报价表格 -->
      <div class="quote-table-panel">
        <div class="quote-table-toolbar">
          <div class="toolbar-left">
            <span class="panel-title">报价表格</span>
            <el-tag v-if="anomalyCount > 0" type="warning" size="small" effect="plain">
              {{ anomalyCount }} 项涨幅异常
            </el-tag>
            <el-tooltip
              content="勾选商品行后点击「批量调价」，输入涨幅%统一调整新报价；新报价相对上次价上涨超过 30% 或下跌超过 10% 会标黄预警"
              placement="top"
            >
              <el-icon class="toolbar-hint"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="toolbar-right">
            <el-button
              v-if="!isViewMode"
              type="primary"
              plain
              size="small"
              :icon="TrendCharts"
              @click="handleBatchAdjust"
              >批量调价</el-button
            >
          </div>
        </div>

        <vxe-table
          border
          show-overflow
          keep-source
          ref="xTable"
          :row-config="{ isHover: true }"
          :mouse-config="{ selected: true }"
          :checkbox-config="{ highlight: true, range: false }"
          :keyboard-config="keyboardConfig"
          :menu-config="isViewMode ? undefined : menuConfig"
          :edit-rules="validRules"
          :edit-config="editConfig"
          :cell-class-name="cellClassName"
          :data="skuQuoteList"
          @menu-click="handleMenuClick"
        >
          <vxe-column type="checkbox" width="48" fixed="left"></vxe-column>
          <vxe-column type="seq" width="56" fixed="left"></vxe-column>
          <vxe-column
            field="productName"
            title="商品名称"
            fixed="left"
            min-width="180"
            :filters="productFilters"
            :filter-method="filterProductNameMethod"
          >
            <template #filter="{ $panel, column }">
              <el-input
                type="text"
                v-for="(option, index) in column.filters"
                :key="index"
                v-model="option.data"
                @input="$panel.changeOption($event, !!option.data, option)"
              />
            </template>
          </vxe-column>
          <vxe-column field="categoryName" title="商品分类" min-width="110"></vxe-column>
          <vxe-column field="productCode" title="商品编号" min-width="110"></vxe-column>
          <vxe-column field="productUnit" title="商品单位" width="90"></vxe-column>
          <vxe-column
            field="basePrice"
            title="上次价"
            align="right"
            width="110"
            :formatter="priceFormatter"
          ></vxe-column>
          <vxe-column field="productSpec" title="商品规格" width="120"></vxe-column>
          <vxe-column
            field="price"
            title="新报价"
            align="right"
            min-width="140"
            :edit-render="{ name: '$input', autoselect: true }"
          >
            <template #default="{ row }">
              <span class="price-cell" :class="{ 'has-anomaly': isPriceAnomaly(row) }">
                {{ formatPrice(row.price) }}
                <el-tooltip
                  v-if="isPriceAnomaly(row)"
                  content="涨幅异常：相对上次价涨幅>30% 或跌幅>10%"
                  placement="top"
                >
                  <span class="anomaly-dot">!</span>
                </el-tooltip>
              </span>
            </template>
          </vxe-column>
          <vxe-column field="op" title="操作" fixed="right" width="70" align="center">
            <template #default="{ row, rowIndex }">
              <el-button
                v-if="!isViewMode"
                link
                type="danger"
                size="small"
                @click="handleRemoveRow(rowIndex)"
                >删除</el-button
              >
            </template>
          </vxe-column>
        </vxe-table>
      </div>

      <!-- 底部工具栏 -->
      <el-form label-width="100px">
        <el-form-item
          style="text-align: center; margin-left: -100px; margin-top: 10px"
        >
          <el-button type="primary" @click="submitForm()" :disabled="isViewMode"
            >提交</el-button
          >
          <el-button @click="close()">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { listCustomerSkuPool } from "@/api/product/customerSku";
import {
  getQuote,
  genQuoteCode,
  createSkuQuote,
  updateSkuQuote,
} from "@/api/product/quote";
import { listQuoteDetail } from "@/api/product/quoteDetail";
import { listCustomer } from "@/api/partner/customer";
import { Refresh, TrendCharts, QuestionFilled } from "@element-plus/icons-vue";

import XEUtils from "xe-utils";
const quotePage = { path: "/basicInfo/quote" };

export default {
  name: "QuoteDetail",
  dicts: ["biz_yes_no"],
  productFilters: [{ data: "" }],
  setup() {
    return { Refresh, TrendCharts, QuestionFilled };
  },
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
      // 默认客户ID
      defaultCustomerId: null,
      // 默认报价ID
      defaultQuoteId: null,
      // 报价表单
      quoteForm: {
        quoteId: null,
        customerId: null,
        quoteCode: null,
        remark: null,
        effectiveStartDate: null,
        effectiveEndDate: null,
        quoteDetails: [],
        effectiveDateRange: [],
      },
      // 客户列表数据
      customerOptions: [],
      // 客户 sku 报价列表
      skuQuoteList: [],
      // 异常预警基准：新报价相对上次价（sku 参考售价）的偏移阈值
      anomalyThresholds: { up: 0.3, down: -0.1 },
      // 键盘配置（Excel 式）：isEnter 在「新报价」列向下移动、isTab 横向移动，
      // 均由 vxe-table 默认编辑键盘处理；仅「新报价」列可编辑（见 checkTableActive），
      // 其余列为只读，Enter/Tab 不会误触编辑，通过 Tab 横向浏览。
      keyboardConfig: {
        isArrow: true,
        isDel: true,
        isEnter: true,
        isTab: true,
        isEdit: true,
        isChecked: true,
      },
      // 右键菜单：向下填充 / 向上填充
      menuConfig: {
        body: {
          options: [
            [
              { code: "fillDown", name: "向下填充", prefixIcon: "vxe-icon-arrow-down" },
              { code: "fillUp", name: "向上填充", prefixIcon: "vxe-icon-arrow-up" },
            ],
          ],
        },
        trigger: "cell",
        className: "quote-context-menu",
      },
      // 编辑配置
      editConfig: {
        trigger: "click",
        mode: "cell",
        showStatus: true,
        beforeEditMethod: this.checkTableActive,
      },
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "报价客户不能为空", trigger: "change" },
        ],
        effectiveDateRange: [
          {
            required: true,
            message: "报价生效时间不能为空",
            trigger: "change",
          },
        ],
        quoteCode: [
          { required: true, message: "报价编号不能为空", trigger: "blur" },
        ],
      },
      // 报价明细列表校验
      validRules: {
        price: [
          { required: true, message: "商品单价必须填写" },
          { type: "number", min: 0, message: "请输入正数" },
        ],
      },
    };
  },
  watch: {
    // 监听 effectiveDateRange
    "quoteForm.effectiveDateRange": {
      handler(val) {
        if (val) {
          this.quoteForm.effectiveStartDate = val[0];
          this.quoteForm.effectiveEndDate = val[1];
        } else {
          this.quoteForm.effectiveStartDate = null;
          this.quoteForm.effectiveEndDate = null;
        }
      },
      deep: true,
    },
  },
  computed: {
    isAddMode() {
      return this.$route.query.mode == "add";
    },
    isViewMode() {
      return this.$route.query.mode == "view";
    },
    isCopyMode() {
      return this.$route.query.mode == "copy";
    },
    /** 涨幅异常的行数 */
    anomalyCount() {
      return this.skuQuoteList.filter((row) => this.isPriceAnomaly(row)).length;
    },
  },
  created() {
    // 从路由获取参数
    const customerIdFromParams = this.$route.params.customerId;
    const quoteIdFromQuery = this.$route.query.quoteId;
    this.defaultCustomerId = customerIdFromParams
      ? parseInt(customerIdFromParams, 10)
      : null;
    this.defaultQuoteId = quoteIdFromQuery
      ? parseInt(quoteIdFromQuery, 10)
      : null;
    // 设置查询参数
    this.quoteForm.customerId = this.defaultCustomerId;
    this.quoteForm.quoteId = this.defaultQuoteId;

    // 初始化数据
    this.getCustomerList();
    this.getQuoteCode();
    // 初始化表单+表格
    this.initSkuQuoteData();
  },
  methods: {
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
      });
    },
    /** 获取当前报价单号 */
    getQuoteCode() {
      genQuoteCode({ refresh: true }).then((response) => {
        this.quoteForm.quoteCode = response.msg;
      });
    },
    /** 刷新当前报价单号 */
    refreshQuoteCode() {
      genQuoteCode({ refresh: true }).then((response) => {
        this.quoteForm.quoteCode = response.msg;
      });
    },
    /** 初始化报价表单+明细列表 */
    async initSkuQuoteData() {
      const quoteId = this.quoteForm.quoteId;
      const quoteCode = this.quoteForm.quoteCode;
      let skuQuoteList = [];

      try {
        // 从客户商品池初始化报价明细（重构后标准SKU无客户维度，报价面向客户商品池）
        if (!this.quoteForm.customerId) {
          this.skuQuoteList = [];
          return;
        }
        const response = await listCustomerSkuPool({
          customerId: this.quoteForm.customerId,
        });
        const skuList = response.data || [];
        skuQuoteList = skuList.map((item) => ({
          customerId: this.quoteForm.customerId,
          categoryName: item.categoryName,
          quoteId: null,
          skuId: item.skuId,
          productCode: item.skuCode,
          productName: item.alias || item.skuName,
          productUnit: item.skuUnit,
          productSpec: item.skuSpecName,
          // 上次价（sku 参考售价）作为涨幅预警的基准
          basePrice: item.skuSalePrice,
          price: "0.00",
        }));

        if (!quoteId) {
          this.skuQuoteList = skuQuoteList;
          return;
        }

        // 获取报价信息
        const quoteResponse = await getQuote(quoteId);
        const quote = quoteResponse.data || {};
        this.quoteForm = {
          quoteId: quote.id,
          quoteCode: quote.code,
          effectiveDateRange: [
            quote.effectiveStartDate,
            quote.effectiveEndDate,
          ],
          ...quote,
        };

        // 获取报价详情
        const detailResponse = await listQuoteDetail({ quoteId });
        const quoteList = detailResponse.data || [];
        skuQuoteList = skuQuoteList.map((item) => {
          const quoteItem = quoteList.find((q) => q.skuId === item.skuId);
          if (quoteItem) {
            return {
              ...item,
              quoteId: quoteItem.quoteId,
              price: quoteItem.price,
            };
          } else {
            return item;
          }
        });

        // 若当前为复制模式，重置 quoteId & quoteCode
        if (this.isCopyMode) {
          this.defaultQuoteId = null;
          this.quoteForm.quoteId = null;
          this.quoteForm.quoteCode = quoteCode;
          skuQuoteList = skuQuoteList.map((item) => ({
            ...item,
            quoteId: null,
          }));
        }
        this.skuQuoteList = skuQuoteList;

        // 刷新表格状态
        this.$refs.xTable.reloadData(this.skuQuoteList);
      } catch (error) {
        console.error("Error occurred during initialization:", error);
        // 可以选择显示错误消息给用户
      }
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["quoteForm"].validate((valid) => {
        if (valid) {
          // set quoteDetails
          if (this.skuQuoteList.length == 0) {
            this.$modal.msgError("报价明细列表不能为空！");
            return;
          }
          this.quoteForm.quoteDetails = this.skuQuoteList;

          // save or update
          if (this.quoteForm.quoteId != null) {
            updateSkuQuote(this.quoteForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功");
                this.$tab.closeOpenPage(quotePage);
              }
            });
          } else {
            createSkuQuote(this.quoteForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("新增成功");
                this.$tab.closeOpenPage(quotePage);
              }
            });
          }
        }
      });
    },
    /** 返回按钮 */
    close() {
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前报价单有改动，是否确认关闭？")
          .then(() => {
            this.$tab.closeOpenPage(quotePage);
          })
          .catch(() => {});
      } else {
        this.$tab.closeOpenPage(quotePage);
      }
    },
    /** 格式化商品单价 */
    priceFormatter({ row }) {
      const n = this.parseNum(row.price);
      if (n === null || n === "") return "";
      const val = n < 0 ? 0 : n;
      return XEUtils.commafy(val, { digits: 2 });
    },
    /** 格式化价格（供模板用于展示，保留两位小数） */
    formatPrice(value) {
      const n = this.parseNum(value);
      if (n === null || n === "") return "";
      return XEUtils.commafy(n < 0 ? 0 : n, { digits: 2 });
    },
    /** vxe表格-过滤商品名称方法 */
    filterProductNameMethod({ option, row }) {
      if (row.productName.indexOf(option.data) > -1) {
        return row.productName;
      }
    },
    /** vxe表格-全局禁用编辑（仅「新报价」列可编辑） */
    checkTableActive({ row, column }) {
      return !this.isViewMode && column.field === "price";
    },
    /** vxe表格检测是否改动 */
    checkTableUpdted() {
      return this.$refs.xTable.getUpdateRecords().length > 0;
    },
    /** 安全转数字（兼容逗号、空格、空值） */
    parseNum(value) {
      if (value === null || value === undefined || value === "") return null;
      const n = parseFloat(String(value).replace(/[,，\s]/g, ""));
      return isNaN(n) ? null : n;
    },
    /** 保留两位小数 */
    round2(value) {
      return Math.round(parseFloat(value) * 100) / 100;
    },
    /**
     * 判断某行新报价是否涨幅异常：
     * 仅当已填写且>0 的真实报价才参与判定（未填写的客户池商品 price=0 不误报）
     */
    isPriceAnomaly(row) {
      if (this.isViewMode) return false;
      const price = this.parseNum(row.price);
      const base = this.parseNum(row.basePrice);
      if (price === null || price <= 0 || base === null || base <= 0) return false;
      const dev = (price - base) / base;
      return dev > this.anomalyThresholds.up || dev < this.anomalyThresholds.down;
    },
    /** vxe 单元格样式：异常预警（橙色背景/边框） */
    cellClassName({ row, column }) {
      if (column.field === "price" && this.isPriceAnomaly(row)) {
        return "quote-anomaly-cell";
      }
      return "";
    },
    /** 右键菜单点击 */
    handleMenuClick({ row, rowIndex, column, menu }) {
      if (this.isViewMode) return;
      if (menu.code === "fillDown") {
        this.fillPrice(rowIndex, row, 1);
      } else if (menu.code === "fillUp") {
        this.fillPrice(rowIndex, row, -1);
      }
    },
    /**
     * 向下/向上填充：把当前行的新报价复制到其下的所有行（direction=1）
     * 或其上的所有行（direction=-1）。勾选仅用于「批量调价」；此处以表格边界为填充范围。
     */
    fillPrice(rowIndex, row, direction) {
      const val = this.parseNum(row.price);
      if (val === null || val === "") {
        this.$modal.msgWarning("当前行的新报价为空，无法填充");
        return;
      }
      const rows = this.skuQuoteList;
      if (direction > 0) {
        // 向下填充：复制到以下所有行
        for (let i = rowIndex + 1; i < rows.length; i++) {
          rows[i].price = row.price;
        }
      } else {
        // 向上填充：复制到以上所有行
        for (let i = rowIndex - 1; i >= 0; i--) {
          rows[i].price = row.price;
        }
      }
      this.refreshTable();
      this.$modal.msgSuccess("已填充，请检查后提交");
    },
    /** 批量调价：输入涨幅%，对勾选行统一计算新报价 */
    handleBatchAdjust() {
      const selected = this.$refs.xTable
        ? this.$refs.xTable.getCheckboxRecords()
        : [];
      if (!selected.length) {
        this.$modal.msgWarning("请先勾选需要调价的商品行");
        return;
      }
      this.$modal
        .prompt("请输入涨幅%（正数提价，负数降价，如 5 表示 +5%）")
        .then(({ value }) => {
          const pct = parseFloat(value);
          if (isNaN(pct)) {
            this.$modal.msgWarning("请输入有效的百分比");
            return;
          }
          let count = 0;
          selected.forEach((row) => {
            // 以当前新报价为基准；若为空则以上次价为基准
            let base = this.parseNum(row.price);
            if (base === null || base <= 0) {
              base = this.parseNum(row.basePrice);
            }
            if (base !== null && base > 0) {
              row.price = this.round2(base * (1 + pct / 100));
              count++;
            }
          });
          this.refreshTable();
          this.$modal.msgSuccess(
            "已批量调价 " + count + " 项（" + (pct >= 0 ? "+" : "") + pct + "%）"
          );
        })
        .catch(() => {});
    },
    /** 删除报价行 */
    handleRemoveRow(rowIndex) {
      this.$modal
        .confirm("确认删除该商品报价行？")
        .then(() => {
          this.skuQuoteList.splice(rowIndex, 1);
          this.refreshTable();
          this.$modal.msgSuccess("删除成功，提交后生效");
        })
        .catch(() => {});
    },
    /** 刷新表格（保留勾选） */
    refreshTable() {
      this.$nextTick(() => {
        if (this.$refs.xTable) {
          this.$refs.xTable.refreshData();
        }
      });
    },
  },
};
</script>

<style scoped>
:deep(.el-card__header) {
  padding-bottom: 0px;
}
:deep(.el-card__body) {
  padding-top: 10px;
}

.quote-table-panel {
  margin-top: 4px;
}
.quote-table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.panel-title {
  font-weight: 600;
}
.toolbar-hint {
  cursor: pointer;
  color: #909399;
  font-size: 15px;
}
.toolbar-right {
  display: flex;
  align-items: center;
}

/* 异常预警单元格：橙色背景 + 边框 */
:deep(.vxe-table--body .quote-anomaly-cell) {
  background-color: #fff3e0 !important;
  box-shadow: inset 0 0 0 1px #f5a623;
}
:deep(.vxe-table--body .quote-anomaly-cell:focus) {
  background-color: #fff3e0 !important;
}

.price-cell {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.anomaly-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #f5a623;
  color: #fff;
  font-size: 11px;
  line-height: 14px;
  cursor: help;
}
</style>
