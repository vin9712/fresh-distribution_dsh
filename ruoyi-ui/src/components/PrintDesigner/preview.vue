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
            type="primary"
            icon="el-icon-notebook-2"
            @click.stop="toPdf"
            >导出pdf</el-button
          >
          <el-button
            :loading="waitShowPrinter"
            type="primary"
            icon="el-icon-printer"
            @click.stop="print"
            >打印</el-button
          >

          <el-divider direction="vertical"></el-divider>
          <el-select
            v-model="selectPrinter"
            placeholder="请选择打印机"
            @change="changePrinter"
          >
            <el-option
              v-for="printer in printerList"
              :key="printer.name"
              :label="printer.displayName"
              :value="printer.name"
            />
          </el-select>
          <el-button
            size="small"
            type="success"
            icon="el-icon-refresh"
            circle
            title="刷新打印机列表"
            style="margin-left: 3px"
            @click="refreshPrinters"
          ></el-button>
          <el-divider direction="vertical"></el-divider>

          <el-button
            :loading="waitShowDirectlyPrinter"
            type="primary"
            icon="el-icon-download"
            @click.stop="printDirectly"
            >直接打印</el-button
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
import { autoConnect, disAutoConnect, hiprint } from "@sv-print/hiprint";

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
      // 选择的打印机
      selectPrinter: null,
      // 打印机列表
      printerList: [],
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
  watch: {
    /** 显示预览时才发起连接 */
    visible(val) {
      hiprint.hiwebSocket.stop();
      if (val) {
        hiprint.hiwebSocket.start(() => {});
      }
    },
  },
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
        // 更新打印机列表
        this.printerList = this.hiprintTemplate.getPrinterList();
        if (this.printerList.length > 0) {
          this.selectPrinter = this.printerList[0].name;
        }

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
    refreshPrinters() {
      // 刷新打印机列表+已选打印机
      hiprint.refreshPrinterList(() => {
        this.printerList = this.hiprintTemplate.getPrinterList();
        const exist = this.printerList.find(
          (item) => item.name === this.selectPrinter
        );
        if (exist) {
          this.selectPrinter = exist.name;
        } else {
          this.selectPrinter = null;
        }
      });
    },
    changePrinter(item) {
      this.selectPrinter = item;
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
