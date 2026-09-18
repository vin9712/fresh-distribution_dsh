package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.exception.ServiceException;
import com.lin.distribution.dto.PrintTicketPayload;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.service.PrintTemplateService;
import com.lin.distribution.service.PrintTicketService;
import com.lin.distribution.util.PrintBizKeys;
import com.lin.distribution.util.ShiftCodes;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryMatrixVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 打印数据接口（供 JimuReport API 数据集调用）
 * 
 * <p>W0-4.1 票据鉴权：/print/deliveryHead、/print/deliveryData 仍由 SecurityConfig 放行
 * （JimuReport 服务端回调不带 JWT），但改为强校验短时一次性打印票据（ticket 参数，
 * 由报表视图 URL 经数据集 URL 占位符 {@code ${ticket}} 透传），且票据必须与请求
 * deliveryOrderId 绑定一致，不再是无凭据裸奔。
 * 签发接口 {@code POST /print/ticket} 走正常 JWT 过滤器 + RBAC 权限。
 * 打印品名客户映射叫法优先（DESIGN 不变量 8：无映射用我方品名快照）。
 *
 * @author dsh
 */
@Slf4j
@Tag(name = "打印数据接口")
@RestController
@RequestMapping("/print")
public class PrintController extends BaseController {
    @Autowired
    private DeliveryOrderMapper deliveryOrderMapper;
    @Autowired
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Autowired
    private CustomerSkuMappingMapper customerSkuMappingMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private CustomerDeptMapper customerDeptMapper;
    @Autowired
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Autowired
    private DeliveryBatchService deliveryBatchService;
    @Autowired
    private PrintTicketService printTicketService;
    @Autowired
    private PrintTemplateService printTemplateService;

    /**
     * 按打印主体键解析打印模板（PT-1，《客户日报表打印优化设计》§3.1）：
     * 替代前端硬编码模板ID——bizKey 前缀判形态（matrix→MATRIX / point→FLAT），
     * 后端三级绑定解析（客户+点 &gt; 客户 &gt; 全局默认，同形态已发布），返回报表视图ID 供前端 open。
     *
     * @param bizKey 打印主体键（matrix:{customerId}:{date} / point:{customerId}:{deptId}:{date}）
     * @return PrintTemplateResolveVO（无已发布模板时 templateId=null + warning）
     */
    @Operation(summary = "按打印主体键解析打印模板")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,print:template:list')")
    @GetMapping("/resolve-template")
    public AjaxResult resolveTemplate(@RequestParam("bizKey") String bizKey) {
        return success(printTemplateService.resolveByBizKey(bizKey));
    }

    /**
     * 签发短时一次性打印票据（W0-4.1：替代 URL 携带长期 JWT）
     * 打印送货单用 order:delivery:print；打开报表设计器/预览用 print:template:list。
     *
     * @param body {deliveryOrderId?: Long, bizKey?: String, templateId?: Long}
     *             bizKey 为 D-055 视图化打印主体键（无送货单ID 的 客户+日期(+点) 打印）
     * @return {ticket: "ptk_..."}，TTL 300 秒、一次性兑换
     */
    @Operation(summary = "签发短时一次性打印票据")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,print:template:list')")
    @PostMapping("/ticket")
    public AjaxResult issueTicket(@RequestBody(required = false) Map<String, Object> body) {
        Long deliveryOrderId = body == null ? null : toLong(body.get("deliveryOrderId"));
        Long templateId = body == null ? null : toLong(body.get("templateId"));
        String bizKey = body == null ? null : toBizKey(body.get("bizKey"));
        String ticket = StringUtils.isBlank(bizKey)
                ? printTicketService.issue(deliveryOrderId, templateId)
                : printTicketService.issueByBizKey(bizKey, templateId);
        AjaxResult result = AjaxResult.success();
        result.put("ticket", ticket);
        return result;
    }

