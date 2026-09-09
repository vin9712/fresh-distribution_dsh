<template>
  <div
    ref="containerRef"
    class="split-workspace"
    :class="{ 'split-workspace--dragging': isDragging }"
  >
    <div
      class="split-workspace__pane split-workspace__pane--left"
      :style="leftPaneStyle"
    >
      <slot name="left"></slot>
    </div>

    <div
      v-if="!rightHidden"
      class="split-workspace__handle"
      role="separator"
      :aria-orientation="'vertical'"
      :aria-valuenow="Math.round(leftRatio * 100)"
      :aria-valuemin="0"
      :aria-valuemax="100"
      :aria-label="'拖拽调整左右分栏宽度，双击恢复默认'"
      tabindex="0"
      :title="'拖拽调整宽度；双击恢复默认布局'"
      @pointerdown="onDragStart"
      @dblclick="resetLayout"
      @keydown="onHandleKeydown"
    ></div>

    <div
      v-if="!rightHidden"
      class="split-workspace__pane split-workspace__pane--right"
    >
      <slot name="right"></slot>
    </div>
  </div>
</template>

<script setup>
/**
 * SplitWorkspace 通用分屏工作区组件
 *
 * - 左右双面板，中缝可拖拽调整宽度（鼠标/触摸/键盘方向键）
 * - 最小宽度受控（按像素钳制，适配任意窗口宽度）
 * - 布局按「登录用户 + storageKey」命名空间持久化到本机 localStorage（蓝图 §2 共享设备隔离）
 * - 双击中缝或调用 resetLayout() 一键恢复默认布局
 *
 * 持久化格式（v1）：{ v: 1, r: <左侧占比 0~1> }
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import useUserStore from '@/store/modules/user'

const props = defineProps({
  /** 业务唯一键：同一页面传入固定值，存储自动叠加用户命名空间 */
  storageKey: {
    type: String,
    required: true
  },
  /** 默认左侧占比 0~1 */
  defaultRatio: {
    type: Number,
    default: 0.66
  },
  /** 左侧最小宽度(px)，拖拽与恢复时钳制 */
  leftMinWidth: {
    type: Number,
    default: 320
  },
  /** 右侧最小宽度(px) */
  rightMinWidth: {
    type: Number,
    default: 260
  },
  /** 左侧最大占比 0~1（0.95 可保证中缝始终可见） */
  maxRatio: {
    type: Number,
    default: 0.95
  },
  /** 隐藏右侧面板（OA 验收模式：单栏全宽，选单区不渲染） */
  rightHidden: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['change', 'drag-end'])

const userStore = useUserStore()
const containerRef = ref(null)
const leftRatio = ref(clampRatio(props.defaultRatio))
const containerWidth = ref(0)
const isDragging = ref(false)

let storageKeyResolved = null
let resizeObserver = null

// ---------------- 持久化（用户命名空间） ----------------

function getStorageKey() {
  if (!storageKeyResolved) {
    const uid = userStore.id || userStore.name || 'anon'
    storageKeyResolved = `splitws:u${uid}:${props.storageKey}`
  }
  return storageKeyResolved
}

function loadRatio() {
  try {
    const raw = localStorage.getItem(getStorageKey())
    if (!raw) return false
    const saved = JSON.parse(raw)
    if (saved && saved.v === 1 && typeof saved.r === 'number' && saved.r > 0 && saved.r < 1) {
      leftRatio.value = clampRatio(saved.r)
      emit('change', leftRatio.value)
      return true
    }
  } catch (e) {
    console.warn('[SplitWorkspace] 布局恢复失败，使用默认布局', e)
  }
  return false
}

function saveRatio() {
  try {
    localStorage.setItem(
      getStorageKey(),
      JSON.stringify({ v: 1, r: Number(leftRatio.value.toFixed(4)) })
    )
  } catch (e) {
    console.warn('[SplitWorkspace] 布局保存失败', e)
  }
}

function clampRatio(ratio) {
  return Math.min(props.maxRatio, Math.max(1 - props.maxRatio, ratio))
}

