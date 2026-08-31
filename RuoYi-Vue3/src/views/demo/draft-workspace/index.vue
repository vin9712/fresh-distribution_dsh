<template>
  <div class="demo-draft-workspace">
    <el-card shadow="never">
      <div class="tips">
        W0-5.3 草稿工具治理演示：用户命名空间（key 含
        <code>u{userId}</code>）、版本号（ver/rev）、容量治理（单条 256KB / 每用户 20 条）、
        7 天过期惰性清扫、结构恢复校验。当前用户命名空间：<code>{{ prefix }}</code>
      </div>
    </el-card>

    <el-card shadow="never" class="mt12">
      <template #header>模拟草稿操作（对 saleDraft 工具真实读写）</template>
      <el-form inline>
        <el-form-item label="模拟配送点 ID">
          <el-input-number v-model="deptId" :min="1" />
        </el-form-item>
        <el-form-item label="明细行数">
          <el-input-number v-model="rows" :min="1" :max="50" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSave">保存草稿</el-button>
          <el-button @click="onSave(3000)">保存超大草稿(3MB)</el-button>
          <el-button @click="fillToLimit">灌满至 20 条上限</el-button>
          <el-button type="danger" @click="onRemoveAll">删除全部</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="drafts" size="small" border>
        <el-table-column prop="key" label="key" min-width="240" show-overflow-tooltip />
        <el-table-column prop="rev" label="rev" width="60" />
        <el-table-column prop="ver" label="ver" width="60" />
        <el-table-column prop="savedAt" label="保存时间" width="180" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="onRestore(row.key)">恢复(消费)</el-button>
            <el-button size="small" type="danger" @click="onRemove(row.key)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="DemoDraftWorkspace">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listDrafts,
  saveDraft,
  removeDraft,
  restoreDraft,
  validateDraft
} from '@/utils/saleDraft'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const prefix = ref('')
const deptId = ref(9001)
const rows = ref(2)
const drafts = ref([])

function refresh() {
  drafts.value = listDrafts()
}

onMounted(() => {
  prefix.value = `saleDraft:u${userStore.id || 'anon'}:*`
  refresh()
})

function onSave(sizeRows) {
  const n = sizeRows || rows.value
  const draft = saveDraft({
    orderId: null,
    customerId: 1,
    customerDeptId: deptId.value,
    deptName: `演示配送点${deptId.value}`,
    deliveryDate: '2026-09-10',
    remark: n > 100 ? '超大草稿' : '演示草稿',
    details: Array.from({ length: n }, (_, i) => ({
      skuId: i + 1,
      productName: `商品${i + 1}`,
      num: i + 1,
      price: 1.5
    }))
  })
  if (!draft) {
    ElMessage.warning('草稿超出本地容量上限（仅后端双写），已被本地拒绝')
  }
  refresh()
}

/** 连续用不同 deptId 保存，把用户草稿压到上限，验证最旧淘汰 */
function fillToLimit() {
  for (let i = 1; i <= 22; i++) {
    saveDraft({
      customerDeptId: deptId.value * 1000 + i,
      deptName: `压测${i}`,
      details: [{ skuId: 1, productName: '商品', num: 1, price: 1 }]
    })
  }
  refresh()
  ElMessage.success(`已写入 22 条，当前 ${drafts.value.length} 条（上限 20，最旧淘汰）`)
}

function onRestore(key) {
  const draft = restoreDraft(key)
  if (!draft) {
    ElMessage.error('草稿不存在/过期/校验失败')
  } else {
    const v = validateDraft(draft)
    // 消费式恢复：与 detail.vue restoreDraftIntoForm 一致，恢复后删除草稿
    removeDraft(key)
    ElMessage.success(`恢复校验通过（rev=${draft.rev}, ver=${draft.ver}, reason=${v.reason}），草稿已消费删除`)
  }
  refresh()
}

function onRemove(key) {
  removeDraft(key)
  refresh()
}

function onRemoveAll() {
  drafts.value.forEach((d) => removeDraft(d.key))
  refresh()
}
</script>

<style scoped>
.tips {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.mt12 {
  margin-top: 12px;
}
</style>
