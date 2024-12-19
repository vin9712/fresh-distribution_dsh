<template>
  <el-card>
    <el-row style="margin-bottom: 10px">
      <el-col :span="20">
        <!-- 纸张设置 -->
        <el-button-group style="margin: 0 10px">
          <el-button
            v-for="(value, type) in paperTypes"
            :type="curPaperType === type ? 'primary' : ''"
            @click="setPaper(type, value)"
            :key="type"
          >
            {{ type }}
          </el-button>
        </el-button-group>

        <!-- 自定义宽高 -->
        <el-popover
          placement="bottom"
          width="300"
          title="设置纸张宽高(mm)"
          v-model="paperPopVisible"
        >
          <div
            style="
              display: flex;
              align-items: center;
              justify-content: space-between;
              margin-bottom: 10px;
            "
          >
            <el-input-number
              :min="0"
              :max="400"
              :step="10"
              size="small"
              v-model="paperWidth"
              style="width: 120px; text-align: center"
              place="宽（mm）"
            ></el-input-number
            >~
            <el-input-number
              :min="0"
              :max="400"
              :step="10"
              size="small"
              v-model="paperHeight"
              style="width: 120px; text-align: center"
              place="高（mm）"
            ></el-input-number>
          </div>
          <div>
            <el-button
              type="primary"
              style="width: 100%"
              @click="otherPaper"
              size="mini"
              >确定</el-button
            >
          </div>
          <el-button
            slot="reference"
            :type="curPaperType === 'other' ? 'primary' : ''"
            style="margin: 0 10px"
            >自定义宽高</el-button
          >
        </el-popover>

        <!-- 缩放 -->
        <el-input-number
          style="margin: 0 10px"
          size="small"
          :value="scaleValue"
          :precision="2"
          :step="0.1"
          :min="scaleMin"
          :max="scaleMax"
          @change="changeScale"
        ></el-input-number>

        <!-- 预览/打印 -->
        <el-button-group>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-refresh-left"
            @click="rotatePaper()"
            >旋转</el-button
          >
          <el-button
            type="primary"
            size="small"
            icon="el-icon-view"
            @click="preView"
          >
            预览
          </el-button>
          <!-- <el-button
            type="primary"
            size="small"
            icon="el-icon-printer"
            @click="print"
          >
            直接打印
          </el-button> -->
          <el-button
            type="primary"
            size="small"
            icon="el-icon-s-management"
            @click="copy"
          >
            复制
          </el-button>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-s-management"
            @click="save"
          >
            保存
          </el-button>
          <el-button
            type="danger"
            size="small"
            icon="el-icon-delete"
            @click="clearPaper"
          >
            清空
          </el-button>
        </el-button-group>
        <!-- 保存/清空 -->
      </el-col>
    </el-row>

    <!-- 读取 json 更新 -->
    <el-row :gutter="8">
      <el-col :span="4">
        <el-input
          type="textarea"
          placeholder="请输入json样式"
          v-model="jsonIn"
        />
      </el-col>
      <el-col :span="4">
        <el-button
          type="primary"
          size="small"
          icon="el-icon-s-management"
          @click="updateJson"
        >
          更新
        </el-button></el-col
      >
    </el-row>

    <el-row :gutter="8">
      <el-col :span="4">
        <el-card style="height: 100vh">
          <el-row>
            <el-col
              :span="24"
              class="rect-printElement-types hiprintEpContainer"
            >
            </el-col>
          </el-row>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card class="card-design">
          <div id="hiprint-printTemplate" class="hiprint-printTemplate"></div>
        </el-card>
      </el-col>
      <el-col :span="6" class="params_setting_container">
        <el-card>
          <el-row class="hinnn-layout-sider">
            <div id="PrintElementOptionSetting"></div>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
    <!-- 预览 -->
    <print-preview ref="preView" />
  </el-card>
</template>

<script>
import { hiprint } from "vue-plugin-hiprint";
import printPreview from "./preview";
import CustomProvider from "./customProvider";

