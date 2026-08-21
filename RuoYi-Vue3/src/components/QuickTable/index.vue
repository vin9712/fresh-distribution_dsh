<template>
  <div class="quick-table" :class="{ 'is-zoomed': isZoomed }">
    <!-- 搜索区（紧凑、可折叠） -->
    <div
      v-if="$slots.search"
      v-show="searchVisible"
      ref="searchArea"
      class="quick-search"
    >
      <div class="quick-search-form">
        <slot name="search"></slot>
      </div>
      <div class="quick-search-actions">
        <el-button
          type="primary"
          size="small"
          :icon="Search"
          @click="emitQuery"
        >搜索</el-button>
        <el-button size="small" :icon="Refresh" @click="emitReset">重置</el-button>
      </div>
      <div class="quick-search-toggle" :title="searchVisible ? '收起搜索' : '展开搜索'" @click="searchVisible = !searchVisible">
        <el-icon><ArrowUp v-if="searchVisible" /><ArrowDown v-else /></el-icon>
      </div>
    </div>

    <!-- 表格区（vxe-grid） -->
    <vxe-grid
      ref="xGrid"
      :id="id"
      auto-resize
      border
      show-overflow
      :height="height"
      :data="data"
      :loading="loading"
      :columns="mergedColumns"
      :row-config="rowConfig"
      :checkbox-config="checkboxConfig"
      :custom-config="customConfig"
      :scroll-y="scrollYConfig"
      :menu-config="mergedMenuConfig"
      :keyboard-config="keyboardConfig"
      :column-config="columnConfig"
      :toolbar-config="toolbarConfig"
      :pager-config="pagerConfig"
      :tree-config="treeConfig"
      @checkbox-change="updateSelection"
      @checkbox-all="updateSelection"
      @cell-dblclick="handleCellDblclick"
      @menu-click="handleMenuClick"
      @page-change="handlePageChange"
      @zoom="handleZoom"
    >
      <!-- 透传列插槽（columns[].slots.default -> 页面同名插槽） -->
      <template v-for="name in columnSlotNames" :key="name" #[name]="slotProps">
        <slot :name="name" v-bind="slotProps"></slot>
      </template>

      <!-- 工具栏：左侧操作按钮 -->
      <template #quick_buttons="{ $grid, $table }">
        <slot name="buttons" :$grid="$grid" :$table="$table"></slot>
      </template>

      <!-- 工具栏：右侧工具（刷新 + 快捷键提示） -->
      <template #quick_tools>
        <slot name="toolbarRight"></slot>
        <el-tooltip content="刷新" placement="top">
          <vxe-button mode="text" icon="vxe-icon-refresh" @click="emitRefresh"></vxe-button>
        </el-tooltip>
        <el-popover
          v-if="shortcuts"
          placement="bottom"
          :width="300"
          trigger="click"
          popper-class="quick-shortcut-popover"
        >
          <template #reference>
            <vxe-button mode="text" icon="vxe-icon-question-circle-fill" title="快捷键"></vxe-button>
          </template>
          <div class="quick-shortcut-title">键盘快捷键</div>
          <ul class="quick-shortcut-list">
            <li v-for="s in shortcutList" :key="s.key">
              <span class="sc-key">{{ s.key }}</span>
              <span class="sc-desc">{{ s.desc }}</span>
            </li>
          </ul>
        </el-popover>
      </template>
    </vxe-grid>

    <!-- 批量操作条（选中行后浮现） -->
    <transition name="quick-batch">
      <div v-if="selection.length > 0 && batchActions.length > 0" class="quick-batch-bar">
        <span class="batch-count">已选 {{ selection.length }} 项</span>
        <el-button
          v-for="action in batchActions"
          :key="action.key"
          size="small"
          :type="action.type || 'primary'"
          :plain="action.plain !== false"
          :icon="action.icon"
          :disabled="action.disabled && action.disabled(selection)"
          @click="emitBatchAction(action)"
        >{{ action.label }}</el-button>
        <el-button size="small" text :icon="Close" @click="clearSelection">清除选择</el-button>
        <span class="batch-hint">提示：Delete 键可快速删除选中项</span>
      </div>
    </transition>
  </div>
