import { hiprint } from "@sv-print/hiprint";

// 左侧拖拽区域设计: customProviderModule
export default function (options) {
  var addElementTypes = function (context) {
    context.removePrintElementTypes("customProviderModule");
    context.addPrintElementTypes("customProviderModule", [
      new hiprint.PrintElementTypeGroup("常规", [
        {
          tid: "customProviderModule.header",
          title: "标题",
          data: "送货单",
          type: "text",
          options: {
            height: 17,
            fontSize: 16.5,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
            fontFamily: "SimSun",
          },
        },
        {
          tid: "customProviderModule.text",
          title: "文本",
          type: "text",
        },
        {
          tid: "customProviderModule.longText",
          title: "长文本",
          type: "longText",
        },
        {
          tid: "customProviderModule.hline",
          title: "横线",
          type: "hline",
        },
        {
          tid: "customProviderModule.vline",
          title: "竖线",
          type: "vline",
        },
        {
          tid: "customProviderModule.rect",
          title: "矩形",
          type: "rect",
        },
        {
          tid: "customProviderModule.oval",
          title: "椭圆",
          type: "oval",
        },
        {
          tid: "customProviderModule.barcode",
          title: "barcode",
          type: "text",
          options: {
            textType: "barcode",
            height: 50,
            width: 100,
          },
        },
        {
          tid: "customProviderModule.qrcode",
          title: "二维码",
          type: "text",
          options: {
            textType: "qrcode",
            height: 50,
            width: 50,
          },
        },
        {
          tid: "customProviderModule.logo",
          title: "图片",
          data: "",
          type: "image",
        },
        {
          tid: "customProviderModule.table",
          title: "表格",
          type: "table",
          width: 50,
          columns: [
            [
              {
                title: "Col1",
                field: "column1",
                width: 50,
              },
              {
                title: "Col2",
                field: "column2",
                width: 50,
              },
            ],
          ],
        },
        {
          tid: "customProviderModule.html",
          title: "html",
          formatter: function (data, options) {
            return '<div style="height:50pt;width:50pt;background:red;"></div>';
          },
          type: "html",
        },
      ]),
      new hiprint.PrintElementTypeGroup("送货订单", [
        {
          tid: "customProviderModule.deliveryHeader",
          title: "送货单",
          data: "送货单",
          type: "text",
          options: {
            height: 17,
            testData: "送货单",
            fontSize: 16.5,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
            fontFamily: "SimSun",
          },
        },
        {
          tid: "customProviderModule.customerDeptName",
          title: "客户",
          data: "xxxx公司",
          type: "text",
          options: {
            field: "customerDeptName",
            testData: "xxxx公司",
            height: 16,
            fontSize: 9.75,
            fontWeight: "600",
            textAlign: "left",
            textContentVerticalAlign: "middle",
            fontFamily: "SimSun",
          },
        },
        {
          tid: "customProviderModule.deliveryDate",
          title: "日期",
          data: "2024.01.01",
          type: "text",
          options: {
            field: "deliveryDate",
            testData: "2024.01.01",
            height: 16,
            width: 90,
            fontSize: 9.75,
            fontWeight: "600",
            textAlign: "left",
            textContentVerticalAlign: "middle",
            fontFamily: "SimSun",
          },
        },
        {
          tid: "customProviderModule.deliveryTable",
          title: "送货表格",
          type: "table",
          options: {
            field: "table",
            tableHeaderRepeat: "page",
            tableFooterRepeat: "last",
            fields: [
              {
                text: "序号",
                field: "index",
              },
              {
                text: "商品名称",
                field: "productName",
              },
              {
                text: "单位",
                field: "productUnit",
              },
              {
                text: "计划数量",
                field: "num",
              },
              {
                text: "实收数量",
                field: "actualNum",
              },
              {
                text: "单价",
                field: "productPrice",
              },
              {
                text: "金额",
                field: "expectAmount",
              },
            ],
          },
          editable: true,
          columnDisplayEditable: true, //列显示是否能编辑
          columnDisplayIndexEditable: true, //列顺序显示是否能编辑
          columnTitleEditable: true, //列标题是否能编辑
          columnResizable: true, //列宽是否能调整
          columnAlignEditable: true, //列对齐是否调整
          isEnableEditField: true, //编辑字段
          isEnableContextMenu: true, //开启右键菜单 默认true
          isEnableInsertRow: true, //插入行
          isEnableDeleteRow: true, //删除行
          isEnableInsertColumn: true, //插入列
          isEnableDeleteColumn: true, //删除列
          isEnableMergeCell: true, //合并单元格
          columns: [
            [
              {
                title: "序号",
                align: "center",
                field: "index",
                width: 10,
                tableTextType: "sequence",
                tableSummaryTitle: true,
                tableSummaryText: "小计",
                tableSummaryColspan: "6",
                tableSummaryAlign: "left",
                tableSummary: "",
              },
              {
                title: "商品名称",
                align: "center",
                field: "productName",
                width: 40,
                tableSummaryTitle: true,
                tableSummaryColspan: "0",
                tableSummary: "",
              },
              {
                title: "单位",
                align: "center",
                field: "productUnit",
                width: 10,
                tableSummaryTitle: true,
                tableSummaryColspan: "0",
                tableSummary: "",
              },
              {
                title: "计划数量",
                align: "center",
                field: "num",
                width: 20,
                tableSummaryTitle: true,
                tableSummaryColspan: "0",
                tableSummary: "",
                formatter2: function (value, row, index, options) {
                  const numericValue = parseFloat(value);
                  if (!isNaN(numericValue)) {
                    return numericValue.toFixed(2);
                  } else {
                    return value;
                  }
                },
              },
              {
                title: "实收数量",
                align: "center",
                field: "actualNum",
                width: 20,
                tableSummaryTitle: true,
                tableSummaryColspan: "0",
                tableSummary: "",
                formatter2: function (value, row, index, options) {
                  return "";
                },
              },
              {
                title: "单价",
                align: "center",
                field: "productPrice",
                width: 20,
                tableSummaryTitle: true,
                tableSummaryColspan: "0",
                tableSummary: "",
                formatter2: function (value, row, index, options) {
                  const numericValue = parseFloat(value);
                  if (!isNaN(numericValue)) {
                    return numericValue.toFixed(2);
                  } else {
                    return value;
                  }
                },
              },
              {
                title: "金额",
                align: "center",
                field: "expectAmount",
                width: 20,
                tableSummaryTitle: false,
                tableSummaryColspan: "0",
                tableSummary: "sum",
                formatter2: function (value, row, index, options) {
                  const numericValue = parseFloat(value);
                  if (!isNaN(numericValue)) {
                    return numericValue.toFixed(2);
                  } else {
                    return value;
                  }
                },
              },
              {
                title: "规格",
                align: "center",
                field: "productSpec",
                width: 20,
                checked: false,
              },
              {
                title: "备注",
                align: "center",
                field: "remark",
                width: 20,
                checked: false,
              },
              {
                title: "条码",
                align: "center",
                field: "TM",
                width: 25,
                checked: false,
              },
            ],
          ],
          gridColumnsFooterFormatter: function (options, rows, data, pageData) {
            if (data) {
              // 设置自定义内容，id 为 custom-grid-footer
              const textContent = `
                    <div id="custom-grid-footer"; style="display: flex; justify-content: space-between; padding-top: 8px; font-size: 11pt; font-weight: bold; font-family: 'SimSun'">
                        <sapn>收货单位：</sapn>
                        <sapn>送货单位：${data.deliveryName || ""}</sapn>
                    </div>
                `;
              // 遍历 elements 并替换内容
              const elements = document.getElementsByClassName(
                "hiprint-gridColumnsFooter"
              );
              for (let i = 0; i < elements.length; i++) {
                const element = elements[i];
                element.innerHTML = textContent;
              }
              return textContent;
            }
            return "自定义表尾内容";
          },
        },
        {
          tid: "customProviderModule.deliveryName",
          title: "送货单位",
          data: "xxxx公司",
          type: "text",
          options: {
            testData: "xxxx公司",
            height: 16,
            fontSize: 9.75,
            fontWeight: "600",
            textAlign: "left",
            textContentVerticalAlign: "middle",
            fontFamily: "SimSun",
          },
        },
      ]),
    ]);
  };
  return {
    addElementTypes: addElementTypes,
  };
}
