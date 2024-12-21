<template>
  <div class="sv-print-container">
    <Designer
      :template="template"
      :printData="printData"
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
import "sv-print/dist/style.css"; // sv-print 样式

export default {
  components: { Designer },
  data() {
    return {
      // 设计器工具类
      designerUtils: null,
      printTemplate: null,
      hiprint: null,

      // 打印模板+数据
      template: {},
      printData: {
        table: [
          {
            index: 1,
            productName: "测试商品01",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 2,
            productName: "测试商品02",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 3,
            productName: "测试商品03",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 4,
            productName: "测试商品04",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 5,
            productName: "测试商品05",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 6,
            productName: "测试商品06",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 7,
            productName: "测试商品07",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 8,
            productName: "测试商品08",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 9,
            productName: "测试商品09",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 10,
            productName: "测试商品10",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 11,
            productName: "测试商品11",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 12,
            productName: "测试商品12",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 13,
            productName: "测试商品13",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 14,
            productName: "测试商品14",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 15,
            productName: "测试商品15",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 16,
            productName: "测试商品16",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 17,
            productName: "测试商品17",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 18,
            productName: "测试商品18",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 19,
            productName: "测试商品19",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 20,
            productName: "测试商品20",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 21,
            productName: "测试商品21",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 22,
            productName: "测试商品22",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 23,
            productName: "测试商品23",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 24,
            productName: "测试商品24",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 25,
            productName: "测试商品25",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 26,
            productName: "测试商品26",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 27,
            productName: "测试商品27",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 28,
            productName: "测试商品28",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 29,
            productName: "测试商品29",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
          {
            index: 30,
            productName: "测试商品30",
            num: 2,
            productUnit: "斤",
            productSpec: "1*24g",
            TM: "2O22010100110",
            productPrice: "6.8",
            expectAmount: "13.6",
          },
        ],
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
  },
};
</script>

<style scoped>
.sv-print-container {
  width: 100%;
  height: 100%;
}
</style>