</template>

<script>
import { Search, Refresh, Close, ArrowUp, ArrowDown } from '@element-plus/icons-vue'

/**
 * 快捷列表页核心组件（deepseek_ui_redesign.md §2/§3/§4/§5）
 *  - vxe-grid 高密度表格：虚拟滚动、行高亮、双击编辑、右键菜单、列设置（localStorage 持久化）
 *  - 批量操作条：选中行后浮出
 *  - 键盘快捷键：F2 新增 / F3 聚焦搜索 / Delete 删除选中 / Ctrl+S 保存 / Esc 清除选择
 *  - 紧凑布局：搜索区可折叠，工具栏自定义列/刷新/全屏
 */
export default {
  name: 'QuickTable',
  props: {
    /** localStorage 列设置唯一键，必填 */
    id: { type: String, required: true },
    /** vxe-column 配置数组（不含 checkbox/seq，自动前置） */
    columns: { type: Array, default: () => [] },
    data: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    total: { type: Number, default: 0 },
    page: { type: Number, default: 1 },
    limit: { type: Number, default: 10 },
    pageSizes: { type: Array, default: () => [10, 20, 50, 100, 200] },
    height: { type: [String, Number], default: 'calc(100vh - 230px)' },
    /** 批量操作条按钮：{ key, label, type, icon, plain, disabled(rows) } */
    batchActions: {
      type: Array,
      default: () => [{ key: 'delete', label: '删除', type: 'danger', icon: 'Delete' }],
    },
    /** 右键菜单配置，覆盖默认 */
    menuConfig: { type: Object, default: null },
    rowKey: { type: String, default: 'id' },
    treeConfig: { type: Object, default: null },
    showPager: { type: Boolean, default: true },
    scrollY: { type: Boolean, default: true },
    shortcuts: { type: Boolean, default: true },
    showSearch: { type: Boolean, default: true },
  },
  emits: [
    'update:page', 'update:limit', 'update:showSearch',
    'query', 'reset', 'refresh', 'page-change', 'selection-change',
    'add', 'edit', 'delete', 'save', 'global-search',
    'batch-action', 'menu-click', 'row-dblclick',
  ],
  setup() {
    return { Search, Refresh, Close, ArrowUp, ArrowDown }
  },
  data() {
    return {
      isZoomed: false,
      selection: [],
      localShowSearch: this.showSearch,
      shortcutList: [
        { key: 'F2', desc: '新增记录' },
        { key: 'F3', desc: '聚焦搜索框' },
        { key: 'Delete', desc: '删除选中行' },
        { key: 'Ctrl + S', desc: '保存' },
        { key: 'Esc', desc: '清除选择' },
        { key: '双击行', desc: '打开编辑' },
        { key: '右键行', desc: '快捷菜单' },
      ],
    }
  },
  computed: {
    /** 搜索区显示（双向同步，父组件可用 v-model:showSearch） */
    searchVisible: {
      get() {
        return this.localShowSearch
      },
      set(val) {
        this.localShowSearch = val
        this.$emit('update:showSearch', val)
      },
    },
    columnSlotNames() {
      const names = []
      this.columns.forEach((col) => {
        if (col.slots && col.slots.default) {
          names.push(col.slots.default)
        }
      })
      return [...new Set(names)]
    },
    mergedColumns() {
      const cols = [
        { type: 'checkbox', width: 46, fixed: 'left', align: 'center' },
        { type: 'seq', title: '#', width: 50, fixed: 'left', align: 'center' },
      ]
      this.columns.forEach((col) => {
        const c = { ...col }
        // 列设置持久化要求每列有 field；操作列给个占位 field 便于存储
        if (!c.field && c.slots && c.slots.default) {
          c.field = c.slots.default
        }
        cols.push(c)
      })
      return cols
    },
    rowConfig() {
      return { isHover: true, useKey: true, keyField: this.rowKey, height: 44 }
    },
    checkboxConfig() {
      // 树形表格不支持 range 连选
      return this.treeConfig ? { highlight: true } : { highlight: true, range: true }
    },
    customConfig() {
      return { storage: true, allowFixed: true }
    },
    scrollYConfig() {
      return { enabled: this.scrollY, gt: 100 }
    },
    keyboardConfig() {
      return {
        isArrow: true,
        isEnter: true,
        isTab: true,
        isEdit: true,
        isChecked: true,
        isDel: false, // 服务端 CRUD，删除走事件
      }
    },
    columnConfig() {
      return { resizable: true }
    },
    toolbarConfig() {
      return {
        custom: true,
        zoom: true,
        slots: {
          buttons: 'quick_buttons',
          toolSuffix: 'quick_tools',
        },
      }
    },
    pagerConfig() {
      return {
        enabled: this.showPager,
        pageSize: this.limit,
        currentPage: this.page,
        pageSizes: this.pageSizes,
        total: this.total,
        layouts: ['Total', 'PrevJump', 'PrevPage', 'Number', 'NextPage', 'NextJump', 'Sizes', 'FullJump'],
      }
    },
    mergedMenuConfig() {
      if (this.menuConfig) {
        return this.menuConfig
      }
      return {
        body: {
          options: [
            [
              { code: 'add', name: '新增', prefixIcon: 'vxe-icon-add' },
              { code: 'edit', name: '修改', prefixIcon: 'vxe-icon-edit' },
            ],
            [
              { code: 'delete', name: '删除', prefixIcon: 'vxe-icon-delete' },
              { code: 'refresh', name: '刷新', prefixIcon: 'vxe-icon-refresh' },
            ],
          ],
        },
        header: {
          options: [
            [
              { code: 'refresh', name: '刷新', prefixIcon: 'vxe-icon-refresh' },
              { code: 'resetCustom', name: '重置列设置', prefixIcon: 'vxe-icon-custom-column' },
            ],
          ],
        },
      }
    },
  },
  watch: {
    showSearch(val) {
      this.localShowSearch = val
    },
  },
  mounted() {
    if (this.shortcuts) {
      window.addEventListener('keydown', this.onKeydown)
    }
  },
  beforeUnmount() {
    if (this.shortcuts) {
      window.removeEventListener('keydown', this.onKeydown)
    }
  },
  methods: {
    /** 快捷键处理 */
    onKeydown(e) {
      const tag = (e.target && e.target.tagName) || ''
      const inInput =
        tag === 'INPUT' ||
        tag === 'TEXTAREA' ||
        tag === 'SELECT' ||
        (e.target && e.target.isContentEditable)
      const key = String(e.key || '').toLowerCase()
      if (key === 'f2') {
        e.preventDefault()
        this.$emit('add')
        return
      }
      if (key === 'f3') {
        e.preventDefault()
        this.focusSearch()
        return
      }
      if ((e.ctrlKey || e.metaKey) && key === 's') {
        e.preventDefault()
        this.$emit('save')
        return
      }
      if ((e.ctrlKey || e.metaKey) && key === 'k') {
        e.preventDefault()
        this.$emit('global-search')
        return
      }
      if (inInput) return
      if (key === 'delete') {
        if (this.selection.length) {
          e.preventDefault()
          this.$emit('delete', this.selection.map((r) => r.id))
        }
        return
      }
      if (key === 'escape') {
        this.clearSelection()
      }
    },
    /** 聚焦搜索区第一个输入框 */
    focusSearch() {
      const area = this.$refs.searchArea
      if (!area) return
      const input = area.querySelector('input')
      if (input) {
        input.focus()
        input.select && input.select()
      }
    },
    /** 勾选变化 -> 同步选中集合 */
    updateSelection() {
      const grid = this.$refs.xGrid
      if (!grid) return
      this.selection = grid.getCheckboxRecords()
      this.$emit('selection-change', this.selection)
    },
    /** 清除选择 */
    clearSelection() {
      const grid = this.$refs.xGrid
      if (grid && grid.clearCheckboxRow) {
        grid.clearCheckboxRow()
      }
      this.selection = []
      this.$emit('selection-change', [])
    },
    handleCellDblclick({ row }) {
      this.$emit('edit', row)
      this.$emit('row-dblclick', row)
    },
    /** 右键菜单点击 */
    handleMenuClick({ menu, row }) {
      switch (menu.code) {
        case 'add':
          this.$emit('add')
          break
        case 'edit':
          this.$emit('edit', row)
          break
        case 'delete':
          this.$emit('delete', row ? [row.id] : this.selection.map((r) => r.id))
          break
        case 'refresh':
          this.emitRefresh()
          break
        case 'resetCustom':
          if (this.$refs.xGrid && this.$refs.xGrid.resetCustom) {
            this.$refs.xGrid.resetCustom()
          }
          break
        default:
          this.$emit('menu-click', { menu, row })
      }
    },
    handlePageChange({ currentPage, pageSize }) {
      this.$emit('update:page', currentPage)
      this.$emit('update:limit', pageSize)
      this.$emit('page-change', { page: currentPage, limit: pageSize })
    },
    handleZoom({ type }) {
      this.isZoomed = type === 'max'
    },
    emitQuery() {
      this.$emit('query')
    },
    emitReset() {
      this.$emit('reset')
    },
    emitRefresh() {
      this.$emit('refresh')
    },
    emitBatchAction(action) {
      this.$emit('batch-action', { key: action.key, rows: this.selection })
    },
    /** 获取当前勾选行 */
    getSelection() {
      return this.selection
    },
    /** 强制刷新表格布局（窗口/容器变化后调用） */
    recalculate() {
      if (this.$refs.xGrid && this.$refs.xGrid.recalculate) {
        this.$refs.xGrid.recalculate()
      }
    },
  },
}
</script>

