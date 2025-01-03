<template>
  <el-dialog
    :visible.sync="visible"
    :maskClosable="false"
    @close="hideModal"
    :width="dialogWidth"
  >
    <div id="preview_content"></div>
    <template slot="title">
      <div style="margin-right: 20px">打印预览</div>
      <el-row :gutter="20">
        <el-col :span="12" :offset="6" style="text-align: center">
          <el-button
            :loading="waitShowPrinter"
            type="primary"
            icon="printer"
            @click.stop="print"
            >打印</el-button
          >

          <el-button
            :loading="waitShowDirectlyPrinter"
            type="primary"
            icon="printer"
            @click.stop="printDirectly"
            >直接打印</el-button
          >
          <el-button type="primary" icon="printer" @click.stop="toPdf"
            >pdf</el-button
          >
        </el-col>
      </el-row>
      <el-divider class="header-divider"></el-divider>
    </template>
    <template slot="footer">
      <el-divider class="footer-divider"></el-divider>
      <el-button key="close" type="info" @click="hideModal"> 关闭 </el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: "printPreview",
  props: {},
  data() {
    return {
      visible: false,
      spinning: true,
      waitShowPrinter: false,
      waitShowDirectlyPrinter: false,
      // 纸张宽 mm
      width: 400,
      // 模板
      hiprintTemplate: {},
      // 数据
      printData: {},
    };
  },
  computed: {
    // 计算属性用于将 mm 转换为 pt
    dialogWidth() {
      // 每毫米大约等于3.78磅
      const mmToPx = Math.round(this.width * 3.78);
      return `${mmToPx}px`;
    },
  },
  watch: {},
  created() {},
  mounted() {},
  methods: {
    hideModal() {
      this.visible = false;
    },
    show(hiprintTemplate, printData, width = "400") {
      this.visible = true;
      this.spinning = true;
      this.width = width;
      this.hiprintTemplate = hiprintTemplate;
      this.printData = printData;
      setTimeout(() => {
        // eslint-disable-next-line no-undef
        $("#preview_content").html(hiprintTemplate.getHtml(printData));
        this.spinning = false;
      }, 500);
    },
    print() {
      this.waitShowPrinter = true;
      this.hiprintTemplate.print(
        this.printData,
        {},
        {
          callback: () => {
            console.log("callback");
            this.waitShowPrinter = false;
          },
        }
      );
    },
    printDirectly() {
      this.waitShowDirectlyPrinter = true;
      this.hiprintTemplate.print2(this.printData, {});
      this.hiprintTemplate.on("printSuccess", function (data) {
        console.log("直接打印完成");
      });
      this.hiprintTemplate.on("printError", function (data) {
        console.log("直接打印失败");
      });
      this.waitShowDirectlyPrinter = false;
    },
    toPdf() {
      this.hiprintTemplate.toPdf({}, "打印预览");
    },
  },
};
</script>

<style lang="scss" scoped>
::v-deep .el-dialog__body {
  background-color: #f3f7fe;
  padding: 0;
  overflow-y: auto;
  height: calc(100vh - 200px);
}

::v-deep .el-dialog {
  margin-bottom: 24px;
}

#preview_content {
  display: flex;
  justify-content: center;
}

::v-deep .hiprint-printTemplate {
  margin-top: 10px;
  border-bottom: 0 !important;
}

.header-divider {
  margin: 10px 0 0 0;
}

.footer-divider {
  margin: 0 0 10px 0;
}
</style>
