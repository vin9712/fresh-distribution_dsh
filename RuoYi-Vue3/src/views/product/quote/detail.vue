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
            <span v-if="skuQuoteList.length" class="panel-count"
              >共 {{ skuQuoteList.length }} 项</span
            >
          </div>
          <div class="toolbar-right">
            <el-button
              v-if="!isViewMode"
              type="primary"
              plain
              size="small"
              :icon="Plus"
              @click="openSkuPicker"
              >添加商品</el-button
            >
            <el-button
              v-if="!isViewMode"
              type="success"
              plain
              size="small"
              :icon="TrendCharts"
              @click="handleBatchAdjust"
              >批量调价</el-button
            >
          </div>
        </div>

        <!-- 空客户商品引导：与录单页一致，直接展示商品库供挑选录入 -->
        <el-alert
          v-if="!isViewMode && skuQuoteList.length === 0"
          class="quote-empty-hint"
          type="info"
          :closable="false"
          show-icon
        >
          <template #title>
            当前客户还没有客户商品。点「添加商品」从商品库批量挑选（支持搜索、多选、自定义客户别名），
            选中的商品会自动加入该客户的客户商品，下次录单/报价可直接选择。
          </template>
        </el-alert>

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
          ></vxe-column>          <vxe-column field="productSpec" title="商品规格" width="120"></vxe-column>
          <vxe-column
            field="price"
            title="新报价"
            align="right"
            min-width="140"
            :edit-render="{ name: '$input', autoselect: true }"
          >
            <template #default="{ row }">
              <span class="price-cell">{{ formatPrice(row.price) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="op" title="操作" fixed="right" width="120" align="center">
            <template #default="{ row, rowIndex }">
              <template v-if="!isViewMode">
                <el-button
                  v-if="row.skuId"
                  link
                  type="primary"
                  size="small"
                  @click="handleEditAlias(row)"
                  >别名</el-button
                >
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="handleRemoveRow(rowIndex)"
                  >删除</el-button
                >
              </template>
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

    <!-- 商品库选品弹窗：批量挑选标准SKU + 自定义客户别名 + 同步客户商品 -->
    <el-dialog
      align-center
      title="从商品库添加商品"
      v-model="skuPicker.open"
      width="920px"
      append-to-body
      :close-on-click-modal="false"
      @closed="handleSkuPickerClosed"
    >
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
        <template #title>
          勾选商品即可批量加入报价；「客户别名」留空则使用商品原名，「参考售价」可直接改。
          确认后：参考价填入「新报价」列；新商品无上次价（显示 -）；并自动同步为该客户的客户商品。
        </template>
      </el-alert>
      <div class="sku-picker-bar">
        <el-input
          v-model="skuPicker.keyword"
          placeholder="商品名 / 编号 / 助记码 / 规格 / 分类"
          clearable
          size="small"
          style="width: 260px"
          :prefix-icon="Search"
        />
        <el-select
          v-model="skuPicker.categoryId"
          placeholder="全部分类"
          clearable
          filterable
          size="small"
          style="width: 160px"
        >
          <el-option
            v-for="c in skuCategoryOptions"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-checkbox v-model="skuPicker.hidePicked" size="small"
          >隐藏已在报价的商品</el-checkbox
        >
        <span class="sku-picker-count">
          已选 <b>{{ skuPickerSelectedCount }}</b> / 共
          {{ skuPickerFiltered.length }} 个
        </span>
        <el-link
          v-if="skuPickerSelectedCount"
          type="danger"
          :underline="false"
          style="font-size: 12px"
          @click="clearSkuPickerSelection"
          >清空已选</el-link
        >
      </div>
      <el-table
        ref="skuPickerTable"
        :data="skuPickerPaged"
        height="380"
        size="small"
        border
        row-key="id"
        @selection-change="handleSkuPickerSelectionChange"
      >
        <el-table-column
          type="selection"
          width="45"
          reserve-selection
          :selectable="skuPickerSelectable"
        />
        <el-table-column label="编号" prop="code" width="105" align="center" />
        <el-table-column label="商品名称" prop="name" min-width="150" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="60" align="center" />
        <el-table-column label="规格" prop="specName" width="95" show-overflow-tooltip />
        <el-table-column label="分类" prop="categoryName" width="105" show-overflow-tooltip />
        <el-table-column label="参考售价" width="130" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row._refPrice"
              size="small"
              :min="0"
              :precision="2"
              :step="0.5"
              :controls="false"
              :disabled="row._inQuote"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="客户别名" width="170">
          <template #default="{ row }">
            <el-input
              v-model="row._alias"
              size="small"
              clearable
              :placeholder="row.name"
              :disabled="row._inQuote"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._inQuote" type="info" size="small">已在报价</el-tag>
            <el-tag v-else-if="row._inPool" type="success" size="small"
              >已是客户商品</el-tag
            >
            <el-tag v-else type="warning" size="small">新商品</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="skuPicker.pageNum"
        v-model:page-size="skuPicker.pageSize"
        layout="total, prev, pager, next, sizes"
        small
        :total="skuPickerFiltered.length"
        :page-sizes="[10, 20, 50, 100]"
        style="margin-top: 8px; justify-content: flex-end"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="skuPicker.open = false">取 消</el-button>
          <el-button
            type="primary"
            :loading="skuPicker.submitting"
            :disabled="!skuPickerSelectedCount"
            @click="confirmSkuPicker"
            >添加 {{ skuPickerSelectedCount }} 个商品</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  listCustomerSkuPool,
  addCustomerSku,
  updateCustomerSku,
} from "@/api/product/customerSku";
import { listSku } from "@/api/product/sku";
import {
  getQuote,
  genQuoteCode,
  createSkuQuote,
  updateSkuQuote,
} from "@/api/product/quote";
import { listQuoteDetail } from "@/api/product/quoteDetail";
import { listCustomer } from "@/api/partner/customer";
import { Refresh, TrendCharts, Plus, Search } from "@element-plus/icons-vue";

import XEUtils from "xe-utils";
const quotePage = { path: "/basicInfo/quote" };

export default {
  name: "QuoteDetail",
  dicts: ["biz_yes_no"],
  productFilters: [{ data: "" }],
  setup() {
    return { Refresh, TrendCharts, Plus, Search };
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
      // 客户商品池原始数据（含 id/alias，用于别名同步与选品状态）
      customerSkuPool: [],
      // 全部标准SKU（商品库选品数据源）
      allSkuOptions: [],
      // 商品库选品弹窗
      skuPicker: {
        open: false,
        keyword: "",
        categoryId: null,
        hidePicked: false,
        pageNum: 1,
        pageSize: 20,
        rows: [],
        submitting: false,
      },
      // 客户列表数据
      customerOptions: [],
      // 客户 sku 报价列表
      skuQuoteList: [],
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
    // 选品弹窗：关键字/分类变化时回到第一页
    "skuPicker.keyword"() {
      this.skuPicker.pageNum = 1;
    },
    "skuPicker.categoryId"() {
      this.skuPicker.pageNum = 1;
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
    /** 选品弹窗：按关键字/分类过滤商品库 */
    skuPickerFiltered() {
      const kw = (this.skuPicker.keyword || "").trim().toLowerCase();
      return this.skuPicker.rows.filter((r) => {
        if (
          this.skuPicker.categoryId &&
          r.categoryId !== this.skuPicker.categoryId
        ) {
          return false;
        }
        if (this.skuPicker.hidePicked && r._inQuote) return false;
        if (!kw) return true;
        return [r.name, r.code, r.mnemonicCode, r.specName, r.categoryName].some(
          (v) => v && String(v).toLowerCase().includes(kw)
        );
      });
    },
    /** 选品弹窗：当前页数据 */
    skuPickerPaged() {
      const start =
        (this.skuPicker.pageNum - 1) * this.skuPicker.pageSize;
      return this.skuPickerFiltered.slice(
        start,
        start + this.skuPicker.pageSize
      );
    },
    /** 选品弹窗：已勾选数量 */
    skuPickerSelectedCount() {
      return this.skuPicker.rows.filter((r) => r._selected).length;
    },
    /** 选品弹窗：分类选项（从商品库推导，免额外请求） */
    skuCategoryOptions() {
      const map = new Map();
      this.allSkuOptions.forEach((s) => {
        if (s.categoryId && !map.has(s.categoryId)) {
          map.set(s.categoryId, {
            id: s.categoryId,
            name: s.categoryName || "未分类",
          });
        }
      });
      return Array.from(map.values());
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

    // 新增报价：优先使用列表页建单弹窗传入的生效时间/备注，缺省给 7 天有效期
    if (this.isAddMode) {
      const q = this.$route.query || {};
      if (q.startDate && q.endDate) {
        this.quoteForm.effectiveDateRange = [q.startDate, q.endDate];
      } else {
        const now = new Date();
        const later = new Date(now.getTime() + 3600 * 1000 * 24 * 7);
        this.quoteForm.effectiveDateRange = [
          this.formatDateValue(now),
          this.formatDateValue(later),
        ];
      }
      if (q.remark) {
        this.quoteForm.remark = q.remark;
      }
    }

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
        this.customerSkuPool = skuList;
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
          this._localDirty = false;
          this.maybeAutoOpenSkuPicker();
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
        this._localDirty = false;
        this.maybeAutoOpenSkuPicker();
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
    /** 格式化商品单价（上次价：无报价显示 -） */
    priceFormatter({ row }) {
      const n = this.parseNum(row.basePrice);
      if (n === null || n === "" || n <= 0) return "-";
      return XEUtils.commafy(n, { digits: 2 });
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
    /** vxe表格检测是否改动（含选品新增/删行/别名等本地改动） */
    checkTableUpdted() {
      if (this._localDirty) return true;
      return this.$refs.xTable.getUpdateRecords().length > 0;
    },
    /** 安全转数字（兼容逗号、空格、空值） */
    parseNum(value) {
      if (value === null || value === undefined || value === "") return null;
      const n = parseFloat(String(value).replace(/[,，\s]/g, ""));
      return isNaN(n) ? null : n;
    },
    /** 日期格式化为 YYYY-MM-DD */
    formatDateValue(date) {
      if (!(date instanceof Date) || isNaN(date.getTime())) return "";
      const y = date.getFullYear();
      const m = String(date.getMonth() + 1).padStart(2, "0");
      const d = String(date.getDate()).padStart(2, "0");
      return `${y}-${m}-${d}`;
    },
    /** 保留两位小数 */
    round2(value) {
      return Math.round(parseFloat(value) * 100) / 100;
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
          this._localDirty = true;
          this.refreshTable(true);
          this.$modal.msgSuccess("删除成功，提交后生效");
        })
        .catch(() => {});
    },

    // ==================== 商品库选品（批量新增客户商品 + 别名） ====================

    /** 加载客户商品池原始数据（含 id/alias，供别名同步与选品状态用） */
    async loadCustomerSkuPool() {
      if (!this.quoteForm.customerId) {
        this.customerSkuPool = [];
        return [];
      }
      const response = await listCustomerSkuPool({
        customerId: this.quoteForm.customerId,
      });
      this.customerSkuPool = response.data || [];
      return this.customerSkuPool;
    },
    /** 加载全部标准SKU（选品数据源，带缓存） */
    async loadAllSkuOptions() {
      if (this.allSkuOptions.length) return this.allSkuOptions;
      const response = await listSku({});
      this.allSkuOptions = (response.data || []).filter((s) => !s.isDeleted);
      return this.allSkuOptions;
    },
    /** 空客户商品时自动打开选品弹窗（仅新增报价、仅一次） */
    maybeAutoOpenSkuPicker() {
      if (!this.isAddMode || this.isViewMode) return;
      if (this._pickerAutoOpened) return;
      if (!this.quoteForm.customerId) return;
      if ((this.skuQuoteList || []).length > 0) return;
      this._pickerAutoOpened = true;
      this.$nextTick(() => this.openSkuPicker());
    },
    /** 打开选品弹窗 */
    async openSkuPicker() {
      if (!this.quoteForm.customerId) {
        this.$modal.msgWarning("请先选择报价客户");
        return;
      }
      this.skuPicker.keyword = "";
      this.skuPicker.categoryId = null;
      this.skuPicker.pageNum = 1;
      this.skuPicker.open = true;
      try {
        await Promise.all([this.loadAllSkuOptions(), this.loadCustomerSkuPool()]);
        this.buildSkuPickerRows();
        this.$nextTick(() => this.clearSkuPickerSelection());
      } catch (e) {
        // 错误提示由请求拦截器统一处理
      }
    },
    /** 构建选品行：标注已在报价/已是客户商品，并回填已有别名 */
    buildSkuPickerRows() {
      const inQuote = new Set(
        (this.skuQuoteList || []).map((r) => r.skuId).filter((v) => v != null)
      );
      const poolMap = new Map();
      (this.customerSkuPool || []).forEach((p) => {
        if (p.skuId != null && !poolMap.has(p.skuId)) poolMap.set(p.skuId, p);
      });
      this.skuPicker.rows = this.allSkuOptions.map((sku) => {
        const pool = poolMap.get(sku.id);
        return {
          ...sku,
          _inQuote: inQuote.has(sku.id),
          _inPool: !!pool,
          _alias: pool && pool.alias ? pool.alias : "",
          // 参考价：默认取商品售价，允许在弹窗内直接改；确认后写入「新报价」列
          _refPrice:
            sku.salePrice != null && sku.salePrice !== ""
              ? Number(sku.salePrice)
              : null,
          _selected: false,
        };
      });
    },
    /** 选品弹窗：已在报价的商品不可重复勾选 */
    skuPickerSelectable(row) {
      return !row._inQuote;
    },
    /** 选品弹窗：勾选变化（跨页保留） */
    handleSkuPickerSelectionChange(rows) {
      const selected = new Set((rows || []).map((r) => r.id));
      this.skuPicker.rows.forEach((r) => {
        r._selected = selected.has(r.id);
      });
    },
    /** 选品弹窗：清空勾选 */
    clearSkuPickerSelection() {
      if (this.$refs.skuPickerTable) {
        this.$refs.skuPickerTable.clearSelection();
      }
      this.skuPicker.rows.forEach((r) => (r._selected = false));
    },
    /** 选品弹窗：关闭后清理，避免下次残留 */
    handleSkuPickerClosed() {
      this.skuPicker.rows = [];
      this.skuPicker.keyword = "";
      this.skuPicker.categoryId = null;
      this.skuPicker.hidePicked = false;
    },
    /**
     * 把选中的标准SKU同步为客户商品：
     * - 不存在 → 新增客户商品（带别名）
     * - 已存在 → 别名有变化时个性化更新（空字符串可清空别名）
     * 返回失败项，便于只把成功的行加入报价（避免刷新后明细丢失）。
     */
    async syncSkuToCustomerPool(selected) {
      const failures = [];
      for (const sku of selected) {
        const alias = (sku._alias || "").trim();
        const poolItem = (this.customerSkuPool || []).find(
          (p) => p.skuId === sku.id
        );
        try {
          if (poolItem) {
            const oldAlias = (poolItem.alias || "").trim();
            if (alias !== oldAlias) {
              await updateCustomerSku({ id: poolItem.id, alias });
            }
          } else {
            await addCustomerSku({
              customerId: this.quoteForm.customerId,
              skuId: sku.id,
              alias: alias || null,
              minOrderQty: 1,
              orderStep: 1,
            });
          }
        } catch (e) {
          const msg = (e && (e.message || e.msg)) || "同步失败";
          // 并发/重复：后端已存在同客户同SKU，视为成功
          if (String(msg).includes("已存在")) continue;
          failures.push({ id: sku.id, name: sku.name, reason: msg });
        }
      }
      return failures;
    },
    /** 选品弹窗：确认添加 */
    async confirmSkuPicker() {
      const selected = this.skuPicker.rows.filter((r) => r._selected);
      if (!selected.length) {
        this.$modal.msgWarning("请先勾选要添加的商品");
        return;
      }
      this.skuPicker.submitting = true;
      try {
        const failures = await this.syncSkuToCustomerPool(selected);
        if (failures.length === selected.length) {
          this.$modal.msgError("同步客户商品失败：" + failures[0].reason);
          return;
        }
        const failIds = new Set(failures.map((f) => f.id));
        const existing = new Set(
          (this.skuQuoteList || []).map((r) => r.skuId).filter((v) => v != null)
        );
        let added = 0;
        selected.forEach((sku) => {
          if (failIds.has(sku.id) || existing.has(sku.id)) return;
          const alias = (sku._alias || "").trim();
          const ref = this.parseNum(sku._refPrice);
          this.skuQuoteList.push({
            customerId: this.quoteForm.customerId,
            categoryName: sku.categoryName,
            quoteId: null,
            skuId: sku.id,
            productCode: sku.code,
            productName: alias || sku.name,
            productUnit: sku.unit,
            productSpec: sku.specName,
            // 新商品无「上次价」基线，显示为空；已是客户商品的保留其售价作基线
            basePrice: sku._inPool ? sku.salePrice : null,
            // 参考价（弹窗内可改）直接落入「新报价」列
            price: ref != null ? this.round2(ref) : "0.00",
          });
          existing.add(sku.id);
          added++;
        });
        if (added > 0) this._localDirty = true;
        this.skuPicker.open = false;
        this.refreshTable(true);
        await this.loadCustomerSkuPool();
        if (failures.length) {
          this.$modal.msgWarning(
            `已添加 ${added} 个商品；${failures.length} 个同步失败：` +
              failures.map((f) => f.name).join("、")
          );
        } else {
          this.$modal.msgSuccess(
            `已添加 ${added} 个商品，并同步为该客户的客户商品`
          );
        }
      } finally {
        this.skuPicker.submitting = false;
      }
    },
    /** 行内修改客户别名（同步客户商品池，保证报价明细不丢） */
    handleEditAlias(row) {
      if (!row || !row.skuId) return;
      const poolItem = (this.customerSkuPool || []).find(
        (p) => p.skuId === row.skuId
      );
      const originalName =
        (poolItem && poolItem.skuName) ||
        (this.allSkuOptions.find((s) => s.id === row.skuId) || {}).name ||
        row.productName;
      const currentAlias = poolItem && poolItem.alias ? poolItem.alias : "";
      this.$modal
        .prompt("留空表示使用商品原名「" + originalName + "」", "修改客户别名", {
          inputValue: currentAlias,
          inputPlaceholder: originalName,
        })
        .then(({ value }) => {
          const alias = (value || "").trim();
          const apply = () => {
            row.productName = alias || originalName;
            this._localDirty = true;
            this.refreshTable(true);
            this.$modal.msgSuccess("别名已更新，提交后生效");
          };
          if (!poolItem) {
            // 该SKU尚未在客户商品池：先新增再改名，避免刷新后明细丢失
            addCustomerSku({
              customerId: this.quoteForm.customerId,
              skuId: row.skuId,
              alias: alias || null,
              minOrderQty: 1,
              orderStep: 1,
            })
              .then(() => this.loadCustomerSkuPool())
              .then(apply)
              .catch(() => {});
          } else {
            updateCustomerSku({ id: poolItem.id, alias })
              .then(() => this.loadCustomerSkuPool())
              .then(apply)
              .catch(() => {});
          }
        })
        .catch(() => {});
    },

    /** 刷新表格（reload=true 用于增删行，否则只刷新单元格值） */
    refreshTable(reload) {
      this.$nextTick(() => {
        const table = this.$refs.xTable;
        if (!table) return;
        if (reload && typeof table.reloadData === "function") {
          table.reloadData(this.skuQuoteList);
        } else if (typeof table.updateData === "function") {
          table.updateData();
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
.panel-count {
  color: #909399;
  font-size: 12px;
}
.quote-empty-hint {
  margin-bottom: 8px;
}
.sku-picker-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.sku-picker-count {
  color: #909399;
  font-size: 12px;
  margin-left: auto;
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

.price-cell {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
