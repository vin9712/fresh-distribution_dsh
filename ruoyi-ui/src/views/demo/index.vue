<template>
  <div class="sv-print-container">
    <Designer
      :key="designerKey"
      :template="template"
      :printData="printData"
      :events="events"
      @onDesigned="onDesigned"
    >
      <!-- 自定义 header -->
      <div class="svp-header svelte-ien9fs" slot="header">
        <div class="children svelte-ien9fs">
          <!-- 左侧部分 -->
          <div class="flex svp-flex-row basis-1/5 svelte-ien9fs">
            <span class="svp-header-title svelte-ien9fs">{{
              "送货单位: " + customerDeptName
            }}</span>
          </div>

          <!-- 中间部分 -->
          <div
            class="flex svp-flex-row px-4 basis-3/5 svp-justify-center svelte-ien9fs"
          >
            <div
              class="svp-header-more-ele svelte-ien9fs"
              style="position: unset"
            >
              <div class="parent svelte-ien9fs">
                <i class="svicon sv-base svelte-ien9fs"></i>
                <p class="svelte-ien9fs">基础</p>
                <div class="children svelte-ien9fs">
                  <!-- 这里可以放置具体的可拖动元素 -->
                </div>
              </div>
              <i class="nav svicon sv-nav-down svelte-ien9fs"></i>
            </div>
            <div
              class="svp-header-menu svelte-ien9fs"
              id="printTest"
              @click="printTest"
            >
              <i class="svicon sv-print svelte-ien9fs"></i>
              <p class="svelte-ien9fs">测试打印</p>
            </div>
          </div>

          <!-- 右侧部分 -->
          <div class="flex svp-flex-row basis-1/5 justify-end svelte-ien9fs">
            <div
              class="svp-header-more-menu svelte-ien9fs"
              style="position: unset"
            >
              <div class="parent svelte-ien9fs">
                <i class="svicon sv-save svelte-ien9fs"></i>
                <p class="svelte-ien9fs">保存</p>
                <div class="children svelte-ien9fs">
                  <!-- 这里可以放置具体的菜单项 -->
                </div>
              </div>
              <i class="nav svicon sv-nav-down svelte-ien9fs"></i>
            </div>
            <div
              class="svp-header-menu svelte-ien9fs"
              id="editTemplate"
              @click="editTemplate"
            >
              <i class="svicon sv-edit svelte-ien9fs"></i>
              <p class="svelte-ien9fs">编辑模板</p>
            </div>
            <div
              class="svp-header-menu svelte-ien9fs"
              id="editPrintData"
              @click="editPrintData"
            >
              <i class="svicon sv-edit-data svelte-ien9fs"></i>
              <p class="svelte-ien9fs">编辑数据</p>
            </div>
            <div
              class="svp-header-menu svelte-ien9fs"
              id="preview"
              @click="preview"
            >
              <i class="svicon sv-preview svelte-ien9fs"></i>
              <p class="svelte-ien9fs">预览</p>
            </div>
            <div class="svp-header-menu svelte-ien9fs">
              <i class="svicon sv-close svelte-ien9fs"></i>
              <p class="svelte-ien9fs">关闭</p>
            </div>
          </div>
        </div>
      </div>
    </Designer>
  </div>
</template>

<script>
import { Designer } from "@sv-print/vue";
import { disAutoConnect, hiprint } from "@sv-print/hiprint";
import "sv-print/dist/style.css"; // sv-print 样式

export default {
  components: { Designer },
  data() {
    return {
      // 设计器工具类
      designerUtils: null,
      printTemplate: null,

      // 默认打印模板+数据
      designerKey: 0,
      template: {},
      printData: { name: "demo" },

      // 事件内重设打印模板+数据
      events: {
        template: {},
        printData: {},
        onSave: function (templateKey, templateLayoutData) {
          this.template = JSON.parse(JSON.stringify(templateLayoutData));
        },
        onEdit: function (templateLayoutData) {
          this.template = JSON.parse(JSON.stringify(templateLayoutData));
        },
        onEditData: function (templatePrintData) {
          this.printData = JSON.parse(JSON.stringify(templatePrintData));
        },
      },

      // 隐藏水印元素
      templateId: null,
      hideStyleElement: null,

      // 当前送货单位名称
      customerDeptName: "demo",
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
    // disAutoConnect();
    this.hideWatermark();
  },
  beforeDestroy() {
    // 直接移除之前添加的 <style> 标签（如果有）
    if (this.hideStyleElement) {
      document.head.removeChild(this.hideStyleElement);
    }
  },
  methods: {
    /** 隐藏水印 */
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
    /** 组件初始化回调方法 */
    onDesigned(e) {
      const designer = e.detail;
      console.log("designer ", designer);

      this.templateId = designer.printTemplate.id;
      this.designerUtils = designer.designerUtils;
      this.hiprint = designer.hiprint;
      this.printTemplate = designer.printTemplate;
    },
    /** 编辑打印模板 */
    editTemplate() {
      this.designerUtils.edit();
    },
    /** 编辑打印数据 */
    editPrintData() {
      this.designerUtils.editData();
    },
    /** 点击预览 */
    preview() {
      console.log("designerUtils", this.designerUtils);
      console.log("hiprint", this.hiprint);
      console.log("printTemplate", this.printTemplate);

      this.designerUtils.preview.show();
    },
    /** 测试打印 */
    printTest() {
      let hiprintTemplate = new hiprint.PrintTemplate({
        template: this.events.template,
      });
      console.log("hiprintTemplate", hiprintTemplate);
      let html = hiprintTemplate.getHtml(this.events.printData);
      console.log("html data", html);

      // 添加隐藏打印时水印和其他元素的样式
      const printHideStyle = document.createElement("style");
      printHideStyle.id = "print-hide-style";
      printHideStyle.innerHTML = `
        div[class*="${hiprintTemplate.id}"],
        .hiprint-printPaper-background,
        #dragBox-rotateTools,
        #SVPrint .svp-footer {
          display: none !important;
        }
      `;

      console.log("printHideStyle", printHideStyle);
      hiprintTemplate.print2(this.events.printData, {
        styleHandler: () => {
          // 这里拼接成放html->head标签内的css/style
          let css =
            '<link rel="stylesheet" type="text/css" media="print" href="/print-lock.css">';

          // 2.重写样式：在原有基础上加上 printHideStyle
          css += printHideStyle.outerHTML;
          return css;
        },
      });
    },
  },
};
</script>

<style scoped>
.sv-print-container {
  width: 100%;
  height: 100%;
}
</style>