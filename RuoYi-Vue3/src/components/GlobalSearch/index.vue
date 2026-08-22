<template>
  <el-dialog
    v-model="visible"
    :width="dialogWidth"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="global-search-dialog"
    append-to-body
    @opened="onOpened"
    @closed="onClosed"
  >
    <div class="gs-input">
      <el-input
        ref="searchInputRef"
        v-model="keyword"
        size="large"
        prefix-icon="Search"
        placeholder="搜索：客户 / 商品 / 订单（支持名称、编码、别名）"
        clearable
        @input="onInput"
        @keydown="onKeydown"
        @keyup.enter.prevent="selectActive"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
      />
    </div>

    <div class="gs-body">
      <el-scrollbar v-loading="loading" class="gs-scrollbar">
        <template v-if="hasResult">
          <div v-for="group in flatGroups" :key="group.type">
            <div class="gs-group-title">
              <span>{{ group.label }}</span>
              <span class="gs-group-count">{{ group.items.length }}</span>
            </div>
            <div
              v-for="(item, i) in group.items"
              :key="item.uid"
              class="gs-item"
              :class="{ 'is-active': uid === item.uid }"
              @mouseenter="uid = item.uid"
              @click="jump(item)"
            >
              <div class="gs-item-left">
                <span class="gs-type-tag" :class="'gs-tag-' + group.type">{{ group.short }}</span>
              </div>
              <div class="gs-item-main">
                <span class="gs-item-title" v-html="highlight(item.title)"></span>
                <span class="gs-item-sub" v-html="highlight(item.sub)"></span>
              </div>
              <svg-icon v-if="uid === item.uid" icon-class="enter" />
            </div>
          </div>
        </template>

        <div v-else-if="searched" class="gs-empty">
          <el-icon class="gs-empty-icon"><Search /></el-icon>
          <p class="gs-empty-text">未找到 "{{ lastKeyword }}" 相关结果</p>
          <p class="gs-empty-tip">试试名称、编码、别名、单号或客户</p>
        </div>

        <div v-else class="gs-empty gs-empty-hint">
          <el-icon class="gs-empty-icon"><Search /></el-icon>
          <p class="gs-empty-text">输入关键词开始全局搜索</p>
          <p class="gs-empty-tip">客户（名称+别名）｜商品（名称+编码+分类）｜订单（单号+客户+日期）</p>
        </div>
      </el-scrollbar>
    </div>

    <div class="gs-footer">
      <span class="gs-kb"><kbd>↑</kbd><kbd>↓</kbd> 切换</span>
      <span class="gs-kb"><kbd>↵</kbd> 新标签打开</span>
      <span class="gs-kb"><kbd>Esc</kbd> 关闭</span>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { globalSearch } from '@/api/search'

const visible = defineModel({ type: Boolean, default: false })
const keyword = ref('')
const lastKeyword = ref('')
const searched = ref(false)
const loading = ref(false)
const isComposing = ref(false)
const uid = ref(-1)
let seq = 0
const searchInputRef = ref(null)

/**
 * 扁平化导航结果。每组内先编号，跨组按顺序累加组成唯一 uid，
 * 用于键盘 ↑↓ 在全部结果间连续移动。
 * 结构：{ type, label, short, items:[{uid, type, id, title, sub, payload}] }
 */
const groups = ref([])
const flatGroups = computed(() => groups.value)

const hasResult = computed(() =>
  flatGroups.value.some((g) => g.items.length > 0)
)

const typeMeta = {
  customer: { label: '客户', short: '客' },
  product: { label: '商品', short: '商' },
  order: { label: '订单', short: '单' },
}

function buildGroups(data) {
  const groupsOut = []
  let uidCounter = 0
  const push = (type, list, map) => {
    const items = (list || []).map((raw) => {
      const m = map(raw)
      return { uid: uidCounter++, type, id: raw.id, ...m }
    })
    if (items.length) {
      groupsOut.push({ type, ...typeMeta[type], items })
    }
  }
  push('customer', data?.customers, (c) => ({
    title: c.name,
    sub: [c.alias && c.alias !== c.name ? '别名：' + c.alias : '', c.tel].filter(Boolean).join(' · '),
    payload: { target: 'customer', id: c.id },
  }))
  push('product', data?.products, (p) => ({
    title: p.name,
    sub: [p.code, p.categoryName, p.specName ? p.specName : ''].filter(Boolean).join(' · '),
    payload: { target: 'product', id: p.id },
  }))
  push('order', data?.orders, (o) => ({
    title: o.code,
    sub: [o.customerName, o.deliveryDate].filter(Boolean).join(' · '),
    payload: { target: 'order', id: o.id },
  }))
  return groupsOut
}

/** 当前激活项（按 uid） */
const currentItem = computed(() => {
  for (const g of flatGroups.value) {
    const found = g.items.find((it) => it.uid === uid.value)
    if (found) return found
  }
  return null
})

function onOpened() {
  nextTick(() => {
    searchInputRef.value && searchInputRef.value.focus()
  })
}

function onClosed() {
  keyword.value = ''
  lastKeyword.value = ''
  searched.value = false
  groups.value = []
  uid.value = -1
}

async function onInput() {
  const kw = keyword.value.trim()
  if (!kw) {
    searched.value = false
    loading.value = false
    groups.value = []
    uid.value = -1
    return
  }
  loading.value = true
  try {
    const res = await globalSearch(kw, 10)
    groups.value = buildGroups(res.data)
    searched.value = true
    lastKeyword.value = kw
    // 自动聚焦首个结果
    const first = flatGroups.value[0]?.items[0]
    uid.value = first ? first.uid : -1
  } catch (e) {
    groups.value = []
    searched.value = true
  } finally {
    loading.value = false
  }
}