    private String toBizKey(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    /**
     * 票据批量签发（PT-4，《客户日报表打印优化设计》§3.4）：批量打印队列预取下一张票据用，
     * 语义与单签完全一致（各自 TTL）。上限 50，超出拒绝。
     *
     * @param body {bizKeys: ["matrix:10:2026-09-08", ...]}
     * @return {tickets: {bizKey: ticket}}
     */
    @Operation(summary = "批量签发打印票据")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,print:template:list')")
    @PostMapping("/ticket/batch")
    public AjaxResult issueTicketsBatch(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("bizKeys");
        if (!(raw instanceof List)) {
            throw new ServiceException("bizKeys 不能为空");
        }
        List<?> keys = (List<?>) raw;
        if (keys.isEmpty()) {
            return AjaxResult.success(new LinkedHashMap<String, String>());
        }
        if (keys.size() > 50) {
            throw new ServiceException("单次批量签发上限 50 张");
        }
        Map<String, String> tickets = new LinkedHashMap<>();
        for (Object key : keys) {
            String bizKey = toBizKey(key);
            if (bizKey != null) {
                tickets.put(bizKey, printTicketService.issueByBizKey(bizKey, null));
            }
        }
        AjaxResult result = AjaxResult.success();
        result.put("tickets", tickets);
        return result;
    }

    /**
     * 打印回执（PT-3，《客户日报表打印优化设计》§3.3）：
     * JimuReport 页面（print-annotation.js，s26 起 v=11）在真实打印动作后回传票据，
     * 凭票据定位打印主体并登记打印分界（开窗不登记，真实打印才登记）。
     * 安全：SecurityConfig 放行 + 票据自证（与数据集回调同源同强度，负载含签发人与主体绑定）；
     * 历史单主体（deliveryOrderId 绑定）忽略——历史单走 /order/delivery/{id}/mark-printed，不混写。
     *
     * @param body {ticket: "ptk_..."}
     */
    @Operation(summary = "打印回执登记")
    @PostMapping("/receipt")
    public AjaxResult receipt(@RequestBody(required = false) Map<String, Object> body) {
        String ticket = body == null ? null : toBizKey(body.get("ticket"));
        PrintTicketPayload payload = printTicketService.consumeForReceipt(ticket);
        AjaxResult result = AjaxResult.success();
        result.put("registered", false);
        if (payload != null && StringUtils.isNotBlank(payload.getBizKey())) {
            try {
                PrintBizKeys.BizKeyInfo info = PrintBizKeys.parse(payload.getBizKey());
                deliveryBatchService.markPrinted(info.getCustomerId(), info.getDeliveryDate(),
                        info.getCustomerDeptId(), payload.getTemplateId());
                result.put("registered", true);
                result.put("bizKey", payload.getBizKey());
            } catch (Exception e) {
                // 回执端永远 2xx：登记失败仅记录，不打断报表页打印（失败方向安全=保持未打印）
                log.warn("[print-receipt] 登记失败 bizKey={}: {}", payload.getBizKey(), e.getMessage());
            }
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ServiceException("非法的打印票据参数: " + value);
        }
    }

    /**
     * 送货单表头打印数据（JimuReport 单值数据集 hd）
     *
     * <p>取数主体二选一（W0-4.1 票据绑定随之二选一）：</p>
     * <ul>
     *   <li>{@code deliveryOrderId}——D-055 前的历史送货单（只读，票据须绑定该单）；</li>
     *   <li>{@code customerId + customerDeptId + deliveryDate}——D-055 视图化点单
     *       （客户+日期+配送点，实时取订单明细，票据绑定 bizKey={@code point:<客户>:<点>:<日期>}）。</li>
     * </ul>
     */
    @Operation(summary = "送货单表头打印数据")
    @GetMapping("/deliveryHead")
    public Map<String, Object> deliveryHead(@RequestParam(value = "deliveryOrderId", required = false) String deliveryOrderIdParam,
                                            @RequestParam(value = "customerId", required = false) String customerIdParam,
                                            @RequestParam(value = "customerDeptId", required = false) String customerDeptIdParam,
                                            @RequestParam(value = "deliveryDate", required = false) String deliveryDateParam,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        Long deliveryOrderId = toIdOrNull(deliveryOrderIdParam);
        Long customerId = toIdOrNull(customerIdParam);
        Long customerDeptId = toIdOrNull(customerDeptIdParam);
        String deliveryDate = blankToNull(deliveryDateParam);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        resp.put("head", head);
        if (deliveryOrderId != null) {
            checkTicket(deliveryOrderId, ticket);
            DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
            if (order == null) {
                return resp;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (DeliveryOrderDetail detail : deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId)) {
                total = total.add(detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());
            }
            head.put("code", order.getCode());
            head.put("customerName", order.getCustomerName());
            head.put("deliveryPointName", order.getCustomerDeptName());
            head.put("deliveryDate", order.getDeliveryDate() == null ? "" : order.getDeliveryDate().toString());
            head.put("totalAmount", total);
            return resp;
        }
        PointPrint point = loadPointPrint(customerId, customerDeptId, deliveryDate, ticket);
        head.put("code", "");
        head.put("customerId", point.customerId);
        head.put("customerDeptId", point.customerDeptId);
        head.put("customerName", point.customerName);
        head.put("deliveryPointName", point.deptName);
        head.put("deliveryDate", point.deliveryDate);
        head.put("totalAmount", point.totalAmount);
        head.put("totalNum", point.totalNum);
        return resp;
    }

    /**
     * 送货单明细打印数据（JimuReport 列表数据集 dd）
     *
     * <p>主体同 {@link #deliveryHead}：历史送货单按 deliveryOrderId；D-055 点单按
     * 客户+日期+配送点 实时取订单明细（应送=num、单价=下单价快照、验收数留空由送货员手填）。</p>
     */
    @Operation(summary = "送货单明细打印数据")
    @GetMapping("/deliveryData")
    public Map<String, Object> deliveryData(@RequestParam(value = "deliveryOrderId", required = false) String deliveryOrderIdParam,
                                            @RequestParam(value = "customerId", required = false) String customerIdParam,
                                            @RequestParam(value = "customerDeptId", required = false) String customerDeptIdParam,
                                            @RequestParam(value = "deliveryDate", required = false) String deliveryDateParam,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        Long deliveryOrderId = toIdOrNull(deliveryOrderIdParam);
        Long customerId = toIdOrNull(customerIdParam);
        Long customerDeptId = toIdOrNull(customerDeptIdParam);
        String deliveryDate = blankToNull(deliveryDateParam);
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        resp.put("rows", rows);
        if (deliveryOrderId != null) {
            checkTicket(deliveryOrderId, ticket);
            DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
            if (order == null) {
                return resp;
            }
            Map<Long, String> aliasBySku = customerAliasBySku(order.getCustomerId());
            int seq = 1;
            for (DeliveryOrderDetail detail : deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seq", seq++);
                String productName = detail.getSkuId() != null ? aliasBySku.get(detail.getSkuId()) : null;
                row.put("productName", StringUtils.isBlank(productName) ? detail.getProductName() : productName);
                row.put("productSpec", detail.getProductSpec());
                row.put("productUnit", detail.getProductUnit());
                row.put("num", detail.getNum());
                row.put("price", detail.getPrice());
                row.put("amount", detail.getAmount());
                // 验收数(留空)：客户签收时由送货员手填，纸面为空白列，仅供模板绑定「验收数」栏
                row.put("acceptanceNum", "");
                row.put("changeTag", "");
                rows.add(row);
            }
            return resp;
        }
        PointPrint point = loadPointPrint(customerId, customerDeptId, deliveryDate, ticket);
        Map<Long, String> aliasBySku = customerAliasBySku(point.customerId);
        int seq = 1;
        for (SaleOrderDetail detail : point.details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", seq++);
            String alias = detail.getSkuId() != null ? aliasBySku.get(detail.getSkuId()) : null;
            row.put("productName", StringUtils.isBlank(alias) ? detail.getProductName() : alias);
            row.put("productSpec", detail.getProductSpec());
            row.put("productUnit", detail.getProductUnit());
            row.put("num", detail.getNum());
            row.put("price", detail.getProductPrice());
            row.put("amount", amount(detail));
            row.put("acceptanceNum", "");
            // 变更标记（D-055）：加单/换货/退货，正常行为空；针式模板可选绑定
            row.put("changeTag", changeTagOf(detail));
            rows.add(row);
        }
        return resp;
    }

    /**
     * 矩阵总表打印数据（JimuReport 数据集 dm，D-044/D-046/D-051 + D-055 视图化）
     *
     * <p>取数主体二选一：{@code deliveryOrderId}（D-055 前历史送货单，仅打本单行）或
     * {@code customerId + deliveryDate}（D-055 视图化：客户+日期 总单，打该日全部行，
     * 数据源=订单明细，票据绑定 bizKey={@code matrix:<客户>:<日期>}）。</p>
     *
     * <p>行=菜品（五元组合并行，不同价必拆行 D-024）、列=配送点（含当日无单空列）、格=应送量。
     * 纸面<b>不打单价与金额</b>（D-046），同名多行以备注列「档①」区分。列数超出 colsPerPage
     * 时按 colBlock 横向分页，每页重复品名列。格位固定输出 c1..c{colsPerPage}（未用位置 null），
     * 保证套打列位不漂移。</p>
     *
     * @param deliveryOrderId 送货单ID（历史单证打印）
     * @param customerId      客户ID（D-055 视图化打印）
     * @param deliveryDate    配送日期 yyyy-MM-dd（D-055 视图化打印）
     * @param colBlock        列块序号（1 起，缺省 1；仅 rowsType=wide 有意义）
     * @param rowsType        行形态：wide=槽位宽表（默认，c1~cN，套打）；long=全交叉长表（菜品×配送点，
     *                        一格一行，横向动态列模板用；deptSeq 全列输出保证首条数据完整）
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {head:{...}, columns:[...], rows:[...]}
     */
    @Operation(summary = "矩阵总表打印数据")
    @GetMapping("/deliveryMatrixData")
    public Map<String, Object> deliveryMatrixData(@RequestParam(value = "deliveryOrderId", required = false) String deliveryOrderIdParam,
                                                  @RequestParam(value = "customerId", required = false) String customerIdParam,
                                                  @RequestParam(value = "deliveryDate", required = false) String deliveryDateParam,
                                                  @RequestParam(value = "colBlock", required = false, defaultValue = "1") Integer colBlock,
                                                  @RequestParam(value = "rowsType", required = false, defaultValue = "wide") String rowsTypeParam,
                                                  @RequestParam(value = "ticket", required = false) String ticket) {
        Long deliveryOrderId = toIdOrNull(deliveryOrderIdParam);
        Long customerId = toIdOrNull(customerIdParam);
        String deliveryDate = blankToNull(deliveryDateParam);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        resp.put("head", head);
        resp.put("columns", new ArrayList<Map<String, Object>>());
        resp.put("rows", new ArrayList<Map<String, Object>>());

        DeliveryOrder order = null;
        Long subjectCustomerId = customerId;
        String subjectDate = deliveryDate;
        if (deliveryOrderId != null) {
            // 历史送货单主体：票据绑定送货单，客户/日期取单头
            checkTicket(deliveryOrderId, ticket);
            order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
            if (order == null || order.getCustomerId() == null || order.getDeliveryDate() == null) {
                return resp;
            }
            subjectCustomerId = order.getCustomerId();
            subjectDate = order.getDeliveryDate().toString();
        } else {
            if (subjectCustomerId == null || subjectDate == null) {
                throw new ServiceException("打印取数须指定送货单或 客户+配送日期");
            }
            checkTicketByBizKey(matrixBizKey(subjectCustomerId, subjectDate), ticket);
        }
        DeliveryMatrixVO matrix = deliveryBatchService.selectMatrix(subjectCustomerId, subjectDate);

        int block = colBlock == null || colBlock < 1 ? 1 : colBlock;
        int totalBlocks = matrix.getColBlocks() == null || matrix.getColBlocks() < 1 ? 1 : matrix.getColBlocks();
        if (block > totalBlocks) {
            block = totalBlocks;
        }
        final int blockNo = block;
        List<DeliveryMatrixVO.ColumnVO> blockColumns = matrix.getColumns().stream()
                .filter(c -> c.getBlockNo() != null && c.getBlockNo().intValue() == blockNo)
                .collect(Collectors.toList());
        int slots = matrix.getColsPerPage() == null || matrix.getColsPerPage() <= 0
                ? Math.max(blockColumns.size(), 1) : matrix.getColsPerPage();

        // 列头（未用槽位补空列，套打列位固定；long 模式不补空槽、只输出实际配送点）
        boolean longRows = "long".equalsIgnoreCase(rowsTypeParam);
        List<Map<String, Object>> columnMetas = new ArrayList<>();
        if (longRows) {
            int deptSeq = 1;
            for (DeliveryMatrixVO.ColumnVO column : blockColumns) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("deptSeq", deptSeq);
                meta.put("seq", meta.get("deptSeq"));
                meta.put("deptId", column.getDeptId());
                meta.put("deptCode", StringUtils.defaultString(column.getCode()));
                meta.put("deptName", StringUtils.defaultString(column.getName()));
                // 列头显示名：带零填充序号（01·点心）。横向动态列的组序由引擎决定不受控，
                // 序号前缀保证纸面列序可读可对位（引擎按字符串序/首现序均稳定）
                meta.put("deptLabel", String.format("%02d·%s", deptSeq, StringUtils.defaultString(column.getName())));
                meta.put("adHoc", column != null && Boolean.TRUE.equals(column.getAdHoc()));
                meta.put("hasData", column != null && Boolean.TRUE.equals(column.getHasData()));
                meta.put("empty", Boolean.FALSE);
                columnMetas.add(meta);
                deptSeq++;
            }
        } else {
            for (int i = 0; i < slots; i++) {
                Map<String, Object> meta = new LinkedHashMap<>();
                DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
                meta.put("seq", i + 1);
                meta.put("deptName", column == null ? "" : StringUtils.defaultString(column.getName()));
                meta.put("adHoc", column != null && Boolean.TRUE.equals(column.getAdHoc()));
                meta.put("empty", column == null);
                columnMetas.add(meta);
            }
        }
        resp.put("columns", columnMetas);

        // 行：历史送货单主体只打本单行；D-055 视图化主体打该客户该日全部行
        List<Map<String, Object>> rowMetas = new ArrayList<>();
        int seq = 1;
        int dishCount = 0;
        for (DeliveryMatrixVO.RowVO row : matrix.getRows()) {
            if (deliveryOrderId != null && !deliveryOrderId.equals(row.getDeliveryId())) {
                continue;
            }
            dishCount++;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("seq", seq++);
            // 打印品名客户叫法优先（DESIGN 不变量 8），品名本身保持干净；档位标注进备注列（D-046/D-053 修订）
            String baseName = StringUtils.defaultIfBlank(row.getCustomerAlias(), row.getProductName());
            line.put("productName", DeliveryMatrixLayout.displayProductName(baseName));
            line.put("productSpec", row.getSpec());
            line.put("productUnit", row.getUnit());
            // 备注列：承载档位标注（同名多行不同价时），单档为空保持干净
            line.put("remark", row.getRemark() == null ? "" : row.getRemark());
            if (longRows) {
                // 全交叉：每个配送点一格一行（num=null=当日无此菜，渲染空白）；列序 deptSeq 稳定，
                // 保证横向动态列“第一条数据包含全部分组值”规则（通用模板设计文档 §4.4）。
                // 交叉报表要求横向分组格与数据格同一数据集：行键 rowKey = 显示品名(+档位标注)，
                // 同菜品多档（品名+规格+单价不同）拆行由 rowKey 区分。
                String rowKey = line.get("productName")
                        + (StringUtils.isBlank((String) line.get("remark")) ? "" : "·" + line.get("remark"));
                int deptSeq = 1;
                for (DeliveryMatrixVO.ColumnVO column : blockColumns) {
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("seq", line.get("seq"));
                    cell.put("rowKey", rowKey);
                    cell.put("productName", line.get("productName"));
                    cell.put("productSpec", line.get("productSpec"));
                    cell.put("productUnit", line.get("productUnit"));
                    cell.put("remark", line.get("remark"));
                    cell.put("deptSeq", deptSeq);
                    cell.put("deptId", column.getDeptId());
                    cell.put("deptName", StringUtils.defaultString(column.getName()));
                    // 列头显示名带零填充序号（01·点心）：横向动态列组序由引擎决定，序号前缀保证纸面列序稳定可读
                    cell.put("deptLabel", String.format("%02d·%s", deptSeq, StringUtils.defaultString(column.getName())));
                    cell.put("deptCode", StringUtils.defaultString(column.getCode()));
                    cell.put("shiftCode", StringUtils.defaultString(column.getShiftCode()));
                    BigDecimal qty = row.getCells().get(ShiftCodes.cellKey(column.getDeptId(), column.getShiftCode()));
                    cell.put("num", qty);                          // null=空格
                    cell.put("rowTotal", row.getTotalQuantity());
                    rowMetas.add(cell);
                    deptSeq++;
                }
                continue;
            }
            for (int i = 0; i < slots; i++) {
                DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
                BigDecimal qty = column == null ? null
                        : row.getCells().get(ShiftCodes.cellKey(column.getDeptId(), column.getShiftCode()));
                // 历史单无点级台账：格位打 —（D-051）
                line.put("c" + (i + 1), qty != null ? qty : (Boolean.TRUE.equals(matrix.getHistoryFallback()) ? "—" : ""));
            }
            line.put("total", row.getTotalQuantity());
            rowMetas.add(line);
        }
        resp.put("rows", rowMetas);

        head.put("code", order == null ? "" : order.getCode());
        head.put("docKind", order == null ? null : order.getDocKind());
        head.put("customerId", subjectCustomerId);
        head.put("customerName", matrix.getCustomerName());
        head.put("deliveryDate", subjectDate);
        // 打印标题：<客户>总单（D-055 定稿：总单=客户日总表矩阵，司机对单）
        head.put("printTitle", StringUtils.defaultIfBlank(matrix.getCustomerName(), "") + "总单");
        head.put("colBlockNo", blockNo);
        head.put("totalColBlocks", totalBlocks);
        head.put("colBlockLabel", "列块 " + blockNo + "/" + totalBlocks);
        // 列名槽位 c1Name..c{colsPerPage}（与 rows 的 c1..c6 槽位对齐，空列给空串）——模板列头绑定用
        for (int i = 0; i < slots; i++) {
            DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
            head.put("c" + (i + 1) + "Name", column == null ? "" : StringUtils.defaultString(column.getName()));
        }
        head.put("totalQuantity", matrix.getTotalQuantity());
        // 通用契约新增字段（只增不改名，老模板不失效）：打印主体键/批次/品项数/点数/打印时间
        head.put("bizKey", order == null ? matrixBizKey(subjectCustomerId, subjectDate) : "delivery:" + deliveryOrderId);
        head.put("batchId", matrix.getBatchId());
        head.put("totalKinds", dishCount);
        head.put("pointCount", blockColumns.size());
        head.put("printTime", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        head.put("layoutVersion", matrix.getLayoutVersion());
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(matrix.getLayoutDerived())) {
            warnings.add("本批次无布局快照，列按当前启用配送点实时推导");
        }
        if (Boolean.TRUE.equals(matrix.getHistoryFallback())) {
            warnings.add("历史单无点级分配台账，各点列以 — 占位");
        }
        if (Boolean.FALSE.equals(matrix.getIdentityOk())) {
            warnings.add("恒等式自检不通过（明细数量≠各点分配量合计）："
                    + matrix.getMismatches().stream()
                    .map(m -> m.getProductName() + " " + m.getNum() + "≠" + m.getCellSum())
                    .collect(Collectors.joining("；")));
        }
        head.put("warnings", warnings);
        head.put("identityOk", matrix.getIdentityOk());
        head.put("remark", order == null ? "" : order.getRemark());
        return resp;
    }

