package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.service.PrintTicketService;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryMatrixVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private DeliveryBatchService deliveryBatchService;
    @Autowired
    private PrintTicketService printTicketService;

    /**
     * 签发短时一次性打印票据（W0-4.1：替代 URL 携带长期 JWT）
     * 打印送货单用 order:delivery:print；打开报表设计器/预览用 print:template:list。
     *
     * @param body {deliveryOrderId?: Long, templateId?: Long}
     * @return {ticket: "ptk_..."}，TTL 300 秒、一次性兑换
     */
    @Operation(summary = "签发短时一次性打印票据")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,print:template:list')")
    @PostMapping("/ticket")
    public AjaxResult issueTicket(@RequestBody(required = false) Map<String, Object> body) {
        Long deliveryOrderId = body == null ? null : toLong(body.get("deliveryOrderId"));
        Long templateId = body == null ? null : toLong(body.get("templateId"));
        String ticket = printTicketService.issue(deliveryOrderId, templateId);
        AjaxResult result = AjaxResult.success();
        result.put("ticket", ticket);
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
     * W0-4.1：必须携带与 deliveryOrderId 绑定一致的短时一次性票据
     *
     * @param deliveryOrderId 送货单ID
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {"head":{...}}
     */
    @Operation(summary = "送货单表头打印数据")
    @GetMapping("/deliveryHead")
    public Map<String, Object> deliveryHead(@RequestParam("deliveryOrderId") Long deliveryOrderId,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        checkTicket(deliveryOrderId, ticket);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        resp.put("head", head);
        if (deliveryOrderId == null) {
            return resp;
        }
        DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (order == null) {
            return resp;
        }
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        BigDecimal total = BigDecimal.ZERO;
        for (DeliveryOrderDetail detail : details) {
            total = total.add(detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());
        }
        head.put("code", order.getCode());
        head.put("customerName", order.getCustomerName());
        head.put("deliveryPointName", order.getCustomerDeptName());
        head.put("deliveryDate", order.getDeliveryDate() == null ? "" : order.getDeliveryDate().toString());
        head.put("totalAmount", total);
        return resp;
    }

    /**
     * 送货单明细打印数据（JimuReport 列表数据集 dd）
     * W0-4.1：必须携带与 deliveryOrderId 绑定一致的短时一次性票据
     *
     * @param deliveryOrderId 送货单ID
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {"rows":[...]}
     */
    @Operation(summary = "送货单明细打印数据")
    @GetMapping("/deliveryData")
    public Map<String, Object> deliveryData(@RequestParam("deliveryOrderId") Long deliveryOrderId,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        checkTicket(deliveryOrderId, ticket);
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        resp.put("rows", rows);
        if (deliveryOrderId == null) {
            return resp;
        }
        DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (order == null) {
            return resp;
        }
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        // 客户 SKU 映射：品名客户叫法优先（无映射用我方品名快照）
        CustomerSkuMapping query = new CustomerSkuMapping();
        query.setCustomerId(order.getCustomerId());
        Map<Long, String> aliasBySku = customerSkuMappingMapper.selectCustomerSkuMappingList(query).stream()
                .filter(m -> m.getSkuId() != null && StringUtils.isNotBlank(m.getCustomerAlias()))
                .collect(Collectors.toMap(CustomerSkuMapping::getSkuId, CustomerSkuMapping::getCustomerAlias, (a, b) -> a));
        for (DeliveryOrderDetail detail : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            String productName = detail.getSkuId() != null ? aliasBySku.get(detail.getSkuId()) : null;
            row.put("productName", StringUtils.isBlank(productName) ? detail.getProductName() : productName);
            row.put("productSpec", detail.getProductSpec());
            row.put("productUnit", detail.getProductUnit());
            row.put("num", detail.getNum());
            row.put("price", detail.getPrice());
            row.put("amount", detail.getAmount());
            // 验收数(留空)：客户签收时由送货员手填，纸面为空白列，仅供模板绑定「验收数」栏
            row.put("acceptanceNum", "");
            rows.add(row);
        }
        return resp;
    }

    /**
     * 矩阵总表打印数据（JimuReport 数据集 dm，D-044/D-046/D-051）
     *
     * <p>行=本单送货明细行（沿用 D-024 不同价必拆行）、列=批次布局快照的配送点（含当日无单空列）、
     * 格=分配量。纸面<b>不打单价与金额</b>，同名多行以 {@code (档①)} 区分（档号取批次快照，
     * 原单与补充单一致）。列数超出 {@code colsPerPage} 时按 colBlock 横向分页，
     * 每页重复品名列，页码「第 i/j 页 · 列块 k/m」。</p>
     *
     * <p>格位固定输出 c1..c{colsPerPage}（未用位置 null），保证套打列位不漂移。</p>
     *
     * @param deliveryOrderId 送货单ID（A 类总单或补充单）
     * @param colBlock        列块序号（1 起，缺省 1）
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {head:{...}, columns:[...], rows:[...]}
     */
    @Operation(summary = "矩阵总表打印数据")
    @GetMapping("/deliveryMatrixData")
    public Map<String, Object> deliveryMatrixData(@RequestParam("deliveryOrderId") Long deliveryOrderId,
                                                  @RequestParam(value = "colBlock", required = false, defaultValue = "1") Integer colBlock,
                                                  @RequestParam(value = "ticket", required = false) String ticket) {
        checkTicket(deliveryOrderId, ticket);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        resp.put("head", head);
        resp.put("columns", new ArrayList<Map<String, Object>>());
        resp.put("rows", new ArrayList<Map<String, Object>>());
        if (deliveryOrderId == null) {
            return resp;
        }
        DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (order == null || order.getCustomerId() == null || order.getDeliveryDate() == null) {
            return resp;
        }
        DeliveryMatrixVO matrix = deliveryBatchService.selectMatrix(order.getCustomerId(), order.getDeliveryDate().toString());

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

        // 列头（未用槽位补空列，套打列位固定）
        List<Map<String, Object>> columnMetas = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            Map<String, Object> meta = new LinkedHashMap<>();
            DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
            meta.put("seq", i + 1);
            meta.put("deptName", column == null ? "" : StringUtils.defaultString(column.getName()));
            meta.put("adHoc", column != null && Boolean.TRUE.equals(column.getAdHoc()));
            meta.put("empty", column == null);
            columnMetas.add(meta);
        }
        resp.put("columns", columnMetas);

        // 行（仅本张送货单的明细行；补充单另成一张纸，列块与档号沿用批次快照）
        List<Map<String, Object>> rowMetas = new ArrayList<>();
        int seq = 1;
        for (DeliveryMatrixVO.RowVO row : matrix.getRows()) {
            if (!deliveryOrderId.equals(row.getDeliveryId())) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("seq", seq++);
            // 打印品名客户叫法优先（DESIGN 不变量 8），品名本身保持干净；档位标注进备注列（D-046/D-053 修订）
            String baseName = StringUtils.defaultIfBlank(row.getCustomerAlias(), row.getProductName());
            line.put("productName", DeliveryMatrixLayout.displayProductName(baseName));
            line.put("productSpec", row.getSpec());
            line.put("productUnit", row.getUnit());
            // 备注列：承载档位标注（同名多行不同价时），单档为空保持干净
            line.put("remark", row.getRemark() == null ? "" : row.getRemark());
            for (int i = 0; i < slots; i++) {
                DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
                BigDecimal qty = column == null ? null : row.getCells().get(column.getDeptId());
                // 历史单无点级台账：格位打 —（D-051）
                line.put("c" + (i + 1), qty != null ? qty : (Boolean.TRUE.equals(matrix.getHistoryFallback()) ? "—" : ""));
            }
            line.put("total", row.getTotalQuantity());
            rowMetas.add(line);
        }
        resp.put("rows", rowMetas);

        head.put("code", order.getCode());
        head.put("docKind", order.getDocKind());
        head.put("customerName", StringUtils.defaultIfBlank(matrix.getCustomerName(), order.getCustomerName()));
        head.put("deliveryDate", order.getDeliveryDate().toString());
        head.put("printTitle", "配送总表");
        head.put("colBlockNo", blockNo);
        head.put("totalColBlocks", totalBlocks);
        head.put("colBlockLabel", "列块 " + blockNo + "/" + totalBlocks);
        // 列名槽位 c1Name..c{colsPerPage}（与 rows 的 c1..c6 槽位对齐，空列给空串）——模板列头绑定用
        for (int i = 0; i < slots; i++) {
            DeliveryMatrixVO.ColumnVO column = i < blockColumns.size() ? blockColumns.get(i) : null;
            head.put("c" + (i + 1) + "Name", column == null ? "" : StringUtils.defaultString(column.getName()));
        }
        head.put("totalQuantity", matrix.getTotalQuantity());
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
        head.put("remark", order.getRemark());
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
}
