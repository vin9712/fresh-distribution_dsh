<template>
  <div class="spreadsheet">
    <ve-table
      style="word-break: break-word"
      fixed-header
      :scroll-width="0"
      :max-height="500"
      border-y
      :columns="columns"
      :table-data="tableData"
      row-key-field-name="rowKey"
      :virtual-scroll-option="virtualScrollOption"
      :cell-autofill-option="cellAutofillOption"
      :edit-option="editOption"
      :contextmenu-body-option="contextmenuBodyOption"
      :contextmenu-header-option="contextmenuHeaderOption"
      :row-style-option="rowStyleOption"
      :column-width-resize-option="columnWidthResizeOption"
    />
  </div>
</template>
<script>
const COLUMN_KEYS = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"];
export default {
  data() {
    return {
      startRowIndex: 0,
      // 是否开启列宽可变
      columnWidthResizeOption: {
        enable: true,
      },
      // 虚拟滚动配置
      virtualScrollOption: {
        enable: true,
        scrolling: this.scrolling,
      },
      // 单元格自动填充配置
      cellAutofillOption: {
        directionX: true,
        directionY: true,
      },
      // 单元格编辑配置
      editOption: {
        afterCellValueChange: ({ row, column, changeValue }) => {
          console.log(row, column, changeValue);
          console.log(this.tableData);
        },
      },
      // header 右键菜单配置
      contextmenuHeaderOption: {
        contextmenus: [
          {
            type: "CUT",
          },
          {
            type: "COPY",
          },
          {
            type: "EMPTY_COLUMN",
          },
        ],
      },
      // body 右键菜单配置
      contextmenuBodyOption: {
        contextmenus: [
          {
            type: "CUT",
          },
          {
            type: "COPY",
          },
          {
            type: "SEPARATOR",
          },
          {
            type: "INSERT_ROW_ABOVE",
          },
          {
            type: "INSERT_ROW_BELOW",
          },
          {
            type: "SEPARATOR",
          },
          {
            type: "REMOVE_ROW",
          },
          {
            type: "EMPTY_ROW",
          },
          {
            type: "EMPTY_CELL",
          },
        ],
      },
      // 行样式配置
      rowStyleOption: {
        clickHighlight: false,
        hoverHighlight: false,
      },
      tableData: [],
    };
  },
  computed: {
    columns() {
      let columns = [
        {
          field: "index",
          key: "index",
          operationColumn: true,
          title: "",
          width: 55,
          fixed: "left",
          renderBodyCell: this.renderRowIndex,
        },
      ];
      columns = columns.concat(
        COLUMN_KEYS.map((keyValue) => {
          return {
            title: keyValue,
            field: keyValue,
            key: keyValue,
            width: 90,
            edit: true,
          };
        })
      );
      return columns;
    },
  },
  methods: {
    renderRowIndex({ row, column, rowIndex }) {
      return <span>{rowIndex + this.startRowIndex + 1}</span>;
    },
    scrolling({
      startRowIndex,
      visibleStartIndex,
      visibleEndIndex,
      visibleAboveCount,
      visibleBelowCount,
    }) {
      this.startRowIndex = startRowIndex;
    },
    // 初始化表格
    initTableData() {
      let tableData = [];
      for (let i = 0; i < 5000; i++) {
        let dataItem = {
          rowKey: i,
        };
        COLUMN_KEYS.forEach((keyValue) => {
          dataItem[keyValue] = "";
        });
        if (i === 1 || i === 3) {
          dataItem["C"] = "YOU";
          dataItem["D"] = "CAN";
          dataItem["E"] = "TRY";
          dataItem["F"] = "ENTER";
          dataItem["G"] = "SOME";
          dataItem["H"] = "WORDS";
          dataItem["I"] = "!!!";
        }
        tableData.push(dataItem);
      }
      this.tableData = tableData;
    },
  },
  created() {
    this.initTableData();
  },
};
</script>
<style lang="scss">
.spreadsheet {
  padding: 0 10px;
  margin: 20px 0;
}
</style>
