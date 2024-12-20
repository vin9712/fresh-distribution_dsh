<template>
  <Designer
    :autoConnect="false"
    :template="template"
    @onDesigned="onDesigned"
  />
</template>

<script>
import { Designer } from "@sv-print/vue";

export default {
  components: { Designer },
  data() {
    return {
      templateId: null,
      hideStyleElement: null,
      template: {},
    };
  },
  watch: {
    templateId(val) {
      if (val) {
        console.log("templateId changed", val);
        this.hideWatermark();
      }
    },
  },
  mounted() {
    this.hideWatermark();
  },
  beforeDestroy() {
    // 直接移除之前添加的 <style> 标签（如果有）
    if (this.hideStyleElement) {
      document.head.removeChild(this.hideStyleElement);
    }
  },
  methods: {
    hideWatermark() {
      if (!this.templateId) return;

      // 如果已经存在旧的隐藏水印样式，则先移除它
      if (this.hideStyleElement) {
        document.head.removeChild(this.hideStyleElement);
      }

      // 添加隐藏水印和其他元素的样式
      const hideStyle = document.createElement("style");
      hideStyle.id = "hide-style";
      hideStyle.innerHTML = `
        div[class*="${this.templateId}"],
        .hiprint-printPaper-background,
        #dragBox-rotateTools,
        #SVPrint .svp-footer {
          display: none !important;
        }
      `;
      document.head.appendChild(hideStyle);

      console.log("style added", hideStyle);
    },
    onDesigned(e) {
      const designer = e.detail;
      console.log("designer ", designer);
      console.log("designerUtils ", designer.designerUtils);
      console.log("hiprint ", designer.hiprint);
      console.log("printTemplate ", designer.printTemplate);

      this.templateId = designer.printTemplate.id;
    },
  },
};
</script>
