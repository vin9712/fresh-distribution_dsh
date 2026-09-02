<template>
  <el-drawer
    :title="'配送后变更 - ' + (order.code || '')"
    v-model="visible"
    size="560px"
    append-to-body
    :destroy-on-close="true"
  >
    <div class="change-page">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="change-alert"
        title="已打印（配送后）：原订单数据不变，变更以标记附加在订单明细上（加单/换货/退货），验收按订单明细对比"
      />
      <el-form label-width="96px" size="small">
        <el-form-item label="变更类型">
          <el-radio-group v-model="changeType">
            <el-radio :value="1">加单</el-radio>
            <el-radio :value="2">换货</el-radio>
            <el-radio :value="3">退货</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 换货/退货：选原明细行 -->
        <el-form-item v-if="changeType === 2 || changeType === 3" label="原明细行" prop="targetDetailId">
          <el-select v-model="targetDetailId" placeholder="选择被换/被退的明细行" filterable style="width: 100%">
            <el-option
              v-for="d in detailOptions"
              :key="d.id"
              :label="(d.productName || '') + (d.productSpec ? ' / ' + d.productSpec : '') + '（' + d.num + (d.productUnit || '') + '）'"
              :value="d.id"
            />
          </el-select>
        </el-form-item>

        <!-- 加单/换货：新商品信息 -->
        <template v-if="changeType === 1 || changeType === 2">
          <el-form-item label="商品名" prop="productName">
            <el-input v-model="form.productName" placeholder="品名（新商品选标品或输入临时名）" />
          </el-form-item>
          <el-form-item label="规格">
            <el-input v-model="form.spec" placeholder="规格（可空）" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="form.unit" placeholder="单位（如 斤/份）" />
          </el-form-item>
          <el-form-item label="应收" prop="num">
            <el-input-number v-model="form.num" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="实收">
            <el-input-number v-model="form.actualNum" :min="0" :precision="2" style="width: 100%" />
            <div class="form-tip">实收默认=应收；现场实际收到的数量（与应收一致可留空）</div>
          </el-form-item>
        </template>

        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如：换货 土豆→番茄 / 客户要求加 3 斤" />
        </el-form-item>
      </el-form>

      <div class="change-actions">
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认变更</el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { deliverySupplement, deliveryExchange, deliveryReturn, getSaleOrder } from "@/api/order/sale";

export default {
  name: "SaleChangeDrawer",
  props: {
    modelValue: { type: Boolean, default: false },
    order: { type: Object, default: () => ({}) },
  },
  emits: ["update:modelValue", "done"],
  data() {
    return {
      changeType: 1,
      targetDetailId: null,
      details: [],
      form: { productName: null, spec: null, unit: null, num: null, actualNum: null, remark: null },
      submitting: false,
    };
  },
  computed: {
    visible: {
      get() {
        return this.modelValue;
      },
      set(v) {
        this.$emit("update:modelValue", v);
      },
    },
    detailOptions() {
      return (this.details || []).filter((d) => d.changeType !== 3 && d.id);
    },
  },
  watch: {
    visible(v) {
      if (v && this.order.id) {
        this.loadDetails();
      }
    },
  },
  methods: {
    loadDetails() {
      getSaleOrder(this.order.id).then((resp) => {
        const data = resp.data || {};
        this.details = data.orderDetails || data.details || [];
      });
    },
    handleSubmit() {
      const orderId = this.order.id;
      if (this.changeType === 1) {
        if (!this.form.productName || !this.form.num) {
          this.$modal.msgWarning("请填写商品名与应收数量");
          return;
        }
        this.submitting = true;
        deliverySupplement(orderId, { ...this.form }).then(() => {
          this.$modal.msgSuccess("加单成功（标记已附加）");
          this.visible = false;
          this.$emit("done");
        }).finally(() => (this.submitting = false));
      } else if (this.changeType === 2) {
        if (!this.targetDetailId || !this.form.productName) {
          this.$modal.msgWarning("请选择被换明细行并填写换入商品");
          return;
        }
        this.submitting = true;
        deliveryExchange(orderId, {
          targetDetailId: this.targetDetailId,
          productName: this.form.productName,
          spec: this.form.spec,
          unit: this.form.unit,
          num: this.form.num || 0,
          actualNum: this.form.actualNum,
          remark: this.form.remark,
        }).then(() => {
          this.$modal.msgSuccess("换货成功（被换行标退货·换入行标换货，同组）");
          this.visible = false;
          this.$emit("done");
        }).finally(() => (this.submitting = false));
      } else {
        if (!this.targetDetailId) {
          this.$modal.msgWarning("请选择被退明细行");
          return;
        }
        this.submitting = true;
        deliveryReturn(orderId, { targetDetailId: this.targetDetailId, remark: this.form.remark }).then(() => {
          this.$modal.msgSuccess("退货成功（原行已标记，应送实收归0）");
          this.visible = false;
          this.$emit("done");
        }).finally(() => (this.submitting = false));
      }
    },
  },
};
</script>

<style scoped>
.change-page {
  padding: 0 4px;
}
.change-alert {
  margin-bottom: 12px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.change-actions {
  margin-top: 16px;
  text-align: right;
}
</style>
