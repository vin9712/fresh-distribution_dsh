<template>
  <div class="app-container">
    <!-- OA 验收模式横幅：一订单一验，左侧下单快照只读，实收/差异/换退加在此完成 -->
    <el-alert
      v-if="acceptanceMode"
      :type="acceptanceReadonly ? 'success' : 'warning'"
      :closable="false"
      show-icon
      class="browse-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          <template v-if="acceptanceReadonly">
            订单已验收：验收单【{{ acceptanceInfo ? acceptanceInfo.code : '' }}】
            <el-button
              v-hasPermi="['acceptance:revoke']"
              type="warning"
              size="small"
              @click="showRevokeDialog"
            >撤销验收</el-button>
          </template>
          <template v-else>
            验收模式：左侧为下单时快照不可修改；实收默认=下单数量，可逐行修改、标记换/退/加，或直接一键验收
          </template>
          <el-button size="small" @click="exitAcceptanceMode">退出验收</el-button>
        </span>
      </template>
    </el-alert>
    <!-- 浏览模式横幅：查看历史订单时常驻「返回我的新单」入口 -->
    <el-alert
      v-if="browseMode && !acceptanceMode"
      type="info"
      :closable="false"
      show-icon
      class="browse-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          浏览模式：正在查看历史订单【{{ browsedOrder ? browsedOrder.code : '' }}】，数据只读、自动保存已暂停
          <el-button type="primary" size="small" @click="exitBrowseMode">← 返回我的新单</el-button>
        </span>
      </template>
    </el-alert>
    <!-- 配送/验收冻结提示条：已进入配送流程的订单明细为生成时快照，不可改单（S14/G6 护栏） -->
    <el-alert
      v-if="frozenBannerVisible"
      type="info"
      :closable="false"
      show-icon
      class="browse-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          该订单已进入配送/验收流程，明细为生成送货单时的冻结快照；配送后的真实退货请使用退货单，补货请新增销售订单
        </span>
      </template>
    </el-alert>
    <!-- 草稿恢复提示条（新单页，检测到未完成草稿时显示） -->
    <el-alert
      v-if="showDraftBanner && availableDrafts.length && !acceptanceMode"
      type="warning"
      :closable="false"
      show-icon
      class="draft-recover-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          检测到未完成的订单草稿{{ availableDrafts[0].deptName ? '【' + availableDrafts[0].deptName + '】' : '' }}（保存于 {{ formatSavedAt(availableDrafts[0].savedAt) }}）
          <el-button link type="primary" @click="restoreLatestDraft">点击恢复</el-button>
          <el-button link @click="dismissDraftBanner">忽略</el-button>
        </span>
      </template>
    </el-alert>
    <!-- 编辑已有草稿订单后的提示横幅：点错了？返回重开新单 -->
    <el-alert
      v-if="showEditExistingBanner && !acceptanceMode"
      type="warning"
      show-icon
      class="edit-existing-banner"
    >
      <template #title>
        <span class="draft-banner-title">
          正在编辑已有草稿订单，点错了？
          <el-button type="danger" size="small" @click="returnToNewOrder">← 返回重开新单</el-button>
        </span>
      </template>
    </el-alert>
    <!-- S1 订单桌面工作台：左右可拖拽分屏（W0-5.1 SplitWorkspace），宽度本机按登录用户记忆 -->
    <split-workspace
      ref="splitWorkspace"
      storage-key="order-sale-detail"
      :default-ratio="0.68"
      :left-min-width="600"
      :right-min-width="320"
      class="order-split"
      :right-hidden="acceptanceMode"
    >
      <!-- 做单区 -->
      <template #left>
        <el-card class="order-card">
          <!-- 订单表单 -->
          <template #header>
            <span class="order-header-title">订单信息</span>
            <el-tooltip
              :disabled="!draftStatusText"
              :content="draftTipText"
              placement="bottom"
            >
              <span
                v-if="draftStatusText"
                class="draft-status-tag"
                :class="draftStatusType"
                >{{ draftStatusText }}</span
              >
            </el-tooltip>
            <!-- 草稿箱（S1-1.5）：草稿可见/可恢复/可删除（验收模式隐藏） -->
            <el-button
              v-if="!acceptanceMode"
              link
              type="primary"
              size="small"
              class="draft-box-btn"
              @click="openDraftBox"
            >草稿箱{{ availableDrafts.length ? '(' + availableDrafts.length + ')' : '' }}</el-button>
            <div v-if="acceptanceMode" class="acc-header-info">
              <span>客户：{{ orderForm.customerName || '—' }}</span>
              <span>配送点：{{ orderForm.customerDeptName || orderForm.customerDeptDisplayName || '—' }}</span>
              <span>配送日期：{{ orderForm.deliveryDate || '—' }}</span>
              <span>订单编号：{{ orderForm.orderCode || '—' }}</span>
              <span v-if="acceptanceInfo">验收单：{{ acceptanceInfo.code }}</span>
            </div>
            <el-form
              v-show="!acceptanceMode"
              ref="orderForm"
              :model="orderForm"
              :rules="rules"
              size="small"
              inline
              label-width="100px"
            >
              <el-form-item label="送货单位" prop="customerDeptId">
                <div class="dept-cascader-line">
                  <el-cascader
                    ref="deptCascader"
                    v-model="selectedCustomerDepts"
                    placeholder="请选择送货单位"
                    :disabled="customerDeptLocked || browseMode"
                    :options="customerDeptOptions"
                    @change="handleFormOptionsChanged"
                    :props="{ expandTrigger: 'hover' }"
                    filterable
                  />
                  <!-- 整体锁定后的重开新单入口：清明细、重取价、重新分配编号 -->
                  <el-button
                    v-if="showReopenNewBtn"
                    link
                    type="danger"
                    class="reopen-new-btn"
                    @click="reopenNewOrder"
                    >重开新单</el-button
                  >
                </div>
              </el-form-item>
              <el-form-item label="配送日期" prop="deliveryDate">
                <el-date-picker
                  v-model="orderForm.deliveryDate"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择配送日期"
                  clearable
                  :disabled="orderInfoReadonly"
                ></el-date-picker>
              </el-form-item>
              <el-form-item prop="orderCode">
                <template #label>
                  订单编号
                  <!-- 仅新建订单可换号：订单编号是录单时固化的快照标识（后端 checkSnapshotImmutable 拒改），
                       修改已有订单时点刷新只会拿到新号再被拒，故编辑态隐去 -->
                  <el-icon
                    v-if="!orderInfoReadonly && !orderForm.orderId"
                    @click="refreshOrderCode"
                    style="cursor: pointer"
                    title="重新生成订单编号"
                  >
                    <Refresh />
                  </el-icon>
                </template>
                <el-input
                  v-model="orderForm.orderCode"
                  placeholder="请输入订单编号"
                  disabled
                >
                </el-input>
              </el-form-item>
              <el-form-item label="订单备注" prop="remark">
                <el-input
                  v-model="orderForm.remark"
                  placeholder="请输入订单备注"
                  :disabled="orderInfoReadonly"
                >
                </el-input>
              </el-form-item>
              <el-form-item label="自动新增">
                <el-switch v-model="isContinueAdd" />
              </el-form-item>
            </el-form>
          </template>

          <!-- 订单表格 -->
          <div class="order-table-container" :style="{ height: tableHeight }">
            <vxe-table
              border
              :column-config="{ resizable: true }"
              show-footer
              show-overflow
              keep-source
              ref="xTable"
              size="small"
              class="order-table"
              :height="tableInnerHeight"
              :row-config="{ isHover: true, useKey: true }"
              :mouse-config="{ selected: true }"
              :keyboard-config="{
                isArrow: true,
                isDel: true,
                isEnter: false,
                isTab: true,
                isEdit: true,
                isChecked: true,
              }"
              :footer-method="footerMethod"
              :edit-rules="validRules"
              :edit-config="{
                trigger: 'click',
                mode: 'cell',
                beforeEditMethod: checkTableActive,
              }"
              :data="orderDetailList"
              @cell-mouseenter="cellMouseenterEvent"
              @cell-mouseleave="cellMouseleaveEvent"
            >
              <!-- 操作列（浏览/验收态隐藏增删拖拽） -->
              <vxe-column v-if="canEditOrder" field="operate" width="63">
                <template #default="{ row, rowIndex }">
                  <!-- 拖动 -->
                  <span v-if="currentHoverRow === row" class="drag-btn">
                    <el-icon><Rank /></el-icon>
                  </span>
                  <!-- 增加 -->
                  <span @click="throttledAddRow(rowIndex)">
                    <el-icon><Plus /></el-icon>
                  </span>
                  <!-- 减少 -->
                  <span @click="handleRemoveRow(row)">
                    <el-icon><Minus /></el-icon>
                  </span>
                </template>
              </vxe-column>

              <!-- OA 验收模式：行内变更（换/退行内确认；加单/换货=插入可编辑行，同录单页商品下拉/取价） -->
              <vxe-column v-if="acceptanceMode" title="变更" width="96" align="center">
                <template #default="{ row }">
                  <template v-if="row._accEditing">
                    <el-button link type="primary" size="small" @click="confirmInlineChange(row)">确认</el-button>
                    <el-button link type="info" size="small" @click="cancelInlineChange(row)">取消</el-button>
                  </template>
                  <template v-else-if="row.changeType != null">
                    <el-button
                      link
                      type="info"
                      size="small"
                      :disabled="acceptanceReadonly"
                      @click="revertInlineChange(row)"
                    >回退</el-button>
                  </template>
                  <template v-else>
                    <el-button
                      link
                      type="warning"
                      size="small"
                      :disabled="acceptanceReadonly || row.changeType === 3"
                      @click="startInlineChange(2, row)"
                    >换</el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      :disabled="acceptanceReadonly || row.changeType === 3"
                      @click="startInlineChange(3, row)"
                    >退</el-button>
                  </template>
                </template>
              </vxe-column>

              <vxe-column type="seq" width="50"></vxe-column>
              <vxe-column
                field="productName"
                title="商品名称"
                :edit-render="{ name: 'VxeInput', autoselect: true }"
                :width="acceptanceMode ? 190 : '25%'"
              >
                <!-- 商品名称 + D-055 变更标记（加单/换货/退货，验收模式可见性更高） -->
                <template #default="{ row }">
                  <span>{{ row.productName }}</span>
                  <el-tag v-if="row.changeType === 1" size="small" type="warning" effect="plain" class="acc-row-tag">加单</el-tag>
                  <el-tag v-else-if="row.changeType === 2" size="small" type="success" effect="plain" class="acc-row-tag">换货</el-tag>
                  <el-tag v-else-if="row.changeType === 3" size="small" type="danger" effect="plain" class="acc-row-tag">退货</el-tag>
                </template>
                <!-- 商品名称+报价详情下拉框 -->
                <template #edit="{ row: parentRow }">
                  <vxe-pulldown
                    ref="pulldownRef"
                    popup-class-name="product-name-dropdown"
                    transfer
                  >
                    <template #default>
                      <vxe-input
                        v-model="parentRow.productName"
                        placeholder="请输入商品名称"
                        clearable
                        @keyup="keyupProductNameEvent({ parentRow, value: $event.value })"
                        @focus="focusProductNameEvent({ parentRow, value: $event.value })"
                        @blur="
                          blurProductNameEvent({
                            parentRow,
                            value: $event.value,
                          })
                        "
                        @clear="clearProductNameEvent(parentRow)"
                      ></vxe-input>
                    </template>

                    <template #dropdown>
                      <div class="product-dropdown-planel">
                        <vxe-grid
                          border
                          auto-resize
                          height="auto"
                          :row-config="{ isHover: true }"
                          :data="pulldownTableData"
                          :columns="pulldownTableColumn"
                          @cell-click="
                            pulldownCellClickEvent({
                              parentRow,
                              row: $event.row,
                            })
                          "
                        >
                          <!-- 商品名称列：关键词高亮 + 来源标签 -->
                          <template #productNameCell="{ row }">
                            <span class="prod-name-cell">
                              <span v-html="highlightKeyword(row.productName)"></span>
                              <el-tag
                                v-if="row.sourceTag"
                                size="small"
                                :type="row.sourceTag === '临时' ? 'info' : 'warning'"
                                class="prod-source-tag"
                                >{{ row.sourceTag }}</el-tag
                              >
                            </span>
                          </template>
                        </vxe-grid>
                      </div>
                    </template>
                  </vxe-pulldown>
                </template>
              </vxe-column>
              <vxe-column
                field="productUnit"
                title="单位"
                :width="acceptanceMode ? 60 : '8%'"
                :edit-render="{ name: '$input', autoselect: true }"
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.productUnit"
                    type="text"
                    @change="changedProductUnitEvent(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
              <vxe-column
                field="num"
                title="数量"
                cell-type="number"
                :width="acceptanceMode ? 80 : null"
                :formatter="decimalFormatter('num')"
                :edit-render="{ name: '$input', autoselect: true }"
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.num"
                    type="text"
                    @change="onNumEdited(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
              <vxe-column
                field="productPrice"
                title="单价"
                cell-type="number"
                :width="acceptanceMode ? 75 : null"
                :formatter="decimalFormatter('productPrice')"
                :class-name="priceCellClass"
                :edit-render="{ name: '$input', autoselect: true }"
              >
                <!-- 单价默认态：手工定价行显示「手」标签 + 原建议价悬浮（S1-1.3 审计可见） -->
                <template #default="{ row }">
                  <span class="price-cell">
                    <span>{{ row.productPrice }}</span>
                    <el-tooltip
                      v-if="row.priceSource === 'manual'"
                      :content="'手工定价：本次 ' + row.productPrice + (row.refPrice != null ? '（原报价 ' + row.refPrice + '）' : '（无有效报价）')"
                      placement="top"
                    >
                      <el-tag size="small" type="warning" class="price-manual-tag">手</el-tag>
                    </el-tooltip>
                  </span>
                </template>
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.productPrice"
                    type="text"
                    @change="handlePriceChange(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
              <vxe-column field="amount" title="金额" :width="acceptanceMode ? 85 : null" :formatter="decimalFormatter('amount')"> </vxe-column>
              <!-- OA 验收模式：实收数量可编辑 + 差异 + 差异原因（载入默认=下单数量，一键/草稿保存落验收单） -->
              <vxe-column
                v-if="acceptanceMode"
                field="acceptActual"
                title="实收数量"
                cell-type="number"
                width="110"
                :class-name="accRowClass"
                :edit-render="{ name: '$input', autoselect: true }"
              >
                <template #edit="{ row }">
                  <vxe-input
                    v-model="row.acceptActual"
                    type="text"
                    :disabled="row._accEditing"
                    @change="onAccActualEdited(row)"
                  ></vxe-input>
                </template>
              </vxe-column>
              <vxe-column
                v-if="acceptanceMode"
                field="acceptDiff"
                title="差异"
                width="80"
                align="center"
              >
                <template #default="{ row }">
                  <span :class="accDiffClass(row)">{{ accDiffOf(row) }}</span>
                </template>
              </vxe-column>
              <vxe-column
                v-if="acceptanceMode"
                field="acceptReason"
                title="差异原因"
                min-width="140"
                :class-name="accRowClass"
              >
                <template #default="{ row }">
                  <el-select
                    v-if="!acceptanceReadonly && accDiffOf(row) !== 0"
                    v-model="row.acceptReason"
                    size="small"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选填（可自填）"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="d in acceptReasonOptions"
                      :key="d.value"
                      :label="d.label"
                      :value="d.value"
                    />
                  </el-select>
                  <span v-else-if="accDiffOf(row) !== 0">{{ row.acceptReason || '—' }}</span>
                  <span v-else class="acc-reason-none">—</span>
                </template>
              </vxe-column>
              <!-- OA：实收金额 = 实收数量 × 下单单价，随录入实时联动（列宽预留合计数字空间） -->
              <vxe-column
                v-if="acceptanceMode"
                field="acceptAmount"
                title="实收金额"
                width="120"
                align="right"
              >
                <template #default="{ row }">{{ accAmountOf(row).toFixed(2) }}</template>
              </vxe-column>
              <!-- 非验收模式：实收区（只读镜像）：实收数据归验收单（C1），此处仅展示验收提交同步的 actual_* 镜像值 -->
              <template v-if="!acceptanceMode">
                <vxe-column
                  v-if="showActualColumns"
                  field="actualNum"
                  title="实收数量"
                  cell-type="number"
                  width="100"
                  :formatter="decimalFormatter('actualNum')"
                />
                <vxe-column
                  v-if="showActualColumns"
                  field="lossReason"
                  title="差异原因"
                  width="120"
                >
                  <template #default="{ row }">
                    <dict-tag
                      v-if="row.lossReason"
                      :options="dict.type.biz_loss_reason"
                      :value="row.lossReason"
                    />
                  </template>
                </vxe-column>
              </template>
              <vxe-column
                field="productSpec"
                title="规格"
                min-width="110"
                :edit-render="{ name: '$input', autoselect: true }"
              ></vxe-column>
              <vxe-column
                field="remark"
                title="备注"
                min-width="130"
                :edit-render="{ name: '$input', autoselect: true }"
              ></vxe-column>
            </vxe-table>
          </div>

          <!-- 底部工具栏 -->
          <el-form class="order-footer" label-width="100px">
            <el-form-item
              style="text-align: center; margin-left: -100px; margin-top: 10px"
            >
              <!-- OA 验收模式工具条：加单（浅绿=新增，居左）/ 一键验收 / 保存草稿 / 验收日期 / 撤销验收（已验收态） -->
              <template v-if="acceptanceMode">
                <el-button
                  v-if="!acceptanceReadonly"
                  type="success"
                  plain
                  :icon="Plus"
                  @click="startInlineAdd"
                  v-hasPermi="['order:sale:edit']"
                >加单</el-button>
                <el-button
                  v-if="!acceptanceReadonly"
                  type="primary"
                  :icon="Check"
                  :loading="quickAccepting"
                  @click="handleQuickAccept"
                  v-hasPermi="['acceptance:submit']"
                >一键验收</el-button>
                <el-button
                  v-if="!acceptanceReadonly"
                  :loading="accSaving"
                  @click="saveAcceptanceDraft"
                  v-hasPermi="['acceptance:edit']"
                >保存草稿</el-button>
                <span v-if="!acceptanceReadonly" class="acc-date-label">
                  验收日期
                  <el-date-picker
                    v-model="accAcceptDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :clearable="false"
                    style="width: 140px"
                  />
                </span>
                <el-button
                  v-if="!acceptanceReadonly"
                  type="warning"
                  plain
                  @click="showRevokeDialog"
                  v-hasPermi="['acceptance:revoke']"
                >撤销验收</el-button>
                <el-button @click="exitAcceptanceMode">退出验收</el-button>
              </template>
              <template v-else>
                <el-button v-if="canEditOrder" @click="resetOrderForm()">重置</el-button>
                <el-button v-if="canEditOrder" type="primary" @click="submitForm()">保存</el-button>
              <!-- 复制为新单：以当前打开的单（含只读历史单）为模板另录一张，商品数量带过来、单价重取 -->
              <el-button
                v-if="canCopyAsNew"
                type="warning"
                plain
                :icon="DocumentCopy"
                @click="handleCopyAsNew()"
                >复制为新单</el-button
              >
              <el-button
                v-if="canEditOrder"
                type="success"
                plain
                :icon="Check"
                @click="openBatchConfirmDrawer()"
                >按客户批量确认</el-button
              >
              <el-button @click="close()">返回</el-button>
              </template>
            </el-form-item>
          </el-form>
        </el-card>
      </template>

      <!-- 选单区 -->
      <template #right>
        <el-card class="recent-order-card">
          <template #header>
            <el-tabs v-model="rightTab" class="right-tabs" stretch>
              <el-tab-pane label="常用" name="frequent" />
              <el-tab-pane label="最近" name="recent" />
              <el-tab-pane label="搜索" name="search" />
            </el-tabs>
          </template>
          <!-- 常用商品面板：近30天下单频率 Top，点击即插入明细 -->
          <div
            v-show="rightTab === 'frequent'"
            class="frequent-panel"
            :style="{ height: recentTableHeight }"
          >
            <div v-if="!orderForm.customerId" class="frequent-empty">
              <el-empty description="请先选择送货单位" :image-size="60" />
            </div>
            <div v-else-if="!frequentList.length" class="frequent-empty">
              <el-empty
                description="暂无常用商品（按近30天下单频率统计）"
                :image-size="60"
              />
            </div>
            <div v-else class="frequent-list">
              <div
                v-for="(item, idx) in frequentList"
                :key="item.skuId"
                class="frequent-item"
                @click="addFrequentProduct(item)"
              >
                <span class="frequent-index">{{ idx + 1 }}</span>
                <span class="frequent-name"
                  >{{ item.productName
                  }}<em v-if="item.productSpec" class="frequent-spec"
                    >（{{ item.productSpec }}）</em
                  ></span
                >
                <span class="frequent-unit">{{ item.productUnit }}</span>
                <span class="frequent-count">×{{ item.orderCount }}</span>
              </div>
            </div>
          </div>
          <!-- 商品搜索面板：按名称/助记码检索客户商品池，点击即插入明细（S1 右侧搜索面板） -->
          <div v-show="rightTab === 'search'" class="search-panel">
            <div class="search-bar">
              <el-input
                ref="searchInput"
                v-model="searchKeyword"
                size="small"
                clearable
                placeholder="商品名称 / 助记码，回车检索"
                @keyup.enter="handleSearchProducts"
                @clear="searchResults = []"
              />
              <el-button type="primary" size="small" @click="handleSearchProducts">搜索</el-button>
            </div>
            <div v-if="!orderForm.customerId" class="frequent-empty">
              <el-empty description="请先选择送货单位" :image-size="60" />
            </div>
            <div v-else-if="!searchResults.length" class="frequent-empty">
              <el-empty description="输入关键词检索客户商品池与临时商品" :image-size="60" />
            </div>
            <div v-else class="frequent-list search-list">
              <div
                v-for="item in searchResults"
                :key="(item.skuId || item.productName) + item.productUnit"
                class="frequent-item"
                @click="insertProductFromPanel(item)"
              >
                <span class="frequent-name"
                  >{{ item.productName
                  }}<em v-if="item.productSpec" class="frequent-spec">（{{ item.productSpec }}）</em></span
                >
                <span class="frequent-unit">{{ item.productUnit }}</span>
                <span class="frequent-count">¥{{ item.price }}</span>
              </div>
            </div>
          </div>
          <!-- 最近订单 -->
          <div v-show="rightTab === 'recent'">
            <!-- 最近订单表单 -->
            <el-form
              :model="recentQuery"
              size="small"
              ref="recentOrderForm"
              label-width="80px"
            >
              <!-- 第一行：天数选择器和客户选择器 -->
              <el-row :gutter="20">
                <el-col :span="9">
                  <el-form-item label="查询天数" prop="days">
                    <el-select
                      v-model="recentQuery.recentDays"
                      placeholder="请选择查询天数"
                      @change="handleRecentQuery"
                      style="width: 100%"
                    >
                      <el-option label="1天" :value="1"></el-option>
                      <el-option label="3天" :value="3"></el-option>
                      <el-option label="7天" :value="7"></el-option>
                      <el-option label="14天" :value="14"></el-option>
                      <el-option label="30天" :value="30"></el-option>
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="15">
                  <el-form-item label="送货客户" prop="customerId">
                    <el-select
                      v-model="recentQuery.customerId"
                      @change="handleRecentQuery"
                      filterable
                      clearable
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in customerOptions"
                        :key="item.id"
                        :label="item.alias ? item.alias : item.name"
                        :value="item.id"
                      ></el-option>
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 第二行：搜索词输入框和按钮 -->
              <el-row :gutter="20" style="display: flex; align-items: center">
                <el-col :span="18">
                  <el-form-item
                    label="搜索词"
                    prop="keyword"
                    style="margin-bottom: 0"
                  >
                    <el-input
                      v-model="recentQuery.keyword"
                      placeholder="请输入订单编号/送货单位"
                      clearable
                      style="width: 100%"
                    ></el-input>
                  </el-form-item>
                </el-col>
                <el-col
                  :span="6"
                  style="display: flex; justify-content: flex-end"
                >
                  <el-button
                    :icon="Search"
                    type="primary"
                    @click="handleRecentQuery"
                    circle
                    title="搜索"
                  ></el-button>
                  <el-button
                    :icon="Refresh"
                    @click="resetQuery"
                    circle
                    title="重置"
                    style="margin-left: 10px"
                  ></el-button>
                </el-col>
              </el-row>
            </el-form>
            <!-- 最近订单列表 -->
            <div
              class="recent-order-table-container"
              :style="{ height: recentTableHeight }"
            >
            <vxe-grid
              border
              auto-resize
              ref="recentOrderTable"
              size="small"
              height="auto"
              :row-config="{ isHover: true, isCurrent: true }"
              :data="recentOrderList"
              :columns="recentTableColumns"
              @current-change="handleRecentOrderRowChange"
            />
            </div>
          </div>
        </el-card>
      </template>
    </split-workspace>

    <!-- 草稿箱（S1-1.5：草稿可见、可恢复、可删除、可解释） -->
    <el-dialog v-model="draftBoxVisible" title="我的订单草稿" width="560px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="草稿每 5 秒自动保存（有改动时），提交订单成功后自动清除；本地与服务器双写，换电脑登录可恢复。"
        style="margin-bottom: 10px"
      />
      <el-table :data="availableDrafts" size="small" empty-text="暂无草稿">
        <el-table-column prop="deptName" label="送货单位" min-width="120" />
        <el-table-column prop="orderCode" label="订单编号" width="130" />
        <el-table-column label="明细行数" width="80">
          <template #default="{ row }">{{ (row.details || []).length }}</template>
        </el-table-column>
        <el-table-column label="保存时间" width="150">
          <template #default="{ row }">{{ formatFullSavedAt(row.savedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="restoreDraftFromBox(row)">恢复</el-button>
            <el-button link type="danger" size="small" @click="deleteDraftFromBox(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 按客户批量确认草稿订单（D-055：确认后才会进入客户日总表与验收链路） -->
    <el-drawer
      v-model="batchConfirmVisible"
      title="按客户批量确认草稿订单"
      size="620px"
      append-to-body
    >
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="客户">
          <el-select
            v-model="batchConfirmCustomerId"
            filterable
            clearable
            placeholder="请选择客户"
            style="width: 200px"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="item.alias ? item.alias : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配送日期">
          <el-date-picker
            v-model="batchConfirmDeliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="不限"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="batchConfirmLoading" @click="queryBatchConfirmList"
            >查询</el-button
          >
        </el-form-item>
      </el-form>

      <el-table
        v-loading="batchConfirmLoading"
        :data="batchConfirmRows"
        size="small"
        border
        row-key="id"
        empty-text="该客户没有待确认的草稿订单"
        @selection-change="batchConfirmSelection = $event"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="订单编号" prop="code" min-width="150" show-overflow-tooltip />
        <el-table-column label="送货单位" prop="deliveryName" min-width="120" show-overflow-tooltip />
        <el-table-column label="配送日期" prop="deliveryDate" width="110" align="center">
          <template #default="scope">{{ parseTime(scope.row.deliveryDate, "{y}-{m}-{d}") }}</template>
        </el-table-column>
        <el-table-column label="总金额" prop="amount" width="90" align="right" />
      </el-table>

      <div class="batch-confirm-footer">
        <span>
          已选 <b>{{ batchConfirmSelection.length }}</b> / {{ batchConfirmRows.length }} 条草稿，
          合计金额 {{ batchConfirmAmountTotal }}
        </span>
        <el-button
          type="primary"
          :disabled="!batchConfirmSelection.length"
          :loading="batchConfirmSubmitting"
          @click="submitBatchConfirm"
          >确认选中订单</el-button
        >
      </div>
      <div class="batch-confirm-tip">
        确认后订单变为「已确认」，即进入客户日总表（矩阵/配货/点单）与验收链路；确认后的订单不可再修改。
      </div>
    </el-drawer>

    <!-- OA 撤销验收弹窗（复用验收页同款交互：原因必填，已结算拒撤由后端兜底） -->
    <el-dialog align-center title="撤销验收单" v-model="accRevokeOpen" width="480px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="撤销后验收单回到草稿、订单回退可验收状态，已录实收保留；撤销前将保留完整审计快照，订单已月结则无法撤销"
        style="margin-bottom: 12px"
      />
      <el-form ref="accRevokeFormRef" :model="accRevokeForm" :rules="accRevokeRules" label-width="80px">
        <el-form-item label="撤销原因" prop="reason">
          <el-input
            v-model="accRevokeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入撤销原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="accRevoking" @click="submitAccRevoke">确认撤销</el-button>
          <el-button @click="accRevokeOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- OA：换/退/加均在行内操作，不再使用抽屉（行内编辑行见 vxe 表格 _accEditing 分支） -->
  </div>