    /**
     * W0-4.1：数据接口票据强校验（无效/过期/与送货单绑定不一致一律拒绝）
     */
    private void checkTicket(Long deliveryOrderId, String ticket) {
        if (!printTicketService.validateDataAccess(ticket, deliveryOrderId)) {
            throw new ServiceException("打印票据无效、过期或与单据不匹配，请回到系统重新打印");
        }
    }

    // ==================== D-055 视图化打印辅助 ====================

    /** 矩阵总单打印主体键：matrix:{customerId}:{deliveryDate}（PT-1 起委托 PrintBizKeys 统一契约） */
    static String matrixBizKey(Long customerId, String deliveryDate) {
        return PrintBizKeys.matrix(customerId, deliveryDate);
    }

    /** 点单打印主体键：point:{customerId}:{customerDeptId}:{deliveryDate}（PT-1 起委托 PrintBizKeys 统一契约） */
    static String pointBizKey(Long customerId, Long customerDeptId, String deliveryDate) {
        return PrintBizKeys.point(customerId, customerDeptId, deliveryDate);
    }

    /**
     * D-055 视图化：数据接口票据强校验（按打印主体键绑定，防跨客户/跨日期越权取数）
     */
    private void checkTicketByBizKey(String bizKey, String ticket) {
        if (!printTicketService.validateDataAccessByBizKey(ticket, bizKey)) {
            throw new ServiceException("打印票据无效、过期或与客户+日期取数主体不匹配，请回到系统重新打印");
        }
    }

