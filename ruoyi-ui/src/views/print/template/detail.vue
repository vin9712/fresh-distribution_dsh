<template>
  <div>
    <div class="print-template-container">
      <el-form
        ref="templateForm"
        :model="templateForm"
        :rules="rules"
        size="small"
        inline
        label-width="100px"
      >
        <el-form-item
          label="适用客户"
          prop="customerId"
          v-if="templateForm.customerId"
        >
          <el-select
            v-model="templateForm.customerId"
            placeholder="请选择适用客户"
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
        <el-form-item label="模板编号" prop="code">
          <span slot="label">
            模板编号
            <i
              class="el-icon-refresh"
              @click="refreshTemplateCode"
              style="cursor: pointer"
            ></i>
          </span>
          <el-input
            v-model="templateForm.code"
            placeholder="请输入模板编号"
            disabled
          >
          </el-input>
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="templateForm.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板备注" prop="remark">
          <el-input
            v-model="templateForm.remark"
            placeholder="请输入模板备注"
          />
        </el-form-item>
      </el-form>

      <PrintDesigner
        ref="printDesigner"
        printTitle="报表设计器"
        @save="saveTemplate"
        @close="closeTemplate"
      />
    </div>
  </div>
</template>

<script>
import { listCustomer } from "@/api/partner/customer";
import {
  getTemplate,
  genTemplateCode,
  addTemplate,
  updateTemplate,
} from "@/api/print/template";
import PrintDesigner from "@/components/PrintDesigner";
const printTemplatePage = { path: "/print/template" };

export default {
  name: "PrintTemplate",
  components: { PrintDesigner },
  data() {
    return {
      // 客户列表数据
      customerOptions: [],
      rules: {
        name: [
          { required: true, message: "模板名称不能为空", trigger: "blur" },
        ],
        code: [
          { required: true, message: "模板编号不能为空", trigger: "blur" },
        ],
      },
      // 模板数据表单
      templateForm: {
        id: null,
        customerId: 0,
        code: null,
        name: null,
        // 模板 json
        content: null,
        remark: null,
      },
    };
  },
  mounted() {
    // 从路由获取并设置表单参数
    const templateIdFromQuery = this.$route.query.templateId;
    this.templateForm.id = templateIdFromQuery
      ? parseInt(templateIdFromQuery, 10)
      : null;
    this.getTemplateDetail();
  },
  created() {
    this.getCustomerList();
    this.getTemplateCode();
  },
  methods: {
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "默认客户" });
      });
    },
    /** 打印模板数据 */
    getTemplateDetail() {
      const templateId = this.templateForm.id;
      console.log("templateId", templateId);
      if (templateId) {
        getTemplate(templateId).then((response) => {
          this.templateForm = response.data;
          console.log("templateForm", this.templateForm);
          // 更新获取模板样式
          this.$refs.printDesigner.template = JSON.parse(
            this.templateForm.content
          );
          console.log("designer template", this.$refs.printDesigner.template);
        });
      }
    },
    /** 获取当前模板单号 */
    getTemplateCode() {
      genTemplateCode().then((response) => {
        this.templateForm.code = response.msg;
      });
    },
    /** 刷新模板编号 */
    refreshTemplateCode() {
      let param = { currentCode: this.templateForm.code };
      genTemplateCode(param).then((response) => {
        this.templateForm.code = response.msg;
      });
    },
    /** 保存打印模板设计器 */
    saveTemplate() {
      const printTemplate = this.$refs.printDesigner.printTemplate;
      console.log("printTemplate", printTemplate);

      this.$refs["templateForm"].validate((valid) => {
        if (valid) {
          // 校验并赋值 template content(json)

          const defaultJson = printTemplate.historyList.filter(
            (item) => item.type === "初始"
          ).json;
          const lastJson =
            printTemplate.historyList.length > 1
              ? printTemplate.lastJson
              : defaultJson;
          console.log("lastJson", lastJson, "defaultJson", defaultJson);
          if (!lastJson || defaultJson === lastJson) {
            this.$modal.msgError("请先设计模板内容");
            return;
          }
          this.templateForm.content = JSON.stringify(lastJson);

          if (this.templateForm.id != null) {
            updateTemplate(this.templateForm).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.$tab.closeOpenPage(printTemplatePage);
            });
          } else {
            addTemplate(this.templateForm).then((response) => {
              this.$modal.msgSuccess("新增成功");
              this.$tab.closeOpenPage(printTemplatePage);
            });
          }
        }
      });
    },
    /** 关闭打印模板设计器 */
    closeTemplate() {
      const historyList = this.$refs.printDesigner.printTemplate.historyList;
      const isChanged =
        historyList.filter((item) => item.type !== "初始").length > 0;
      if (isChanged) {
        this.$confirm("当前模板有修改，是否关闭？", "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        })
          .then(() => {
            this.$tab.closeOpenPage(printTemplatePage);
          })
          .catch(() => {});
      } else {
        this.$tab.closeOpenPage(printTemplatePage);
      }
    },
  },
};
</script>

<style scoped>
.print-template-container {
  height: 100vh;
}

::v-deep .el-form-item {
  padding: 10px 0 0 10px;
}

::v-deep .el-form-item {
  margin-bottom: 15px;
}
</style>