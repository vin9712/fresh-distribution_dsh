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
      <el-form-item label="客户名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入客户名称"
          clearable
          @keyup.enter="handleQuery"
        />
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
      <el-form-item label="组单策略" prop="docScopeType">
        <el-select
          v-model="queryParams.docScopeType"
          placeholder="送货单组单方式"
          clearable
          style="width: 170px"
        >
          <el-option label="每配送点一张单" value="DELIVERY_POINT_DATE" />
          <el-option label="跨点总单（按客户日合并）" value="CUSTOMER_DATE" />
        </el-select>
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
          v-hasPermi="['partner:customer:add']"
          >新增</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          :icon="Edit"
          size="small"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['partner:customer:edit']"
          >修改</el-button
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
          v-hasPermi="['partner:customer:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          :icon="Upload"
          size="small"
          @click="handleImport"
          >导入</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Download"
          size="small"
          @click="handleExport"
          v-hasPermi="['partner:customer:export']"
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
      :data="customerList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="客户编号" align="center" prop="id" />
      <el-table-column label="客户名称" align="center" prop="name">
        <template #default="scope">
          <router-link
            title="查看配送点列表"
            :to="'/basicInfo/customer-dept/index/' + scope.row.id"
            class="link-type"
          >
            <span>{{ scope.row.name }}</span>
          </router-link>
        </template>
      </el-table-column>
      <el-table-column label="客户别名" align="center" prop="alias" />
      <el-table-column label="客户类型" align="center" prop="type">
        <template #default="scope">
          <dict-tag
            v-if="scope.row.type !== null && scope.row.type !== undefined && scope.row.type !== ''"
            :options="dict.type.t_customer_type"
            :value="scope.row.type"
          />
          <!-- 兜底：字典无值/客户未设置类型时显示 - -->
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="是否有效" align="center" prop="valid">
        <template #default="scope">
          <dict-tag :options="dict.type.biz_yes_no" :value="scope.row.valid" />
        </template>
      </el-table-column>
      <!-- 送货单客户维度（D-040）：组单口径在列表直接可见，不必进编辑弹窗 -->
      <el-table-column label="送货单组单策略" align="center" width="150">
        <template #default="scope">
          <el-tag
            :type="scope.row.docScopeType === 'CUSTOMER_DATE' ? 'primary' : 'info'"
            size="small"
            effect="plain"
          >
            {{ scope.row.docScopeType === "CUSTOMER_DATE" ? "跨点总单" : "每点一单" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="相同商品合并" align="center" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.docMergeSameItem === false ? 'warning' : 'success'" size="small" effect="plain">
            {{ scope.row.docMergeSameItem === false ? "不合行" : "合并" }}
          </el-tag>
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
            size="small"
            link
            :icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['partner:customer:edit']"
            >修改</el-button
          >
          <el-button
            size="small"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['partner:customer:remove']"
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

    <!-- 添加或修改客户对话框 -->
    <el-dialog align-center :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="客户类别" prop="type">
          <el-select v-model="form.type" placeholder="请选择客户类别" clearable>
            <el-option
              v-for="dict in dict.type.t_customer_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="客户别名" prop="alias">
          <el-input v-model="form.alias" placeholder="请输入客户别名" />
        </el-form-item>
        <el-form-item label="手机号" prop="tel">
          <el-input v-model="form.tel" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="客户地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入客户地址" />
        </el-form-item>
        <el-form-item label="是否有效" prop="valid">
          <el-radio-group v-model="form.valid">
            <el-radio
              v-for="dict in dict.type.biz_yes_no"
              :key="dict.value"
              :value="parseInt(dict.value)"
              >{{ dict.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="组单策略" prop="docScopeType">
          <el-select v-model="form.docScopeType" placeholder="请选择组单策略" style="width: 100%">
            <el-option label="每配送点一张单" value="DELIVERY_POINT_DATE" />
            <el-option label="跨点总单（按客户日合并）" value="CUSTOMER_DATE" />
          </el-select>
          <div class="scope-hint">生成送货单时的单据范围：默认每配送点一张；跨点总单适合统一配送的客户（配送点不可单独覆盖）</div>
          <div class="scope-hint">生效时机：仅对尚未建批次的「客户+配送日期」生效；当日已生成过送货单则按当日批次快照不变</div>
        </el-form-item>
        <el-form-item label="相同商品合并" prop="docMergeSameItem">
          <el-switch v-model="form.docMergeSameItem" />
          <span class="scope-hint">开：同一客户多张订单的相同商品合并为一行（不同价必拆行）；关：一订单行一行</span>
        </el-form-item>
        <el-form-item label="打印模板">
          <div v-loading="templateLoading" style="width: 100%">
            <template v-if="form.id != null">
              <template v-if="boundTemplates.length">
                <el-tag
                  v-for="t in boundTemplates"
                  :key="t.id"
                  size="small"
                  effect="plain"
                  style="margin: 0 8px 4px 0"
                >
                  {{ t.name }}（联数 {{ t.copies }}·{{ bindLevelText(t.bindType) }}）
                </el-tag>
              </template>
              <span v-else-if="!templateLoading && !templateDenied" class="scope-hint">
                未绑定客户级模板，打印时自动回退「全局默认」模板
              </span>
              <span v-else-if="templateDenied" class="scope-hint">无打印模板查看权限，请联系管理员绑定</span>
            </template>
            <span v-else class="scope-hint">客户保存后，可在「打印管理 → 打印模板」中绑定该客户专属模板</span>
            <div class="scope-hint">
              绑定解析优先级：客户+配送点级 &gt; 客户级 &gt; 全局默认；打印送货单默认取解析到的已发布模板，打印时可临时切换。
              <el-link type="primary" :underline="false" style="font-size: 12px" @click="gotoTemplatePage">去配置打印模板</el-link>
            </div>
          </div>
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

    <!-- 客户导入对话框 -->
    <el-dialog align-center
      :title="upload.title"
      v-model="upload.open"
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
        <el-icon><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            <span>仅允许导入xls、xlsx格式文件。</span>
            <el-link
              type="primary"
              :underline="false"
              style="font-size: 12px; vertical-align: baseline"
              @click="importTemplate"
              >下载模板</el-link
            >
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFileForm">确 定</el-button>
          <el-button @click="upload.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  pageCustomer,
  listCustomer,
  getCustomer,
  delCustomer,
  addCustomer,
  updateCustomer,
} from "@/api/partner/customer";
import { getToken } from "@/utils/auth";
import { listPrintTemplate } from "@/api/print/template";
import { Search, Refresh, Plus, Edit, Delete, Upload, Download } from "@element-plus/icons-vue";

export default {
  name: "Customer",

  dicts: ["biz_yes_no", "t_customer_type"],
  setup() {
    return { Search, Refresh, Plus, Edit, Delete, Upload, Download };
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
      // 客户表格数据
      customerList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 客户绑定送货单打印模板（已发布，bind_type 1=客户+点 2=客户级）
      boundTemplates: [],
      templateLoading: false,
      templateDenied: false,
      // 用户导入参数
      upload: {
        // 是否显示弹出层（用户导入）
        open: false,
        // 弹出层标题（用户导入）
        title: "",
        // 是否禁用上传
        isUploading: false,
        // 设置上传的请求头部
        headers: { Authorization: "Bearer " + getToken() },
        // 上传的地址
        url: import.meta.env.VITE_APP_BASE_API + "/partner/customer/importData",
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        type: null,
        valid: null,
        docScopeType: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        name: [
          { required: true, message: "客户名称不能为空", trigger: "blur" },
        ],
        type: [
          { required: true, message: "客户类型不能为空", trigger: "change" },
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
    this.getPageList();
  },
  methods: {
    /** 查询客户管理列表 */
    getList() {
      this.loading = true;
      listCustomer(this.queryParams).then((response) => {
        this.customerList = response.data;
        this.loading = false;
      });
    },
    /** 分页查询客户管理列表 */
    getPageList() {
      this.loading = true;
      pageCustomer(this.queryParams).then((response) => {
        this.customerList = response.rows;
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
        name: null,
        alias: null,
        type: null,
        tel: null,
        address: null,
        valid: 1,
        docScopeType: "DELIVERY_POINT_DATE",
        docMergeSameItem: true,
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
      this.boundTemplates = [];
      this.open = true;
      this.title = "添加客户";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getCustomer(id).then((response) => {
        this.form = response.data;
        this.open = true;
        this.title = "修改客户";
        this.loadBoundTemplates(this.form.id);
      });
    },
    /** 客户绑定的送货单打印模板（type=0 送货单、已发布；bind_type 1=客户+点 2=客户级） */
    loadBoundTemplates(customerId) {
      this.boundTemplates = [];
      this.templateLoading = true;
      this.templateDenied = false;
      listPrintTemplate({ customerId, type: 0, status: 2 })
        .then((response) => {
          this.boundTemplates = (response.data || []).filter(
            (t) => t.bindType === 1 || t.bindType === 2
          );
        })
        .catch(() => {
          this.templateDenied = true;
        })
        .finally(() => {
          this.templateLoading = false;
        });
    },
    bindLevelText(bindType) {
      return bindType === 1 ? "客户+配送点级" : "客户级";
    },
    gotoTemplatePage() {
      this.$router.push("/print/template");
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if (this.form.id != null) {
            updateCustomer(this.form).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getPageList();
            });
          } else {
            addCustomer(this.form).then((response) => {
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
        .confirm('是否确认删除客户编号为"' + ids + '"的数据项？')
        .then(function () {
          return delCustomer(ids);
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
        "partner/customer/export",
        {
          ...this.queryParams,
        },
        `customer_${new Date().getTime()}.xlsx`
      );
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "客户导入";
      this.upload.open = true;
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download(
        "partner/customer/importTemplate",
        {},
        `customer_template_${new Date().getTime()}.xlsx`
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
      this.getList();
    },
    // 提交上传文件
    submitFileForm() {
      this.$refs.upload.submit();
    },
  },
};
</script>

<style scoped>
.scope-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 2px;
}
</style>