</template>

<script>
import { pageSaleOrder, getSaleOrder, genOrderCode, createSaleOrder, updateSaleOrder, recentSaleOrder, checkExistingDraft, listSale, updateOrderStatus, deliverySupplement, deliveryExchange, deliveryReturn, revokeDeliveryChange } from "@/api/order/sale";
import { listSaleDetail, frequentSaleDetail } from "@/api/order/saleDetail";
import {
  getAcceptance,
  listAcceptanceItems,
  updateAcceptance,
  createAcceptanceByOrder,
  quickAcceptOrder,
  revokeAcceptance,
} from "@/api/acceptance/acceptance";
import { listDrafts, saveDraft, removeDraft, restoreDraft, syncDraftToServer, fetchDraftFromServer, removeDraftFromServer, draftKeyOf } from "@/utils/saleDraft";
import { listCustomerSku } from "@/api/product/customerSku";
import { listTemp } from "@/api/product/temp";
import { queryPrice } from "@/api/price/query";
import { listCustomer } from "@/api/partner/customer";
import { listCustomerDept } from "@/api/partner/customerDept";
import { Refresh, Rank, Plus, Minus, Search, DocumentCopy, Check } from "@element-plus/icons-vue";
import SplitWorkspace from "@/components/SplitWorkspace/index.vue";
import { registerShortcuts, setEnabledByOwner, SCOPE } from "@/utils/shortcut";

import XEUtils from "xe-utils";
import Sortable from "sortablejs";
const orderPage = { path: "/order/sale" };

/**
 * @description: 节流
 * @param {*} delay
 * @param {*} fn
 * @return {*}
 */
function throttle(delay, fn) {
  let firstTime = true
  let timer = null
  return function () {
    const args = [].slice.apply(arguments)
    if (firstTime) {
      fn.apply(this, args)
      firstTime = false
      return
    }
    if (timer) return
    timer = setTimeout(() => {
      fn.apply(this, args)
      clearTimeout(timer)
      timer = null
    }, delay)
  }
}

/** 深拷贝含忽略字段 */
function deepCloneWithoutFields(obj, ignoreFields = []) {
  // 兼容旧浏览器（structuredClone 为 Chrome 98+ API），订单明细为纯 JSON 数据，JSON 克隆等效。
  // 注意：Vue3 响应式对象是 Proxy，structuredClone 会抛 DataCloneError，需回退 JSON。
  let clone
  try {
    clone = typeof structuredClone === 'function' ? structuredClone(obj) : JSON.parse(JSON.stringify(obj))
  } catch (e) {
    clone = JSON.parse(JSON.stringify(obj))
  }

  function removeFields(o) {
    if (Array.isArray(o)) {
      o.forEach(item => removeFields(item));
    } else if (typeof o === 'object' && o !== null) {
      ignoreFields.forEach(field => delete o[field]);
      Object.values(o).forEach(removeFields);
    }
  }

  removeFields(clone);
  return clone;
}

/** 深对象对比(含注释) */
function deepEqual(obj1, obj2, path = '') {
  if (obj1 === obj2) return true; // 相同引用或基本类型相等

  // 检查是否为对象或 null
  if (typeof obj1 !== 'object' || obj1 === null || typeof obj2 !== 'object' || obj2 === null) {
    console.log(`Mismatch at ${path}: Types differ.`, { obj1, obj2 });
    return false;
  }

  const keys1 = Object.keys(obj1);
  const keys2 = Object.keys(obj2);

  // 比较键的数量
  if (keys1.length !== keys2.length) {
    console.log(`Mismatch at ${path}: Key lengths differ.`, { keys1, keys2 });
    return false;
  }

  // 比较每个键的值
  for (let key of keys1) {
    const newPath = path ? `${path}.${key}` : key;

    // 检查键是否存在
    if (!keys2.includes(key)) {
      console.log(`Mismatch at ${newPath}: Key does not exist in obj2.`);
      return false;
    }

    // 深度递归比较
    if (!deepEqual(obj1[key], obj2[key], newPath)) {
      console.log(`Mismatch at ${newPath}: Values do not match.`);
      return false;
    }
  }

  return true;
}

