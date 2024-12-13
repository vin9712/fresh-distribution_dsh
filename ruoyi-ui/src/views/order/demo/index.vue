<template>
  <div>
    <button @click="print">打印自定义模板</button>
    <div ref="printArea" class="custom-template">
      <h1>{{ title }}</h1>
      <table>
        <tr>
          <th>名称</th>
          <th>数量</th>
          <th>价格</th>
        </tr>
        <tr v-for="item in items" :key="item.name">
          <td>{{ item.name }}</td>
          <td>{{ item.quantity }}</td>
          <td>{{ item.price }}</td>
        </tr>
      </table>
    </div>
  </div>
</template>
<script>
export default {
  data() {
    return {
      title: "自定义模板示例", // 堆代码 duidaima.com
      items: [
        { name: "商品A", quantity: 1, price: "100元" },
        { name: "商品B", quantity: 2, price: "200元" },
      ],
    };
  },
  methods: {
    print() {
      this.$hiprint.print(this.$refs.printArea);
    },
  },
};
</script>
<style>
.custom-template {
  font-family: Arial, sans-serif;
  text-align: center;
}
.custom-template table {
  width: 100%;
  border-collapse: collapse;
}
.custom-template th,
.custom-template td {
  border: 1px solid #ddd;
  padding: 8px;
}
.custom-template th {
  background-color: #f2f2f2;
}
</style>
