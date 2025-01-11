<template>
  <div>
    <div class="print-template-container">
      <!-- 模板设计表单 -->
      <el-form
        ref="templateForm"
        :model="templateForm"
        :rules="rules"
        size="small"
        inline
        label-width="100px"
      >
        <el-form-item label="适用客户" prop="customerId">
          <el-select
            v-model="templateForm.customerId"
            placeholder="请选择适用客户"
            :disabled="templateForm.id != null"
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
              v-if="!templateForm.id"
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
import { deepEqual } from "@/utils";
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
        data: null,
        remark: null,
      },
    };
  },
  created() {
    // 从路由获取并设置表单参数
    const templateIdFromQuery = this.$route.query.templateId;
    this.templateForm.id = templateIdFromQuery
      ? parseInt(templateIdFromQuery, 10)
      : null;

    this.getCustomerList();
    this.getTemplateCode();
    this.getTemplateDetail();
  },
  methods: {
    /** 查询客户列表 */
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
        this.customerOptions.unshift({ id: 0, name: "所有客户" });
      });
    },
    /** 打印模板数据 */
    getTemplateDetail() {
      const templateId = this.templateForm.id;
      if (templateId) {
        getTemplate(templateId).then((response) => {
          this.templateForm = response.data;
          // 更新获取模板样式
          const templateContent = JSON.parse(this.templateForm.content);
          this.$refs.printDesigner.printTemplate.update(templateContent);
          // 更新测试数据
          const testData = JSON.parse(this.templateForm.data);
          if (testData) {
            this.$refs.printDesigner.printData = testData;
          }
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
      const printData = this.$refs.printDesigner.printData;
      const templateId = this.templateForm.id;

      this.$refs["templateForm"].validate((valid) => {
        if (valid) {
          // 校验并赋值 template content(json)
          if (!templateId && !this.changeTemplate()) {
            this.$modal.msgError("请先设计模板内容");
            return;
          }
          this.templateForm.content = JSON.stringify(printTemplate.getJson());
          this.templateForm.data = JSON.stringify(printData);

          if (templateId) {
            updateTemplate(this.templateForm).then((response) => {
              this.$modal.msgSuccess("修改成功");
              this.getTemplateDetail();
            });
          } else {
            addTemplate(this.templateForm).then((response) => {
              this.$modal.msgSuccess("新增成功");
              printTemplate.update(JSON.parse(this.templateForm.content));
              this.templateForm = response.data;
            });
          }
        }
      });
    },
    /** 关闭打印模板设计器 */
    closeTemplate() {
      if (this.changeTemplate()) {
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
    /** 是否更改打印模板 */
    changeTemplate() {
      const printTemplate = this.$refs.printDesigner.printTemplate;
      const templateId = this.templateForm.id;
      const currentJson = printTemplate.getJson();
      // 修改模式
      if (templateId) {
        const templateContent = JSON.parse(this.templateForm.content);
        const currentTemplate = JSON.parse(JSON.stringify(currentJson));
        return !deepEqual(templateContent, currentTemplate);
      }

      // 新增模式
      return currentJson &&
        currentJson?.panels &&
        currentJson.panels[0].printElements.length > 0
        ? true
        : false;
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
