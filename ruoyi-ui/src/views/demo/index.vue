<template>
  <div class="sv-print-container">
    <Designer
      :template="template"
      :printData="printData"
      :events="events"
      :plugins="plugins"
      :providers="providerList"
      :providerMap="providerMapList"
      theme="winter"
      @onDesigned="onDesigned"
    >
      <!-- 自定义 header -->
      <div class="svp-header svelte-ien9fs" slot="header">
        <el-row :gutter="20" class="desinger-header">
          <el-col :span="4" style="text-align: left">{{
            "送货单位: " + customerDeptName
          }}</el-col>
          <el-col :span="16" class="svp-header-center-menu-container">
            <ul class="svp-header-menu-list">
              <li
                v-for="(menu, index) in headerMenus"
                :key="index"
                :id="menu.id"
                class="svp-header-menu svelte-ien9fs"
                @click="menu.action"
              >
                <i :class="['svicon', menu.iconClass, 'svelte-ien9fs']"></i>
                <p class="svelte-ien9fs">{{ menu.label }}</p>
              </li>
            </ul>
          </el-col>
          <el-col :span="4" class="svp-header-right-menu-container">
            <ul class="svp-header-menu-list">
              <li id="save" class="svp-header-menu svelte-ien9fs" @click="save">
                <i class="svicon sv-save svelte-ien9fs"></i>
                <p class="svelte-ien9fs">保存</p>
              </li>
              <li
                id="close"
                class="svp-header-menu svelte-ien9fs"
                @click="close"
              >
                <i class="svicon sv-close svelte-ien9fs"></i>
                <p class="svelte-ien9fs">关闭</p>
              </li>
            </ul>
          </el-col>
        </el-row>
      </div>
    </Designer>
  </div>
</template>

<script>
import { Designer } from "@sv-print/vue";
import { disAutoConnect, hiprint } from "@sv-print/hiprint";
import "sv-print/dist/style.css"; // sv-print 样式
import provider from "./provider";
import svPrintPlugin from "@/views/demo/sv-print-plugin.js";

export default {
  components: { Designer },
  data() {
    return {
      // 设计器工具类
      designerUtils: null,
      printTemplate: null,

      // 默认打印模板+数据
      template: {},
      printData: { name: "demo" },
      // 插件列表
      plugins: [],
      // 重设回调事件
      events: {
        onSave: function (templateKey, templateLayoutData) {
          this.template = JSON.parse(JSON.stringify(templateLayoutData));
        },
        onEdit: function (templateLayoutData) {
          this.template = JSON.parse(JSON.stringify(templateLayoutData));
        },
        onEditData: function (templatePrintData) {
          this.printData = JSON.parse(JSON.stringify(templatePrintData));
        },
        onKeyDownEvent: function (events, self) {
          console.log("e", events);
          console.log("b", self);

          // 如果点下 Esc，则关闭设计器弹窗
          if (events.keyCode === 27) {
            const openModalDom = document.querySelector(".modal-open");
            if (openModalDom) {
              // 编辑弹窗
              const editModal = openModalDom.querySelector(".editorBox");
              if (editModal) {
                self.editor.close();
                return;
              }

              // 预览弹窗
              const previewModal =
                openModalDom.querySelector("#preview_content");
              if (previewModal) {
                self.preview.hide();
                return;
              }
            }
          }
        },
      },
      // 自定义拖拽元素
      providerList: [new provider()],
      providerMapList: {
        container: ".hiprintEpContainer",
        value: "customProviderModule",
      },

      // 当前送货单位名称
      customerDeptName: "demo",
      // 自定义表头按钮
      headerMenus: [
        {
          id: "editTemplate",
          iconClass: "sv-edit",
          label: "编辑模板",
          action: this.editTemplate,
        },
        {
          id: "editPrintData",
          iconClass: "sv-edit-data",
          label: "编辑数据",
          action: this.editPrintData,
        },
        {
          id: "preview",
          iconClass: "sv-preview",
          label: "预览",
          action: this.preview,
        },
        {
          id: "printTest",
          iconClass: "sv-print",
          label: "测试打印",
          action: this.printTest,
        },
        {
          id: "printTestDirectly",
          iconClass: "sv-print",
          label: "测试直接打印",
          action: this.printTestDirectly,
        },
      ],
    };
  },
  mounted() {
    // disAutoConnect();
    this.initSvPrintPlugin();
  },
  methods: {
    /** 初始化插件 */
    initSvPrintPlugin() {
      this.plugins = [];
      this.plugins.push(svPrintPlugin());
    },
    /** 组件初始化回调方法 */
    onDesigned(e) {
      const designer = e.detail;
      console.log("designer ", designer);

      // this.hiprint = designer.hiprint;
      this.designerUtils = designer.designerUtils;
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
      this.designerUtils.preview.show();
    },
    /** 测试打印 */
    printTest() {
      // 重设打印模板
      this.template = this.printTemplate.getJson();
      let hiprintTemplate = new hiprint.PrintTemplate({
        template: this.template,
      });

      hiprintTemplate.print(this.printData);
    },
    /** 测试直接打印 */
    printTestDirectly() {
      // 重设打印模板
      this.template = this.printTemplate.getJson();
      console.log("this.template", this.template);
      let hiprintTemplate = new hiprint.PrintTemplate({
        template: this.template,
      });

      hiprintTemplate.print2(this.printData);
    },
    /** 保存样式 */
    save() {
      console.log("close template designer");
    },
    /** 关闭 */
    close() {
      console.log("close designer window");
    },
  },
};
</script>

<style scoped>
.sv-print-container {
  width: 100%;
  height: 100%;
}

.desinger-header {
  width: 100vw;
  justify-content: space-between;
}

.svp-header-center-menu-container {
  display: flex;
  text-align: center;
  justify-content: center;
  padding-top: 3px;
}

.svp-header-right-menu-container {
  display: flex;
  padding-right: 0px !important;
  text-align: right;
  justify-content: right;
  padding-top: 3px;
}

.svp-header-menu-list {
  list-style: none; /* 移除默认的列表符号 */
  padding: 0;
  margin: 0;
  display: flex;
  justify-content: center; /* 水平居中对齐 */
  align-items: center; /* 垂直居中对齐 */
}

.svp-header-menu-list li {
  display: inline-block; /* 确保列表项并排显示 */
  margin: 0 1px; /* 列表项之间的间距 */
  cursor: pointer; /* 更改鼠标指针为手型 */
}

.svp-header-menu-list li:hover {
  background-color: #f5f5f5; /* 鼠标悬停时改变背景色 */
}
</style>
