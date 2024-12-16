import { hiprint } from "vue-plugin-hiprint";

// 左侧拖拽区域设计: customProviderModule
export default function (options) {
  var addElementTypes = function (context) {
    context.removePrintElementTypes("customProviderModule");
    context.addPrintElementTypes("customProviderModule", [
      new hiprint.PrintElementTypeGroup("常规", [
        {
          tid: "customProviderModule.header",
          title: "单据表头",
          data: "单据表头",
          type: "text",
          options: {
            testData: "单据表头",
            height: 17,
            fontSize: 16.5,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
          },
        },
        {
          tid: "customProviderModule.type",
          title: "单据类型",
          data: "单据类型",
          type: "text",
          options: {
            testData: "单据类型",
            height: 16,
            fontSize: 15,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
          },
        },
        {
          tid: "customProviderModule.order",
          title: "订单编号",
          data: "XS888888888",
          type: "text",
          options: {
            field: "orderId",
            testData: "XS888888888",
            height: 16,
            fontSize: 6.75,
            fontWeight: "700",
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "customProviderModule.date",
          title: "业务日期",
          data: "2020-01-01",
          type: "text",
          options: {
            field: "date",
            testData: "2020-01-01",
            height: 16,
            fontSize: 6.75,
            fontWeight: "700",
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "customProviderModule.barcode",
          title: "条形码",
          data: "XS888888888",
          type: "text",
          options: {
            field: "barcode",
            testData: "XS888888888",
            height: 32,
            fontSize: 12,
            lineHeight: 18,
            textType: "barcode",
          },
        },
        {
          tid: "customProviderModule.qrcode",
          title: "二维码",
          data: "XS888888888",
          type: "text",
          options: {
            field: "qrcode",
            testData: "XS888888888",
            height: 32,
            fontSize: 12,
            lineHeight: 18,
            textType: "qrcode",
          },
        },
        {
          tid: "customProviderModule.platform",
          title: "平台名称",
          data: "平台名称",
          type: "text",
          options: {
            testData: "平台名称",
            height: 17,
            fontSize: 16.5,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
          },
        },
        {
          tid: "customProviderModule.image",
          title: "Logo",
          data: "",
          type: "image",
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
              },
              {
                title: "商品名称",
                align: "center",
                field: "productName",
                width: 40,
              },
              {
                title: "单位",
                align: "center",
                field: "productUnit",
                width: 10,
              },
              {
                title: "计划数量",
                align: "center",
                field: "num",
                width: 20,
              },
              {
                title: "实收数量",
                align: "center",
                field: "actualNum",
                width: 20,
              },
              {
                title: "单价",
                align: "center",
                field: "productPrice",
                width: 20,
              },
              {
                title: "金额",
                align: "center",
                field: "expectAmount",
                width: 20,
              },
              {
                title: "规格",
                align: "center",
                field: "productSpec",
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
          footerFormatter: function (
            options,
            rows,
            data,
            currentPageGridRowsData
          ) {
            if (data && data["totalCap"]) {
              return `<td style="padding:0 10px" colspan="100">${"小计: " + data["totalCap"]
                }</td>`;
            }
            return '<td style="padding:0 10px" colspan="100">小计: </td>';
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
