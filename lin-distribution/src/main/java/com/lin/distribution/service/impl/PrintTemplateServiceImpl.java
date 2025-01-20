package com.lin.distribution.service.impl;

import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.dto.print.PrintTemplateExcelCell;
import com.lin.distribution.dto.print.PrintTemplateExcelRequestDTO;
import com.lin.distribution.dto.print.PrintTemplateExportExcelDTO;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.service.PrintTemplateService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 打印模板Service业务层处理
 *
 * @author lin
 * @date 2024-12-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl implements PrintTemplateService {
    private final PrintTemplateMapper printTemplateMapper;
    private final RedissonClient redissonClient;

    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    @Override
    public PrintTemplate selectPrintTemplateById(Long id) {
        return printTemplateMapper.selectPrintTemplateById(id);
    }

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板
     */
    @Override
    public List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate) {
        return printTemplateMapper.selectPrintTemplateList(printTemplate);
    }

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public PrintTemplate insertPrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setCreateTime(DateUtils.getNowDate());
        printTemplateMapper.insertPrintTemplate(printTemplate);
        genPrintTemplateNo(true);
        return printTemplate;
    }

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public int updatePrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setUpdateTime(DateUtils.getNowDate());
        return printTemplateMapper.updatePrintTemplate(printTemplate);
    }

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateByIds(Long[] ids) {
        return printTemplateMapper.deletePrintTemplateByIds(ids);
    }

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateById(Long id) {
        return printTemplateMapper.deletePrintTemplateById(id);
    }

    @Override
    public String generatePrintTemplateNo(Boolean refresh, String currentCode) {
        return genPrintTemplateNo(refresh, currentCode);
    }

    @Override
    public void downloadPrintTemplateExcel(PrintTemplateExcelRequestDTO request, HttpServletResponse response) {
        Document document;
        try {
            document = Jsoup.parse(request.getHtml());
        } catch (Exception e) {
            log.error("解析html失败, error: {}", e.getMessage());
            throw new RuntimeException("解析html失败");
        }

        List<PrintTemplateExportExcelDTO> list = new ArrayList<>();
        Elements papers = document.select(".hiprint-printPaper");
        for (Element paper : papers) {
            Element tableHeaderEle = paper.select(".hiprint-printElement-table").first();
            Integer tableTop = PrintTemplateExcelCell.getElementTop(tableHeaderEle);
            Element tableEle = paper.select("table").first();
            Elements textEleList = paper.select(".hiprint-printElement-text");
            Elements gridFooter = paper.select("#custom-grid-footer").first().children();
            PrintTemplateExportExcelDTO dto = PrintTemplateExcelCell.fromTable(textEleList, tableEle, tableTop, gridFooter);
            list.add(dto);
        }
        if (CollectionUtils.isEmpty(list)) {
            throw new RuntimeException("没有找到打印数据");
        }

        // poi build excel
        try (Workbook workbook = new XSSFWorkbook()) {
            // 创建工作表
            Sheet sheet = workbook.createSheet("Sheet1");

            // 设置单元格样式
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setTopBorderColor(IndexedColors.BLACK.getIndex());
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBottomBorderColor(IndexedColors.BLACK.getIndex());
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setLeftBorderColor(IndexedColors.BLACK.getIndex());
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setRightBorderColor(IndexedColors.BLACK.getIndex());

            int rowNo = 0;
            // 逐行构建表格
            for (PrintTemplateExportExcelDTO dto : list) {
                List<String> mergeCellList = new ArrayList<>();
                Integer tableHeadColNum = dto.getTableHeadColNum();

                // 填充表格前标题
                List<String> tableHeadTextList = dto.getTableHeadTextList();
                for (int i = 0; i < tableHeadTextList.size(); i++) {
                    String text = tableHeadTextList.get(i);
                    Row row = sheet.createRow(rowNo++);
                    String[] textArr = text.split("\t");
                    for (int j = 0; j < textArr.length; j++) {
                        row.createCell(j).setCellValue(textArr[j]);
                    }

                    // 设置标题行样式
                    if (i == 0) {
                        String mergeRule = (rowNo - 1) + "_" + (rowNo - 1) + "_0_" + (tableHeadColNum - 1);
                        mergeCellList.add(mergeRule);
                        row.getCell(0).setCellStyle(cellStyle);
                    }
                }

                // 填充表格表头
                boolean multiTable = Optional.ofNullable(dto.getMultiTable()).orElse(false);
                List<List<PrintTemplateExcelCell>> tableHeadList = dto.getTableHeadList();
                if (CollectionUtils.isNotEmpty(tableHeadList)) {
                    int tableHeadSize = tableHeadList.size();
                    List<PrintTemplateExcelCell> headerCellList = tableHeadList.get(tableHeadSize - 1);

                    // 合并单元格
                    if (multiTable) {
                        for (int i = 0; i < tableHeadSize - 1; i++) {
                            Row multiHeaderRow = sheet.createRow(rowNo++);
                            List<PrintTemplateExcelCell> multiHeadList = tableHeadList.get(i);
                            int lastColSpan = 1;
                            for (int j = 0; j < multiHeadList.size(); j++) {
                                PrintTemplateExcelCell multiHeadCell = multiHeadList.get(j);
                                int cellIdx = lastColSpan > 1 ? j + lastColSpan - 1 : j;
                                Cell cell = multiHeaderRow.createCell(cellIdx);
                                cell.setCellValue(multiHeadCell.getText());
                                cell.setCellStyle(cellStyle);
                                if (multiHeadCell.getColspan() > 1) {
                                    String mergeRule = (rowNo - 1) + "_" + (rowNo - 1) + "_" + cellIdx + "_" + (cellIdx + multiHeadCell.getColspan() - 1);
                                    mergeCellList.add(mergeRule);
                                }
                                lastColSpan = multiHeadCell.getColspan();
                            }
                        }

                    }
                    Row headerRow = sheet.createRow(rowNo++);
                    for (int i = 0; i < headerCellList.size(); i++) {
                        PrintTemplateExcelCell headerCell = headerCellList.get(i);
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headerCell.getText());
                        cell.setCellStyle(cellStyle);
                    }
                }

                // 填充表格内容
                List<List<PrintTemplateExcelCell>> tableBodyList = dto.getTableBodyList();
                if (CollectionUtils.isNotEmpty(tableBodyList)) {
                    for (List<PrintTemplateExcelCell> bodyCellList : tableBodyList) {
                        Row row = sheet.createRow(rowNo++);
                        for (int i = 0; i < bodyCellList.size(); i++) {
                            PrintTemplateExcelCell bodyCell = bodyCellList.get(i);
                            Cell cell = row.createCell(i);
                            cell.setCellValue(bodyCell.getText());
                            cell.setCellStyle(cellStyle);
                        }
                    }
                }

                // 填充表格尾部
                List<List<PrintTemplateExcelCell>> tableFootList = dto.getTableFootList();
                if (CollectionUtils.isNotEmpty(tableFootList)) {
                    for (List<PrintTemplateExcelCell> footCellList : tableFootList) {
                        Row row = sheet.createRow(rowNo++);
                        int lastFootColSpan = 1;
                        for (int i = 0; i < footCellList.size(); i++) {
                            PrintTemplateExcelCell footCell = footCellList.get(i);
                            int footCellIdx = lastFootColSpan > 1 ? i + lastFootColSpan - 1 : i;
                            Cell cell = row.createCell(footCellIdx);
                            cell.setCellValue(footCell.getText());
                            cell.setCellStyle(cellStyle);
                            if (footCell.getColspan() > 1) {
                                String mergeRule = (rowNo - 1) + "_" + (rowNo - 1) + "_" + footCellIdx + "_" + (footCellIdx + footCell.getColspan() - 1);
                                mergeCellList.add(mergeRule);
                            }
                            lastFootColSpan = footCell.getColspan();
                        }
                    }
                }

                // 填充表格分组尾部
                String tableGridFooter = dto.getTableGridFooter();
                if (StringUtils.isNotEmpty(tableGridFooter)) {
                    Row gridFootRow = sheet.createRow(rowNo++);
                    String[] tableGridTextArr = tableGridFooter.split("\t");
                    for (int i = 0; i < tableGridTextArr.length; i++) {
                        String tableGridText = tableGridTextArr[i];
                        gridFootRow.createCell(i).setCellValue(tableGridText);
                    }
                }

                // 合并单元格
                if (CollectionUtils.isNotEmpty(mergeCellList)) {
                    for (String mergeRule : mergeCellList) {
                        String[] ruleArr = mergeRule.split("_");
                        int startRow = Integer.parseInt(ruleArr[0]);
                        int endRow = Integer.parseInt(ruleArr[1]);
                        int startCol = Integer.parseInt(ruleArr[2]);
                        int endCol = Integer.parseInt(ruleArr[3]);
                        CellRangeAddress region = new CellRangeAddress(startRow, endRow, startCol, endCol);
                        sheet.addMergedRegion(region);

                        // 设置合并区域的边框
                        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                    }
                }

                // 创建空行
                sheet.createRow(rowNo++);
            }

            // 导出 Excel 文件
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=table_data.xlsx");
            OutputStream out = null;
            try {
                out = response.getOutputStream();
                workbook.write(out);
            } catch (IOException e) {
                log.error("导出Excel文件失败, error:{}", e.getMessage(), e);
                throw new RuntimeException("导出Excel文件失败");
            } finally {
                IOUtils.closeQuietly(out);
                IOUtils.closeQuietly(workbook);
            }

        } catch (Exception e) {
            log.error("生成Excel文件失败, error:{}", e.getMessage(), e);
            throw new RuntimeException("生成Excel文件失败");
        }
    }

    private String genPrintTemplateNo(Boolean refresh) {
        return genPrintTemplateNo(refresh, null);
    }

    private String genPrintTemplateNo(Boolean refresh, String currentCode) {
        String prefix = "PT";
        RMap<String, Integer> rMap = redissonClient.getMap("printTemplate");
        // get current redis seq
        int redisSeq = rMap.getOrDefault(prefix, 0);
        String redisQuoteCode = prefix + String.format("%05d", redisSeq);
        // if current code = redis code, return
        if (StringUtils.equals(redisQuoteCode, currentCode)) {
            return redisQuoteCode;
        }

        int seqNbr = BooleanUtils.isTrue(refresh) ? rMap.addAndGet(prefix, 1) : redisSeq;
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }

}