<style scoped lang="scss">
.quick-table {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;

  // 全屏态（配合 vxe-grid zoom）
  &.is-zoomed {
    position: fixed;
    inset: 0;
    z-index: 2000;
    padding: 10px;
    background: var(--el-bg-color, #fff);
  }

  // 搜索区
  .quick-search {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 10px;
    margin-bottom: 8px;
    background: var(--el-bg-color, #fff);
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 4px;

    :deep(.el-form--inline .el-form-item) {
      margin-right: 12px;
      margin-bottom: 0;
      vertical-align: middle;
    }
    :deep(.el-form-item__label) {
      font-size: 13px;
      color: var(--el-text-color-regular, #606266);
    }

    .quick-search-form {
      flex: 1;
      min-width: 0;
    }
    .quick-search-actions {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .quick-search-toggle {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      margin-top: 2px;
      cursor: pointer;
      border-radius: 3px;
      color: var(--el-text-color-secondary, #909399);
      &:hover {
        background: var(--el-fill-color-light, #f5f7fa);
        color: var(--el-color-primary, #409eff);
      }
    }
  }

  // 批量操作条
  .quick-batch-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    padding: 6px 12px;
    border: 1px solid var(--el-color-primary-light-5, #a0cfff);
    border-radius: 4px;
    background: var(--el-color-primary-light-9, #ecf5ff);

    .batch-count {
      font-size: 13px;
      font-weight: 600;
      color: var(--el-color-primary, #409eff);
      margin-right: 4px;
    }
    .batch-hint {
      margin-left: auto;
      font-size: 12px;
      color: var(--el-text-color-secondary, #909399);
    }
  }
}

// 批量条动画
:global(.quick-batch-enter-active),
:global(.quick-batch-leave-active) {
  transition: all 0.2s ease;
}
:global(.quick-batch-enter-from),
:global(.quick-batch-leave-to) {
  opacity: 0;
  transform: translateY(-6px);
}
</style>

<style lang="scss">
// 快捷键提示弹层
.quick-shortcut-popover {
  .quick-shortcut-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 8px;
  }
  .quick-shortcut-list {
    margin: 0;
    padding: 0;
    list-style: none;
    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 4px 0;
      font-size: 13px;
      color: var(--el-text-color-regular, #606266);
      .sc-key {
        min-width: 90px;
        padding: 1px 8px;
        border: 1px solid var(--el-border-color, #dcdfe6);
        border-bottom-width: 2px;
        border-radius: 3px;
        background: var(--el-fill-color-light, #f5f7fa);
        font-family: Consolas, Monaco, monospace;
        font-size: 12px;
        text-align: center;
      }
      .sc-desc {
        color: var(--el-text-color-secondary, #909399);
      }
    }
  }
}
</style>
