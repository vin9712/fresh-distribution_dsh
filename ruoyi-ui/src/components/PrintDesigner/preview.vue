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
        <el-col :span="17" :offset="3" style="text-align: center">
          <!-- 打印模板 -->
          <el-select
            v-model="selectPrintTemplate"
            placeholder="请选择打印模板"
            @change="changePrintTemplate"
            filterable
            style="width: 15%"
          >
            <el-option
              v-for="template in printTemplateList"
              :key="template.id"
              :label="template.name"
              :value="template.id"
            />
          </el-select>

          <!-- PDF/浏览器打印 -->
          <el-divider direction="vertical"></el-divider>
          <el-button
            type="primary"
            icon="el-icon-notebook-2"
            @click.stop="toPdf"
            >导出pdf</el-button
          >
          <el-button
            type="primary"
            icon="el-icon-notebook-2"
            @click.stop="toExcel"
            >导出Excel</el-button
          >
          <el-button
            :loading="waitShowPrinter"
            type="primary"
            icon="el-icon-printer"
            @click.stop="print"
            >打印</el-button
          >

          <!-- 直接打印 -->
          <el-divider
            v-if="printerList.length > 0"
            direction="vertical"
          ></el-divider>
          <el-select
            v-if="printerList.length > 0"
            v-model="selectPrinter"
            placeholder="请选择打印机"
            @change="changePrinter"
            style="width: 15%"
          >
            <el-option
              v-for="printer in printerList"
              :key="printer.name"
              :label="printer.displayName"
              :value="printer.name"
            />
          </el-select>
          <el-button
            v-if="printerList.length > 0"
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
            v-if="printerList.length > 0"
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
import { hiprint } from "@sv-print/hiprint";
import { listTemplate } from "@/api/print/template";
import { deliveryPrintData } from "@/api/order/sale";

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
      // 选择的打印模板
      selectPrintTemplate: null,
      // 打印模板列表
      printTemplateList: [],
      // 默认订单ID
      defaultOrderId: null,
    };
  },
  computed: {
    // 计算属性用于将 mm 转换为 pt
    dialogWidth() {
      // 每毫米大约等于3.78磅
      const mmToPx = Math.round(this.width * 3.78);
      return `${mmToPx}px`;
    },
    // 显示直接打印模块
  },
  watch: {
    /** 显示预览时才发起连接 */
    visible(val) {
      hiprint.hiwebSocket.stop();
      if (val) {
        hiprint.hiwebSocket.start(() => {
          const socket = hiprint.hiwebSocket.socket;
          // 处理 socket 连接异常的情况
          socket.on("connect_error", (e) => {
            hiprint.hiwebSocket.stop();
            this.$message.error(
              "WebSocket 连接失败，请检查是否安装打印客户端或联系管理员。"
            );
            return;
          });
          socket.on("connect_timeout", (e) => {
            hiprint.hiwebSocket.stop();
            this.$message.error(
              "WebSocket 启动超时，请检查是否安装打印客户端或联系管理员。"
            );
            return;
          });
        });
      }
    },
  },
  created() {
    this.getTemplateList();
  },
  mounted() {},
  methods: {
    getTemplateList() {
      listTemplate().then((res) => {
        this.printTemplateList = res.data;
      });
    },
    hideModal() {
      this.visible = false;
    },
    /** 显示预览窗口 */
    show(hiprintTemplate, printData, orderId = null, templateId = null) {
      this.visible = true;
      this.spinning = true;
      this.hiprintTemplate = hiprintTemplate;
      this.printData = printData;
      this.defaultOrderId = orderId;
      this.selectPrintTemplate = templateId;
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
            console.log("print callback");
            this.waitShowPrinter = false;
          },
        }
      );
    },
    printDirectly() {
      if (this.printerList.length <= 0) {
        this.$modal.msgError({
          title: "提示",
          content: "请先安装打印客户端",
        });
        return;
      }

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
    changePrintTemplate(item) {
      this.selectPrintTemplate = item;
      // 刷新当前打印模板+数据
      this.refreshPrintTemplate();
    },
    refreshPrintTemplate() {
      const orderId = this.defaultOrderId;
      const templateId = this.selectPrintTemplate;
      if (!orderId) return;

      // 获取打印模板数据
      deliveryPrintData({
        orderId: orderId,
        templateId: templateId,
      }).then((res) => {
        const printObject = res.data || {};
        if (!printObject) {
          console.error("[refreshPrintTemplate] print object is null");
          return;
        }

        let panel = JSON.parse(printObject.template);
        const hiprintTemplate = new hiprint.PrintTemplate({
          template: panel,
        });
        const printData = printObject.data || {};

        // 重新加载当前预览窗口
        this.show(hiprintTemplate, printData, orderId, templateId);
      });
    },
    toPdf() {
      this.hiprintTemplate.toPdf({}, "打印预览");
    },
    toExcel() {
      const table = $(".hiprint-printPaper").find("table");
      console.log("table", table);
      if (!table || table.length <= 0) {
        this.$message.error("当前模板没有表格数据，无法导出Excel");
        return;
      }

      // 交由后端解析并生成 excel
      let html = this.hiprintTemplate.getHtml(this.printData).html();
      this.download(
        "/print/template/download",
        {
          html: html,
        },
        `print_template_${new Date().getTime()}.xlsx`
      );
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
