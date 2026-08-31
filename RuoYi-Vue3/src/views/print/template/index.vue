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
      <el-form-item label="模板名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入模板名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="绑定类型" prop="bindType">
        <el-select v-model="queryParams.bindType" placeholder="请选择绑定类型" clearable>
          <el-option label="客户+配送点组合" :value="1" />
          <el-option label="客户" :value="2" />
          <el-option label="全局默认" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 130px">
          <el-option label="草稿" :value="0" />
          <el-option label="已测试" :value="1" />
          <el-option label="已发布" :value="2" />
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
          v-hasPermi="['print:template:add']"
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
          @click="handleUpdate()"
          v-hasPermi="['print:template:edit']"
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
          @click="handleDelete()"
          v-hasPermi="['print:template:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          :icon="Design"
          size="small"
          @click="openDesigner"
          >打开打印设计器</el-button
        >
      </el-col>
      <!-- W0-6 模板导入导出 -->
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          :icon="Upload"
          size="small"
          @click="openImport"
          v-hasPermi="['print:template:edit']"
          >导入模板</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          :icon="Picture"
          size="small"
          @click="openAssetDialog"
          v-hasPermi="['print:template:list']"
          >资源管理</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getPageList"></right-toolbar>
    </el-row>

    <!-- 隐藏的导入文件输入 -->
    <input
      ref="importInput"
      type="file"
      accept=".json,application/json"
      style="display: none"
      @change="handleImportFile"
    />

    <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模板编号" align="center" prop="code" width="160" />
      <el-table-column label="模板名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" prop="type" width="90">
        <template #default="scope">
          <dict-tag :options="dict.type.t_print_template_type" :value="scope.row.type" />
        </template>
      </el-table-column>
      <el-table-column label="绑定类型" align="center" width="120">
        <template #default="scope">
          <el-tag size="small" :type="bindTagType(scope.row.bindType)">{{ bindTypeText(scope.row.bindType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="客户" align="center" width="150">
        <template #default="scope">
          <span v-if="scope.row.bindType === 1 || scope.row.bindType === 2">{{ customerName(scope.row.customerId) }}<span class="id-muted">（ID {{ scope.row.customerId }}）</span></span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="配送点" align="center" width="130">
        <template #default="scope">
          <span v-if="scope.row.bindType === 1">{{ deptName(scope.row.deliveryPointId) }}<span class="id-muted">（ID {{ scope.row.deliveryPointId }}）</span></span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="联数" align="center" prop="copies" width="60" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag size="small" :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="测试水印" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.testWatermark" size="small" type="danger">水印中</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="打印报表" align="center" prop="content" width="170" :show-overflow-tooltip="true">
        <template #default="scope">{{ jimuReportLabel(scope.row.content) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="330">
        <template #default="scope">
          <el-tooltip
            content="已发布模板不可直接修改，请修改后走「测试发布 → 发布」生成新版本"
            placement="top"
            :disabled="!isPublished(scope.row)"
          >
            <span>
              <el-button
                size="small"
                link
                :icon="Edit"
                :disabled="isPublished(scope.row)"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['print:template:edit']"
                >修改</el-button
              >
            </span>
          </el-tooltip>
          <el-button
            size="small"
            link
            :icon="View"
            v-if="scope.row.content"
            @click="openView(scope.row)"
            >预览</el-button
          >
          <el-tooltip
            v-if="scope.row.bindType === 3"
            content="以全局模板为底稿创建未绑定草稿副本，改好后绑定到客户（适合每个客户定制不同版式）"
            placement="top"
          >
            <span>
              <el-button
                size="small"
                link
                :icon="CopyDocument"
                @click="handleCopy(scope.row)"
                v-hasPermi="['print:template:add']"
                >复制</el-button
              >
            </span>
          </el-tooltip>
          <el-tooltip
            v-else
            content="客户模板不可作为底稿复制（蓝图§2：客户模板只允许从全局模板复制），请从全局默认模板复制"
            placement="top"
          >
            <span>
              <el-button size="small" link :icon="CopyDocument" disabled>复制</el-button>
            </span>
          </el-tooltip>
          <el-tooltip
            content="测试发布后整页带测试水印，测试打印不计正式打印次数"
            placement="top"
            :disabled="isPublished(scope.row)"
          >
            <span>
              <el-button
                size="small"
                link
                type="warning"
                :disabled="isPublished(scope.row)"
                @click="handleTestPublish(scope.row)"
                v-hasPermi="['print:template:edit']"
                >测试发布</el-button
              >
            </span>
          </el-tooltip>
          <el-tooltip
            content="必须先完成测试打印（测试发布），才可正式发布"
            placement="top"
            :disabled="scope.row.status === 1"
          >
            <span>
              <el-button
                size="small"
                link
                type="primary"
                :disabled="scope.row.status !== 1"
                @click="handlePublish(scope.row)"
                v-hasPermi="['print:template:edit']"
                >发布</el-button
              >
            </span>
          </el-tooltip>
          <el-button
            size="small"
            link
            :icon="Clock"
            @click="openVersions(scope.row)"
            >版本</el-button
          >
          <el-button
            size="small"
            link
            :icon="Download"
            @click="handleExport(scope.row)"
            v-hasPermi="['print:template:export']"
            >导出</el-button
          >
          <el-tooltip
            content="已发布模板不可删除（曾正式使用仅可停用）"
            placement="top"
            :disabled="!isPublished(scope.row)"
          >
            <span>
              <el-button
                size="small"
                link
                type="danger"
                :icon="Delete"
                :disabled="isPublished(scope.row)"
                @click="handleDelete(scope.row)"
                v-hasPermi="['print:template:remove']"
                >删除</el-button
              >
            </span>
          </el-tooltip>
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

    <!-- 添加或修改打印模板对话框 -->
    <el-dialog align-center :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item v-if="form.id" label="当前状态">
          <el-tag size="small" :type="statusTagType(form.status)">{{ statusText(form.status) }}</el-tag>
          <span class="hint" style="margin-left: 8px">保存修改后将回到「草稿」，需重新测试发布、正式发布</span>
        </el-form-item>
        <el-form-item label="模板编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入模板编号" />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="0">送货单</el-radio>
            <el-radio :value="1">汇总表</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="绑定类型" prop="bindType">
          <el-radio-group v-model="form.bindType" @change="onBindTypeChange">
            <el-radio :value="1">客户+配送点组合</el-radio>
            <el-radio :value="2">客户</el-radio>
            <el-radio :value="3">全局默认</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.bindType === 1 || form.bindType === 2" label="绑定客户" prop="customerId">
          <el-tree-select
            v-if="customerTree.length"
            v-model="bindSelection"
            :data="bindTreeData"
            :props="{ value: 'value', label: 'label', children: 'children', disabled: 'disabled' }"
            check-strictly
            filterable
            clearable
            :placeholder="form.bindType === 1 ? '选择客户下的配送点' : '选择客户'"
            style="width: 100%"
            @change="onBindSelectionChange"
            @clear="onBindSelectionClear"
          />
          <!-- 客户/配送点数据加载失败时回退为 ID 输入 -->
          <template v-else>
            <el-input-number v-model="form.customerId" :min="0" :controls="false" placeholder="客户ID（0=通用）" style="width: 100%" />
            <el-input-number
              v-if="form.bindType === 1"
              v-model="form.deliveryPointId"
              :min="1"
              :controls="false"
              placeholder="配送点ID"
              style="width: 100%; margin-top: 6px"
            />
          </template>
        </el-form-item>
        <el-form-item v-if="form.bindType === 3" label="设为全局默认" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio value="1">是</el-radio>
            <el-radio value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联数（打印份数）" prop="copies">
          <el-input-number v-model="form.copies" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="打印报表" prop="content">
          <el-select
            v-model="form.content"
            filterable
            clearable
            :loading="jimuLoading"
            placeholder="选择积木报表"
            style="width: 100%"
          >
            <el-option
              v-for="r in jimuReports"
              :key="r.id"
              :label="r.name + '（' + r.code + '）'"
              :value="r.id"
            />
          </el-select>
          <div class="hint">
            从积木报表设计器中选择本模板对应的报表，无需手工复制 ID；
            <el-link type="primary" :underline="false" style="font-size: 12px" @click="openDesigner">打开设计器新建</el-link>
            ，设计保存后
            <el-link type="primary" :underline="false" style="font-size: 12px" @click="loadJimuReports">刷新报表列表</el-link>
          </div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 正式发布对话框（W0-4.4 发布门禁） -->
    <el-dialog align-center title="正式发布模板" v-model="publishOpen" width="640px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="发布门禁：请逐项确认后发布；发布将生成版本快照，已发布版本在新版本发布前继续使用"
        style="margin-bottom: 12px"
      />
      <el-form label-width="110px">
        <el-form-item label="待发布模板">
          <span>{{ publishTemplate.name }}（{{ publishTemplate.code }}，联数 {{ publishTemplate.copies }}）</span>
        </el-form-item>
        <el-form-item v-for="(item, idx) in publishChecklist" :key="idx" :label="item.label">
          <el-checkbox v-model="item.checked">{{ item.text }}</el-checkbox>
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="publishRemark" type="textarea" :rows="2" placeholder="选填；不填写系统自动记录" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="publishSubmitting" @click="submitPublish">确认发布</el-button>
          <el-button @click="publishOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog align-center :title="'版本历史 - ' + (versionsTemplate ? versionsTemplate.name : '')" v-model="versionsOpen" width="860px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="发布/回滚均生成新版本，不覆盖历史；回滚以所选版本快照重新发布为新版本"
        style="margin-bottom: 12px"
      />
      <el-table v-loading="versionsLoading" :data="versionsList" size="small">
        <el-table-column label="版本号" align="center" prop="versionNo" width="80" />
        <el-table-column label="模板名称" align="center" prop="name" :show-overflow-tooltip="true" />
        <el-table-column label="绑定类型" align="center" width="110">
          <template #default="scope">{{ bindTypeText(scope.row.bindType) }}</template>
        </el-table-column>
        <el-table-column label="联数" align="center" prop="copies" width="60" />
        <el-table-column label="发布人" align="center" prop="publishedBy" width="100" />
        <el-table-column label="发布时间" align="center" prop="publishedTime" width="160" />
        <el-table-column label="版本说明" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button
              size="small"
              link
              type="warning"
              @click="handleRollback(scope.row)"
              v-hasPermi="['print:template:edit']"
              >回滚此版</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- W0-6 资源管理：上传/列表/删除（被引用资源不可删除） -->
    <el-dialog v-model="assetOpen" title="打印资源管理（Logo/底图，PNG/JPG ≤5MB）" width="760px" append-to-body>
      <div style="margin-bottom: 8px; color: #909399; font-size: 12px">
        资源存于本机文件目录并与数据库同批备份；content 可直接引用返回的 URL；历史引用资源不可物理删除。
      </div>
      <div style="margin-bottom: 12px">
        <el-upload
          :show-file-list="false"
          :before-upload="handleAssetUpload"
          accept=".png,.jpg,.jpeg"
        >
          <el-button type="primary" :icon="Upload" size="small">上传资源</el-button>
        </el-upload>
      </div>
      <el-table :data="assetList" size="small" border max-height="420">
        <el-table-column label="预览" align="center" width="80">
          <template #default="scope">
            <el-image
              :src="scope.row.url"
              style="width: 40px; height: 40px"
              fit="cover"
            />
          </template>
        </el-table-column>
        <el-table-column label="原始文件名" align="center" prop="originName" :show-overflow-tooltip="true" />
        <el-table-column label="URL" align="center" prop="url" :show-overflow-tooltip="true" />
        <el-table-column label="大小" align="center" width="90">
          <template #default="scope">{{ (scope.row.sizeBytes / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button
              size="small"
              link
              type="danger"
              :icon="Delete"
              @click="handleAssetDelete(scope.row)"
              v-hasPermi="['print:template:edit']"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import {
  pagePrintTemplate,
  getPrintTemplate,
  addPrintTemplate,
  updatePrintTemplate,
  delPrintTemplate,
  testPublishPrintTemplate,
  publishPrintTemplate,
  rollbackPrintTemplate,
  listPrintTemplateVersions,
  exportPrintTemplate,
  importPrintTemplate,
  listPrintAsset,
  uploadPrintAsset,
  delPrintAsset,
} from "@/api/print/template";
import { issuePrintTicket } from "@/api/print/ticket";
import { listJimuReports } from "@/api/print/template";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { Search, Refresh, Plus, Delete, Edit, View, Brush, Clock, Download, Upload, Picture, CopyDocument } from "@element-plus/icons-vue";

/** W0-4.4 发布门禁清单（蓝图：必填字段/纸张/分页/长文本溢出校验） */
const PUBLISH_CHECKLIST = [
  {
    label: "必填字段",
    text: "模板必填字段齐全：送货单号、客户/配送点、配送日期、商品/数量/单价/金额、页码（第 N/M 张）",
  },
  {
    label: "纸张介质",
    text: "纸张与介质正确：A4 纵向单面，或针式预印多联连续纸（参考 241mm × 140mm，以实测为准）",
  },
  {
    label: "分页规则",
    text: "分页规则符合：针式每页 10 条并跨页重复表头；A4 按页高自动分页并重复表头；单页也显示「第 1/1 张」",
  },
  {
    label: "边界测试",
    text: "已用长商品名等边界数据测试打印，无溢出或截断异常",
  },
];

export default {
  name: "PrintTemplate",
  dicts: ["t_print_template_type"],
  setup() {
    return { Search, Refresh, Plus, Delete, Edit, View, Design: Brush, Clock, Download, Upload, Picture };
  },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      templateList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        bindType: null,
        status: null,
      },
      form: {},
      rules: {
        code: [{ required: true, message: "模板编号不能为空", trigger: "blur" }],
        name: [{ required: true, message: "模板名称不能为空", trigger: "blur" }],
        bindType: [{ required: true, message: "请选择绑定类型", trigger: "change" }],
        copies: [{ required: true, message: "联数不能为空", trigger: "change" }],
      },
      // 正式发布
      publishOpen: false,
      publishSubmitting: false,
      publishTemplate: {},
      publishChecklist: PUBLISH_CHECKLIST.map((item) => ({ ...item, checked: false })),
      publishRemark: "",
      // 版本历史
      versionsOpen: false,
      versionsLoading: false,
      versionsTemplate: null,
      versionsList: [],
      // W0-6 模板导入导出与资源治理
      assetOpen: false,
      assetList: [],
      assetLoading: false,
      // 客户/配送点选择树（绑定类型 1/2），bindSelection 为树节点值 c{id}/d{id}
      customerTree: [],
      bindSelection: null,
      // 积木报表设计器可用报表清单
      jimuReports: [],
      jimuLoading: false,
    };
  },
  computed: {
    /** 绑定树：bindType=1 客户为父节点（禁选，选配送点）、bindType=2 仅客户节点 */
    bindTreeData() {
      if (this.form.bindType === 2) {
        return this.customerTree.map((c) => ({ ...c, disabled: false, children: undefined }));
      }
      return this.customerTree;
    },
  },
  created() {
    this.getPageList();
    this.loadCustomerTree();
    this.loadJimuReports();
  },
  methods: {
    getPageList() {
      this.loading = true;
      pagePrintTemplate(this.queryParams).then((response) => {
        this.templateList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getPageList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleSelectionChange(selection) {
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    bindTypeText(bindType) {
      return { 1: "客户+配送点", 2: "客户", 3: "全局默认" }[bindType] || "-";
    },
    bindTagType(bindType) {
      return { 1: "warning", 2: "primary", 3: "success" }[bindType] || "info";
    },
    statusText(status) {
      return { 0: "草稿", 1: "已测试", 2: "已发布" }[status] || "草稿";
    },
    statusTagType(status) {
      return { 0: "info", 1: "warning", 2: "success" }[status] || "info";
    },
    isPublished(row) {
      return row.status === 2;
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        id: null,
        customerId: 0,
        code: null,
        name: null,
        content: null,
        type: 0,
        renderEngine: "jimureport",
        bindType: 3,
        deliveryPointId: null,
        copies: 1,
        isDefault: "0",
        remark: null,
        status: 0,
      };
      this.resetForm("form");
      this.bindSelection = null;
    },
    /** 加载客户/配送点选择树（失败则回退为 ID 输入） */
    loadCustomerTree() {
      Promise.all([listCustomer(), listCustomerDept()])
        .then(([customerRes, deptRes]) => {
          const customers = customerRes.data || [];
          const depts = deptRes.data || [];
          const customerMap = new Map();
          customers.forEach((c) => {
            customerMap.set(c.id, {
              value: "c" + c.id,
              label: c.alias || c.name,
              type: "customer",
              customerId: c.id,
              deliveryPointId: null,
              disabled: true,
              children: [],
            });
          });
          depts.forEach((d) => {
            const parent = customerMap.get(d.customerId);
            if (!parent) return; // 客户不存在（已删除）的孤儿配送点不进树
            parent.children.push({
              value: "d" + d.id,
              label: d.name,
              type: "dept",
              customerId: d.customerId,
              deliveryPointId: d.id,
              disabled: false,
              children: undefined,
            });
          });
          this.customerTree = Array.from(customerMap.values());
        })
        .catch(() => {
          this.customerTree = [];
        });
    },
    /** 树节点选中：按节点类型回填 customerId / deliveryPointId */
    onBindSelectionChange(value) {
      if (!value) {
        this.onBindSelectionClear();
        return;
      }
      const node = this.findTreeNode(this.customerTree, value);
      if (!node) return;
      this.form.customerId = node.customerId;
      this.form.deliveryPointId = node.type === "dept" ? node.deliveryPointId : null;
    },
    onBindSelectionClear() {
      this.form.customerId = null;
      this.form.deliveryPointId = null;
    },
    findTreeNode(nodes, value) {
      for (const n of nodes || []) {
        if (n.value === value) return n;
        const hit = this.findTreeNode(n.children, value);
        if (hit) return hit;
      }
      return null;
    },
    customerName(customerId) {
      const node = this.findTreeNode(this.customerTree, "c" + customerId);
      return node ? node.label : "未知客户";
    },
    deptName(deptId) {
      const node = this.findTreeNode(this.customerTree, "d" + deptId);
      return node ? node.label : "未知配送点";
    },
    /** 切换绑定类型时清空已选绑定，避免类型与选中节点不匹配 */
    onBindTypeChange() {
      this.bindSelection = null;
      if (this.form.bindType === 1 || this.form.bindType === 2) {
        this.form.customerId = null;
        this.form.deliveryPointId = null;
      }
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加打印模板";
    },
    handleUpdate(row) {
      this.reset();
      const id = row ? row.id : this.ids;
      getPrintTemplate(id).then((response) => {
        this.form = response.data;
        // 编辑回显：按已有绑定反选树节点（树未含该客户/点时留空，仍保留原 ID）
        if (this.form.bindType === 1 && this.form.deliveryPointId) {
          this.bindSelection = "d" + this.form.deliveryPointId;
        } else if (this.form.bindType === 2 && this.form.customerId) {
          this.bindSelection = "c" + this.form.customerId;
        }
        this.open = true;
        this.title = "修改打印模板";
      });
    },
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          if ((this.form.bindType === 1 || this.form.bindType === 2) && !this.form.customerId) {
            this.$modal.msgWarning("请选择绑定的客户");
            return;
          }
          if (this.form.bindType === 1 && !this.form.deliveryPointId) {
            this.$modal.msgWarning("请选择绑定的配送点");
            return;
          }
          if (this.form.id != null) {
            updatePrintTemplate(this.form).then(() => {
              this.$modal.msgSuccess("修改成功（已回到草稿状态，请重新测试发布、正式发布）");
              this.open = false;
              this.getPageList();
            });
          } else {
            addPrintTemplate(this.form).then(() => {
              this.$modal.msgSuccess("新增成功（初始为草稿状态）");
              this.open = false;
              this.getPageList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      const ids = row ? row.id : this.ids;
      const target = row ? row : this.templateList.find((t) => t.id === ids);
      if (target && this.isPublished(target)) {
        this.$modal.msgWarning("已发布模板不可删除（曾正式使用仅可停用）");
        return;
      }
      this.$modal
        .confirm('是否确认删除打印模板编号为"' + ids + '"的数据项？')
        .then(() => delPrintTemplate(ids))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    /** 测试发布：置已测试 + 整页测试水印（测试打印不计正式次数） */
    handleTestPublish(row) {
      this.$modal
        .confirm(
          "将模板【" + row.name + "】置为「已测试」并打开测试水印预览？测试打印不计正式打印次数。"
        )
        .then(() => testPublishPrintTemplate(row.id))
        .then(() => {
          this.getPageList();
          this.$modal.msgSuccess("已置为「已测试」，请打开预览完成测试打印");
          this.openView({ ...row, status: 1 });
        })
        .catch(() => {});
    },
    /** 打开正式发布门禁对话框 */
    handlePublish(row) {
      this.publishTemplate = row;
      this.publishChecklist = PUBLISH_CHECKLIST.map((item) => ({ ...item, checked: false }));
      this.publishRemark = "";
      this.publishOpen = true;
    },
    /** 提交正式发布：门禁清单全勾选 + 后端门禁校验 + 版本快照 */
    submitPublish() {
      const unchecked = this.publishChecklist.filter((item) => !item.checked);
      if (unchecked.length) {
        this.$modal.msgWarning("发布门禁未通过：" + unchecked.map((item) => item.label).join("、"));
        return;
      }
      this.publishSubmitting = true;
      publishPrintTemplate(this.publishTemplate.id, this.publishRemark || null)
        .then((response) => {
          this.publishOpen = false;
          this.getPageList();
          this.$modal.msgSuccess("发布成功，当前版本 v" + response.data);
        })
        .finally(() => {
          this.publishSubmitting = false;
        });
    },
    /** 版本历史 */
    openVersions(row) {
      this.versionsTemplate = row;
      this.versionsOpen = true;
      this.versionsLoading = true;
      listPrintTemplateVersions(row.id)
        .then((response) => {
          this.versionsList = response.data || [];
        })
        .finally(() => {
          this.versionsLoading = false;
        });
    },
    /** 回滚历史版本：以快照重新发布为新版本 */
    handleRollback(version) {
      this.$modal
        .confirm(
          "将模板【" + this.versionsTemplate.name + "】回滚至版本 v" + version.versionNo +
            "？回滚会生成新版本 v" + (version.versionNo + 1) + " 并发布，不覆盖历史。"
        )
        .then(() => rollbackPrintTemplate(this.versionsTemplate.id, version.id))
        .then((response) => {
          this.$modal.msgSuccess("回滚成功，当前版本 v" + response.data);
          this.openVersions(this.versionsTemplate);
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 加载积木报表清单（设计器里保存后点刷新即可见） */
    loadJimuReports() {
      this.jimuLoading = true;
      listJimuReports()
        .then((response) => {
          this.jimuReports = response.data || [];
        })
        .catch(() => {
          this.jimuReports = [];
        })
        .finally(() => {
          this.jimuLoading = false;
        });
    },
    /** 报表 ID → 名称（未加载到时退回原 ID） */
    jimuReportLabel(reportId) {
      if (!reportId) return "—";
      const hit = this.jimuReports.find((r) => r.id === reportId);
      return hit ? hit.name : reportId;
    },
    /** 以全局模板为底稿创建未绑定草稿副本（蓝图 §2：客户模板不可作为底稿复制，入口已按 bindType 收敛到全局默认行） */
    handleCopy(row) {
      this.$modal
        .confirm(`将以「${row.name}」为底稿创建未绑定草稿副本（需重新绑定客户并走测试发布→发布），是否继续？`)
        .then(() =>
          addPrintTemplate({
            ...row,
            id: null,
            code: `${row.code}_copy_${String(Date.now()).slice(-6)}`,
            name: `${row.name} 副本`,
            bindType: 3,
            customerId: 0,
            deliveryPointId: null,
            isDefault: "0",
            status: 0,
            version: 0,
            createBy: null,
            createTime: null,
            updateBy: null,
            updateTime: null,
          })
        )
        .then(() => {
          this.$modal.msgSuccess("副本已创建（草稿态，未绑定）");
          this.getPageList();
        })
        .catch(() => {});
    },
    /** 打开 JimuReport 在线打印设计器（新窗口） */
    openDesigner() {
      // W0-4.1：URL 不再携带长期 JWT，改签发短时一次性打印票据（无单据绑定）
      issuePrintTicket({}).then((res) => {
        window.open("/jmreport/list?token=" + res.ticket, "_blank");
      });
    },
    /** 预览 JimuReport 模板视图 */
    openView(row) {
      // W0-4.1：同上，票据只绑模板不绑单据
      issuePrintTicket({ templateId: row.content }).then((res) => {
        window.open("/jmreport/view/" + row.content + "?token=" + res.ticket, "_blank");
      });
    },
    // ==================== W0-6 模板导入导出与资源治理 ====================
    /** 导出当前行为开放 JSON 包（脱敏，可选携带历史版本） */
    handleExport(row) {
      this.$modal.confirm("导出模板包将脱敏（剔除内部 ID/绑定/操作者），是否同时携带历史发布版本快照？取消则仅导出当前内容。")
        .then(() => this.exportAndDownload(row, true))
        .catch(() => this.exportAndDownload(row, false));
    },
    exportAndDownload(row, includeVersions) {
      exportPrintTemplate(row.id, includeVersions).then((response) => {
        const data = response.data;
        const json = JSON.stringify(data, null, 2);
        const blob = new Blob([json], { type: "application/json" });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = (row.name || "template") + (includeVersions ? "_full.json" : ".json");
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.$modal.msgSuccess("导出成功（已脱敏，可安全迁移）");
      });
    },
    /** 打开导入文件选择 */
    openImport() {
      this.$refs.importInput.value = "";
      this.$refs.importInput.click();
    },
    /** 读取导入文件并提交（全有或全无安全校验在服务端） */
    handleImportFile(e) {
      const file = e.target.files[0];
      if (!file) return;
      if (file.size > 20 * 1024 * 1024) {
        this.$modal.msgError("导入包超出 20MB 上限");
        return;
      }
      const reader = new FileReader();
      reader.onload = (ev) => {
        const text = ev.target.result;
        this.$modal.confirm("导入后为重命名的未绑定草稿，须重走完整发布门禁（测试发布→发布）。是否继续？")
          .then(() => importPrintTemplate(text))
          .then((res) => {
            this.$modal.msgSuccess("导入成功 " + res.data + " 个模板（未绑定草稿）");
            this.getPageList();
          })
          .catch(() => {});
      };
      reader.readAsText(file);
    },
    /** 打开资源管理弹窗 */
    openAssetDialog() {
      this.assetOpen = true;
      this.loadAssetList();
    },
    loadAssetList() {
      this.assetLoading = true;
      listPrintAsset({}).then((res) => {
        this.assetList = res.data || [];
        this.assetLoading = false;
      }).catch(() => { this.assetLoading = false; });
    },
    /** 上传资源（before-upload 返回 false 阻止默认上传） */
    handleAssetUpload(file) {
      const okTypes = ["image/png", "image/jpeg", "image/jpg"];
      if (!okTypes.includes(file.type)) {
        this.$modal.msgError("仅支持 PNG/JPG/JPEG");
        return false;
      }
      if (file.size > 5 * 1024 * 1024) {
        this.$modal.msgError("资源大小超出 5MB 上限");
        return false;
      }
      uploadPrintAsset(file).then(() => {
        this.$modal.msgSuccess("上传成功");
        this.loadAssetList();
      }).catch(() => {});
      return false;
    },
    /** 删除资源（被引用则服务端拒绝） */
    handleAssetDelete(row) {
      this.$modal.confirm("确认删除资源「" + (row.originName || row.fileName) + "」？若被模板/历史版本引用将拒绝删除。")
        .then(() => delPrintAsset(row.id))
        .then(() => {
          this.$modal.msgSuccess("删除成功");
          this.loadAssetList();
        })
        .catch(() => {});
    },
  },
};
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
.id-muted {
  color: #909399;
  font-size: 12px;
}
</style>