function onKeydown(e) {
  const key = e.key
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    return
  }
  if (isComposing.value) return
  if (key === 'ArrowDown') {
    e.preventDefault()
    navigate(1)
  } else if (key === 'ArrowUp') {
    e.preventDefault()
    navigate(-1)
  } else if (key === 'Enter') {
    // Enter 在 @keyup.enter 统一处理，避免 composition 误触
  }
}

function navigate(dir) {
  const all = flatGroups.value.flatMap((g) => g.items)
  if (!all.length) return
  let idx = all.findIndex((it) => it.uid === uid.value)
  if (idx === -1) idx = dir > 0 ? -1 : 0
  idx = (idx + dir + all.length) % all.length
  uid.value = all[idx].uid
}

function selectActive() {
  // 输入法组合中回车不触发跳转
  if (isComposing.value) return
  const item = currentItem.value
  if (item) jump(item)
}

function jump(item) {
  if (!item || !item.payload) return
  // 跳转前先保存录单草稿（若有），避免丢失
  flushDraft()
  const { target, id } = item.payload
  let url = ''
  if (target === 'customer') {
    url = `/basicInfo/customer-dept/index/${id}`
  } else if (target === 'product') {
    url = `/basicInfo/sku?name=${encodeURIComponent(item.title)}`
  } else if (target === 'order') {
    url = `/order/sale-detail/index?orderId=${id}`
  }
  if (!url) return
  window.open(url, '_blank')
  close()
}

/** 通知录单页立即落盘草稿（Phase 1 saleDraft 联动） */
function flushDraft() {
  window.dispatchEvent(new CustomEvent('sale-draft-flush'))
}

function close() {
  visible.value = false
}

function highlight(text) {
  const raw = String(text == null ? '' : text)
  const kw = keyword.value.trim()
  if (!kw) return escapeHtml(raw)
  const escaped = escapeHtml(raw)
  const kl = kw.toLowerCase()
  const lower = raw.toLowerCase()
  let out = ''
  let idx = 0
  let pos = lower.indexOf(kl)
  while (pos !== -1) {
    out += escaped.slice(idx, pos)
    out += '<span class="gs-hl">' + escaped.slice(pos, pos + kw.length) + '</span>'
    idx = pos + kw.length
    pos = lower.indexOf(kl, idx)
  }
  out += escaped.slice(idx)
  return out
}

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function open() {
  visible.value = true
  if (visible.value) {
    nextTick(() => {
      searchInputRef.value && searchInputRef.value.focus()
    })
  }
}

/** 全局 Ctrl+K 唤起（任一面板），过滤输入法组合态；打开时 Esc 关闭 */
function onGlobalKeydown(e) {
  if (e.key === 'Escape' && visible.value) {
    // 关闭逻辑统一走 el-dialog 的关闭，避免与输入项冲突
    e.preventDefault()
    close()
    return
  }
  if (e.ctrlKey || e.metaKey) {
    const key = String(e.key || '').toLowerCase()
    if (key === 'k') {
      e.preventDefault()
      open()
      return
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

defineExpose({ open })
</script>

<style lang="scss" scoped>
.global-search-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
  }
  :deep(.el-dialog__body) {
    padding: 0;
  }
}

.gs-input {
  padding: 16px 16px 8px;
}

.gs-body {
  height: 360px;
  margin: 0 8px;
}

.gs-scrollbar {
  height: 100%;
}

.gs-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 4px;
  color: #909399;
  font-size: 12px;
  letter-spacing: 1px;

  .gs-group-count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 9px;
    background: #f0f2f5;
    color: #606266;
    font-size: 11px;
  }
}

.gs-item {
  display: flex;
  align-items: center;
  height: 46px;
  padding: 0 14px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s;

  &:hover {
    background: #f5f7fa;
  }

  &.is-active {
    background: var(--current-color, #409eff);
    color: #fff;

    .gs-item-sub {
      color: rgba(255, 255, 255, 0.85);
    }

    .gs-hl {
      color: #fff;
    }

    .gs-type-tag {
      background: rgba(255, 255, 255, 0.22);
      color: #fff;
    }
  }
}

.gs-item-left {
  width: 42px;
  flex-shrink: 0;
  text-align: center;
}

.gs-type-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  border-radius: 5px;
  font-size: 12px;
  background: #ecf5ff;
  color: #409eff;
}

.gs-tag-product {
  background: #f0f9eb;
  color: #67c23a;
}

.gs-tag-order {
  background: #fdf6ec;
  color: #e6a23c;
}

.gs-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  line-height: 1.4;
}

.gs-item-title {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gs-item-sub {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gs-hl {
  color: #f56c6c;
  font-weight: 600;
}

.gs-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;

  .gs-empty-icon {
    font-size: 42px;
    color: #e0e0e0;
    margin-bottom: 14px;
  }

  .gs-empty-text {
    margin: 0 0 6px;
    font-size: 14px;
    color: #909399;
  }

  .gs-empty-tip {
    margin: 0;
    font-size: 12px;
    color: #c0c4cc;
  }
}

.gs-empty-hint {
  .gs-empty-tip {
    max-width: 420px;
    text-align: center;
  }
}

.gs-footer {
  display: flex;
  align-items: center;
  gap: 26px;
  padding: 10px 20px;
  border-top: 1px solid #f0f0f0;
  color: #909399;
  font-size: 12px;

  kbd {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 20px;
    height: 20px;
    padding: 0 5px;
    border: 1px solid #ddd;
    border-radius: 4px;
    background: #f7f7f7;
    color: #555;
    font-size: 11px;
    line-height: 1;
    box-shadow: 0 1px 0 #ccc;
  }
}
</style>
