<template>
  <Designer :template="template" @onDesigned="onDesigned" />
</template>

<script>
import { Designer } from "@sv-print/vue";

export default {
  components: { Designer },
  data() {
    return { template: {} };
  },
  methods: {
    onDesigned(e) {
      console.log("designer ", e);
      console.log("hiprint ", e.hiprint); // hiprint 模块
    },
  },
};
</script>
