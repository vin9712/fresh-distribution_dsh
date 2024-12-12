<template>
  <Designer
    :template="template"
    :printData="printData"
    @onDesigned="onDesigned"
  >
    <!-- 自定义 header -->
    <div class="header" slot="header">
      <div>header slot test1</div>
    </div>
    <!-- 自定义 左侧拖拽 元素 -->
    <div slot="draggableEls">
      <!-- 注意这里 tid="defaultModule.text" 如果是自定义 provider 的内容, 也需要先传对应的 provider -->
      <div class="ep-draggable-item" tid="defaultModule.text">
        <i class="iconfont sv-text" />
        <p>-文本-</p>
      </div>
    </div>
  </Designer>
</template>

<script>
import { Designer } from "@sv-print/vue";

export default {
  components: { Designer },
  data() {
    return { template: {}, printData: { name: "abcde" } };
  },
  methods: {
    onDesigned(e) {
      // 这里会 回调 2 个 关键对象
      console.log(e);
      console.log(e.hiprint); // hiprint 模块
      // 更多 API 可查看 log
      console.log(e.designerUtils);
    },
  },
};
</script>
