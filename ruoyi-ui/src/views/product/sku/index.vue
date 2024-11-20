<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      :rules="queryFormRules"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="当前客户" prop="customerId">
        <el-select
          v-model="queryParams.customerId"
          filterable
          @change="handleQuery"
        >
          <el-option
            v-for="item in customerOptions"
            :key="item.id"
            :label="item.alias ? item.alias : item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="商品分类" prop="categoryId">
        <el-cascader
          v-model="querySelectedOptions"
          placeholder="请选择商品分类"
          @change="handleQueryCascaderChange"
          :options="categoryOptions"
          :props="{ expandTrigger: 'hover' }"
          :show-all-levels="false"
          filterable
          clearable
        />
      </el-form-item>
      <el-form-item label="商品名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入商品名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否上架" prop="saleable">
        <el-select
          v-model="queryParams.saleable"
          placeholder="请选择是否上架"
          clearable
        >
          <el-option
            v-for="dict in dict.type.biz_yes_no"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否有效" prop="valid">
        <el-select
          v-model="queryParams.valid"
          placeholder="请选择是否有效"
          clearable
        >
          <el-option
            v-for="dict in dict.type.biz_yes_no"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否匹配" prop="matchedSpu">
        <el-select
          v-model="queryParams.matchedSpu"
          placeholder="请选择是否匹配商品库"
          clearable
        >
          <el-option
            v-for="dict in dict.type.biz_yes_no"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
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
          v-hasPermi="['product:sku:add']"
          >新增</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="ids.length == 0"
          @click="handleMatched"
          >匹配商品库</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['product:sku:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="handleImport"
          >导入</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['product:sku:export']"
          >导出</el-button
        >
      </el-col>
      <right-toolbar
        :showSearch.sync="showSearch"
        @queryTable="getPageList"
      ></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="skuList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="商品编号" align="center" prop="code" />
      <el-table-column
        label="商品分类"
        align="center"
        prop="categoryId"
        :formatter="categoryFormatter"
      />
      <el-table-column label="商品名称" align="center" prop="name" />
      <el-table-column label="商品单位" align="center" prop="unit" />
      <el-table-column label="商品规格" align="center" prop="spec" />
      <el-table-column label="当期售价" align="center" prop="salePrice" />
      <el-table-column label="是否上架" align="center" prop="saleable">
        <template slot-scope="scope">
          <dict-tag
            :options="dict.type.biz_yes_no"
            :value="scope.row.saleable"
          />
        </template>
      </el-table-column>
      <el-table-column label="是否有效" align="center" prop="valid">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.biz_yes_no" :value="scope.row.valid" />
        </template>
      </el-table-column>
      <el-table-column label="是否匹配" align="center" prop="matchedSpu">
        <template slot-scope="scope">
          <dict-tag
            :options="dict.type.biz_yes_no"
            :value="scope.row.spuId ? '1' : '0'"
          />
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
            v-hasPermi="['product:sku:edit']"
            >修改</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['product:sku:remove']"
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

    <!-- 添加或修改商品信息对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="当前客户" prop="customerId">
          <el-select v-model="form.customerId" disabled>
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="item.alias ? item.alias : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-cascader
            v-model="formSelectedOptions"
            placeholder="请选择商品分类"
            :options="categoryOptions"
            @change="handleFormOptionsChanged"
            :props="{ expandTrigger: 'hover' }"
            :show-all-levels="false"
            filterable
            clearable
          />
        </el-form-item>
        <el-form-item prop="name">
          <span slot="label"
            >商品名称
            <el-tooltip
              v-if="!form.categoryId"
              content="请先选择商品分类"
              placement="top"
            >
              <i class="el-icon-question"></i> </el-tooltip
          ></span>
          <el-autocomplete
            v-model="form.name"
            @select="handleSelectSpu"
            @change="handleUpdateSkuName"
            @input="handleUpdateMnemonicCode"
            :fetch-suggestions="querySpuList"
            placeholder="请输入商品名称"
            :disabled="!form.categoryId"
            clearable
          ></el-autocomplete>
        </el-form-item>
        <el-form-item label="助记码" prop="mnemonicCode">
          <el-input
            v-model="form.mnemonicCode"
            placeholder="请输入助记码"
            :disabled="form.id == null"
          />
        </el-form-item>
        <el-form-item label="商品单位" prop="unit">
          <el-select
            v-model="form.unit"
            placeholder="请选择商品单位"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option
              v-for="dict in dict.type.t_sku_unit"
              :key="dict.value"
              :label="dict.label"
              :value="dict.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品规格" prop="spec">
          <el-input v-model="form.spec" placeholder="请输入商品规格" />
        </el-form-item>
        <el-form-item label="商品售价" prop="salePrice">
          <el-input-number
            v-model="form.salePrice"
            :precision="2"
            placeholder="请输入商品售价"
          />
        </el-form-item>
        <el-form-item label="是否上架" prop="saleable">
          <el-radio-group v-model="form.saleable">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :label="parseInt(dict.value)"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :label="parseInt(dict.value)"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品备注" prop="remark">
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

    <!-- 批量匹配弹窗 -->
    <el-dialog
      :visible.sync="matchDialog.open"
      title="批量匹配"
      width="1000px"
      append-to-body
    >
      <vxe-table
        border
        show-overflow
        keep-source
        ref="xTable"
        :row-config="{ isHover: true }"
        :mouse-config="{ selected: true }"
        :keyboard-config="{
          isArrow: true,
          isDel: true,
          isEnter: true,
          isTab: true,
          isEdit: true,
          isChecked: true,
        }"
        :edit-config="{
          trigger: 'click',
          mode: 'cell',
          showStatus: true,
        }"
        :data="selectedSkuList"
      >
        <vxe-column type="seq" title="序号" width="60"></vxe-column>
        <vxe-column field="code" title="商品编号"></vxe-column>
        <vxe-column field="categoryName" title="商品分类"></vxe-column>
        <vxe-column field="name" title="商品名称"></vxe-column>
        <vxe-column
          field="spuId"
          title="关联商品库"
          :edit-render="{}"
        ></vxe-column>
        <vxe-column field="unit" title="商品单位"></vxe-column>
        <vxe-column field="spec" title="商品规格"></vxe-column>
      </vxe-table>

      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitMatchForm">确 定</el-button>
        <el-button @click="matchDialog.open = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 商品导入对话框 -->
    <el-dialog
      :title="upload.title"
      :visible.sync="upload.open"
      width="400px"
      append-to-body
    >
      <el-upload
        ref="upload"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url + '?updateSupport=' + upload.updateSupport"
        :disabled="upload.isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleFileSuccess"
        :auto-upload="false"
        drag
      >
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <div class="el-upload__tip text-center" slot="tip">
          <span>仅允许导入xls、xlsx格式文件。</span>
          <el-link
            type="primary"
            :underline="false"
            style="font-size: 12px; vertical-align: baseline"
            @click="importTemplate"
            >下载模板</el-link
          >
        </div>
      </el-upload>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitFileForm">确 定</el-button>
        <el-button @click="upload.open = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageSku,
  listSku,
  getSku,
  delSku,
  addSku,
  updateSku,
} from "@/api/product/sku";
import { listCategory } from "@/api/product/category";
import { listSpu } from "@/api/product/spu";
import { listCustomer } from "@/api/partner/customer";
import { pinyin } from "pinyin-pro";
import Treeselect from "@riophae/vue-treeselect";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";
import { getToken } from "@/utils/auth";

