<template>
  <div style="display: inline-block" @click.stop>
    <el-popover
      ref="popover"
      placement="bottom"
      title="查询条件"
      width="300"
      trigger="click"
    >
      <!-- 单个文本框 -->
      <div v-if="filterType == 'text'">
        <el-input
          v-model.trim="conditions.text"
          size="mini"
          clearable
          placeholder="请输入查询内容"
          @keyup.native.enter="confirm()"
        />
      </div>
      <!-- 数值范围 -->
      <div v-else-if="filterType == 'number'">
        <el-input
          v-model.trim="conditions.number1"
          size="mini"
          clearable
          type="number"
          step="0.01"
          placeholder="请输入开始数值"
        />
        <el-input
          v-model.trim="conditions.number2"
          size="mini"
          clearable
          step="0.01"
          style="margin-top: 10px"
          placeholder="请输入结束数值"
        />
      </div>
      <!-- 日期-->
      <div v-else-if="filterType == 'date'">
        <el-date-picker
          v-model="conditions.date1"
          type="date"
          clearable
          size="mini"
          placeholder="开始时间"
          value-format="yyyy-MM-dd"
        />
        <el-date-picker
          v-model="conditions.date2"
          style="margin-top: 10px"
          type="date"
          size="mini"
          clearable
          placeholder="结束时间"
          value-format="yyyy-MM-dd"
        />
      </div>
      <!-- 下拉框-->
      <div v-else-if="filterType == 'select'">
        <el-select
          v-model="conditions.select"
          placeholder="请选择"
          size="mini"
          clearable
        >
          <el-option
            v-for="(item, index) in customArrList"
            :key="index"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <!-- 复选框-->
      <div v-else-if="filterType == 'checkbox'">
        <el-checkbox-group v-model="conditions.checkbox">
          <el-checkbox
            v-for="(item, index) in customArrList"
            :key="index"
            :label="item.value"
            >{{ item.label }}</el-checkbox
          >
        </el-checkbox-group>
      </div>
      <!--单选按钮-->
      <div v-else-if="filterType == 'radio'">
        <el-radio-group v-model="conditions.radio">
          <el-radio
            v-for="(item, index) in customArrList"
            :key="index"
            :label="item.value"
            border
            size="mini"
            >{{ item.label }}</el-radio
          >
        </el-radio-group>
      </div>
      <!-- confirm 确定框-->
      <div class="text-right mgt10">
        <el-button type="warning" size="mini" @click="reset">重置</el-button>
        <el-button type="primary" size="mini" @click="confirm">确定</el-button>
      </div>
      <!-- 标题-->
      <span
        slot="reference"
        onselectstart="return false"
        oncontextmenu="return false"
        :class="labelColor"
        >{{ column.label }} &nbsp;<i />
      </span>
    </el-popover>
  </div>
</template>
<script>
export default {
  name: "FilterHeader",
  props: {
    column: {
      type: Object,
      defalut: null,
    },
    fieldName: {
      type: String,
      defalut: "",
    },
    filterType: {
      type: String,
      defalut: "txt",
    },
    customArrList: {
      type: Array,
      defalut: [],
    },
  },
  data() {
    return {
      conditions: {
        text: "",
        number1: "",
        number2: "",
        date1: "",
        date2: "",
        select: "",
        checkbox: [],
        radio: "",
      },
    };
  },
  computed: {
    // 有条件的话高亮显示标题
    labelColor() {
      switch (this.filterType) {
        case "text":
          if (this.conditions.text) {
            return "heighLight";
          }
          return "";
        case "number":
          if (this.conditions.number1 || this.conditions.number2) {
            return "heighLight";
          }
          return "";
        case "date":
          if (this.conditions.date1 || this.conditions.date2) {
            return "heighLight";
          }
          return "";
        case "select":
          if (this.conditions.select) {
            return "heighLight";
          }
          return "";
        case "checkbox":
          if (this.conditions.checkbox.length > 0) {
            return "heighLight";
          }
          return "";
        case "radio":
          if (this.conditions.radio !== "") {
            return "heighLight";
          }
          return "";
      }
      return "";
    },
  },
  methods: {
    confirm() {
      this.$refs.popover.doClose();
      this.$emit("tableFilter", {
        filterType: this.filterType,
        fieldName: this.fieldName,
        conditions: this.conditions,
      });
    },
    reset() {
      switch (this.filterType) {
        case "text":
          this.conditions.text = "";
          break;
        case "number":
          this.conditions.number1 = "";
          this.conditions.number2 = "";
          break;
        case "date":
          this.conditions.date1 = "";
          this.conditions.date2 = "";
          break;
        case "select":
          this.conditions.select = "";
          break;
        case "checkbox":
          this.conditions.checkbox = [];
          break;
        case "radio":
          this.conditions.radio = "";
          break;
      }
      this.$refs.popover.doClose();
      this.$emit("resetFilter", {
        filterType: this.filterType,
        fieldName: this.fieldName,
        conditions: this.conditions,
      });
    },
  },
};
</script>
<style scoped>
.label {
  user-select: none;
}
.heighLight {
  color: #409eff;
}
.filter-radio {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
}
.filter-radio .el-radio {
  width: 45%;
  margin-right: 0;
}
</style>