/** 按当前容器宽度与左右最小宽度钳制占比 */
function clampRatioByWidth(ratio) {
  const w = containerWidth.value
  if (!w) return clampRatio(ratio)
  const minLeft = props.leftMinWidth / w
  const minRight = props.rightMinWidth / w
  const upper = Math.min(props.maxRatio, 1 - minRight)
  const lower = Math.max(1 - props.maxRatio, minLeft)
  if (lower > upper) {
    // 窗口过窄放不下两侧最小宽度时，保左舍右
    return Math.min(upper, Math.max(0.05, lower))
  }
  return Math.min(upper, Math.max(lower, ratio))
}

function applyRatio(ratio, { persist = false } = {}) {
  const clamped = clampRatioByWidth(ratio)
  if (Math.abs(clamped - leftRatio.value) >= 0.0005) {
    leftRatio.value = clamped
    emit('change', clamped)
  }
  if (persist) saveRatio()
}

// ---------------- 拖拽 ----------------

function onDragStart(e) {
  e.preventDefault()
  isDragging.value = true
  document.addEventListener('pointermove', onDragMove)
  document.addEventListener('pointerup', onDragEnd)
}

function onDragMove(e) {
  const rect = containerRef.value?.getBoundingClientRect()
  if (!rect || rect.width === 0) return
  applyRatio((e.clientX - rect.left) / rect.width)
}

function onDragEnd() {
  document.removeEventListener('pointermove', onDragMove)
  document.removeEventListener('pointerup', onDragEnd)
  isDragging.value = false
  saveRatio()
  emit('drag-end', leftRatio.value)
}

/** 键盘微调：左右方向键 ±2%，配合纯键盘优先的录单文化 */
function onHandleKeydown(e) {
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    applyRatio(leftRatio.value - 0.02, { persist: true })
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    applyRatio(leftRatio.value + 0.02, { persist: true })
  }
}

// ---------------- 对外 API ----------------

/** 恢复默认布局（清除本机记忆并回默认占比） */
function resetLayout() {
  try {
    localStorage.removeItem(getStorageKey())
  } catch (e) {
    console.warn('[SplitWorkspace] 清除布局存储失败', e)
  }
  leftRatio.value = clampRatio(props.defaultRatio)
  emit('change', leftRatio.value)
}

defineExpose({ resetLayout })

// ---------------- 样式与生命周期 ----------------

const leftPaneStyle = computed(() => {
  if (props.rightHidden) return { width: '100%' }
  const collapsed = containerWidth.value > 0
  return collapsed
    ? { width: `${(leftRatio.value * 100).toFixed(3)}%` }
    : { width: `${(props.defaultRatio * 100).toFixed(3)}%` }
})

onMounted(async () => {
  await nextTick()
  loadRatio()
  if (containerRef.value) {
    containerWidth.value = containerRef.value.getBoundingClientRect().width
    resizeObserver = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect?.width || 0
      containerWidth.value = w
      if (w > 0) {
        // 窗口变化后按最小宽度重新钳制，但不覆盖用户偏好
        applyRatio(leftRatio.value)
      }
    })
    resizeObserver.observe(containerRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  document.removeEventListener('pointermove', onDragMove)
  document.removeEventListener('pointerup', onDragEnd)
})
</script>

<style lang="scss" scoped>
.split-workspace {
  display: flex;
  align-items: stretch;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;

  &__pane {
    min-width: 0;
    min-height: 0;
    overflow: hidden;

    &--left {
      flex: 0 0 auto;
    }

    &--right {
      flex: 1 1 auto;
    }
  }

  &__handle {
    flex: 0 0 6px;
    cursor: col-resize;
    background-color: transparent;
    border-left: 1px solid var(--el-border-color-light, #e4e7ed);
    transition: background-color 0.2s;
    outline: none;
    touch-action: none;
    z-index: 10;

    &:hover,
    &:focus-visible,
    &:active {
      background-color: var(--el-color-primary-light-5, #a0cfff);
      border-left-color: var(--el-color-primary-light-5, #a0cfff);
    }

    &:focus-visible {
      box-shadow: inset 0 0 0 1px var(--el-color-primary, #409eff);
    }
  }

  // 拖拽中：禁用文本选中，避免 iframe/表格抢夺指针事件
  &--dragging {
    user-select: none;
    cursor: col-resize;

    .split-workspace__pane {
      pointer-events: none;
    }
  }
}
</style>
