<template>
  <div class="print-template-container">
    <PrintDesigner
      ref="printDesigner"
      :printTitle="printTemplateTitle"
      @save="saveTemplate"
      @close="closeTemplate"
    />
  </div>
</template>

<script>
import PrintDesigner from "@/components/PrintDesigner";
const printTemplatePage = { path: "/print/template" };

export default {
  name: "PrintTemplate",
  components: { PrintDesigner },
  data() {
    return {
      printTemplateTitle: "打印模板设计",
    };
  },
  methods: {
    /** 保存打印模板设计器 */
    saveTemplate() {
      const historyList = this.$refs.printDesigner.printTemplate.historyList;
      const defaultJson = historyList.filter(
        (item) => item.type === "初始"
      ).json;
      console.log("historyList", historyList, "defaultJson", defaultJson);
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
</style>