/**
 * QuickTable 页面通用逻辑 mixin（deepseek_ui_redesign.md）
 * 提供：行选择状态（ids/single/multiple）、分页事件处理、批量操作分发
 * 页面需自行实现：
 *   - reloadTable()：重新查询列表（getList / getPageList）
 *   - handleDelete(idsOrRow)：删除逻辑（默认已接线）
 */
export default {
  data() {
    return {
      // 选中行的 id 集合
      ids: [],
      // 选中行对象集合
      selectionRows: [],
      // 是否仅选中一行（false = 可用）
      single: true,
      // 是否无选中（true = 禁用）
      multiple: true,
    }
  },
  methods: {
    /** QuickTable @selection-change */
    handleSelectionChange(rows) {
      this.selectionRows = rows || []
      this.ids = this.selectionRows.map((item) => item.id)
      this.single = this.selectionRows.length !== 1
      this.multiple = !this.selectionRows.length
    },
    /** QuickTable @page-change */
    handleQuickPageChange({ page, limit }) {
      this.queryParams.pageNum = page
      this.queryParams.pageSize = limit
      if (typeof this.reloadTable === 'function') {
        this.reloadTable()
      }
    },
    /** QuickTable @query（搜索） */
    handleQuickQuery() {
      this.queryParams.pageNum = 1
      if (typeof this.reloadTable === 'function') {
        this.reloadTable()
      }
    },
    /** QuickTable @reset（重置搜索） */
    handleQuickReset() {
      this.resetForm(this.queryFormRef || 'queryForm')
      this.handleQuickQuery()
    },
    /** QuickTable @delete（右键/Delete 键） */
    handleQuickDelete(ids) {
      if (!ids || !ids.length) return
      this.handleDelete(ids)
    },
    /** QuickTable @batch-action（批量条按钮） */
    handleQuickBatchAction({ key, rows }) {
      this.selectionRows = rows || this.selectionRows
      this.ids = this.selectionRows.map((item) => item.id)
      this.single = this.selectionRows.length !== 1
      this.multiple = !this.selectionRows.length
      // 默认删除动作：所有列表页通用
      if (key === 'delete') {
        if (typeof this.handleDelete === 'function') {
          this.handleDelete()
        }
        return
      }
      if (typeof this.handleBatchAction === 'function') {
        this.handleBatchAction(key, rows)
      }
    },
    /** 清除选择 */
    clearQuickSelection() {
      const qt = this.$refs.quickTable
      if (qt && qt.clearSelection) {
        qt.clearSelection()
      }
      this.selectionRows = []
      this.ids = []
      this.single = true
      this.multiple = true
    },
  },
}