let hiprintTemplate;
export default {
  name: "printCustom",
  components: { printPreview },
  data() {
    return {
      // 打印数据
      printData: {},
      // 当前纸张
      curPaper: {
        type: "other",
        width: 140,
        height: 216,
        paperHeader: this.mmToPt(22),
        paperFooter: this.mmToPt(186),
      },
      // 纸张类型
      paperTypes: {
        A3: {
          width: 420,
          height: 297,
          paperHeader: this.mmToPt(18),
          paperFooter: this.mmToPt(275),
        },
        A4: {
          width: 210,
          height: 297,
          paperHeader: this.mmToPt(18),
          paperFooter: this.mmToPt(275),
        },
        A5: {
          width: 148,
          height: 210,
          paperHeader: this.mmToPt(13),
          paperFooter: this.mmToPt(193),
        },
        B3: {
          width: 500,
          height: 353,
          paperHeader: this.mmToPt(21),
          paperFooter: this.mmToPt(324),
        },
        B4: {
          width: 250,
          height: 353,
          paperHeader: this.mmToPt(21),
          paperFooter: this.mmToPt(324),
        },
        B5: {
          width: 176,
          height: 250,
          paperHeader: this.mmToPt(15),
          paperFooter: this.mmToPt(230),
        },
      },
      // 缩放
      scaleValue: 1,
      scaleMax: 5,
      scaleMin: 0.5,
      // 自定义纸张
      paperPopVisible: false,
      paperWidth: 140,
      paperHeight: 216,
      // 导入的 json
      jsonIn: "",
    };
  },
  computed: {
    curPaperType() {
      let type = "other";
      let types = this.paperTypes;
      for (const key in types) {
        let item = types[key];
        let { width, height } = this.curPaper;
        if (
          (item.width === width && item.height === height) ||
          (item.width === height && item.height === width)
        ) {
          type = key;
        }
      }
      return type;
    },
    // 面板配置
    templatePanel() {
      return {
        panels: [
          {
            name: 0,
            width: this.curPaper.width,
            height: this.curPaper.height,
            paperFooter: this.curPaper.paperFooter,
            paperHeader: this.curPaper.paperHeader,
          },
        ],
      };
    },
  },
  watch: {
    curPaperType(val) {
      if (val === "other") {
      }
    },
  },
  mounted() {
    this.init();
    this.otherPaper();
  },
  methods: {
    init() {
      hiprint.init({
        providers: [new CustomProvider()],
      });
      $(".hiprintEpContainer").empty();
      hiprint.PrintElementTypeManager.build(
        ".hiprintEpContainer",
        "customProviderModule"
      );
      $("#hiprint-printTemplate").empty();
      console.log("templatePanel", this.templatePanel);
      hiprintTemplate = new hiprint.PrintTemplate({
        template: this.templatePanel,
        settingContainer: "#PrintElementOptionSetting",
        paginationContainer: ".hiprint-printPagination",
      });
      hiprintTemplate.design("#hiprint-printTemplate");
      console.log(hiprintTemplate);
      // 获取当前放大比例, 当zoom时传true 才会有
      this.scaleValue = hiprintTemplate.editingPanel.scale || 1;
    },
    /**
     * 设置纸张大小
     * @param type [A3, A4, A5, B3, B4, B5, other]
     * @param value {width,height} mm
     */
    setPaper(type, value) {
      try {
        if (Object.keys(this.paperTypes).includes(type)) {
          this.curPaper = {
            type: type,
            width: value.width,
            height: value.height,
            paperHeader: value.paperHeader,
            paperFooter: value.paperFooter,
          };
          hiprintTemplate.setPaper(value.width, value.height);
          this.setPageHeaderFooter();
        } else {
          this.curPaper = {
            type: "other",
            width: value.width,
            height: value.height,
            paperHeader: value.paperHeader,
            paperFooter: value.paperFooter,
          };
          hiprintTemplate.setPaper(value.width, value.height);
          this.setPageHeaderFooter();
        }
      } catch (error) {
        this.$message.error(`操作失败: ${error}`);
      }
    },
    setPageHeaderFooter() {
      let templateJson = hiprintTemplate.getJson();
      const { paperHeader, paperFooter } = this.curPaper;
      if (this.curPaper && paperHeader && paperFooter) {
        templateJson.panels[0].name = 0;
        // 设置页眉页脚
        templateJson.panels[0].paperHeader = paperHeader;
        templateJson.panels[0].paperFooter = paperFooter;
        hiprintTemplate.update(templateJson);
      }
    },
    changeScale(currentValue, oldValue) {
      let big = false;
      currentValue <= oldValue ? (big = false) : (big = true);
      let scaleValue = this.scaleValue;
      if (big) {
        scaleValue += 0.1;
        if (scaleValue > this.scaleMax) scaleValue = 5;
      } else {
        scaleValue -= 0.1;
        if (scaleValue < this.scaleMin) scaleValue = 0.5;
      }
      if (hiprintTemplate) {
        // scaleValue: 放大缩小值, false: 不保存(不传也一样), 如果传 true, 打印时也会放大
        hiprintTemplate.zoom(scaleValue);
        this.scaleValue = scaleValue;
      }
    },
    clearPaper() {
      this.$modal
        .confirm("是否确认清空模板信息?", "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        })
        .then(() => {
          try {
            hiprintTemplate.clear();
          } catch (error) {
            this.$message.error(`操作失败: ${error}`);
          }
        })
        .catch((err) => {
          console.log(err);
        });
    },
    otherPaper() {
      let value = {};
      value.width = this.paperWidth;
      value.height = this.paperHeight;
      this.paperPopVisible = false;
      this.setPaper("other", value);
    },
    rotatePaper() {
      if (hiprintTemplate) {
        hiprintTemplate.rotatePaper();
        // reverse paper width and height
        let { width, height } = this.curPaper;
        this.curPaper.width = height;
        this.curPaper.height = width;
      }
    },
    preView() {
      let { width } = this.curPaper;
      this.$refs.preView.show(hiprintTemplate, printData, width);
    },
    print() {
      if (window.hiwebSocket.opened) {
        const printerList = hiprintTemplate.getPrinterList();
        console.log(printerList);
        hiprintTemplate.print2(printData, {
          printer: "",
          title: "hiprint测试打印",
        });
        return;
      }
      this.$message.error("客户端未连接,无法直接打印");
    },
    updateJson() {
      if (hiprintTemplate && this.jsonIn) {
        try {
          hiprintTemplate.update(JSON.parse(this.jsonIn));
        } catch (e) {
          this.$message.error(`更新失败: ${e}`);
        }
      }
    },
    copy() {
      // 将对象转换为 JSON 字符串
      const jsonString = JSON.stringify(hiprintTemplate.getJson(), null, 2);

      // 使用 Clipboard API 复制文本到剪贴板
      navigator.clipboard
        .writeText(jsonString)
        .then(() => {
          this.$message({
            message: "模板 json 已复制到剪贴板",
            type: "success",
          });
        })
        .catch((err) => {
          console.error("无法复制JSON: ", err);
          this.$message.error("复制JSON失败");
        });
    },
    save() {
      // let { mode } = this;
      // let provider = providers[mode];
      // this.setTemplate({
      //   name: provider.value,
      //   json: hiprintTemplate.getJson(),
      // });
    },
    setTemplate(payload) {
      let templates = this.$ls.get("KEY_TEMPLATES", {});
      console.log(payload.json);
      templates[payload.name] = payload.json;
      this.$ls.set("KEY_TEMPLATES", templates);
      this.$message.info("保存成功");
    },
    /** 单位换算 mm 为 pt */
    mmToPt(value) {
      return value ? Math.round((value * 72) / 25.4, 2) : 0;
    },
  },
};
</script>

<style lang="scss" scoped>
// build 拖拽
::v-deep .hiprint-printElement-type > li > ul > li > a {
  padding: 4px;
  color: #1296db;
  line-height: 1;
  height: auto;
  text-overflow: ellipsis;
}

// 默认图片
::v-deep .hiprint-printElement-image-content img {
  content: url("~@/assets/logo/logo.png");
}

// 设计容器
.card-design {
  overflow: hidden;
  overflow-x: auto;
  overflow-y: auto;
}

// 修改 页眉/页脚线 样式
::v-deep .hiprint-headerLine,
::v-deep .hiprint-footerLine {
  border-color: red !important;
}

::v-deep .hiprint-headerLine:hover,
::v-deep .hiprint-footerLine:hover {
  border-top: 3px dashed red !important;
}

::v-deep .hiprint-headerLine:hover:before {
  content: "页眉线";
  left: calc(50% - 18px);
  position: relative;
  background: #ffff;
  top: -12px;
  color: red;
  font-size: 12px;
}

::v-deep .hiprint-footerLine:hover:before {
  content: "页脚线";
  left: calc(50% - 18px);
  position: relative;
  color: red;
  background: #ffff;
  top: -12px;
  font-size: 12px;
}
</style>
