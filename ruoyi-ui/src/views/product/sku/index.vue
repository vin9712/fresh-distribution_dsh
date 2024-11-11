<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      :rules="queryFormRules"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
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
          v-model="queryParams.categoryId"
          placeholder="请选择商品分类"
          :options="categoryOptions"
        ></el-cascader>
        <!-- <treeselect
          v-model="queryParams.categoryId"
          :normalizer="normalizer"
          :show-count="true"
          :multiple="false"
          :options="categoryOptions"
          placeholder="请选择商品分类"
          style="width: 200px"
          clearable
        >
        </treeselect> -->
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
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['product:sku:edit']"
          >修改</el-button
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
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
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
        <el-form-item label="商品名称" prop="name">
          <el-input
            v-model="form.name"
            @input="handleUpdateMnemonicCode"
            placeholder="请输入商品名称"
          />
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
import { listCustomer } from "@/api/partner/customer";
import { pinyin } from "pinyin-pro";
import Treeselect from "@riophae/vue-treeselect";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";

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
      // 客户列表数据
      customerOptions: [],
      // 商品信息表格数据
      skuList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
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
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        customerId: this.defaultCustomerId,
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
        this.open = true;
        this.title = "修改商品信息";
      });
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
    handleSpuNameInput() {
      // 查询spu信息， 如果没有查到返回

      // 更新助记码，提取拼音首字母并转换为大写
      this.handleUpdateMnemonicCode();
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
    /** 转换菜单数据结构 */
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children;
      }
      return {
        id: node.id,
        label: node.name,
        children: node.children,
      };
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCategory().then((response) => {
        const treeList = this.handleTree(response.data);
        console.log("treeList", treeList);
        this.categoryOptions = treeList.map((item) => {
          const { id, name, children } = item;
          return {
            value: id,
            label: name,
            // todo add children
            children: children,
          };
        });
      });
    },
  },
};
</script>
