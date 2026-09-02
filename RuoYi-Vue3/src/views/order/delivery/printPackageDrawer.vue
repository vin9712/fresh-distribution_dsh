<template>
  <el-drawer
    :title="'打印包 - ' + (batch.customerName || '') + ' / ' + batch.deliveryDate"
    v-model="visible"
    size="720px"
    append-to-body
    :destroy-on-close="true"
  >
    <div class="pkg-page">
      <!-- 操作条 -->
      <div class="pkg-toolbar">
        <el-button type="primary" size="small" :icon="Plus" :loading="creating" @click="handleCreate"
          >建包（建议先预览后再打印）</el-button
        >
        <el-button size="small" :icon="Refresh" @click="loadPackage">刷新</el-button>
        <span v-if="pkg" class="pkg-status">
          包 {{ pkg.packageNo }} · 共 {{ pkg.totalCount }} 张 · 成功 {{ pkg.successCount }} · 失败
          {{ pkg.failCount }} · <span>{{ statusText }}</span>
        </span>
      </div>

      <el-alert
        v-if="matchGlobalWarning"
        type="warning"
        :closable="false"
        show-icon
        class="pkg-alert"
        title="该批次存在未配客户级模板的单，将使用全局默认版式，请逐张核对"
      />

      <!-- 包内任务清单 -->
      <el-table v-loading="loading" :data="tasks" size="small" border>
        <el-table-column label="序号" prop="seqNo" width="50" align="center" />
        <el-table-column label="送货单号" prop="deliveryCode" min-width="130" :show-overflow-tooltip="true" />
        <el-table-column label="配送点" prop="customerDeptName" min-width="100" :show-overflow-tooltip="true">
          <template #default="scope">
            <span v-if="scope.row.customerDeptName">{{ scope.row.customerDeptName }}</span>
            <el-tag v-else size="small" type="info" effect="plain">跨点总单</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模板" min-width="130" :show-overflow-tooltip="true">
          <template #default="scope">
            <el-select
              v-model="scope.row._templateId"
              size="small"
              placeholder="模板"
              style="width: 120px"
              :disabled="scope.row.status === 3 || scope.row.status === 4"
              @change="(v) => handleTaskChange(scope.row, { templateId: v })"
            >
              <el-option v-for="t in templateOptions" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="份数" width="90" align="center">
          <template #default="scope">
            <el-input-number
              v-model="scope.row._copies"
              size="small"
              :min="1"
              :max="10"
              :disabled="scope.row.status === 3 || scope.row.status === 4"
              style="width: 70px"
              @change="(v) => handleTaskChange(scope.row, { copies: v })"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag size="small" :type="taskStatusType(scope.row.status)">
              {{ taskStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="失败原因" min-width="110" :show-overflow-tooltip="true" />
      </el-table>

      <!-- 打印操作 -->
      <div v-if="pkg && pkg.status < 2" class="pkg-actions">
        <el-button type="primary" size="small" :icon="View" :loading="previewing" @click="handlePreview"
          >① 汇总预览</el-button
        >
        <el-button type="success" size="small" :icon="Printer" :loading="printing" :disabled="!previewed" @click="handlePrintQueue"
          >② 开始打印（队列连续输出）</el-button
        >
        <span class="pkg-hint">流程：汇总预览确认版式 → 开始打印（逐个确认成功/失败，失败即停并保留未执行）</span>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import {
  createPrintPackage,
  getPrintPackage,
  listPrintPackage,
  previewPrintPackage,
  startPrintPackage,
  receiptPrintTask,
  updatePrintTask,
} from "@/api/order/delivery";
import { issuePrintTicket } from "@/api/print/ticket";
import { recordPrintPreview } from "@/api/print/template";
import { Plus, Refresh, View, Printer } from "@element-plus/icons-vue";

export default {
  name: "PrintPackageDrawer",
  props: {
    modelValue: { type: Boolean, default: false },
    batch: { type: Object, default: () => ({}) },
  },
  emits: ["update:modelValue", "done"],
  setup() {
    return { Plus, Refresh, View, Printer };
  },
  data() {
    return {
      loading: false,
      creating: false,
      previewing: false,
      printing: false,
      pkg: null,
      tasks: [],
      templateOptions: [],
      matchGlobalWarning: false,
      previewed: false,
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
    statusText() {
      if (!this.pkg) return "";
      return { 0: "待打印", 1: "打印中", 2: "已完成", 3: "已取消" }[this.pkg.status] || "";
    },
  },
  watch: {
    visible(v) {
      if (v && this.batch.customerId && this.batch.deliveryDate) {
        this.loadPackage();
      }
    },
  },
  methods: {
    /** 建包：客户+日期（后端已有未完成包则复用） */
    handleCreate() {
      this.creating = true;
      createPrintPackage({ customerId: this.batch.customerId, deliveryDate: this.batch.deliveryDate })
        .then((resp) => {
          this.$modal.msgSuccess("打印包已建立（包号 " + (resp.data.packageNo || "") + "）");
          this.loadPackage(resp.data.id);
        })
        .finally(() => (this.creating = false));
    },
    loadPackage(packageId) {
      this.loading = true;
      const load = (pid) => {
        if (!pid) {
          this.loading = false;
          return;
        }
        getPrintPackage(pid).then((resp) => {
          this.pkg = resp.data || {};
          this.tasks = (this.pkg.tasks || []).map((t) => ({
            ...t,
            _templateId: t.templateId,
            _copies: t.copies || 1,
          }));
          this.templateOptions = (this.pkg.tasks || [])
            .filter((t) => t.templateId)
            .map((t) => ({ id: t.templateId, name: t.templateName || "模板" + t.templateId }))
            .filter((t, i, arr) => arr.findIndex((x) => x.id === t.id) === i);
          this.previewed = (this.pkg.tasks || []).some((t) => t.status >= 1);
          this.matchGlobalWarning = false;
          this.loading = false;
        });
      };
      // 指定包ID直接查；否则查批次下最新未完成包
      if (packageId) {
        load(packageId);
        return;
      }
      listPrintPackage({ customerId: this.batch.customerId, deliveryDate: this.batch.deliveryDate })
        .then((resp) => {
          const list = resp.data || [];
          const active = list.find((p) => p.status < 2) || list[0];
          load(active ? active.id : null);
        })
        .catch(() => {
          this.loading = false;
        });
    },
    /** 汇总预览：标记包内全部待打任务为已预览（留痕） */
    handlePreview() {
      this.previewing = true;
      previewPrintPackage(this.pkg.id)
        .then(() => {
          // 逐个打开预览（逐单票据），用户核对版式
          const pendingTasks = this.tasks.filter((t) => t.status === 0 || t.status === 1);
          pendingTasks.forEach((t) => {
            if (t.templateId) {
              issuePrintTicket({ deliveryOrderId: t.deliveryOrderId, templateId: t.templateId }).then((res) => {
                window.open(
                  "/jmreport/view/" + t.templateId + "?token=" + res.ticket + "&deliveryOrderId=" + t.deliveryOrderId,
                  "_blank"
                );
                recordPrintPreview(t.templateId, t.deliveryOrderId).catch(() => {});
              });
            }
          });
          this.$modal.msgSuccess("已标记汇总预览并打开预览窗口");
          this.loadPackage(this.pkg.id);
        })
        .finally(() => (this.previewing = false));
    },
    /** 开始打印：前端队列逐张（票据 → window.open → 用户确认回执；失败即停） */
    async handlePrintQueue() {
      this.printing = true;
      try {
        await startPrintPackage(this.pkg.id);
        const queue = this.tasks.filter((t) => t.status === 0 || t.status === 1);
        for (const task of queue) {
          if (!task._templateId) {
            this.$modal.msgWarning("任务 " + task.deliveryCode + " 未选模板，跳过（请在队列里补选）");
            continue;
          }
          // 打开打印窗口
          const res = await issuePrintTicket({ deliveryOrderId: task.deliveryOrderId, templateId: task._templateId });
          window.open(
            "/jmreport/view/" + task._templateId + "?token=" + res.ticket + "&deliveryOrderId=" + task.deliveryOrderId,
            "_blank"
          );
          // 用户确认成功/失败（失败即停队）
          let ok = true;
          try {
            await this.$modal.confirm(
              "请在打印窗口完成《" + task.deliveryCode + "》打印。\n是否成功？\n· 成功：记录次数并推进状态\n· 失败：不计数，停止队列（可修后重试）"
            );
          } catch (e) {
            ok = false;
          }
          await receiptPrintTask(task.id, { success: ok, errorMsg: ok ? null : "用户确认打印失败" });
          if (!ok) {
            this.$modal.msgWarning("《" + task.deliveryCode + "》打印失败，队列已停止（后续 " + (queue.length - queue.indexOf(task) - 1) + " 张未执行）");
            break;
          }
        }
        this.$modal.msgSuccess("打印队列执行完成");
        this.loadPackage(this.pkg.id);
        this.$emit("done");
      } catch (e) {
        // 保留错误详情
      } finally {
        this.printing = false;
      }
    },
    /** 逐张改模板/份数（仅本次生效） */
    handleTaskChange(row, patch) {
      updatePrintTask(row.id, patch).then(() => this.loadPackage(this.pkg.id));
    },
    taskStatusText(s) {
      return { 0: "待打", 1: "已预览", 2: "打印中", 3: "成功", 4: "失败", 5: "取消", 6: "跳过" }[s] || "-";
    },
    taskStatusType(s) {
      return { 3: "success", 4: "danger", 5: "info", 6: "warning" }[s] || "primary";
    },
  },
};
</script>

<style scoped>
.pkg-page {
  padding: 0 4px;
}
.pkg-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.pkg-status {
  font-size: 12px;
  color: #606266;
}
.pkg-alert {
  margin-bottom: 8px;
}
.pkg-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.pkg-hint {
  font-size: 12px;
  color: #909399;
}
</style>