    /** 点单打印取数结果（客户+日期+配送点 的订单明细行 + 表头要素） */
    private static class PointPrint {
        Long customerId;
        String customerName;
        Long customerDeptId;
        String deptName;
        String deliveryDate;
        List<SaleOrderDetail> details = new ArrayList<>();
        BigDecimal totalNum = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
    }

    /**
     * 装载点单打印数据（D-055：客户+日期+配送点 实时取订单明细，与页面点单口径同源）
     *
     * @param ticket 票据（须绑定 {@code point:<客户>:<点>:<日期>}）
     */
    private PointPrint loadPointPrint(Long customerId, Long customerDeptId, String deliveryDate, String ticket) {
        if (customerId == null || customerDeptId == null || StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("点单打印须指定 客户+配送点+配送日期");
        }
        checkTicketByBizKey(pointBizKey(customerId, customerDeptId, deliveryDate), ticket);
        PointPrint point = new PointPrint();
        point.customerId = customerId;
        point.customerDeptId = customerDeptId;
        point.deliveryDate = deliveryDate;
        Customer customer = customerMapper.selectCustomerById(customerId);
        point.customerName = customer == null ? "" : StringUtils.defaultIfBlank(customer.getAlias(), customer.getName());
        CustomerDept dept = customerDeptMapper.selectCustomerDeptById(customerDeptId);
        point.deptName = dept == null ? "" : dept.getName();
        point.details = saleOrderDetailMapper.selectValidByCustomerPointDateForView(
                customerId, customerDeptId, LocalDate.parse(deliveryDate));
        for (SaleOrderDetail detail : point.details) {
            point.totalNum = point.totalNum.add(detail.getNum() == null ? BigDecimal.ZERO : detail.getNum());
            point.totalAmount = point.totalAmount.add(amount(detail));
        }
        return point;
    }