export default {
  name: "SaleDetail",
  components: { SplitWorkspace },
  // 损耗原因固定字典（验收实收差异行必选；OA 差异原因下拉取短收/超收字典）
  dicts: ["biz_loss_reason", "t_sale_order_status", "acceptance_shortfall_reason", "acceptance_overage_reason"],
  setup() {
    return { Refresh, Rank, Plus, Minus, Search, DocumentCopy, Check };
  },
  data() {
    return {
      // 默认订单id
      defaultOrderId: null,
      // 当前加载订单的状态（null=新单；2=已配送进入验收态；3+只读）
      orderStatus: null,
      // 浏览模式：查看历史订单（收起/恢复模型，不覆盖录入工作区）
      browseMode: false,
      browsedOrder: null,
      /* ===== OA 订单验收模式（《订单页一键验收链路设计》） ===== */
      acceptanceMode: false,
      // 当前订单的验收单（acceptance 表行摘要：id/code/status/totalAmount）
      acceptanceInfo: null,
      // 验收日期（一键验收/草稿保存携带）
      accAcceptDate: null,
      // 验收草稿保存中/最近自动保存时间
      accSaving: false,
      accSavedAt: null,
      // 一键验收提交中
      quickAccepting: false,
      // OA 行内变更：加单/换货=插入可编辑行（_accEditing），退=行内确认；不再使用抽屉
      // 撤销验收弹窗
      accRevokeOpen: false,
      accRevokeForm: { reason: null },
      accRevokeRules: {
        reason: [{ required: true, message: "撤销原因不能为空", trigger: "blur" }],
      },
      accRevoking: false,
      // 差异超阈值告警（仅提示不阻断；sys_config 可后续接入）
      accWarnPercent: 20,
      // 浏览前收起的录入工作区快照
      stashedWorkspace: null,
      stashWasEmpty: true,
      // 明细区门禁提示节流标记
      _gateWarnAt: 0,
      // 最近订单查询条件
      recentQuery: {
        customerId: null,
        keyword: null,
        recentDays: 3,
      },
      // 最近订单表格列
      recentTableColumns: [
        { field: "code", title: "订单编号" },
        { field: "deliveryDate", title: "配送时间" },
        { field: "deliveryName", title: "送货单位" },
        { field: "remark", title: "备注" },
      ],
      // 最近订单列表
      recentOrderList: [],
      // 客户列表数据
      customerOptions: [],
      // 已选择的送货单位
      selectedCustomerDepts: [],
      // 送货单位map: <customerDeptId, customerId>
      customerDeptMap: {},
      // 送货单位下拉选项
      customerDeptOptions: [],
      // 送货单位是否禁用
      customerDeptDisabled: false,
      // 订单明细列表
      orderDetailList: [],
      // 原订单明细列表，用于对比
      originalOrderDetailList: [],
      // 订单表单
      orderForm: {
        orderId: null,
        orderCode: null,
        customerId: null,
        customerDeptId: null,
        deliveryDate: null,
        remark: null,
        orderDetails: [],
      },
      // 订单校验
      rules: {
        orderCode: [
          { required: true, message: "订单编号不能为空", trigger: "blur" },
        ],
        customerDeptId: [
          { required: true, message: "送货单位不能为空", trigger: "blur" },
        ],
        deliveryDate: [
          { required: true, message: "送货日期不能为空", trigger: "blur" },
        ],
      },
      // 订单明细表格校验
      validRules: {
        productName: [
          { required: true, message: "商品名称不能为空", trigger: "blur" },
        ],
        productUnit: [
          { required: true, message: "商品单位不能为空", trigger: "blur" },
        ],
        num: [{ required: true, message: "商品数量不能为空", trigger: "blur" }],
        productPrice: [
          { required: true, message: "商品单价不能为空", trigger: "blur" },
        ],
      },
      // 行拖拽
      sortableX: null,
      // 商品报价明细
      skuQuoteDetails: [],
      // 下拉表格数据
      pulldownTableData: [],
      // 下拉表格列配置
      pulldownTableColumn: [
        {
          field: "productName",
          title: "商品名称",
          minWidth: 200,
          slots: { default: "productNameCell" },
        },
        { field: "productUnit", title: "单位" },
        { field: "price", title: "单价" },
        { field: "productSpec", title: "规格" },
        { field: "remark", title: "备注" },
      ],
      // 当前鼠标悬停的行
      currentHoverRow: null,
      // 右侧面板当前 tab（frequent=常用 / recent=最近）
      rightTab: "recent",
      // 常用商品列表（近30天下单频率 Top）
      frequentList: [],
      // 草稿自动保存状态文案 / 类型（saving/saved/idle）
      draftStatusText: "",
      draftStatusType: "",
      // 可恢复的草稿列表（新单页提示恢复）
      availableDrafts: [],
      // 是否显示草稿恢复横幅
      showDraftBanner: false,
      // 商品下拉检索关键词（高亮用）
      pulldownKeyword: "",
      // 表单初始快照（判定是否脏）
      originalOrderForm: null,
      // 每行的大致高度，单位为像素
      rowHeight: 40,
      // 最大显示行数
      maxRows: 15,
      // 是否继续添加订单
      isContinueAdd: true,
      // 编辑已有草稿订单后的提示横幅（点错了？返回重开新单）
      showEditExistingBanner: false,
      // 当前正在编辑的草稿订单ID（用于重开新单后清空）
      existingDraftOrderId: null,
      /* ========== S1 订单桌面工作台 ========== */
      // 右侧搜索面板
      searchKeyword: "",
      searchResults: [],
      // S1-1.3 手工定价：已去除必填原因弹窗（简化录价），仅保留价格来源/原建议价/操作者时间的服务端审计
      // 草稿箱弹窗（S1-1.5）
      draftBoxVisible: false,
      // 按客户批量确认（草稿 → 已确认）
      batchConfirmVisible: false,
      batchConfirmLoading: false,
      batchConfirmSubmitting: false,
      batchConfirmCustomerId: null,
      batchConfirmDeliveryDate: null,
      batchConfirmRows: [],
      batchConfirmSelection: [],
      // 配送日期变更提示（S1-1.4）用的旧值缓存
      _prevDeliveryDate: null,
      // 撤销栈（S1-1.2 撤销最近编辑）：每次进入单元格编辑/结构性变更前推入快照
      undoStack: [],
    };
  },
  mounted() {
    // 对添加行事件的加入节流处理, 150毫秒内多次触发只会执行一次
    this.throttledAddRow = throttle(150, this.handleAddRow.bind(this));
    // 初始加载标记：initOrderDetailPage 完成前置 false，完成后置 true
    // 用于区分 handleAddRow 是初始化调用还是用户手动操作
    this._initialLoadDone = false;

    // 组件挂载完成后添加一行
    this.throttledAddRow();
    this.rowDrop();

    // 页面刷新/关闭前立即保存草稿（配合 5s 防抖自动保存）
    window.addEventListener("beforeunload", this.handleBeforeUnload);
    // 全局搜索 Jump 前立即落盘草稿（Phase 3 联动）
    window.addEventListener("sale-draft-flush", this.handleBeforeUnload);

    // S1-1.2 键盘焦点流与高频命令（集中注册，遵守 docs/快捷键规范与冲突表.md 准入检查）
    // 事件归属护栏：页面刚加载焦点在 body（未落入任何页面 DOM）时放行；非本页 DOM 事件返回 false 下传。
    // keep-alive 失活页由 activated/deactivated 钩子整体启停（见下方钩子），双保险。
    const own = (e) => {
      const t = e.target;
      return !t || t === document.body || !this.$el || this.$el.contains(t);
    };
    this._unregShortcuts = registerShortcuts([
      {
        scope: SCOPE.WORKSPACE,
        key: "f2",
        owner: "SaleOrderDetail",
        description: "插入空行（当前行下方）",
        handler: (e) => {
          if (!own(e) || !this.canEditOrder) return false;
          this.insertRowAtActive();
        },
      },
      {
        scope: SCOPE.WORKSPACE,
        key: "f3",
        owner: "SaleOrderDetail",
        description: "切到右侧搜索面板并聚焦",
        handler: (e) => {
          if (!own(e)) return false;
          this.focusSearchPanel();
        },
      },
      {
        scope: SCOPE.WORKSPACE,
        key: "f4",
        owner: "SaleOrderDetail",
        description: "重复上一行（当前行复制上一行商品）",
        handler: (e) => {
          if (!own(e) || !this.canEditOrder) return false;
          this.repeatPrevRow();
        },
      },
      {
        scope: SCOPE.TABLE,
        key: "ctrl+d",
        owner: "SaleOrderDetail",
        description: "复制当前行",
        handler: (e) => {
          if (!own(e) || !this.canEditOrder) return false;
          this.copyActiveRow();
        },
      },
      {
        scope: SCOPE.TABLE,
        key: "ctrl+z",
        owner: "SaleOrderDetail",
        description: "撤销最近一次编辑",
        allowInInput: true,
        handler: (e) => {
          if (!own(e) || !this.canEditOrder || !this.undoStack.length) return false;
          this.undoLastEdit();
        },
      },
      {
        scope: SCOPE.TABLE,
        key: "delete",
        owner: "SaleOrderDetail",
        description: "删除当前激活行（无激活行则下传）",
        handler: (e) => {
          if (!own(e) || !this.canEditOrder) return false;
          const cell = this.$refs.xTable && this.$refs.xTable.getEditCell();
          if (!cell || !cell.row) return false;
          this.handleRemoveRow(cell.row);
        },
      },
      {
        scope: SCOPE.TABLE,
        key: "ctrl+s",
        owner: "SaleOrderDetail",
        description: "保存订单",
        handler: (e) => {
          if (!own(e)) return false;
          if (!this.canEditOrder) return false;
          this.submitForm();
        },
      },
      {
        scope: SCOPE.TABLE,
        key: "enter",
        owner: "SaleOrderDetail",
        description: "焦点流：数量→单价→下一行商品名",
        allowInInput: true,
        handler: (e) => {
          if (!own(e) || !this.canEditOrder) return false;
          return this.handleEnterFocusFlow();
        },
      },
    ]);

    // S1-1.2 批量粘贴：表格容器上的原生 paste 事件（非 keydown 快捷键，不进冲突表）
    const tableContainer = this.$el.querySelector(".order-table-container");
    if (tableContainer) {
      this._pasteHandler = (e) => this.handleTablePaste(e);
      tableContainer.addEventListener("paste", this._pasteHandler);
    }
  },
  activated() {
    // keep-alive 失活页不响应快捷键（蓝图：仅当前活动工作区响应）
    setEnabledByOwner("SaleOrderDetail", true);
  },
  deactivated() {
    setEnabledByOwner("SaleOrderDetail", false);
  },
  beforeUnmount() {
    window.removeEventListener("beforeunload", this.handleBeforeUnload);
    window.removeEventListener("sale-draft-flush", this.handleBeforeUnload);
    if (this._unregShortcuts) {
      this._unregShortcuts();
      this._unregShortcuts = null;
    }
    const tableContainer = this.$el && this.$el.querySelector(".order-table-container");
    if (tableContainer && this._pasteHandler) {
      tableContainer.removeEventListener("paste", this._pasteHandler);
      this._pasteHandler = null;
    }
    if (this._draftTimer) clearTimeout(this._draftTimer);
    if (this.sortableX) {
      this.sortableX.destroy();
    }
  },
  computed: {
    /** 批量确认抽屉：所选草稿订单合计金额 */
    batchConfirmAmountTotal() {
      return (this.batchConfirmSelection || [])
        .reduce((sum, o) => sum + Number(o.amount || 0), 0)
        .toFixed(2);
    },
    // 计算表格容器的高度
    tableHeight() {
      const headerFooterHeight = 120; // 头部和底部的高度总和，可以根据实际情况调整
      const calculatedHeight = this.maxRows * this.rowHeight + "px";
      const viewportHeight = `calc(100vh - ${headerFooterHeight}px)`;

      // 返回较小的那个值作为表格容器的最大高度
      return `min(${calculatedHeight}, ${viewportHeight})`;
    },
    // 计算最近订单表格容器的高度
    recentTableHeight() {
      const headerFooterHeight = 120; // 头部和底部的高度总和，可以根据实际情况调整
      const calculatedHeight = 17 * this.rowHeight + "px";
      const viewportHeight = `calc(100vh - ${headerFooterHeight}px)`;

      // 返回较小的那个值作为表格容器的最大高度
      return `min(${calculatedHeight}, ${viewportHeight})`;
    },
    // 计算表格内部的高度
    tableInnerHeight() {
      // 固定15行的高度
      return `${this.maxRows * this.rowHeight}px`;
    },
    // 订单是否有未保存改动（明细对比 + 表单字段对比）
    isOrderDirty() {
      if (this.checkTableUpdted()) return true;
      if (!this.originalOrderForm) return false;
      const { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark } = this.orderForm;
      const cur = { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark };
      return JSON.stringify(cur) !== JSON.stringify(this.originalOrderForm);
    },
    /* ========== 录单页交互细化（客户锁定 / 浏览模式 / 验收态） ========== */
    /** OA：验收模式只读判定 = 验收单已提交（此时差异/实收/换退加全部锁定） */
    acceptanceReadonly() {
      return !!this.acceptanceInfo && Number(this.acceptanceInfo.status) === 1;
    },
    /** OA：差异原因下拉选项（短收+超收字典合并，行差异方向由占位提示与后端校验引导） */
    acceptReasonOptions() {
      const merged = [
        ...(this.dict.type.acceptance_shortfall_reason || []),
        ...(this.dict.type.acceptance_overage_reason || []),
      ];
      return merged.map((d) => ({ label: d.label, value: d.value }));
    },
    /** OA：一键验收预览汇总（应收金额合计 / 差异行数与金额 / 实收合计） */
    accSummary() {
      let orderAmount = 0;
      let actualAmount = 0;
      let diffRows = 0;
      let diffAmount = 0;
      (this.orderDetailList || []).forEach((row) => {
        const num = XEUtils.toNumber(row.num);
        const price = XEUtils.toNumber(row.productPrice);
        const actual = row.acceptActual == null ? num : XEUtils.toNumber(row.acceptActual);
        orderAmount += num * price;
        actualAmount += actual * price;
        if (Math.round((actual - num) * 100) / 100 !== 0) {
          diffRows += 1;
          diffAmount += (actual - num) * price;
        }
      });
      const r2 = (v) => Math.round(v * 100) / 100;
      return { orderAmount: r2(orderAmount), actualAmount: r2(actualAmount), diffRows, diffAmount: r2(diffAmount) };
    },
    /** 客户+配送点整体锁定：编辑已有单、浏览态；或新单已录入商品后（未录商品前允许直接改选配送点） */
    customerDeptLocked() {
      if (this.browseMode || !!this.orderForm.orderId) return true;
      return !!this.orderForm.customerDeptId && this.hasDetailContent;
    },
    /** 重开新单入口：新单录入中且已因录入商品而锁定 */
    showReopenNewBtn() {
      return !this.browseMode && !this.orderForm.orderId && !!this.orderForm.customerDeptId && this.hasDetailContent;
    },
    /** 是否处于可编辑的录单态（非浏览、非只读、非验收模式）；实收编辑已下线（C1：数据归验收单） */
    canEditOrder() {
      return !this.browseMode && !this.viewOnlyMode && !this.acceptanceMode;
    },
    /** 订单信息是否只读（浏览/只读查看模式下，表头信息不可修改） */
    orderInfoReadonly() {
      return !this.canEditOrder;
    },
    /** 只读查看：已验收/已结算订单 */
    viewOnlyMode() {
      return !this.browseMode && !!this.orderForm.orderId && this.orderStatus != null && this.orderStatus >= 3;
    },
    /** 冻结提示条：已配送及之后状态的订单，明细为生成送货单时的快照（验收模式由验收横幅接管） */
    frozenBannerVisible() {
      return (
        !this.browseMode &&
        !this.acceptanceMode &&
        !!this.orderForm.orderId &&
        this.orderStatus != null &&
        this.orderStatus >= 2
      );
    },
    /** 实收列展示：订单状态≥已配送 */
    showActualColumns() {
      return (
        (!this.browseMode && !!this.orderForm.orderId && this.orderStatus != null && this.orderStatus >= 2) ||
        (this.browseMode && !!this.browsedOrder && Number(this.browsedOrder.status) >= 2)
      );
    },
    /** 有效商品行：商品名+单位齐全 且 数量>0（页面默认空白行不算） */
    hasValidProductRow() {
      return (this.orderDetailList || []).some(
        (row) => row.productName && row.productUnit && XEUtils.toNumber(row.num) > 0
      );
    },
    /** 明细已录入内容：任一行有商品/数量/单价/备注（默认空白行不算），用于配送点锁定判定 */
    hasDetailContent() {
      return (this.orderDetailList || []).some(
        (row) =>
          row.productName ||
          row.remark ||
          XEUtils.toNumber(row.num) > 0 ||
          XEUtils.toNumber(row.productPrice) > 0
      );
    },
    /** 空工作区判定（§3.6）：没有有效商品行 或 没有选配送点，任一满足即视为空 */
    isWorkspaceEmpty() {
      return !this.hasValidProductRow || !this.orderForm.customerDeptId;
    },
    /** 复制来源单：浏览态=正在看的历史单；编辑/只读态=当前已保存的单；空白新单无来源 */
    copySourceOrder() {
      if (this.browseMode) return this.browsedOrder;
      if (this.orderForm.orderId) {
        return {
          id: this.orderForm.orderId,
          customerId: this.orderForm.customerId,
          customerDeptId: this.orderForm.customerDeptId,
        };
      }
      return null;
    },
    /** 复制为新单准入：只要当前打开着一张已保存的单（含已验收/已结算的只读历史单）就能照着重录 */
    canCopyAsNew() {
      return !!this.copySourceOrder;
    },
    /* ========== S1 订单桌面工作台 ========== */
    /** 草稿状态悬浮说明（S1-1.5 可解释性） */
    draftTipText() {
      if (this.draftStatusType === "saving") return "检测到改动，5 秒内自动保存草稿（本地 + 服务器双写）";
      if (this.draftStatusType === "saved") return "草稿已自动保存（本地 + 服务器双写）；提交订单成功后自动清除";
      return "当前改动不足以生成草稿（需先选送货单位并录入商品）";
    },
  },
  created() {
    // 从路由获取参数
    const orderIdFromParams = this.$route.query.orderId;
    this.defaultOrderId = orderIdFromParams
      ? parseInt(orderIdFromParams, 10)
      : null;

    // OA：验收模式入口（订单列表「去验收」→ /order/sale-detail/index?mode=acceptance&orderId=xxx[&acceptanceId=yyy]）
    if (this.$route.query.mode === "acceptance" && this.defaultOrderId) {
      this.acceptanceMode = true;
    }

    // 初始化数据
    this.getTreeselect();
    this.getCustomerList();
    const pageReady = this.initOrderDetailPage(this.defaultOrderId);

    // 草稿：列表页跳转（query.draft）自动恢复；新单页展示恢复横幅
    if (!this.acceptanceMode) {
      this.checkDraftOnEnter();
    } else {
      // OA：等订单明细载入完成后定位/建验收草稿并合并实收行
      Promise.resolve(pageReady).then(() => this.enterAcceptanceMode());
    }
  },
  watch: {
    orderForm: {
      handler() {
        this.scheduleDraftSave();
      },
      deep: true,
    },
    orderDetailList: {
      handler() {
        if (this.acceptanceMode) {
          this.scheduleAccAutoSave();
          return;
        }
        this.scheduleDraftSave();
      },
      deep: true,
    },
    "orderForm.customerId"(val) {
      this.loadFrequentProducts();
    },
    "orderForm.customerDeptId"() {
      // 送货单位变化：常用商品按新配送点白名单重新过滤
      this.loadFrequentProducts();
    },
    "orderForm.deliveryDate"(val, oldVal) {
      // S1-1.4 头字段变更提示：配送日期影响取价基准与商品池；已录明细保留但单价快照不自动刷新
      if (!val || !oldVal || val === oldVal) return;
      // 用户取消后的程序性还原不再二次确认（防死循环）
      if (this._revertingDeliveryDate) {
        this._revertingDeliveryDate = false;
        return;
      }
      if (this.browseMode || !this.canEditOrder) return;
      if (!this.hasDetailContent) return;
      this.$modal
        .confirm(
          "配送日期已从 " + oldVal + " 改为 " + val + "：将影响后续取价基准，已录入明细的单价快照不会自动刷新（如需刷新请重新选择商品）。是否确认修改？"
        )
        .catch(() => {
          this._revertingDeliveryDate = true;
          this.orderForm.deliveryDate = oldVal;
        });
    },
  },
  methods: {
    /** 查询最近订单列表 */
    handleRecentQuery() {
      recentSaleOrder(this.recentQuery).then((response) => {
        this.recentOrderList = response.data;
      });
    },
    /** 查询客户列表 */
    /** 按客户批量确认：打开抽屉（默认带出当前表单客户与配送日期） */
    openBatchConfirmDrawer() {
      this.batchConfirmCustomerId = this.orderForm.customerId || null;
      this.batchConfirmDeliveryDate = this.orderForm.deliveryDate || null;
      this.batchConfirmRows = [];
      this.batchConfirmSelection = [];
      this.batchConfirmVisible = true;
      this.queryBatchConfirmList();
    },
    /** 查询该客户（可限配送日期）的草稿订单（status=0，后端列表已排除已删除） */
    queryBatchConfirmList() {
      if (!this.batchConfirmCustomerId) {
        this.$modal.msgWarning("请先选择客户");
        this.batchConfirmRows = [];
        return;
      }
      this.batchConfirmLoading = true;
      listSale({
        customerId: this.batchConfirmCustomerId,
        status: 0,
        deliveryDate: this.batchConfirmDeliveryDate || undefined,
      })
        .then((response) => {
          this.batchConfirmRows = response.data || [];
          this.batchConfirmSelection = [];
        })
        .finally(() => {
          this.batchConfirmLoading = false;
        });
    },
    /** 批量确认所选草稿订单（后端要求全部为制单状态，确认后进入客户日总表与验收链路） */
    submitBatchConfirm() {
      const orderIds = this.batchConfirmSelection.map((o) => o.id);
      if (!orderIds.length) {
        return;
      }
      this.$modal
        .confirm(
          "将把选中的 " +
            orderIds.length +
            " 条草稿订单确认为「已确认」，确认后订单不可再修改，是否继续？"
        )
        .then(() => {
          this.batchConfirmSubmitting = true;
          return updateOrderStatus({ orderIds, status: 1 });
        })
        .then(() => {
          this.$modal.msgSuccess("已确认 " + orderIds.length + " 条订单，可在「单据管理 → 客户日总表」查看与打印");
          // 若当前正在编辑的订单也在其中，同步状态避免页面残留旧状态
          if (this.orderForm.orderId && orderIds.includes(this.orderForm.orderId)) {
            this.orderStatus = 1;
          }
          this.queryBatchConfirmList();
          this.handleRecentQuery();
        })
        .catch(() => {})
        .finally(() => {
          this.batchConfirmSubmitting = false;
        });
    },
    getCustomerList() {
      listCustomer().then((response) => {
        this.customerOptions = response.data;
      });
    },
    /** 重置查询条件 */
    resetQuery() {
      this.resetForm("recentOrderForm");
      this.handleRecentQuery();
    },
    /** 获取当前客户的商品选项列表（客户商品池按配送点白名单过滤+临时商品） */
    async getSkuQuoteDetailList(keyword) {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.skuQuoteDetails = [];
        return;
      }
      const [poolRes, tempRes] = await Promise.all([
        listCustomerSku({
          customerId,
          deliveryPointId: this.orderForm.customerDeptId || undefined,
          keyword: keyword || undefined,
        }),
        listTemp({ customerId, name: keyword || undefined }),
      ]);
      // 客户商品池：已按配送点白名单过滤（通用池∪本点专属），别名/展示价取自池条目，交易价仍走取价引擎
      const pool = (poolRes.data || []).map((item) => ({
        skuId: item.skuId,
        productName: item.alias || item.skuName,
        productUnit: item.skuUnit,
        productSpec: item.skuSpecName || "",
        productMnemonicCode: item.skuMnemonicCode || "",
        price: item.priceOverride != null ? item.priceOverride : item.skuSalePrice,
        isTemp: false,
        sourceTag: item.alias && item.alias !== item.skuName ? "别名" : "",
        remark: "",
      }));
      // 临时商品：全局 + 客户专用（未转正）
      const temps = (tempRes.data || []).map((item) => ({
        skuId: null,
        productName: item.name,
        productUnit: item.unit || "斤",
        productSpec: item.spec || "",
        productMnemonicCode: "",
        price: item.defaultPrice,
        isTemp: true,
        sourceTag: "临时",
        remark: item.remark || "",
      }));
      this.skuQuoteDetails = [...pool, ...temps].map((item) => ({
        ...item,
        price: XEUtils.commafy(item.price == null ? 0 : item.price, { digits: 2 }),
      }));
    },
    /** 初始化订单明细页 */
    initOrderDetailPage(orderId) {
      // 重置浏览模式与订单状态标记（acceptanceMode 由 created 设置，此处不清除）
      this.browseMode = false;
      this.browsedOrder = null;
      this.stashedWorkspace = null;
      this.orderStatus = null;
      // 页面就绪 Promise：验收模式需等订单明细载入后再合并验收行
      let pagePromise = Promise.resolve();
      if (orderId) {
        // 获取当前 order 信息
        pagePromise = getSaleOrder(orderId)
          .then((response) => {
            const orderData = response.data;
            this.orderForm = {
              ...orderData,
              orderId: orderData.id,
              orderCode: orderData.code,
            };
            // 记录状态：2=已配送（验收态），≥3 只读
            this.orderStatus = orderData.status != null ? Number(orderData.status) : null;
            // 初始化送货单位下拉列表
            this.customerDeptDisabled = true;
            this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              this.orderForm.customerDeptId.toString()
            );
          })
          .then(() => {
            // 初始化订单表格
            listSaleDetail({ orderId: orderId }).then((response) => {
              const responseOrderDetails = response.data.map((item) => {
                const row = {
                  ...item,
                  productPrice: XEUtils.commafy(item.productPrice, {
                    digits: 2,
                  }),
                  num: XEUtils.commafy(item.num, {
                    digits: 2,
                  }),
                  amount: XEUtils.commafy(item.expectAmount, {
                    digits: 2,
                  }),
                };
                // 实收列：已配送及之后状态展示
                if (item.actualNum !== null && item.actualNum !== undefined) {
                  row.actualNum = XEUtils.commafy(item.actualNum, { digits: 2 });
                }
                return row;
              });
              this.orderDetailList =
                this.deepCloneOrderDetailList(responseOrderDetails);
              this.originalOrderDetailList =
                this.deepCloneOrderDetailList(responseOrderDetails);
              this.snapshotOriginalOrderForm();
              this.draftStatusText = "";

              // 初始化下拉列表
              this.getSkuQuoteDetailList();
            });
          });
      } else {
        // 初始化 orderForm
        this.orderForm = {
          orderId: null,
          orderCode: null,
          customerId: null,
          customerDeptId: null,
          deliveryDate: null,
          remark: null,
        };
        pagePromise = genOrderCode({ refresh: true })
          .then((response) => {
            // 初始化订单编号
            this.orderForm.orderCode = response.msg;
          })
          .then(() => {
            // 初始化送货单位下拉列表
            this.customerDeptDisabled = false;
            this.selectedCustomerDepts = [];

            // 初始化送货日期
            const today = new Date();
            const nowHour = today.getHours();
            const tomorrow = new Date(today);
            tomorrow.setDate(today.getDate() + 1);
            const formatDay = (d) => {
              const y = d.getFullYear();
              const m = String(d.getMonth() + 1).padStart(2, "0");
              const day = String(d.getDate()).padStart(2, "0");
              return `${y}-${m}-${day}`;
            };

            // 若当前时间小于15点，则送货时间为今天，否则为明天
            const deliveryDate = nowHour < 15 ? formatDay(today) : formatDay(tomorrow);
            this.orderForm.deliveryDate = deliveryDate;

            // 初始化订单表格
            this.orderDetailList = [];
            this.handleAddRow();
            this.originalOrderDetailList = this.deepCloneOrderDetailList(
              this.orderDetailList
            );
            this.snapshotOriginalOrderForm();
            this.draftStatusText = "";

            // 初始化下拉列表
            this.getSkuQuoteDetailList();
          });
      }
      // 初始化最近订单列表
      this.handleRecentQuery();
      return pagePromise;
    },
    /** 刷新订单编号（补传 currentCode：相同则保持，被占用才 +1，避免浪费号段） */
    refreshOrderCode() {
      genOrderCode({ refresh: true, currentCode: this.orderForm.orderCode || undefined }).then(
        (response) => {
          this.orderForm.orderCode = response.msg;
        }
      );
    },
    /** 重置订单表单 */
    resetOrderForm() {
      const orderId = this.isContinueAdd ? null : this.orderForm.orderId;
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认重置？")
          .then(() => {
            this.initOrderDetailPage(orderId);
          })
          .catch(() => {});
      } else {
        this.initOrderDetailPage(orderId);
      }
    },
    /** 保存订单信息 */
    submitForm() {
      this.$refs["orderForm"].validate((valid) => {
        if (valid) {
          // set orderDetails
          if (this.orderDetailList.length == 0) {
            this.$modal.msgError("订单明细列表不能为空！");
            return;
          }
          // 去除空行，保留 productName, productUnit 都不为空，num > 0 的数据
          this.orderForm.orderDetails = this.orderDetailList.filter((item) => {
            // 判断是否为有效的商品数量
            let productNum = XEUtils.toNumber(item.num);
            const isValidNum =
              !isNaN(productNum) && productNum && productNum > 0;
            return item.productName && item.productUnit && isValidNum;
          });

          // 格式化金额
          this.orderForm.orderDetails = this.orderForm.orderDetails.map(
            (item) => {
              return {
                ...item,
                num: XEUtils.toNumber(item.num, /,/g, ".", 0),
                productPrice: XEUtils.toNumber(item.productPrice, /,/g, ".", 0),
                amount: XEUtils.toNumber(item.amount, /,/g, ".", 0),
              };
            }
          );

          // 校验订单总金额：为 0 不允许保存
          const totalAmount = this.orderForm.orderDetails.reduce(
            (sum, item) => sum + (XEUtils.toNumber(item.amount) || 0),
            0
          );
          if (totalAmount <= 0) {
            this.$modal.msgWarning(
              "订单金额为 0，不允许保存，请检查商品单价或数量"
            );
            return;
          }

          // S1-1.3 手工定价：已去除必填原因弹窗与二次确认，改价即生效；来源/原价/操作者时间由服务端确认时审计
          const proceedSave = Promise.resolve();
          proceedSave.then(() => {
          if (this.orderForm.orderId) {
            updateSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("修改成功，草稿已清除");
                // 已保存订单：清除对应草稿（本地 + 后端）
                removeDraft(draftKeyOf(this.orderForm.orderId, null));
                removeDraftFromServer(this.orderForm.orderId, this.orderForm.customerDeptId);
                this.refreshDraftBanner();
                const orderId = this.isContinueAdd
                  ? null
                  : this.orderForm.orderId;
                this.initOrderDetailPage(orderId);
              }
            });
          } else {
            createSaleOrder(this.orderForm).then((response) => {
              if (response.code === 200) {
                this.$modal.msgSuccess("新增成功，草稿已清除");
                // 新单已保存：清除对应草稿（本地 + 后端）
                removeDraft(draftKeyOf(null, this.orderForm.customerDeptId));
                removeDraftFromServer(null, this.orderForm.customerDeptId);
                this.refreshDraftBanner();
                const orderId = this.isContinueAdd ? null : response.data.id;
                this.initOrderDetailPage(orderId);
              }
            });
          }
          });
        }
      });
    },
    /** 返回按钮 */
    close() {
      const isUpdated = this.checkTableUpdted();
      if (isUpdated) {
        this.$modal
          .confirm("当前订单明细有改动，是否确认关闭？")
          .then(() => {
            this.$tab.closeOpenPage(orderPage);
          })
          .catch(() => {});
      } else {
        this.$tab.closeOpenPage(orderPage);
      }
    },

    /* ==============================================================
     * OA：订单维度验收（《订单页一键验收链路设计》）
     * 一订单一验：订单明细页验收模式；左侧下单快照只读，
     * 实收默认=下单数量，行级换/退/加同 D-055 配送后变更链路，
     * 一键验收 = 全部实收与下单一致直接提交（后端 quick-accept 原子完成）。
     * ============================================================== */

    /** OA：进入验收模式（created 中已置 acceptanceMode；此处定位/建草稿并合并验收行） */
    enterAcceptanceMode() {
      const orderId = this.defaultOrderId;
      if (!orderId) {
        return Promise.resolve();
      }
      // 已带 acceptanceId（订单列表定位过）→ 直取；否则建/同步草稿（后端幂等）
      const acceptanceId = this.$route.query.acceptanceId;
      const fetchAcc = acceptanceId
        ? getAcceptance(Number(acceptanceId)).then((resp) => {
            const acc = resp.data || null;
            // 归属校验：验收单不属于该订单时退回建/同步链路
            return acc && Number(acc.saleOrderId) === Number(orderId) ? acc : null;
          })
        : Promise.resolve(null);
      return fetchAcc
        .then((acc) => (acc ? acc : createAcceptanceByOrder(orderId).then((resp) => resp.data || null)))
        .then((acc) => {
          this.acceptanceInfo = acc
            ? {
                id: acc.id,
                code: acc.code,
                status: acc.status,
                totalAmount: acc.totalAmount,
                remark: acc.remark,
              }
            : null;
          this.accAcceptDate =
            acc && acc.acceptDate
              ? String(acc.acceptDate).slice(0, 10)
              : this.orderForm.deliveryDate || null;
          return this.applyAcceptanceItems();
        })
        .catch(() => {
          // 建单失败（订单未送达/无可验收明细等，后端已提示）：退回订单列表
          this.acceptanceMode = false;
          this.acceptanceInfo = null;
          this.$tab.closeOpenPage(orderPage);
        });
    },

    /** OA：验收行合并进明细行（acceptDetailId/acceptActual/acceptReason；缺行时同步验收单） */
    applyAcceptanceItems() {
      const acc = this.acceptanceInfo;
      if (!acc) {
        return Promise.resolve();
      }
      return listAcceptanceItems(acc.id)
        .then((resp) => {
          const items = resp.data || [];
          const byDetailId = {};
          items.forEach((it) => {
            if (it.saleOrderDetailId != null) {
              byDetailId[String(it.saleOrderDetailId)] = it;
            }
          });
          let missing = 0;
          (this.orderDetailList || []).forEach((row) => {
            const it = byDetailId[String(row.id)];
            const orderNum = XEUtils.toNumber(row.num);
            row.deliveredQuantity = it ? Number(it.deliveredQuantity || 0) : orderNum;
            row.acceptDetailId = it ? it.id : null;
            row.acceptActual = it ? Number(it.actualQuantity || 0) : orderNum;
            row.acceptReason = it ? it.lossReason || null : null;
            if (!it) {
              missing += 1;
            }
          });
          // 订单明细存在验收单缺失行（验收中途加单/换货/退货）→ 同步缺失行后重拉一次
          if (missing > 0 && !this.acceptanceReadonly) {
            return createAcceptanceByOrder(orderId)
              .then(() => listAcceptanceItems(acc.id))
              .then((again) => {
                (again.data || []).forEach((it) => {
                  if (it.saleOrderDetailId == null) return;
                  const row = (this.orderDetailList || []).find(
                    (r) => String(r.id) === String(it.saleOrderDetailId)
                  );
                  if (row && !row.acceptDetailId) {
                    row.acceptDetailId = it.id;
                    row.acceptActual = Number(it.actualQuantity || 0);
                    row.acceptReason = it.lossReason || null;
                    row.deliveredQuantity = Number(it.deliveredQuantity || 0);
                  }
                });
                this.refreshAcceptanceInfo();
              })
              .catch(() => {});
          }
          return null;
        })
        .then(() => {
          // 合并完成后的快照参与关闭页脏检查，避免误报「有改动」
          this.originalOrderDetailList = this.deepCloneOrderDetailList(
            this.orderDetailList
          );
        });
    },

    /** OA：重取验收单表头（总额/状态，行内保存或同步后刷新） */
    refreshAcceptanceInfo() {
      if (!this.acceptanceInfo) return;
      getAcceptance(this.acceptanceInfo.id).then((resp) => {
        const acc = resp.data;
        if (acc) {
          this.acceptanceInfo = {
            id: acc.id,
            code: acc.code,
            status: acc.status,
            totalAmount: acc.totalAmount,
            remark: acc.remark,
          };
        }
      });
    },

    /** OA：差异=实收−下单（退货行 delivered=0，差异仅随实收变动） */
    accDiffOf(row) {
      const actual = row.acceptActual == null ? 0 : Number(row.acceptActual);
      const delivered =
        row.deliveredQuantity != null ? Number(row.deliveredQuantity) : XEUtils.toNumber(row.num);
      return Math.round((actual - delivered) * 100) / 100;
    },
    /** OA：差异单元格配色（负=短收红，正=超收橙） */
    accDiffClass(row) {
      const diff = this.accDiffOf(row);
      if (diff < 0) return "acc-diff-short";
      if (diff > 0) return "acc-diff-over";
      return "";
    },
    /** OA：差异行高亮（含超阈值强化） */
    accRowClass({ row }) {
      const diff = this.accDiffOf(row);
      if (diff === 0) return "";
      return this.accDiffOver(row) ? "acc-row-diff-over" : "acc-row-diff";
    },
    /** OA：差异是否超阈值（默认 ±20%，仅提示不阻断） */
    accDiffOver(row) {
      const num = XEUtils.toNumber(row.num);
      if (num <= 0) return false;
      return Math.abs(this.accDiffOf(row)) / num > this.accWarnPercent / 100;
    },

    /** OA：实收金额 = 实收数量 × 下单单价（随录入实时联动） */
    accAmountOf(row) {
      const price = XEUtils.toNumber(row.productPrice);
      const actual = row.acceptActual == null || row.acceptActual === "" ? 0 : Number(row.acceptActual);
      return Math.round(price * actual * 100) / 100;
    },
    /** OA：实收编辑后 2s 防抖自动保存草稿（仅草稿态；提交态不写）；方向键/回车单元格导航由 vxe keyboard-config（isArrow）原生承担，同录单 */
    scheduleAccAutoSave() {
      if (!this.acceptanceMode || this.acceptanceReadonly) return;
      if (this._accSaveTimer) clearTimeout(this._accSaveTimer);
      this._accSaveTimer = setTimeout(() => {
        this._accSaveTimer = null;
        this.saveAcceptanceDraft(true);
      }, 2000);
    },

    /** OA：保存验收草稿（差异原因选填，OA 定稿 2026-09-09；silent=自动保存） */
    saveAcceptanceDraft(silent) {
      const acc = this.acceptanceInfo;
      if (!acc || this.acceptanceReadonly) return Promise.resolve();
      if (!silent && !this.ensureNoAccEditingRow()) return Promise.resolve();
      const items = (this.orderDetailList || [])
        .filter((row) => row.acceptDetailId != null)
        .map((row) => ({
          id: row.acceptDetailId,
          actualQuantity: row.acceptActual == null || row.acceptActual === "" ? 0 : Number(row.acceptActual),
          lossReason: this.accDiffOf(row) === 0 ? null : row.acceptReason || null,
        }));
      this.accSaving = true;
      return updateAcceptance({
        id: acc.id,
        acceptDate: this.accAcceptDate,
        remark: acc.remark,
        items,
      })
        .then(() => {
          this.accSavedAt = new Date().toTimeString().slice(0, 5);
          this.refreshAcceptanceInfo();
          if (!silent) {
            this.$modal.msgSuccess("验收草稿已保存");
          }
        })
        .catch(() => {})
        .finally(() => {
          this.accSaving = false;
        });
    },

    /** OA：一键验收（全部实收=下单数量+金额批量确认；差异原因选填，OA 定稿） */
    handleQuickAccept() {
      if (this.acceptanceReadonly) return;
      if (!this.ensureNoAccEditingRow()) return;
      const rows = this.orderDetailList || [];
      const diffs = rows.filter((row) => this.accDiffOf(row) !== 0);
      const overRows = diffs.filter((row) => this.accDiffOver(row));
      const doConfirm = () => {
        const s = this.accSummary;
        this.$modal
          .confirm(
            `应收 ${rows.length} 行 / ¥${s.orderAmount.toFixed(2)}，实收 ${rows.length} 行 / ¥${s.actualAmount.toFixed(2)}，` +
              `差异 ${s.diffRows} 行 / ¥${s.diffAmount.toFixed(2)}。确认按订单数量和金额提交验收？（提交后如需修改请走撤销）`
          )
          .then(() => {
            this.quickAccepting = true;
            return quickAcceptOrder({
              orderId: Number(this.defaultOrderId),
              acceptDate: this.accAcceptDate || undefined,
              items: diffs.map((row) => ({
                saleOrderDetailId: Number(row.id),
                actualQuantity: row.acceptActual == null ? 0 : Number(row.acceptActual),
                lossReason: row.acceptReason || null,
              })),
            });
          })
          .then((resp) => {
            const acc = resp.data || {};
            this.$modal.msgSuccess(
              "订单已验收" + (acc.code ? "，验收单【" + acc.code + "】" : "")
            );
            this.acceptanceInfo = acc.id
              ? { id: acc.id, code: acc.code, status: acc.status, totalAmount: acc.totalAmount, remark: acc.remark }
              : this.acceptanceInfo;
            this.orderStatus = 3; // ACCEPTED：整页转已验收只读
            return this.applyAcceptanceItems();
          })
          .catch(() => {})
          .finally(() => {
            this.quickAccepting = false;
          });
      };
      if (overRows.length) {
        this.$modal
          .confirm(
            `有 ${overRows.length} 行差异超过 ±${this.accWarnPercent}%（如：${overRows[0].productName}），确认继续？`
          )
          .then(doConfirm)
          .catch(() => {});
      } else {
        doConfirm();
      }
    },

    /** OA：刷新表尾合计（vxe 表尾不随行内单元格变化自动重渲染） */
    refreshAccFooter() {
      this.$nextTick(() => {
        const t = this.$refs.xTable;
        t && t.updateFooter && t.updateFooter();
      });
    },

    /** 数量编辑收口（录单+验收模式共用）：重算金额；验收模式编辑行实收数量跟随同步 */
    onNumEdited(row) {
      this.calcAmount(row);
      if (row._accEditing) {
        const n = XEUtils.toNumber(row.num);
        row.acceptActual = Math.round(n * 100) / 100;
        this.refreshAccFooter();
      }
    },

    /** OA：实收编辑完成（vxe 编辑态同录单交互）：归一两位小数 + 刷新表尾 + 防抖自动保存 */
    onAccActualEdited(row) {
      const n = parseFloat(row.acceptActual);
      row.acceptActual = isNaN(n) ? 0 : Math.round(n * 100) / 100;
      this.refreshAccFooter();
      this.scheduleAccAutoSave();
    },

    /**
     * OA：行内变更——加单（type=1）：在表尾插入编辑行并直接激活商品名单元格，
     * 激活即弹出报价选择面板（与录单页同一套 vxe-pulldown 交互），选中后自动取价。
     */
    /**
     * OA：确保无未确认的变更行（加单/换/退/一键验收前调用）：
     * 空编辑行（未选商品）自动取消放行；有内容的定位到该行并阻断，避免误丢用户已录内容。
     *
     * @return {boolean} true=可继续
     */
    ensureNoAccEditingRow() {
      const editing = (this.orderDetailList || []).find((r) => r._accEditing);
      if (!editing) return true;
      if (!editing.productName) {
        this.cancelInlineChange(editing);
        return true;
      }
      this.$modal.msgWarning(
        `存在未确认的变更行【${editing.productName}】，请先点「确认」或「取消」`
      );
      const t = this.$refs.xTable;
      if (t && t.scrollToRow) t.scrollToRow(editing);
      return false;
    },

    startInlineAdd() {
      if (this.acceptanceReadonly) {
        this.$modal.msgWarning("验收单已提交，请先撤销验收再变更");
        return;
      }
      if (!this.ensureNoAccEditingRow()) return;
      const editing = {
        // 合成负数 id：vxe row-config.useKey 以 id 为行身份，无 id 会导致替换行不重渲染
        id: -(Date.now()),
        _accEditing: true,
        _accMode: 1, // 1加单 2换货
        _accTargetId: null,
        productName: "",
        productUnit: "斤",
        productSpec: "",
        productPrice: "0.00",
        num: "0.00",
        amount: "0.00",
        refPrice: null,
        priceChanged: false,
        changeType: 1,
      };
      this.orderDetailList.push(editing);
      this.refreshAccFooter();
      this.$nextTick(async () => {
        const t = this.$refs.xTable;
        if (!t) return;
        // 强制重载行数据（useKey 下 splice/push 不会触发行重渲染）
        if (t.loadData) await t.loadData(this.orderDetailList);
        const reactiveRow = this.orderDetailList[this.orderDetailList.length - 1];
        if (!reactiveRow || !reactiveRow._accEditing) return;
        if (t.scrollToRow) t.scrollToRow(reactiveRow);
        if (t.setEditCell) t.setEditCell(reactiveRow, "productName");
        // 兑底聚焦内层 input，触发 @focus 弹出报价面板
        setTimeout(() => {
          const input = t.$el && t.$el.querySelector(".vxe-body--row .vxe-input--inner");
          input && input.focus();
        }, 80);
      });
    },

    /** OA：行内变更——换货（type=2）/退货（type=3）入口；退货直接行内确认 */
    startInlineChange(type, row) {
      if (this.acceptanceReadonly) {
        this.$modal.msgWarning("验收单已提交，请先撤销验收再变更");
        return;
      }
      if (!this.ensureNoAccEditingRow()) return;
      if (type === 3) {
        // 退货（整行）：行内一步确认（Q3 定稿：整行退，应送/实收归 0）
        this.$modal
          .confirm(
            `整行退货【${row.productName}】？确认后该行应送/实收归 0（验收金额不计）；部分退货请走退货单`
          )
          .then(() => deliveryReturn(this.orderForm.orderId, { targetDetailId: row.id }))
          .then(() => {
            this.$modal.msgSuccess("退货成功（原行已标退货，应送实收归0）");
            return this.reloadDetailsForAcc();
          })
          .catch(() => {});
        return;
      }
      // 换货：把当前行替换为编辑行（保留位置），激活商品名单元格选新商品
      if ((this.orderDetailList || []).some((r) => r._accEditing)) {
        this.$modal.msgWarning("请先完成或取消当前编辑中的变更行");
        return;
      }
      const idx = this.orderDetailList.indexOf(row);
      const editing = {
        id: -(Date.now()),
        _accEditing: true,
        _accMode: 2,
        _accTargetId: row.id,
        _accReplaceIdx: idx,
        productName: "",
        productUnit: "斤",
        productSpec: "",
        productPrice: "0.00",
        num: "0.00",
        amount: "0.00",
        refPrice: null,
        priceChanged: false,
        changeType: 2,
      };
      // 原行暂存；编辑行插在原行位置（确认后原行标退货由后端处理）
      this._accStashedRow = { row, idx };
      this.orderDetailList.splice(idx, 1, editing);
      this.$nextTick(async () => {
        const t = this.$refs.xTable;
        if (!t) return;
        if (t.loadData) await t.loadData(this.orderDetailList);
        const reactiveRow = this.orderDetailList[idx];
        if (!reactiveRow || !reactiveRow._accEditing) return;
        if (t.setEditCell) t.setEditCell(reactiveRow, "productName");
        setTimeout(() => {
          const input = t.$el && t.$el.querySelector(".vxe-body--row .vxe-input--inner");
          input && input.focus();
        }, 80);
      });
    },

    /** OA：回退行内变更（加单=删行；退货=恢复原数量；换货=整组恢复），后端自动同步验收草稿 */
    revertInlineChange(row) {
      if (this.acceptanceReadonly) {
        this.$modal.msgWarning("验收单已提交，请先撤销验收再回退");
        return;
      }
      const labels = { 1: "加单", 2: "换货", 3: "退货" };
      const effect =
        row.changeType === 1
          ? "该加单行将被删除"
          : row.changeType === 2
            ? "整组恢复：删除换入行、被换行恢复原数量"
            : "该行恢复原数量（应送/实收）";
      this.$modal
        .confirm(`回退【${row.productName}】的${labels[row.changeType]}变更？${effect}。`)
        .then(() => revokeDeliveryChange(this.orderForm.orderId, row.id))
        .then(() => {
          this.$modal.msgSuccess("变更已回退");
          return this.reloadDetailsForAcc();
        })
        .catch(() => {});
    },

    /** OA：确认行内变更（加单/换货 → D-055 接口落标记） */
    confirmInlineChange(row) {
      if (!row.productName) {
        this.$modal.msgWarning("请填写或选择商品");
        return;
      }
      const num = XEUtils.toNumber(row.num);
      if (!(num > 0)) {
        this.$modal.msgWarning("请填写大于 0 的数量");
        return;
      }
      const orderId = this.orderForm.orderId;
      if (row._accMode === 1) {
        // 加单（补充单据：应收+实收，change_type=1）
        deliverySupplement(orderId, {
          skuId: row.skuId,
          productName: row.productName,
          spec: row.productSpec,
          unit: row.productUnit,
          num: num,
          price: XEUtils.toNumber(row.productPrice),
          actualNum: num,
          remark: null,
        })
          .then(() => {
            this.$modal.msgSuccess("加单成功（标记已附加）");
            return this.reloadDetailsForAcc();
          })
          .catch(() => {});
      } else {
        // 换货（原行标退货+新行标换货，同组）
        deliveryExchange(orderId, {
          targetDetailId: row._accTargetId,
          skuId: row.skuId,
          productName: row.productName,
          spec: row.productSpec,
          unit: row.productUnit,
          num: num,
          actualNum: num,
          remark: null,
        })
          .then(() => {
            this.$modal.msgSuccess("换货成功（被换行标退货·换入行标换货，同组）");
            return this.reloadDetailsForAcc();
          })
          .catch(() => {});
      }
    },

    /** OA：取消行内变更（加单=移除编辑行；换货=还原原行） */
    cancelInlineChange(row) {
      if (row._accMode === 2 && this._accStashedRow) {
        const { row: original, idx } = this._accStashedRow;
        this.orderDetailList.splice(idx, 1, original);
        this._accStashedRow = null;
      } else {
        const idx = this.orderDetailList.indexOf(row);
        if (idx > -1) this.orderDetailList.splice(idx, 1);
      }
      this.refreshAccFooter();
    },

    /** OA：行内变更落库后重拉明细 → 同步验收缺失行 → 合并实收 */
    reloadDetailsForAcc() {
      const orderId = this.orderForm.orderId;
      if (!orderId) return Promise.resolve();
      return listSaleDetail({ orderId }).then((response) => {
        const details = (response.data || []).map((item) => ({
          ...item,
          productPrice: XEUtils.commafy(item.productPrice, { digits: 2 }),
          num: XEUtils.commafy(item.num, { digits: 2 }),
          amount: XEUtils.commafy(item.expectAmount, { digits: 2 }),
        }));
        this.orderDetailList = details;
        this.originalOrderDetailList = this.deepCloneOrderDetailList(details);
        this._accStashedRow = null;
        if (this.acceptanceInfo && !this.acceptanceReadonly) {
          return createAcceptanceByOrder(Number(orderId))
            .then(() => this.applyAcceptanceItems())
            .catch(() => {})
            .finally(() => this.refreshAccFooter());
        }
        this.refreshAccFooter();
        return null;
      });
    },

    /** OA：退出验收模式返回订单列表 */
    exitAcceptanceMode() {
      if (this._accSaveTimer) clearTimeout(this._accSaveTimer);
      this.acceptanceMode = false;
      this.acceptanceInfo = null;
      this.$tab.closeOpenPage(orderPage);
    },

    /** OA：撤销验收弹窗（已提交态入口） */
    showRevokeDialog() {
      if (!this.acceptanceReadonly) return;
      this.accRevokeForm = { reason: null };
      this.accRevokeOpen = true;
      this.$nextTick(() => {
        this.$refs.accRevokeFormRef && this.$refs.accRevokeFormRef.clearValidate();
      });
    },
    submitAccRevoke() {
      this.$refs.accRevokeFormRef.validate((valid) => {
        if (!valid) return;
        this.accRevoking = true;
        revokeAcceptance(this.acceptanceInfo.id, this.accRevokeForm.reason)
          .then(() => {
            this.$modal.msgSuccess("验收已撤销，订单回到可验收状态");
            this.accRevokeOpen = false;
            this.orderStatus = 1; // 回到可验收
            return this.refreshAcceptanceInfo();
          })
          .catch(() => {})
          .finally(() => {
            this.accRevoking = false;
          });
      });
    },
    /** 格式化小数类型 */
    decimalFormatter(key) {
      return ({ row }) => {
        if (!row || typeof row[key] === "undefined") {
          return "0.00";
        }
        let value = XEUtils.toNumber(row[key]);
        let formatValue = XEUtils.commafy(value, {
          digits: 2,
        });
        if (formatValue <= 0 || formatValue < 0.01) {
          formatValue = "0.00";
        }
        // 将格式化后的值赋值回去
        row[key] = formatValue;
        return formatValue;
      };
    },
    /** 计算商品小计 */
    calcAmount(row) {
      if (!row) return;
      let price = XEUtils.toNumber(row.productPrice);
      let num = XEUtils.toNumber(row.num);
      let formatAmount = XEUtils.commafy(price * num, {
        digits: 2,
      });
      row.amount = formatAmount;
      return formatAmount;
    },
    /** 表尾合计方法 */
    sumNum(list, field) {
      let count = 0;
      if (list && list.length) {
        list.forEach((item) => {
          const value = XEUtils.toNumber(item[field]);
          if (!isNaN(value)) {
            count += value;
          }
        });
      }
      return count;
    },
    sumNumWithGroup(list, field, groupField) {
      if (!list || !list.length) {
        return "";
      }

      const groupedData = XEUtils.groupBy(list, groupField);
      const results = [];

      for (const [groupKey, groupItems] of Object.entries(groupedData)) {
        let count = 0;
        groupItems.forEach((item) => {
          const value = XEUtils.toNumber(item[field]);
          if (!isNaN(value)) {
            count += value;
          }
        });
        results.push(`${count}${groupKey}`);
      }

      return results.join(" + ");
    },
    /** 表尾渲染方法 */
    footerMethod({ columns, data }) {
      const acceptanceMode = this.acceptanceMode;
      const s = this.accSummary;
      // 验收模式：编辑中的行内变更行不计入合计
      const rows = acceptanceMode ? data.filter((r) => !r._accEditing) : data;
      return [
        columns.map((column, columnIndex) => {
          if (columnIndex === 0) {
            return "合计";
          }
          if (column.property === "num") {
            return this.sumNumText(rows, "num");
          } else if (column.property === "amount") {
            return this.sumNumText(rows, "amount");
          } else if (column.property === "actualNum") {
            return this.sumNumText(data, "actualNum");
          } else if (acceptanceMode && column.property === "acceptActual") {
            return this.sumNumText(rows, "acceptActual");
          } else if (acceptanceMode && column.property === "acceptAmount") {
            return "¥" + s.actualAmount.toFixed(2);
          } else if (acceptanceMode && column.property === "acceptDiff") {
            return (s.diffAmount > 0 ? "+" : "") + s.diffAmount.toFixed(2);
          }
          return "";
        }),
      ];
    },
    /** 合计行数值：千分位+固定两位小数（数量/金额/实收合计统一口径） */
    sumNumText(list, field) {
      return XEUtils.commafy(this.sumNum(list, field), { digits: 2 });
    },
    /** vxe表格检测是否改动 */
    checkTableUpdted() {
      const oldOrderDetails = this.deepCloneOrderDetailList(
        this.originalOrderDetailList
      );
      const orderDetails = this.deepCloneOrderDetailList(this.orderDetailList);
      return !deepEqual(oldOrderDetails, orderDetails);
    },
    /** 深拷贝订单明细列表 */
    deepCloneOrderDetailList(orderDetails) {
      if (!orderDetails) return [];
      return deepCloneWithoutFields(orderDetails, ["_X_ROW_KEY"]);
    },
    /** vxe表格-过滤商品名称方法 */
    filterProductNameMethod({ option, row }) {
      if (row.productName.indexOf(option.data) > -1) {
        return row.productName;
      }
    },
    /** vxe表格-编辑门禁：未选送货单位禁录；浏览/只读全禁（实收编辑已下线 C1） */
    checkTableActive({ row, column }) {
      // OA 验收模式：普通行仅实收数量可编辑（vxe 编辑态，同录单交互）；
      // 行内变更编辑行（加单/换货）允许 商品/数量/单价 编辑（商品列激活即报价选择面板）
      if (this.acceptanceMode) {
        if (row && row._accEditing) {
          return ["productName", "num", "productPrice"].includes(column.field);
        }
        return column.field === "acceptActual" && !this.acceptanceReadonly;
      }
      // 浏览模式与已验收/已结算订单：全部只读
      if (this.browseMode || this.viewOnlyMode) {
        return false;
      }
      // S1-1.2 撤销支持：进入单元格编辑前推入快照（编辑前的明细状态）
      this.pushUndoSnapshot();
      // 用户真正点击单元格编辑时，隐藏"编辑已有订单"提示横幅
      if (this.showEditExistingBanner) {
        this.showEditExistingBanner = false;
      }
      // 录单态明细区门禁：未选定客户(+配送点)前禁止编辑，引导先选送货单位
      if (!this.orderForm.customerDeptId) {
        const now = Date.now();
        if (now - this._gateWarnAt > 2000) {
          this._gateWarnAt = now;
          this.$modal.msgWarning("请先选择送货单位");
        }
        return false;
      }
      return true;
    },
    /** 行拖拽 */
    rowDrop() {
      const xTable = this.$refs.xTable;
      // vxe-table v4 中 body 结构与 v3 不同：.vxe-table--body 不再是 .body--wrapper 的直接子元素，改用后代选择器
      const tbody = xTable.$el.querySelector(".body--wrapper .vxe-table--body tbody");
      if (!tbody) {
        return;
      }
      this.sortableX = Sortable.create(
        tbody,
        {
          handle: ".drag-btn",
          onEnd: ({ newIndex, oldIndex }) => {
            const currRow = this.orderDetailList.splice(oldIndex, 1)[0];
            this.orderDetailList.splice(newIndex, 0, currRow);
            const newArr = this.orderDetailList.slice(0);
            this.orderDetailList = [];
            // 重新赋值
            this.$nextTick(() => {
              this.orderDetailList = newArr;
            });
          },
        }
      );
    },
    /** 添加行（明细区门禁：浏览态禁加行；未选送货单位时仅保留默认空行，禁止加行） */
    handleAddRow(rowIndex) {
      if (this.browseMode && (this.orderDetailList || []).length >= 1) return;
      if (!this.orderForm.customerDeptId && (this.orderDetailList || []).length >= 1) {
        const now = Date.now();
        if (now - this._gateWarnAt > 2000) {
          this._gateWarnAt = now;
          this.$modal.msgWarning("请先选择送货单位");
        }
        return;
      }
      // 用户真正新增行时，隐藏"编辑已有订单"提示横幅（初始加载不算）
      if (this._initialLoadDone && this.showEditExistingBanner) {
        this.showEditExistingBanner = false;
      }
      if (!this.orderDetailList) {
        this.orderDetailList = [];
      }
      const newRecord = {
        productUnit: "斤",
        num: "0.00",
        productPrice: "0.00",
        amount: "0.00",
        refPrice: null,
        priceChanged: false,
      };

      const index =
        rowIndex == null || rowIndex === -1
          ? this.orderDetailList.length
          : rowIndex + 1;
      this.orderDetailList.splice(index, 0, newRecord);

      // check & update customerDept cascader status
      this.updateCustomerDeptStatus();
    },
    /** 减少行 */
    handleRemoveRow(row) {
      if (!row) return;
      // 浏览态只读，禁止删行
      if (this.browseMode) return;
      const index = this.orderDetailList.indexOf(row);
      this.$confirm("确定要删除第【" + (index + 1) + "】行数据吗?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          const length = this.orderDetailList.length;
          this.orderDetailList.splice(index, 1);
          // 防止全部删完了
          if (length <= 1) {
            this.throttledAddRow();
          }
          // check & update customerDept cascader status
          this.updateCustomerDeptStatus();
        })
        .catch(() => {});
    },
    /** 鼠标进入悬浮单元格事件 */
    cellMouseenterEvent({ row, rowIndex, column }) {
      if (this.currentHoverRow && this.currentHoverRow === row) return;
      this.currentHoverRow = this.$refs.xTable.hoverRow;
    },
    /** 鼠标离开悬浮单元格事件 */
    cellMouseleaveEvent({ row, rowIndex, column }) {
      if (
        this.currentHoverRow &&
        this.currentHoverRow === row &&
        rowIndex !== 0 &&
        rowIndex !== this.orderDetailList.length - 1
      )
        return;

      // reset hover row
      this.currentHoverRow = null;
    },
    /** 商品名称输入框-聚焦事件 */
    focusProductNameEvent({ parentRow, value }) {
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        this.initPulldownData(value, parentRow);
        $pulldown.showPanel();
      }
    },
    /** 商品名称输入框-键盘按下事件（服务端按关键字检索，防抖） */
    keyupProductNameEvent({ parentRow, value }) {
      this.initPulldownData(value, parentRow);
    },
    /** 商品名称输入框-值变更事件 */
    blurProductNameEvent({ parentRow, value }) {
      if (!parentRow) return;

      const quoteItem = this.pulldownTableData.find(
        (q) => q.productName === value
      );
      if (quoteItem) {
        parentRow.skuId = quoteItem.skuId;
        parentRow.productUnit = quoteItem.productUnit;
        parentRow.productSpec = quoteItem.productSpec;
        parentRow.remark = quoteItem.remark;
      } else {
        parentRow.skuId = null;
        parentRow.productUnit = "斤";
        parentRow.productSpec = "";
        parentRow.remark = "";
      }

      // 若当前为最后一行且当前商品名称不为空，新增一行（OA 验收模式：编辑行由确认按钮收口，不自动加行）
      const isLastRow =
        this.orderDetailList.length - 1 ===
        this.orderDetailList.indexOf(parentRow);
      const isValidRow = !XEUtils.isEmpty(parentRow.productName);
      if (isLastRow && isValidRow && !this.acceptanceMode) {
        this.throttledAddRow(-1);
      }
    },
    /** 商品名称输入框-清除按钮事件 */
    clearProductNameEvent(parentRow) {
      if (!parentRow) return;

      // 重设已选项
      parentRow.skuId = null;
      parentRow.productName = "";
      parentRow.productUnit = "";
      parentRow.productSpec = "";
      parentRow.remark = "";
      parentRow.num = "0.00";
      parentRow.productPrice = "0.00";
      parentRow.amount = "0.00";

      // 重设列表（过滤掉其它行已添加的 SKU）
      this._pulldownExcludeRow = parentRow;
      this.pulldownTableData = this.filterAddedSku(this.skuQuoteDetails);
    },
    /** 商品名称下拉容器-选中元素事件 */
    pulldownCellClickEvent({ parentRow, row }) {
      const $table = this.$refs.xTable;
      const $pulldown = this.$refs.pulldownRef;
      if ($pulldown) {
        // 设置选中的商品到订单详情
        parentRow.productName = row.productName;
        parentRow.productUnit = row.productUnit;
        parentRow.productSpec = row.productSpec;
        parentRow.productPrice = row.price;
        parentRow.priceChanged = false;
        parentRow.skuId = row.skuId;
        parentRow.isTemp = row.isTemp ? 1 : 0;

        if (row.isTemp) {
          // 临时商品：使用默认单价，不参与取价（以默认单价作为参考价）
          parentRow.refPrice = row.price;
          parentRow.priceSource = "temp";
          this.calcAmount(parentRow);
        } else {
          // 正式SKU：两层取价（客户报价 > 客户模板），未命中提示人工填写。
          // 参考价待取价引擎返回后再设置，避免用展示价误触发改价提醒。
          parentRow.refPrice = null;
          parentRow.priceSource = "quote";
          parentRow.priceReason = "";
          this.applyPriceQuery(parentRow);
        }

        // 聚焦到数量单元格
        $table.setEditCell(parentRow, "num");

        // 若当前为最后一行，则新增一行（OA 验收模式：编辑行由确认按钮收口，不自动加行）
        const isLastRow =
          this.orderDetailList.length - 1 ===
          this.orderDetailList.indexOf(parentRow);
        if (isLastRow && !parentRow._accEditing) {
          this.throttledAddRow(-1);
        }
      }
    },
    /** 取价：选中商品后按 客户报价>客户模板 取当期有效价；未命中清空价格并提示人工填写 */
    applyPriceQuery(parentRow) {
      const params = {
        customerId: this.orderForm.customerId,
        deliveryPointId: this.orderForm.customerDeptId,
        skuId: parentRow.skuId,
        deliveryDate: this.orderForm.deliveryDate,
      };
      if (!params.customerId || !params.skuId || !params.deliveryDate) {
        return;
      }
      queryPrice(params)
        .then((response) => {
          const result = response.data || {};
          if (result.price != null) {
            parentRow.productPrice = result.price;
            // 记录取价引擎参考价，用于改价提醒比对
            parentRow.refPrice = result.price;
            parentRow.priceChanged = false;
            parentRow.priceSource = "quote";
            this.calcAmount(parentRow);
          } else {
            // 未命中有效报价：清空价格，并重置参考价，避免用初始展示价误触发改价提醒
            parentRow.productPrice = "";
            parentRow.amount = "0.00";
            parentRow.refPrice = null;
            parentRow.priceChanged = false;
            this.$modal.msgWarning(
              "商品【" + parentRow.productName + "】无有效报价，请人工填写价格"
            );
          }
        })
        .catch(() => {
          // 取价失败不阻塞录单，保留报价明细默认价
        });
    },
    /** 单价与参考价不一致的单元格高亮（实现见下方 S1-1.3 handlePriceChange） */
    priceCellClass({ row }) {
      return row.priceChanged ? "col-price-changed" : "";
    },
    /** 商品名称下拉容器-初始化数据（服务端按关键字检索客户商品池+临时商品） */
    initPulldownData(value, excludeRow) {
      // 记录关键词供下拉单元格高亮
      this.pulldownKeyword = value || "";
      // 记录当前编辑行，用于过滤「已在其它行添加」的 SKU
      this._pulldownExcludeRow = excludeRow || null;
      if (this._pulldownTimer) {
        clearTimeout(this._pulldownTimer);
      }
      this._pulldownTimer = setTimeout(() => {
        this.getSkuQuoteDetailList(value || "").then(() => {
          this.pulldownTableData = this.filterAddedSku(this.skuQuoteDetails);
        });
      }, 200);
    },
    /** 行或候选的 SKU 标识键（有 skuId 用 sku，临时商品用名称） */
    skuRowKey(row) {
      if (!row) return null;
      if (row.skuId != null && row.skuId !== "") return "sku:" + row.skuId;
      return row.productName ? "name:" + row.productName : null;
    },
    /** 过滤掉已在订单明细其它行添加过的 SKU，候选列表不重复展示 */
    filterAddedSku(list) {
      const excludeRow = this._pulldownExcludeRow || null;
      const added = new Set();
      (this.orderDetailList || []).forEach((row) => {
        if (row === excludeRow) return;
        const key = this.skuRowKey(row);
        if (key) added.add(key);
      });
      // 当前行自身已有商品始终保留（避免编辑本行时把当前商品也过滤掉）
      const selfKey = this.skuRowKey(excludeRow);
      return (list || []).filter((c) => {
        const key = this.skuRowKey(c);
        if (!key) return true;
        if (selfKey && key === selfKey) return true;
        return !added.has(key);
      });
    },
    /** 选择送货单位树回调 */
    handleFormOptionsChanged(value) {
      // Element Plus 的 togglePopperVisible 在 disabled 状态下会早退（无法关面板），
      // 而选定送货单位后组件立即锁定，故必须在置值锁定前先手动收起下拉面板
      const cascader = this.$refs.deptCascader;
      if (cascader && cascader.togglePopperVisible) {
        cascader.togglePopperVisible(false);
      }
      // init table item
      this.orderDetailList = [];
      this.throttledAddRow();

      // init customerDeptId（兼容清空时传入 null）
      const arr = Array.isArray(value) ? value : [];
      const customerDeptId = arr.length ? arr[arr.length - 1] : null;
      this.orderForm.customerDeptId = customerDeptId;
      this.orderForm.customerId = customerDeptId ? this.customerDeptMap[customerDeptId] : null;

      // init skuQuoteDetails
      this.getSkuQuoteDetailList();

      // 新增订单：选完客户后检测同配送点+同日期的草稿订单，存在则提示可继续添加并跳转已有明细
      if (!this.orderForm.orderId && customerDeptId && this.orderForm.deliveryDate) {
        this.checkExistingDraftOrder();
      }
    },
    /** 新增订单防重复：同配送点+同日期已存在草稿订单时，提示并跳转到对应订单明细 */
    checkExistingDraftOrder() {
      checkExistingDraft({
        customerDeptId: this.orderForm.customerDeptId,
        deliveryDate: this.orderForm.deliveryDate,
      })
        .then((response) => {
          const order = response.data || null;
          if (!order || !order.id) return;
          const deptName = this.deptNameOf(order.customerDeptId) || "该配送点";
          this.$confirm(
            "【" + deptName + "】在 " + this.orderForm.deliveryDate +
              " 已有一个可继续添加的草稿订单（编号：" + order.code + "），可直接在原有明细上继续录入。",
            "提示",
            {
              confirmButtonText: "去编辑已有订单",
              cancelButtonText: "重开新单",
              type: "warning",
            }
          )
            .then(() => {
              // 去编辑已有订单：加载该草稿订单，并显示提示横幅
              this.openDraftForEdit(order.id);
            })
            .catch(() => {
              // 重开新单：清空送货单位，回到新单状态
              this.clearDeliveryUnit();
            });
        })
        .catch(() => {});
    },
    /** 查询商品分类下拉树结构 */
    getTreeselect() {
      listCustomerDept()
        .then((response) => {
          // init customerDeptMap
          this.customerDeptMap = Object.fromEntries(
            response.data.map(({ id, customerId }) => [id, customerId])
          );

          // init customerDeptOptions
          const treeList = this.handleTree(response.data);
          this.customerDeptOptions = this.transformData(treeList);
        })
        .then(() => {
          this.customerDeptDisabled = false;
          // 构造级联选择器选中的数据
          const customerDeptId = this.orderForm.customerDeptId;
          if (customerDeptId) {
            this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              customerDeptId.toString()
            );
          }
        });
    },
    /** 树形列表转换为级联列表 */
    transformData(data) {
      return data.map((item) => {
        const newItem = {
          value: item.id.toString(),
          label: item.name,
        };
        if (Array.isArray(item.children) && item.children.length > 0) {
          newItem.children = this.transformData(item.children);
        }
        return newItem;
      });
    },
    /** 根据 id 构造父节点列表，并添加自身 */
    fillWithParentCustomerDeptId(list, id) {
      if (!id) return [];
      function getParents(nodes, targetId, path = []) {
        for (const node of nodes) {
          path.push(node.value);
          if (
            node.value === targetId ||
            (node.children && getParents(node.children, targetId, path))
          ) {
            return path;
          }
          path.pop();
        }
        return null;
      }

      return getParents(list, id) || [];
    },
    /** 商品单位变更事件 */
    changedProductUnitEvent(row) {
      const skuQuote = this.skuQuoteDetails.find(
        (item) =>
          item.productName === row.productName &&
          item.productUnit === row.productUnit &&
          item.productSpec === row.productSpec
      );

      // 重设 skuId
      if (skuQuote) {
        row.skuId = skuQuote.skuId;
      } else {
        row.skuId = null;
      }
    },
    /** 更新送货单位下拉选择器状态（客户+配送点整体锁定的兼容入口） */
    updateCustomerDeptStatus() {
      // 整体锁定规则由 computed customerDeptLocked 控制，此处仅同步旧字段
      this.customerDeptDisabled =
        !!this.orderForm.orderId || !!this.orderForm.customerDeptId;
    },
    /* ========== 浏览模式（收起/恢复模型） ========== */
    /**
     * 最近订单行点击：
     * · 工作区为空（没在做新单，典型如送货单位未选）→ 直接以「编辑模式」打开该草稿单；
     * · 正在录新单 → 收起当前工作区，进只读浏览模式（顶部可「返回我的新单」）。
     */
    handleRecentOrderRowChange(event) {
      if (!event || !event.row) return;
      const row = event.row;
      const orderId = row.id;
      if (this.browseMode) {
        // 已在浏览中：直接切换目标订单
        this.loadBrowseOrder(orderId);
        return;
      }
      const workspaceEmpty = this.isWorkspaceEmpty;
      // 「最近」面板当前只列草稿（selectRecentOrderList 写死 status=0）；
      // 万一以后放开状态，非草稿仍只能只读浏览，不能就地改
      const editable = row.status != null && Number(row.status) === 0;
      if (editable && workspaceEmpty) {
        this.openDraftForEdit(orderId);
        return;
      }
      const proceed = () => {
        this.enterBrowseMode(orderId, workspaceEmpty);
      };
      if (!workspaceEmpty) {
        this.$modal
          .confirm("当前正在录入的新单将暂时收起，进入浏览模式？")
          .then(proceed)
          .catch(() => {
            this.$refs.recentOrderTable.clearCurrentRow();
          });
      } else {
        proceed();
      }
    },
    /** 以编辑模式打开一张草稿订单（S15 防重复弹窗与「最近」面板共用），并挂「返回重开新单」横幅 */
    openDraftForEdit(orderId) {
      this.existingDraftOrderId = orderId;
      this.initOrderDetailPage(orderId);
      // 延迟显示横幅，等订单加载完成后
      this.$nextTick(() => {
        this.showEditExistingBanner = true;
      });
    },
    /** 收起当前工作区并载入历史订单浏览 */
    enterBrowseMode(orderId, wasEmpty) {
      this.stashCurrentWorkspace(wasEmpty);
      // 浏览态为只读，不应再挂“正在编辑已有草稿”的横幅
      this.showEditExistingBanner = false;
      this.existingDraftOrderId = null;
      // 清除新单态下“送货单位”失焦残留的“不能为空”校验态，避免进入浏览模式后仍挂红字
      this.$nextTick(() => {
        this.$refs.orderForm && this.$refs.orderForm.clearValidate("customerDeptId");
      });
      this.loadBrowseOrder(orderId);
    },
    /** 快照当前录入工作区（表头+明细+级联选中），供返回时原样恢复 */
    stashCurrentWorkspace(wasEmpty) {
      this.stashWasEmpty = !!wasEmpty;
      this.stashedWorkspace = {
        orderForm: JSON.parse(JSON.stringify(this.orderForm)),
        orderDetailList: this.deepCloneOrderDetailList(this.orderDetailList),
        originalOrderDetailList: this.deepCloneOrderDetailList(
          this.originalOrderDetailList
        ),
        selectedCustomerDepts: [...(this.selectedCustomerDepts || [])],
        originalOrderForm: this.originalOrderForm
          ? JSON.parse(JSON.stringify(this.originalOrderForm))
          : null,
        orderStatus: this.orderStatus,
      };
    },
    /** 载入历史订单到只读浏览态 */
    loadBrowseOrder(orderId) {
      getSaleOrder(orderId).then((response) => {
        const orderData = response.data;
        this.browsedOrder = orderData;
        // 更新订单信息（表头）到当前浏览的历史订单，否则上方表单不会跟随刷新
        this.orderForm = {
          ...orderData,
          orderId: orderData.id,
          orderCode: orderData.code,
        };
        this.orderStatus =
          orderData.status != null ? Number(orderData.status) : null;
        this.customerDeptDisabled = true;
        this.selectedCustomerDepts = this.orderForm.customerDeptId
          ? this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              this.orderForm.customerDeptId.toString()
            )
          : [];
        this.snapshotOriginalOrderForm();
        // 载入历史订单后清除送货单位残留校验态（浏览态只读，不应再挂“不能为空”）
        this.$nextTick(() => {
          this.$refs.orderForm && this.$refs.orderForm.clearValidate("customerDeptId");
        });
      });
      listSaleDetail({ orderId }).then((response) => {
        const details = (response.data || []).map((item) => ({
          ...item,
          productPrice: XEUtils.commafy(item.productPrice, { digits: 2 }),
          num: XEUtils.commafy(item.num, { digits: 2 }),
          amount: XEUtils.commafy(item.expectAmount, { digits: 2 }),
          actualNum:
            item.actualNum !== null && item.actualNum !== undefined
              ? XEUtils.commafy(item.actualNum, { digits: 2 })
              : item.actualNum,
        }));
        // 直接替换展示列表；浏览态自动保存已暂停，不污染草稿
        this.orderDetailList = details;
        this.originalOrderDetailList = this.deepCloneOrderDetailList(details);
      });
      this.browseMode = true;
    },
    /** 返回我的新单：原样恢复收起的录入工作区 */
    exitBrowseMode() {
      const stash = this.stashedWorkspace;
      if (stash) {
        this.orderForm = stash.orderForm;
        this.orderDetailList = stash.orderDetailList.map((r) => ({ ...r }));
        this.originalOrderDetailList = stash.originalOrderDetailList;
        this.selectedCustomerDepts = stash.selectedCustomerDepts;
        this.originalOrderForm = stash.originalOrderForm;
        this.orderStatus = stash.orderStatus;
      }
      this.browseMode = false;
      this.browsedOrder = null;
      this.stashedWorkspace = null;
      // 清除右侧最近订单的当前行高亮
      this.$nextTick(() => {
        if (this.$refs.recentOrderTable) {
          this.$refs.recentOrderTable.clearCurrentRow();
        }
      });
    },
    /* ========== 复制为新单 ========== */
    /** 历史订单 → 新单：商品+数量灌入；单价按当前执行价逐行重取；日期/编号重新分配 */
    /**
     * 底部「复制为新单」：先拦住会丢数据的两种情况，再以当前打开的单为模板另录一张。
     * ① 浏览态：收起的新单工作区会被丢弃；② 编辑态：当前订单的未保存改动会被丢弃。
     */
    handleCopyAsNew() {
      const source = this.copySourceOrder;
      if (!source) {
        this.$modal.msgWarning("当前没有可复制的订单，请先打开一张已有订单");
        return;
      }
      const willLoseStash =
        this.browseMode && this.stashedWorkspace && !this.stashWasEmpty;
      const willLoseEdits = !this.browseMode && this.isOrderDirty;
      if (!willLoseStash && !willLoseEdits) {
        this.copyAsNew(source);
        return;
      }
      this.$modal
        .confirm(
          willLoseEdits
            ? "当前订单有未保存的改动，复制为新单会丢弃这些改动，是否继续？"
            : "复制为新单会丢弃已收起的新单工作区，是否继续？"
        )
        .then(() => this.copyAsNew(source))
        .catch(() => {});
    },
    /** 以 sourceOrder 为模板灌一张新单（商品+数量带过来，单价按当前执行价重取） */
    copyAsNew(sourceOrder) {
      if (!sourceOrder) return;
      const sourceOrderId = sourceOrder.id;
      // 同步订单信息中的送货单位（客户 + 配送点）
      const sourceCustomerId = sourceOrder.customerId;
      const sourceDeptId = sourceOrder.customerDeptId;
      listSaleDetail({ orderId: sourceOrderId }).then((response) => {
        const sourceRows = (response.data || []).filter(
          (d) => d.isDeleted === false || d.isDeleted === 0 || d.isDeleted == null
        );
        if (!sourceRows.length) {
          this.$modal.msgWarning("该历史订单没有有效商品行");
          return;
        }
        // 重置为新单工作区
        this.browseMode = false;
        this.browsedOrder = null;
        this.stashedWorkspace = null;
        // 已切到空白新单，不应再挂“正在编辑已有草稿”横幅
        this.showEditExistingBanner = false;
        this.existingDraftOrderId = null;
        this.orderForm = {
          orderId: null,
          orderCode: null,
          customerId: sourceCustomerId,
          customerDeptId: sourceDeptId,
          deliveryDate: null,
          remark: "",
        };
        // 同步送货单位：构造级联选中路径（有配送点用配送点，否则用客户）
        this.selectedCustomerDepts = sourceDeptId
          ? this.fillWithParentCustomerDeptId(
              this.customerDeptOptions,
              sourceDeptId.toString()
            )
          : sourceCustomerId
            ? this.fillWithParentCustomerDeptId(
                this.customerDeptOptions,
                sourceCustomerId.toString()
              )
            : [];
        // 送货日期重算：15 点前=今天，之后=明天
        const today = new Date();
        const tomorrow = new Date(today);
        tomorrow.setDate(today.getDate() + 1);
        const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
        this.orderForm.deliveryDate = today.getHours() < 15 ? fmt(today) : fmt(tomorrow);
        // 订单号按 §3.2 重新分配
        genOrderCode({ refresh: false }).then((res) => {
          this.orderForm.orderCode = res.msg;
        });
        // 商品+数量灌入，单价清零待取价引擎回填
        this.orderDetailList = sourceRows.map((d) => ({
          productName: d.productName,
          productUnit: d.productUnit || "斤",
          productSpec: d.productSpec || "",
          skuId: d.skuId,
          isTemp: 0,
          num: XEUtils.commafy(d.num, { digits: 2 }),
          productPrice: "0.00",
          refPrice: null,
          priceChanged: false,
          amount: "0.00",
          remark: "",
        }));
        this.originalOrderDetailList = this.deepCloneOrderDetailList(
          this.orderDetailList
        );
        this.snapshotOriginalOrderForm();
        this.draftStatusText = "";
        // 刷新商品池与常用商品，使下拉/报价按复制的客户+配送点生效
        this.getSkuQuoteDetailList();
        this.loadFrequentProducts();
        this.$modal.msgSuccess("已复制商品清单，单价正在按当前执行价重新获取");
        // 逐行按 配送点覆盖价 > 客户报价 > 报价模板 重取价
        this.$nextTick(() => {
          this.orderDetailList.forEach((row) => {
            if (row.skuId) {
              this.applyPriceQuery(row);
            }
          });
        });
      });
    },
    /* ========== 重开新单（客户+配送点整体换选入口） ========== */
    reopenNewOrder() {
      const deptName = this.deptNameOf(this.orderForm.customerDeptId);
      this.$modal
        .confirm(
          "重开新单将清空当前明细并重新分配订单编号" +
            (deptName ? "（当前送货单位【" + deptName + "】）" : "") +
          "，是否继续？"
        )
        .then(() => {
          this.initOrderDetailPage(null);
          this.$modal.msgSuccess("已重开新单，请重新选择送货单位");
        })
        .catch(() => {});
    },
    /** 清空送货单位：彻底重置为新单状态（与重置按钮效果一致） */
    clearDeliveryUnit() {
      this.showEditExistingBanner = false;
      this.existingDraftOrderId = null;
      this.initOrderDetailPage(null);
    },
    /** 横幅点击：返回重开新单 */
    returnToNewOrder() {
      this.clearDeliveryUnit();
      this.$modal.msgSuccess("已清空，可重新选择送货单位开新单");
    },

    /* ========== 草稿自动保存（Phase 1.1） ========== */
    /** 进入页面时检查草稿：路由带 draft key 则自动恢复，否则新单页显示恢复横幅 */
    checkDraftOnEnter() {
      const draftKey = this.$route.query.draft;
      if (draftKey) {
        const draft = restoreDraft(draftKey);
        // 后端为主：同 key 后端草稿更新时间更新则优先
        fetchDraftFromServer(draft.orderId, draft.customerDeptId).then((serverDraft) => {
          let target = draft;
          if (serverDraft) {
            const serverAt = new Date(serverDraft.savedAt).getTime();
            const localAt = new Date(draft.savedAt).getTime();
            if (serverAt > localAt + 1000) {
              target = { key: draft.key, ...serverDraft };
            }
          }
          if (target && !this.defaultOrderId) {
            this.restoreDraftIntoForm(target);
          } else {
            this.$modal.msgWarning("草稿不存在或已过期");
          }
        });
        return;
      }
      // 无指定草稿时，若后端有本配送点的新单草稿也提示恢复（换电脑场景）
      this.refreshDraftBanner();
    },
    /** 刷新草稿横幅（新单页且有未过期草稿时显示） */
    refreshDraftBanner() {
      this.availableDrafts = listDrafts();
      this.showDraftBanner =
        !this.defaultOrderId && this.availableDrafts.length > 0;
    },
    /** 恢复最新一条草稿 */
    restoreLatestDraft() {
      if (this.availableDrafts.length) {
        this.restoreDraftIntoForm(this.availableDrafts[0]);
      }
    },
    /** 忽略草稿（仅隐藏横幅，不删除数据） */
    dismissDraftBanner() {
      this.showDraftBanner = false;
    },
    /** 将草稿恢复到表单：校验客户/配送点仍存在，恢复后清除本地+后端草稿 */
    restoreDraftIntoForm(draft) {
      const deptId = draft.customerDeptId;
      // 客户配送点树已加载时校验存在性
      if (
        deptId &&
        Object.keys(this.customerDeptMap).length &&
        !this.customerDeptMap[deptId]
      ) {
        this.$modal.msgWarning("草稿对应的送货单位已不存在，草稿已失效");
        removeDraft(draft.key);
        this.refreshDraftBanner();
        return;
      }
      this.orderForm = {
        orderId: draft.orderId || null,
        orderCode: draft.orderCode || null,
        customerId: draft.customerId || null,
        customerDeptId: draft.customerDeptId || null,
        deliveryDate: draft.deliveryDate || null,
        remark: draft.remark || null,
      };
      this.selectedCustomerDepts = this.fillWithParentCustomerDeptId(
        this.customerDeptOptions,
        deptId ? String(deptId) : ""
      );
      this.orderDetailList = (draft.details || []).map((row) => ({ ...row }));
      this.originalOrderDetailList = this.deepCloneOrderDetailList(
        this.orderDetailList
      );
      this.snapshotOriginalOrderForm();
      this.customerDeptDisabled = true;
      removeDraft(draft.key);
      removeDraftFromServer(draft.orderId, draft.customerDeptId);
      this.refreshDraftBanner();
      this.getSkuQuoteDetailList();
      this.$modal.msgSuccess("已恢复草稿（暂存已消费清除，继续录入后重新自动保存）");
    },
    /** 记录表单初始快照（不含 orderDetails，用于脏判定） */
    snapshotOriginalOrderForm() {
      const { orderId, orderCode, customerId, customerDeptId, deliveryDate, remark } = this.orderForm;
      this.originalOrderForm = {
        orderId,
        orderCode,
        customerId,
        customerDeptId,
        deliveryDate,
        remark,
      };
    },
    /** 按配送点ID查名称（级联树） */
    deptNameOf(deptId) {
      if (!deptId) return "";
      let name = "";
      const find = (nodes) => {
        for (const n of nodes) {
          if (String(n.value) === String(deptId)) {
            name = n.label;
            return true;
          }
          if (n.children && find(n.children)) return true;
        }
        return false;
      };
      find(this.customerDeptOptions);
      return name;
    },
    /** 格式化保存时间 HH:mm */
    formatSavedAt(savedAt) {
      if (!savedAt) return "";
      const d = new Date(savedAt);
      const p = (n) => String(n).padStart(2, "0");
      return `${p(d.getHours())}:${p(d.getMinutes())}`;
    },
    /** 数据变更后路由：浏览态暂停；录单态走草稿双写（实收自动保存已下线 C1） */
    scheduleDraftSave() {
      if (this.acceptanceMode) return; // OA 验收模式：改动走验收自动保存，不污染订单草稿
      if (this.browseMode) return; // 浏览模式暂停草稿写入，防止浏览动作污染新单草稿
      if (!this.isOrderDirty) return;
      this.draftStatusText = "草稿保存中...";
      this.draftStatusType = "saving";
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this._draftTimer = setTimeout(() => {
        this.saveDraftIfMeaningful();
      }, 5000);
    },
    /** 保存草稿（有实际内容才保存；后端为主 + localStorage 兜底断网场景） */
    saveDraftIfMeaningful() {
      if (!this.isOrderDirty || this.browseMode) return;
      const deptId = this.orderForm.customerDeptId;
      const hasContent = (this.orderDetailList || []).some(
        (row) =>
          row.productName &&
          (XEUtils.toNumber(row.num) > 0 || row.skuId)
      );
      if (!deptId || !hasContent) {
        this.draftStatusText = "未保存";
        this.draftStatusType = "idle";
        return;
      }
      const payload = {
        orderId: this.orderForm.orderId,
        customerId: this.orderForm.customerId,
        customerDeptId: deptId,
        deptName: this.deptNameOf(deptId),
        orderCode: this.orderForm.orderCode,
        deliveryDate: this.orderForm.deliveryDate,
        remark: this.orderForm.remark,
        details: this.deepCloneOrderDetailList(this.orderDetailList),
        savedAt: new Date().toISOString(),
      };
      // 双写：localStorage 兜底断网，后端为主（换电脑可恢复）
      saveDraft(payload);
      syncDraftToServer(payload);
      this.draftStatusText = `已于 ${this.formatSavedAt(new Date())} 自动保存`;
      this.draftStatusType = "saved";
    },
    /** 页面刷新/关闭前立即保存 */
    handleBeforeUnload() {
      if (this._draftTimer) clearTimeout(this._draftTimer);
      this.saveDraftIfMeaningful();
    },

    /* ========== 常用商品面板（Phase 1.2） ========== */
    /** 加载近30天下单频率 Top20（当前客户+当前配送点白名单） */
    loadFrequentProducts() {
      const customerId = this.orderForm.customerId;
      if (!customerId) {
        this.frequentList = [];
        return;
      }
      frequentSaleDetail({
        customerId,
        days: 30,
        limit: 20,
        deliveryPointId: this.orderForm.customerDeptId || undefined,
      }).then((response) => {
        this.frequentList = response.data || [];
      });
    },
    /** 点击常用商品：插入明细行（末尾）→ 取价 → 光标落数量列 */
    addFrequentProduct(item) {
      this.handleAddRow(-1);
      const row = this.orderDetailList[this.orderDetailList.length - 1];
      row.productName = item.productName;
      row.productUnit = item.productUnit || "斤";
      row.productSpec = item.productSpec || "";
      row.skuId = item.skuId;
      row.isTemp = 0;
      row.remark = "";
      // 带出客户商品池展示价（若有），交易价仍走取价引擎
      const pool = this.skuQuoteDetails.find((q) => q.skuId === item.skuId);
      row.productPrice = pool ? pool.price : "0.00";
      row.refPrice = pool ? pool.price : null;
      row.priceChanged = false;
      row.priceSource = "quote";
      row.priceReason = "";
      row.amount = "0.00";
      this.applyPriceQuery(row);
      // 光标跳至数量列
      this.$nextTick(() => {
        this.$refs.xTable.setEditCell(row, "num");
      });
      // 末尾追加空行，保持录单流
      this.handleAddRow(-1);
    },

    /* ========== S1-1.2 键盘焦点流与高频行命令 ========== */
    /** 推入撤销快照（上限 50 条，浅容量控制） */
    pushUndoSnapshot() {
      if (this.browseMode || !this.canEditOrder) return;
      this.undoStack.push(JSON.stringify(this.orderDetailList));
      if (this.undoStack.length > 50) this.undoStack.shift();
    },
    /** 撤销最近一次编辑：恢复最近一份快照 */
    undoLastEdit() {
      const snap = this.undoStack.pop();
      if (!snap) return;
      try {
        this.orderDetailList = JSON.parse(snap);
        this.$modal.msgSuccess("已撤销最近一次编辑");
      } catch (e) {
        this.$modal.msgError("撤销失败");
      }
    },
    /** 取当前编辑中的行（无则 null） */
    activeEditRow() {
      const cell = this.$refs.xTable && this.$refs.xTable.getEditCell();
      return cell && cell.row ? cell : null;
    },
    /** Enter 焦点流：数量→单价→下一行商品名；未在编辑时不起编状态（返回 false 下传） */
    handleEnterFocusFlow() {
      const $table = this.$refs.xTable;
      if (!$table) return false;
      const cell = this.activeEditRow();
      if (!cell) return false;
      const field = cell.column && cell.column.field;
      if (field === "num") {
        // 数量完成 → 单价
        this.calcAmount(cell.row);
        $table.setEditCell(cell.row, "productPrice");
        return true;
      }
      if (field === "productPrice") {
        // 单价完成 → 下一行商品名（最后一行则自动追加）
        this.calcAmount(cell.row);
        const idx = this.orderDetailList.indexOf(cell.row);
        if (idx === this.orderDetailList.length - 1) {
          this.handleAddRow(-1);
        }
        const nextRow = this.orderDetailList[Math.min(idx + 1, this.orderDetailList.length - 1)];
        this.$nextTick(() => $table.setEditCell(nextRow, "productName"));
        return true;
      }
      if (field === "productName") {
        // 商品名完成 → 数量（与选品下拉的自动跳转一致）
        $table.setEditCell(cell.row, "num");
        return true;
      }
      return false;
    },
    /** F2 插入空行：当前编辑行下方插入，否则末尾追加 */
    insertRowAtActive() {
      if (!this.orderForm.customerDeptId && (this.orderDetailList || []).length >= 1) {
        this.$modal.msgWarning("请先选择送货单位");
        return;
      }
      const cell = this.activeEditRow();
      if (cell) {
        const idx = this.orderDetailList.indexOf(cell.row);
        this.throttledAddRow(idx);
      } else {
        this.throttledAddRow(-1);
      }
    },
    /** Ctrl+D 复制当前行：在下方插入副本并重取当期价 */
    copyActiveRow() {
      const cell = this.activeEditRow();
      const source = cell ? cell.row : this.orderDetailList[this.orderDetailList.length - 1];
      if (!source || !source.productName) {
        this.$modal.msgWarning("当前行没有可复制的商品");
        return;
      }
      this.pushUndoSnapshot();
      const idx = this.orderDetailList.indexOf(source);
      const clone = {
        ...JSON.parse(JSON.stringify(source)),
        num: source.num,
        refPrice: source.refPrice,
        priceChanged: false,
        priceReason: source.priceReason || "",
      };
      this.orderDetailList.splice(idx + 1, 0, clone);
      if (source.skuId) {
        this.applyPriceQuery(clone);
      }
      this.$nextTick(() => this.$refs.xTable.setEditCell(clone, "num"));
    },
    /** F4 重复上一行：将上一行商品/单位/规格复制到当前行（保留当前行数量），重取价 */
    repeatPrevRow() {
      const cell = this.activeEditRow();
      const target = cell ? cell.row : this.orderDetailList[this.orderDetailList.length - 1];
      if (!target) return;
      const idx = this.orderDetailList.indexOf(target);
      if (idx <= 0) {
        this.$modal.msgWarning("当前已是第一行，没有可重复的上一行");
        return;
      }
      const prev = this.orderDetailList[idx - 1];
      if (!prev || !prev.productName) {
        this.$modal.msgWarning("上一行没有商品");
        return;
      }
      this.pushUndoSnapshot();
      target.productName = prev.productName;
      target.productUnit = prev.productUnit;
      target.productSpec = prev.productSpec;
      target.skuId = prev.skuId;
      target.isTemp = prev.isTemp;
      target.productPrice = prev.productPrice;
      target.priceChanged = false;
      if (target.skuId) {
        this.applyPriceQuery(target);
      }
      this.$nextTick(() => this.$refs.xTable.setEditCell(target, "num"));
    },
    /** 批量粘贴：解析剪贴板多行文本（商品名 [数量] [单价] [单位]），按客户商品池匹配后插入 */
    async handleTablePaste(e) {
      if (!this.canEditOrder) return;
      const text = e.clipboardData && e.clipboardData.getData("text/plain");
      if (!text || !text.includes("\n")) return; // 单行粘贴仍走原生行为（不影响输入框）
      e.preventDefault();
      if (!this.orderForm.customerDeptId) {
        this.$modal.msgWarning("请先选择送货单位");
        return;
      }
      const lines = String(text)
        .split(/\r?\n/)
        .map((l) => l.trim())
        .filter(Boolean)
        .slice(0, 200); // 上限保护
      if (!lines.length) return;
      // 确保商品池已加载（无关键词拉全量）
      if (!this.skuQuoteDetails.length) {
        await this.getSkuQuoteDetailList("");
      }
      this.pushUndoSnapshot();
      let matched = 0;
      const newRows = [];
      lines.forEach((line) => {
        const cells = line.split("\t").map((c) => c.trim());
        const name = cells[0];
        if (!name) return;
        const num = cells[1] && !isNaN(parseFloat(cells[1])) ? parseFloat(cells[1]) : 1;
        const price = cells[2] && !isNaN(parseFloat(cells[2])) ? parseFloat(cells[2]) : null;
        const unit = cells[3] || "";
        const lowerName = name.toLowerCase();
        const hit =
          this.skuQuoteDetails.find((q) => q.productName === name) ||
          this.skuQuoteDetails.find(
            (q) => q.productName.toLowerCase() === lowerName ||
              (q.productMnemonicCode && q.productMnemonicCode.toLowerCase() === lowerName)
          );
        const row = {
          productName: hit ? hit.productName : name,
          productUnit: unit || (hit ? hit.productUnit : "斤"),
          productSpec: hit ? hit.productSpec : "",
          skuId: hit ? hit.skuId : null,
          isTemp: hit && hit.isTemp ? 1 : 0,
          num: XEUtils.commafy(num, { digits: 2 }),
          productPrice: price != null ? XEUtils.commafy(price, { digits: 2 }) : hit ? hit.price : "0.00",
          refPrice: null,
          priceChanged: false,
          priceSource: "manual",
          priceReason: "批量粘贴录入",
          amount: "0.00",
          remark: "",
        };
        if (hit) {
          matched++;
          if (price == null) {
            // 未手工给价：走取价引擎，命中报价则来源回归报价
            row.priceSource = "quote";
            row.priceReason = "";
            row.refPrice = null;
          } else {
            row.refPrice = hit.price;
          }
        }
        newRows.push(row);
      });
      if (!newRows.length) {
        this.$modal.msgWarning("剪贴板内容未能解析出商品行");
        return;
      }
      // 去掉末尾空占位行再追加
      const last = this.orderDetailList[this.orderDetailList.length - 1];
      if (last && !last.productName) this.orderDetailList.pop();
      this.orderDetailList.push(...newRows);
      this.handleAddRow(-1);
      newRows.forEach((row) => {
        this.calcAmount(row);
        if (row.skuId && row.priceSource === "quote") {
          this.applyPriceQuery(row);
        }
      });
      this.$modal.msgSuccess(`已粘贴 ${newRows.length} 行（匹配商品池 ${matched} 行）`);
    },

    /* ========== S1-1.3 手工定价留痕（简化版：去必填原因，保留来源/原价审计） ========== */
    /** 单价变更：命中报价偏离或无报价手工填写时标记为手工定价（priceSource=manual）并轻提示 */
    handlePriceChange(row) {
      this.calcAmount(row);
      const price = XEUtils.toNumber(row.productPrice);
      // 无价/零价：不算手工定价（未完成录价状态）
      if (!price) {
        row.priceChanged = false;
        row.priceSource = row.priceSource === "manual" ? null : row.priceSource;
        return;
      }
      const refPrice = row.refPrice != null ? XEUtils.toNumber(row.refPrice) : null;
      const deviated = refPrice == null || Math.abs(price - refPrice) > 0.005;
      if (deviated && !row.priceChanged) {
        // 与当期报价不一致轻提示（不打断，不强制填原因）
        if (refPrice != null) {
          this.$modal.msgWarning(
            "商品【" + row.productName + "】单价 " + price.toFixed(2) + " 与当期报价 " + refPrice.toFixed(2) + " 不一致，请确认"
          );
        }
        // S1-1.3：偏离报价即为手工定价，标记来源供服务端确认时审计（原建议价/操作者/时间留痕）
        row.priceSource = "manual";
        row.priceReason = "";
      } else if (!deviated) {
        // 恢复到与报价一致：回到报价口径
        row.priceSource = refPrice != null ? "quote" : row.priceSource;
      }
      row.priceChanged = deviated;
    },

    /* ========== S1-1.5 草稿箱 ========== */
    openDraftBox() {
      this.availableDrafts = listDrafts();
      this.draftBoxVisible = true;
    },
    restoreDraftFromBox(draft) {
      this.draftBoxVisible = false;
      this.restoreDraftIntoForm(draft);
    },
    deleteDraftFromBox(draft) {
      this.$confirm(`确定删除草稿【${draft.deptName || draft.orderCode || "未命名"}】？删除后不可恢复。`, "提示", {
        type: "warning",
      })
        .then(() => {
          removeDraft(draft.key);
          removeDraftFromServer(draft.orderId, draft.customerDeptId);
          this.availableDrafts = listDrafts();
          this.refreshDraftBanner();
          this.$modal.msgSuccess("草稿已删除");
        })
        .catch(() => {});
    },
    /** 格式化完整保存时间（草稿箱列表用） */
    formatFullSavedAt(savedAt) {
      if (!savedAt) return "";
      const d = new Date(savedAt);
      const p = (n) => String(n).padStart(2, "0");
      return `${d.getMonth() + 1}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    },

    /* ========== S1 右侧搜索面板 ========== */
    focusSearchPanel() {
      this.rightTab = "search";
      this.$nextTick(() => {
        if (this.$refs.searchInput) {
          this.$refs.searchInput.focus();
        }
      });
    },
    /** 搜索面板：按关键词检索客户商品池+临时商品 */
    handleSearchProducts() {
      if (!this.orderForm.customerId) {
        this.$modal.msgWarning("请先选择送货单位");
        return;
      }
      this.getSkuQuoteDetailList(this.searchKeyword || "").then(() => {
        this.searchResults = this.skuQuoteDetails;
      });
    },
    /** 通用插入商品行（常用/搜索面板共用）：插入末尾 → 取价 → 焦点落数量 */
    insertProductFromPanel(item) {
      if (!this.canEditOrder) return;
      this.pushUndoSnapshot();
      // 复用 addFrequentProduct 的插入逻辑（末尾插入已由其内部处理）
      this.addFrequentProduct(item);
    },

    /* ========== 下拉关键词高亮（Phase 1.3） ========== */
    escapeHtml(s) {
      return String(s == null ? "" : s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
    },
    /** 匹配关键词包一层高亮 span（先转义再高亮，防 XSS） */
    highlightKeyword(name) {
      const raw = String(name == null ? "" : name);
      const kw = String(this.pulldownKeyword || "").trim();
      if (!kw) return this.escapeHtml(raw);
      const lower = raw.toLowerCase();
      const kl = kw.toLowerCase();
      let out = "";
      let idx = 0;
      let pos = lower.indexOf(kl);
      while (pos !== -1) {
        out += this.escapeHtml(raw.slice(idx, pos));
        out +=
          '<span class="kw-hl">' +
          this.escapeHtml(raw.slice(pos, pos + kw.length)) +
          "</span>";
        idx = pos + kw.length;
        pos = lower.indexOf(kl, idx);
      }
      out += this.escapeHtml(raw.slice(idx));
      return out;
    },
  },
};
</script>

<style lang="scss" scoped>
/* S1 分屏工作区：占满视口可用高度，左右卡片各自内部滚动 */
.order-split {
  height: calc(100vh - 130px);
  min-height: 480px;
}

.order-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.order-header,
.order-footer {
  flex-shrink: 0; /* 确保头部和底部不被压缩 */
}

.order-table-container {
  flex-grow: 1; /* 让表格区域占据剩余的所有空间 */
  overflow-y: auto; /* 如果内容超出容器高度，允许滚动 */
}

.order-table {
  width: 100%;
  height: 100%;
}

.recent-order-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.recent-order-table-container {
  flex-grow: 1; /* 让表格区域占据剩余的所有空间 */
  overflow-y: auto; /* 如果内容超出容器高度，允许滚动 */
}

.drag-btn {
  cursor: move;
  font-size: 12px;
}

.product-name-dropdown {
  background-color: #fff;
  box-shadow: 0 0 6px 2px rgba(0, 0, 0, 0.1);

  .product-dropdown-planel {
    width: 600px;
    height: 300px;
  }

  .my-footdown4 {
    border-top: 1px solid #e8eaec;
  }
}

/* 草稿恢复横幅 */
.draft-recover-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    .el-button + .el-button {
      margin-left: 8px;
    }
  }
}

/* 编辑已有草稿订单提示横幅 */
.edit-existing-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    display: inline-flex;
    align-items: center;
    gap: 10px;
  }
}

/* 订单头部草稿状态标签 */
.order-header-title {
  margin-right: 10px;
}
.draft-box-btn {
  margin-right: 10px;
  vertical-align: middle;
}
.draft-status-tag {
  display: inline-block;
  padding: 0 8px;
  font-size: 12px;
  line-height: 20px;
  border-radius: 3px;
  margin-right: 10px;
  vertical-align: middle;
  &.saving {
    color: #909399;
    background: #f4f4f5;
  }
  &.saved {
    color: #67c23a;
    background: #f0f9eb;
  }
  &.idle {
    color: #e6a23c;
    background: #fdf6ec;
  }
}

/* 右侧选单区 Tabs + 常用面板 */
.right-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
  :deep(.el-tabs__nav-wrap::after) {
    height: 0;
  }
}
.frequent-panel {
  overflow-y: auto;
  .frequent-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }
  .frequent-list {
    padding: 4px 0;
    .frequent-item {
      display: flex;
      align-items: center;
      padding: 6px 10px;
      cursor: pointer;
      border-bottom: 1px dashed #ebeef5;
      &:hover {
        background: #f5f7fa;
      }
      .frequent-index {
        width: 22px;
        color: #c0c4cc;
        font-size: 12px;
        flex-shrink: 0;
      }
      .frequent-name {
        flex: 1;
        font-size: 13px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        .frequent-spec {
          color: #909399;
          font-style: normal;
        }
      }
      .frequent-unit {
        margin-left: 8px;
        color: #909399;
        font-size: 12px;
        flex-shrink: 0;
      }
      .frequent-count {
        margin-left: 8px;
        color: #409eff;
        font-size: 12px;
        flex-shrink: 0;
      }
    }
  }
}

/* 下拉商品名称：关键词高亮 + 来源标签 */
.prod-name-cell {
  .kw-hl {
    color: #f56c6c;
    font-weight: 600;
  }
  .prod-source-tag {
    margin-left: 6px;
  }
}
/* 客户+配送点整体锁定的级联行 */
.dept-cascader-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.reopen-new-btn {
  padding-left: 4px;
}
/* 浏览模式横幅 */
.browse-banner {
  margin-bottom: 10px;
  .draft-banner-title {
    display: inline-flex;
    align-items: center;
    gap: 10px;
  }
}
/* S1 右侧搜索面板 */
.search-panel {
  .search-bar {
    display: flex;
    gap: 8px;
    padding: 8px 10px;
  }
  .search-list {
    height: calc(100% - 48px);
    overflow-y: auto;
  }
}

/* 手工定价：单价单元格「手」标签 */
.price-cell {
  display: inline-flex;
  align-items: center;
  .price-manual-tag {
    margin-left: 4px;
    transform: scale(0.85);
  }
}

/* 改价提醒：单价与当期报价不一致的单元格高亮 + "改"角标 */
:deep(.col-price-changed) {
  background-color: #fdf6ec !important;
}
:deep(.col-price-changed .vxe-cell)::after {
  content: "改";
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 12px;
  line-height: 18px;
  color: #e6a23c;
  border: 1px solid #e6a23c;
  border-radius: 3px;
}

/* 按客户批量确认抽屉底部 */
.batch-confirm-footer {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.batch-confirm-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 18px;
}

/* ===== OA：订单验收模式（《订单页一键验收链路设计》） ===== */
/* 表头订单/客户信息行（验收模式下替代表单） */
.acc-header-info {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  margin-left: 16px;
  font-size: 13px;
  color: #606266;
}
.acc-header-info span b {
  color: #303133;
}
/* 商品名变更标记 tag */
.acc-row-tag {
  margin-left: 4px;
}
/* 差异配色：负=短收红，正=超收橙（同验收页口径） */
.acc-diff-short {
  color: #f56c6c;
  font-weight: bold;
}
.acc-diff-over {
  color: #e6a23c;
  font-weight: bold;
}
.acc-reason-none {
  color: #c0c4cc;
}
/* 行内编辑：商品名搜索输入 */
/* 差异行底色；超阈值（±20%）行强化 */
:deep(.acc-row-diff) {
  background: #fdf6ec;
}
:deep(.acc-row-diff-over) {
  background: #fef0f0;
}
</style>
