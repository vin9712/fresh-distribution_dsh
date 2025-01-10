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
          <el-divider direction="vertical"></el-divider>
          <el-select
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
import { hiprint } from "@sv-print/hiprint";
import { listTemplate } from "@/api/print/template";
import { deliveryPrintData } from "@/api/order/sale";
import ExcelJs from "exceljs";

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
            console.log("connect_error, e: ", e);
            hiprint.hiwebSocket.stop();
            this.$message.error(
              "WebSocket 连接失败，请检查是否安装打印客户端或联系管理员。"
            );
            return;
          });
          socket.on("connect_timeout", (e) => {
            console.log("connect_timeout, e: ", e);
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
      let html = this.hiprintTemplate.getHtml(this.printData);
      console.log("html", html);

      // 创建一个新的ExcelJs工作簿
      const workbook = new ExcelJs.Workbook();
      const worksheet = workbook.addWorksheet("Sheet1");

      // 将HTML内容转换为Excel表格
      const table = new DOMParser().parseFromString(html, "text/html");
      const rows = table.querySelectorAll("tr");
      rows.forEach((row, rowIndex) => {
        const cells = row.querySelectorAll("td");
        cells.forEach((cell, cellIndex) => {
          worksheet.getCell(rowIndex + 1, cellIndex + 1).value =
            cell.textContent;
        });
      });

      // 保存Excel文件
      workbook.xlsx.writeBuffer().then((buffer) => {
        const blob = new Blob([buffer], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "print_preview.xlsx";
        link.click();
        URL.revokeObjectURL(url);
      });
    },
    parseHtmlDemo() {
      const papers = $(".hiprint-printPaper");
      papers.each(function (index, paper) {
        console.log("paper", paper);
        var orignalHeight = paper.offsetHeight;

        // 文本元素，并按照元素offsetTop高度升序
        const textEles = $(paper)
          .find(".hiprint-printElement-text")
          .sort(function (a, b) {
            return a.offsetTop - b.offsetTop;
          });
        console.log("textEles", textEles);
        // textEles.each(function (index, textEle) {
        //     console.log('textEle', textEle.innerText);
        //     console.log('textEle.offsetTop', textEle.offsetTop);
        // })

        // 页码元素
        const paperNumberEle = $(paper).find(".hiprint-paperNumber");
        // console.log('paperNumberEle', paperNumberEle, paperNumberEle[0].offsetTop);

        // 表格元素
        const tableEle = $(paper).find(".hiprint-printElement-table-content");
        // console.log('tableEle', tableEle);

        // 遍历表头
        const tableHeaderEle = $(paper).find("thead");
        console.log("tableHeaderEle", tableHeaderEle);
        tableHeaderEle.each(function (index, tableHeader) {
          console.log("tableHeader", tableHeader);
        });

        const tableFooterEle = $(paper).find("tfoot");
        const tableGridFooterEle = $(paper).find(".hiprint-gridColumnsFooter");

        // 获取表头<thead>和表尾<tfoot>元素或的高度
        const tableHeaderHeight = tableHeaderEle.offset().top;
        const tableFooterHeight =
          tableFooterEle.length > 0
            ? tableFooterEle.offset().top
            : tableGridFooterEle.offset().top;
        console.log(
          "tableHeaderHeight",
          tableHeaderHeight,
          "tableFooterHeight",
          tableFooterHeight
        );

        // 获取表格中的表身，遍历元素
        const bodyEle = $(paper).find("tbody");
        console.log("bodyEle", bodyEle);
        bodyEle.each(function (index, body) {
          console.log("body", body);
          const rows = $(body).find("tr");
          console.log("rows", rows);
          rows.each(function (index, row) {
            console.log("row", row);
            const cells = $(row).find("td");
            console.log("cells", cells);
          });
        });
      });
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