    /** 客户 SKU 映射叫法（打印品名客户叫法优先，DESIGN 不变量 8） */
    private Map<Long, String> customerAliasBySku(Long customerId) {
        if (customerId == null) {
            return new LinkedHashMap<>();
        }
        CustomerSkuMapping query = new CustomerSkuMapping();
        query.setCustomerId(customerId);
        return customerSkuMappingMapper.selectCustomerSkuMappingList(query).stream()
                .filter(m -> m.getSkuId() != null && StringUtils.isNotBlank(m.getCustomerAlias()))
                .collect(Collectors.toMap(CustomerSkuMapping::getSkuId, CustomerSkuMapping::getCustomerAlias, (a, b) -> a));
    }

    /** 行金额：优先下单快照金额，回退 应送×单价 */
    private BigDecimal amount(SaleOrderDetail detail) {
        if (detail.getExpectAmount() != null) {
            return detail.getExpectAmount();
        }
        BigDecimal num = detail.getNum() == null ? BigDecimal.ZERO : detail.getNum();
        BigDecimal price = detail.getProductPrice() == null ? BigDecimal.ZERO : detail.getProductPrice();
        return num.multiply(price);
    }

    /** 变更标记文案（D-055：加单/换货/退货，正常行为空） */
    private String changeTagOf(SaleOrderDetail detail) {
        Integer type = detail.getChangeType();
        if (type == null) {
            return "";
        }
        switch (type) {
            case 1: return "加单";
            case 2: return "换货";
            case 3: return "退货";
            default: return "";
        }
    }

    /** ID 参数容错解析：空串/JimuReport 未替换的  字面量一律视为未传 */
    private Long toIdOrNull(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            throw new ServiceException("非法的打印取数参数: " + value);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.isBlank(value) || value.startsWith("${") ? null : value.trim();
    }
}
