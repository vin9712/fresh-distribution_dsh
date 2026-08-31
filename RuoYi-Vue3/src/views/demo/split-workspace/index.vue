<template>
  <div class="demo-split-workspace">
    <el-card shadow="never" class="demo-toolbar">
      <div class="demo-toolbar__row">
        <span class="demo-toolbar__title">SplitWorkspace 分屏工作区演示</span>
        <el-tag>左侧占比：{{ (ratio * 100).toFixed(1) }}%</el-tag>
        <el-button size="small" @click="onReset">恢复默认布局</el-button>
        <el-button size="small" @click="toggleRandom">模拟切换右侧面板</el-button>
      </div>
      <div class="demo-toolbar__tips">
        拖拽中缝调整宽度（最小宽度受控）；双击中缝或按
        <kbd>←</kbd>/<kbd>→</kbd> 微调；布局按「当前登录用户 + storageKey」记忆在本机，换账号互不影响。刷新页面后宽度应保持。
        <br />
        快捷键（经集中服务分发，W0-5.2）：<kbd>Ctrl</kbd>+<kbd>K</kbd> 全局搜索（global）
        · <kbd>F2</kbd> 切换右侧面板（workspace）· <kbd>F3</kbd> 聚焦搜索（workspace）；中文输入法组合态不触发；右面板输入框内 F2/F3 不误触。
      </div>
    </el-card>

    <div class="demo-stage">
      <SplitWorkspace
        ref="wsRef"
        storage-key="demo-split-workspace"
        :default-ratio="0.7"
        :left-min-width="360"
        :right-min-width="280"
        @change="onChange"
      >
        <template #left>
          <div class="demo-pane demo-pane--left">
            <el-form inline size="small" class="demo-head">
              <el-form-item label="客户"><el-input model-value="测试客户A" readonly /></el-form-item>
              <el-form-item label="配送点"><el-input model-value="点1-中山路" readonly /></el-form-item>
              <el-form-item label="配送日期"><el-input model-value="2026-09-01" readonly /></el-form-item>
            </el-form>
            <el-table :data="rows" size="small" height="100%" border>
              <el-table-column type="index" width="48" />
              <el-table-column prop="sku" label="SKU" width="100" />
              <el-table-column prop="name" label="商品名称" min-width="140" />
              <el-table-column prop="num" label="数量" width="80" />
              <el-table-column prop="price" label="单价" width="90" />
              <el-table-column prop="amount" label="金额" width="100" />
            </el-table>
          </div>
        </template>

        <template #right>
          <div class="demo-pane demo-pane--right">
            <el-tabs v-model="activeTab">
              <el-tab-pane label="常用商品" name="common">
                <div v-for="g in goods" :key="g" class="demo-goods-item">{{ g }}</div>
              </el-tab-pane>
              <el-tab-pane label="最近订单" name="recent">
                <div v-for="o in recentOrders" :key="o" class="demo-goods-item">{{ o }}</div>
              </el-tab-pane>
              <el-tab-pane label="商品搜索" name="search">
                <el-input ref="searchInputRef" v-model="keyword" placeholder="输入商品名/助记码搜索（演示）" clearable />
                <div v-for="g in filteredGoods" :key="g" class="demo-goods-item">{{ g }}</div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
      </SplitWorkspace>
    </div>
  </div>
</template>

<script setup name="DemoSplitWorkspace">
import { computed, nextTick, ref } from 'vue'
import SplitWorkspace from '@/components/SplitWorkspace/index.vue'
import { SCOPE } from '@/utils/shortcut'
import { useShortcuts } from '@/composables/useShortcuts'

const searchInputRef = ref(null)

const wsRef = ref(null)
const ratio = ref(0.7)
const activeTab = ref('common')
const keyword = ref('')

const rows = Array.from({ length: 18 }, (_, i) => ({
  sku: `SKU${String(i + 1).padStart(3, '0')}`,
  name: `蔬菜-${i + 1}号商品`,
  num: (i % 5) + 1,
  price: ((i * 7) % 23) + 2.5,
  amount: 0
})).map((r) => ({ ...r, amount: (r.num * r.price).toFixed(2) }))

const goods = ['生菜 500g', '上海青 300g', "番茄 1kg", "土豆 1kg", "胡萝卜 500g", "青椒 300g"]
const recentOrders = ['SO20260901001 / 测试客户A / 18 行', 'SO20260901002 / 测试客户B / 6 行']

const filteredGoods = computed(() =>
  keyword.value ? goods.filter((g) => g.includes(keyword.value)) : goods
)

function onChange(v) {
  ratio.value = v
}

function onReset() {
  wsRef.value?.resetLayout()
  ratio.value = 0.7
}

function toggleRandom() {
  activeTab.value = activeTab.value === 'common' ? 'search' : 'common'
}

// 工作区作用域快捷键演示（路由离开/keep-alive 失活自动停用）
useShortcuts([
  {
    scope: SCOPE.WORKSPACE,
    key: 'f2',
    description: '切换右侧面板',
    owner: 'DemoSplitWorkspace',
    handler: toggleRandom
  },
  {
    scope: SCOPE.WORKSPACE,
    key: 'f3',
    description: '切到商品搜索并聚焦',
    owner: 'DemoSplitWorkspace',
    handler: () => {
      activeTab.value = 'search'
      nextTick(() => searchInputRef.value?.focus())
    }
  }
])
</script>

<style lang="scss" scoped>
.demo-split-workspace {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  padding: 12px;
  box-sizing: border-box;
}

.demo-toolbar {
  flex: 0 0 auto;

  &__row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__title {
    font-weight: 600;
  }

  &__tips {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    kbd {
      padding: 0 4px;
      border: 1px solid var(--el-border-color);
      border-radius: 3px;
      background: var(--el-fill-color-light);
    }
  }
}

.demo-stage {
  flex: 1 1 auto;
  min-height: 0;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
}

.demo-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &--left {
    padding: 8px;
    box-sizing: border-box;
    gap: 8px;
  }

  &--right {
    padding: 8px 12px;
    box-sizing: border-box;
  }
}

.demo-head {
  flex: 0 0 auto;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.demo-goods-item {
  padding: 6px 8px;
  font-size: 13px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
</style>
