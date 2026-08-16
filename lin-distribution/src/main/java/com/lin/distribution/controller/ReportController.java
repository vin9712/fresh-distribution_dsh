package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.dto.ReportVO;
import com.lin.distribution.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表Controller（DESIGN.md §10：销售日报 + 客户对账单，导出 Excel）
 *
 * @author dsh
 */
@Tag(name = "报表中心")
@RestController
@RequestMapping("/report")
public class ReportController extends BaseController {
    @Autowired
    private ReportService reportService;

    /**
     * 销售日报：按配送日期、按客户+配送点分组
     */
    @Operation(summary = "销售日报")
    @PreAuthorize("@ss.hasPermi('report:query')")
    @GetMapping("/dailySale")
    public AjaxResult dailySale(@RequestParam("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        return success(reportService.dailySale(deliveryDate));
    }

    /**
     * 销售日报导出 Excel
     */
    @Operation(summary = "销售日报导出")
    @PreAuthorize("@ss.hasPermi('report:export')")
    @Log(title = "销售日报", businessType = BusinessType.EXPORT)
    @GetMapping("/dailySale/export")
    public void dailySaleExport(@RequestParam("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
                                HttpServletResponse response) throws IOException {
        List<ReportVO.DailySaleGroup> groups = reportService.dailySale(deliveryDate);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("销售日报-" + deliveryDate);
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(bold);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            String[] heads = {"客户", "配送点", "商品名称", "规格", "单位", "实收数量", "损耗", "单价", "实收金额"};
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
                header.getCell(i).setCellStyle(boldStyle);
            }
            for (ReportVO.DailySaleGroup group : groups) {
                // 分组小计行
                Row subtotal = sheet.createRow(rowIdx++);
                subtotal.createCell(0).setCellValue(group.getCustomerName());
                subtotal.createCell(1).setCellValue(group.getDeliveryPointName());
                subtotal.createCell(2).setCellValue("小计：实收 " + group.getTotalActualAmount() + "，损耗 " + group.getTotalLossQuantity());
                subtotal.getCell(2).setCellStyle(boldStyle);
                for (ReportVO.DailySaleItem item : group.getItems()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(group.getCustomerName());
                    row.createCell(1).setCellValue(group.getDeliveryPointName());
                    row.createCell(2).setCellValue(item.getProductName());
                    row.createCell(3).setCellValue(item.getProductSpec());
                    row.createCell(4).setCellValue(item.getProductUnit());
                    row.createCell(5).setCellValue(num(item.getActualQuantity()));
                    row.createCell(6).setCellValue(num(item.getLossQuantity()));
                    row.createCell(7).setCellValue(num(item.getUnitPrice()));
                    row.createCell(8).setCellValue(num(item.getActualAmount()));
                }
            }
            writeWorkbook(response, workbook, "销售日报_" + deliveryDate + ".xlsx");
        }
    }

    /**
     * 客户对账单：选客户+起止日期（默认自然月）
     */
    @Operation(summary = "客户对账单")
    @PreAuthorize("@ss.hasPermi('report:query')")
    @GetMapping("/customerStatement")
    public AjaxResult customerStatement(@RequestParam("customerId") Long customerId,
                                        @RequestParam("beginDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
                                        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return success(reportService.customerStatement(customerId, beginDate, endDate));
    }

    /**
     * 客户对账单导出 Excel
     */
    @Operation(summary = "客户对账单导出")
    @PreAuthorize("@ss.hasPermi('report:export')")
    @Log(title = "客户对账单", businessType = BusinessType.EXPORT)
    @GetMapping("/customerStatement/export")
    public void customerStatementExport(@RequestParam("customerId") Long customerId,
                                        @RequestParam("beginDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
                                        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                        HttpServletResponse response) throws IOException {
        ReportVO.CustomerStatement statement = reportService.customerStatement(customerId, beginDate, endDate);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("客户对账单");
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(bold);

            int rowIdx = 0;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue("客户：" + statement.getCustomerName()
                    + "，期间：" + statement.getBeginDate() + " 至 " + statement.getEndDate()
                    + "，合计：" + statement.getTotalAmount());
            title.getCell(0).setCellStyle(boldStyle);

            Row header = sheet.createRow(rowIdx++);
            String[] heads = {"验收单号", "验收日期", "送货单号", "商品名称", "规格", "单位", "实收数量", "单价", "实收金额", "验收单金额"};
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
                header.getCell(i).setCellStyle(boldStyle);
            }
            for (ReportVO.StatementAcceptance acceptance : statement.getAcceptances()) {
                for (ReportVO.StatementItem item : acceptance.getItems()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(acceptance.getCode());
                    row.createCell(1).setCellValue(String.valueOf(acceptance.getAcceptDate()));
                    row.createCell(2).setCellValue(acceptance.getDeliveryCode());
                    row.createCell(3).setCellValue(item.getProductName());
                    row.createCell(4).setCellValue(item.getProductSpec());
                    row.createCell(5).setCellValue(item.getProductUnit());
                    row.createCell(6).setCellValue(num(item.getActualQuantity()));
                    row.createCell(7).setCellValue(num(item.getUnitPrice()));
                    row.createCell(8).setCellValue(num(item.getActualAmount()));
                    row.createCell(9).setCellValue(num(acceptance.getTotalAmount()));
                }
            }
            writeWorkbook(response, workbook, "客户对账单_" + statement.getCustomerName() + "_" + beginDate + "_" + endDate + ".xlsx");
        }
    }

    private double num(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private void writeWorkbook(HttpServletResponse response, XSSFWorkbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        workbook.write(response.getOutputStream());
    }
}