export default {
  name: "Sku",
  dicts: ["t_customer_type", "t_sku_unit", "biz_yes_no"],
  components: { Treeselect },
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
      // 商品分类map
      categoryMap: {},
      // 商品分类树选项
      categoryOptions: [],
      // 客户列表数据
      customerOptions: [],
      // 商品信息表格数据
      skuList: [],
      // 选择的商品列表
      selectedSkuList: [],
      // 商品库列表数据
      formSpuList: [],
      // 筛选后 spu 列表
      filterSpuList: [],
      // 选中的商品分类
      querySelectedOptions: [],
      formSelectedOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 批量匹配弹出层
      matchDialog: {
        open: false,
        title: "批量匹配",
      },
      // 商品导入参数
      upload: {
        // 是否显示弹出层（商品导入）
        open: false,
        // 弹出层标题（商品导入）
        title: "",
        // 是否禁用上传
        isUploading: false,
        // 设置上传的请求头部
        headers: { Authorization: "Bearer " + getToken() },
        // 上传的地址
        url: process.env.VUE_APP_BASE_API + "/product/sku/importData",
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        customerId: 0,
        categoryId: null,
        spuId: null,
        name: null,
        salePrice: null,
        saleable: null,
        valid: null,
        // 辅助查询是否关联spu
        matchedSpu: null,
      },
      // 查询校验
      queryFormRules: {
        customerId: [
          { required: true, message: "当前客户不能为空", trigger: "change" },
        ],
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        customerId: [
          { required: true, message: "客户ID不能为空", trigger: "blur" },
        ],
        spuId: [{ required: true, message: "产品ID不能为空", trigger: "blur" }],
        categoryId: [
          { required: true, message: "商品分类不能为空", trigger: "change" },
        ],
        name: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        mnemonicCode: [
          { required: true, message: "助记码不能为空", trigger: "blur" },
        ],
        unit: [
          { required: true, message: "商品单位不能为空", trigger: "blur" },
        ],
        salePrice: [
          { required: true, message: "商品售价不能为空", trigger: "blur" },
        ],
        saleable: [
          { required: true, message: "是否上架不能为空", trigger: "blur" },
        ],
        valid: [
          { required: true, message: "是否有效不能为空", trigger: "change" },
        ],
        isDeleted: [
          { required: true, message: "逻辑删除不能为空", trigger: "blur" },
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" },
        ],
      },
    };
  },
  watch: {
    // 监听 open 变化，如果为 false 清空表单
    open(val) {
      if (!val) {
        this.reset();
      }
    },
  },
  created() {
    this.defaultCustomerId =
      (this.$route.params && parseInt(this.$route.params.customerId)) || 0;
    this.getTreeselect();
    this.getCustomerList();
    this.getPageList();
  },
  methods: {
    /** 查询商品信息列表 */
    getList() {
      this.loading = true;
      listSku(this.queryParams).then((response) => {
        this.skuList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询商品信息列表 */
    getPageList() {
      this.loading = true;
      pageSku(this.queryParams).then((response) => {
        this.skuList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
      });
    },
    /** 查询商品库列表 */
    querySpuList(queryString, cb) {
      // 获取过滤后列表
      this.filterSpuList = [];
      var results = [];
      // 如果输入框为空，直接返回空数组
      if (!queryString || queryString.trim() === "") {
        cb(results);
        return;
      }

      var param = {
        name: queryString,
        categoryId: this.form.categoryId,
      };
      listSpu(param)
        .then((response) => {
          results = response.data.map((spu) => ({
            value: spu.name,
            spuId: spu.id,
          }));
          this.filterSpuList = results;
          // 调用 callback 返回建议列表
          cb(results);
        })
        .catch((error) => {
          console.error("Error fetching SPUs:", error);
          // 如果请求失败，返回空数组 cb([]);
          cb([]);
        });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset(row) {
      this.form = {
        id: null,
        customerId: row
          ? row.customerId
          : this.queryParams.customerId || this.defaultCustomerId,
        categoryId: null,
        spuId: null,
        name: null,
        mnemonicCode: null,
        unit: "斤",
        spec: null,
        images: null,
        properties: null,
        salePrice: 0,
        visitCount: null,
        saleable: 1,
        valid: 1,
        isDeleted: 0,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null,
      };
      this.resetForm("form");
      // 清空级联选择器
      this.formSelectedOptions = [];
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.$refs["queryForm"].validate((valid) => {
        if (valid) {
          this.queryParams.pageNum = 1;
          this.getPageList();
        }
      });
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.querySelectedOptions = [];
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.selectedSkuList = selection;
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加商品信息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getSku(id).then((response) => {
        this.form = response.data;
        // 构造级联选择器选中的数据
        this.formSelectedOptions = this.fillWithParentCategoryId(
          this.categoryOptions,
          this.form.categoryId.toString()
        );
        this.open = true;
        this.title = "修改商品信息";
      });
    },
    /** 批量匹配按钮操作 */
    handleMatched() {
      // 如果 selectedSkuList 里有 spuId 不为空的，提示
      if (this.selectedSkuList.some((sku) => sku.spuId)) {
        this.$modal.msgError("已匹配的商品不能进行批量匹配");
        return;
      }
      this.matchDialog.open = true;
    },
    /** 提交匹配商品库按钮 */
    submitMatchForm() {
      console.log("this.selectedSkuList", this.selectedSkuList);
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateSku(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addSku(this.form).then((response) => {
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
        .confirm('是否确认删除商品信息编号为"' + ids + '"的数据项？')
        .then(function () {
          return delSku(ids);
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
        "product/sku/export",
        {
          ...this.queryParams,
        },
        `sku_${new Date().getTime()}.xlsx`
      );
    },
    /** 更新助记码，提取拼音首字母并转换为大写 */
    handleUpdateMnemonicCode() {
      const value = this.form.name;
      if (!value) {
        this.form.mnemonicCode = "";
        return;
      }
      this.form.mnemonicCode = pinyin(value, {
        pattern: "first",
        toneType: "none",
        type: "array",
      })
        .join("")
        .toUpperCase();
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCategory().then((response) => {
        // init categoryMap
        this.categoryMap = response.data.reduce((map, item) => {
          map[item.id] = item.name;
          return map;
        });

        // init categoryOptions
        const treeList = this.handleTree(response.data);
        this.categoryOptions = this.transformData(treeList);
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
    /** 处理选择的spu选项 */
    handleSelectSpu(item) {
      if (!item) {
        this.form.spuId = null;
      }
      this.form.spuId = item.id;
    },
    /** 兜底方法，若未选中元素，再尝试填充 spu 信息 */
    handleUpdateSkuName(name) {
      // init spuId
      this.form.spuId = null;
      if (!name) {
        return;
      }
      if (this.filterSpuList.length === 0) {
        return;
      }
      const spuItem = this.filterSpuList.find((spu) => spu.value === name);
      if (spuItem) {
        this.form.spuId = spuItem.spuId;
      }
    },
    /** 处理级联选择器，取最后一个选项 */
    handleQueryCascaderChange(value) {
      this.queryParams.categoryId = value[value.length - 1];
      this.handleQuery();
    },
    handleFormOptionsChanged(value) {
      this.form.categoryId = value[value.length - 1];
    },
    /** 格式化商品分类 */
    categoryFormatter(row) {
      return this.categoryMap
        ? this.categoryMap[row.categoryId] || ""
        : row.categoryId;
    },
    /** 根据 id 构造父节点列表，并添加自身 */
    fillWithParentCategoryId(list, id) {
      if (!id) return [];
      function getParents(nodes, targetId, path = []) {
        for (const node of nodes) {
          path.push(node.value);
          if (
            node.value === targetId ||
            (node.children && getParents(node.children, targetId, path))
          ) {
            return path;
          }
          path.pop();
        }
        return null;
      }

      return getParents(list, id) || [];
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "商品导入";
      this.upload.open = true;
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download(
        "product/sku/importTemplate",
        {},
        `productSku_template_${new Date().getTime()}.xlsx`
      );
    },
    // 文件上传中处理
    handleFileUploadProgress(event, file, fileList) {
      this.upload.isUploading = true;
    },
    // 文件上传成功处理
    handleFileSuccess(response, file, fileList) {
      this.upload.open = false;
      this.upload.isUploading = false;
      this.$refs.upload.clearFiles();
      this.$alert(
        "<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" +
          response.msg +
          "</div>",
        "导入结果",
        { dangerouslyUseHTMLString: true }
      );
      this.getPageList();
    },
    // 提交上传文件
    submitFileForm() {
      this.$refs.upload.submit();
    },
  },
};
</script>
