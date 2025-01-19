package com.lin.distribution.dto.print;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
public class PrintTemplateExcelCell {
    /**
     * 展示文案
     */
    private String text;

    /**
     * 文字尺寸，默认 14
     */
    private Integer fontSize;

    /**
     * 文字是否加粗
     */
    private Boolean bold;

    /**
     * 文字颜色，默认 #000000
     */
    private String color;

    /**
     * 水平对齐方式(left | center | right)，默认 center
     */
    private String align;

    /**
     * 所占行数，默认 1
     */
    private Integer rowspan;

    /**
     * 所占列数，默认 1
     */
    private Integer colspan;

    /**
     * 高度，若一行中有多个单元格设置高度，将使用其中的最大值
     */
    private Integer height;

    /**
     * 背景颜色
     */
    private String bgColor;

    /**
     * 是否绘制对角线
     */
    private Boolean diagonal;

    /**
     * 是否绘制边框(top | right | bottom | left)，默认 true
     */
    private Boolean border;

    /**
     * 动态属性
     */
    private String key;

    public PrintTemplateExcelCell(String text) {
        this.text = text;
        this.fontSize = 14;
        this.bold = false;
        this.color = "#000000";
        this.align = "center";
        this.rowspan = 1;
        this.colspan = 1;
        this.diagonal = false;
        this.border = true;
    }

    public static Map<Integer, String> fromText(Elements elements) {
        Map<Integer, String> map = new TreeMap<>();
        if (CollectionUtils.isEmpty(elements)) {
            return map;
        }
        // text elements
        for (Element element : elements) {
            Integer top = getElementTop(element);
            String text = element.text();
            if (map.containsKey(top)) {
                text = map.get(top) + "\t" + text;
            }
            map.put(top, text);
        }
        return map;
    }

    public static List<List<PrintTemplateExcelCell>> buildTableItem(Elements elements) {
        if (CollectionUtils.isEmpty(elements)) {
            return new ArrayList<>();
        }
        List<List<PrintTemplateExcelCell>> result = new ArrayList<>();
        for (Element element : elements) {
            List<PrintTemplateExcelCell> list = element.children().stream().map(it -> {
                PrintTemplateExcelCell cell = new PrintTemplateExcelCell(it.text().trim());
                Integer colspan1 = Optional.ofNullable(it.attr("colspan")).filter(StringUtils::isNotEmpty).map(Integer::parseInt).orElse(1);
                Integer rowspan1 = Optional.ofNullable(it.attr("rowspan")).filter(StringUtils::isNotEmpty).map(Integer::parseInt).orElse(1);
                cell.setColspan(colspan1);
                cell.setRowspan(rowspan1);
                return cell;
            }).toList();
            result.add(list);
        }
        return result;
    }

    public static PrintTemplateExportExcelDTO fromTable(Elements textEleList, Element tableEle, Integer tableTop, Elements gridFooter) {
        PrintTemplateExportExcelDTO dto = new PrintTemplateExportExcelDTO();
        if (tableEle == null) {
            return dto;
        }

        // table text element
        List<String> tableHeadTextList = new ArrayList<>();
        List<String> tableFootTextList = new ArrayList<>();
        Map<Integer, String> textElementMap = PrintTemplateExcelCell.fromText(textEleList);
        for (Map.Entry<Integer, String> entry : textElementMap.entrySet()) {
            Integer topVal = entry.getKey();
            String text = entry.getValue();
            if (topVal <= tableTop) {
                tableHeadTextList.add(text);
            } else {
                tableFootTextList.add(text);
            }
        }
        dto.setTableHeadTextList(tableHeadTextList);
        dto.setTableFootTextList(tableFootTextList);

        // table
        Elements tableChildren = tableEle.children();
        for (Element tableItem : tableChildren) {
            String tagName = tableItem.tag().getName();
            Elements tableItemChildren = tableItem.children();
            if ("thead".equals(tagName)) {
                dto.setTableHeadList(buildTableItem(tableItemChildren));
                dto.setMultiTable(dto.getTableHeadList().size() > 1);
            } else if ("tbody".equals(tagName)) {
                dto.setTableBodyList(buildTableItem(tableItemChildren));
            } else if ("tfoot".equals(tagName)) {
                dto.setTableFootList(buildTableItem(tableItemChildren));
            }

            // grid footer
            if (CollectionUtils.isNotEmpty(gridFooter)) {
                StringBuilder gridFooterText = new StringBuilder(16);
                for (Element gridFootItem : gridFooter) {
                    gridFooterText.append(gridFootItem.text()).append("\t");
                }
                dto.setTableGridFooter(gridFooterText.toString());
            }
        }
        return dto;
    }

    public static Integer getElementTop(Element element) {
        return Optional.ofNullable(element)
                .map(ele -> ele.attr("style"))
                .map(style -> style.replaceAll(".*top:([^;]+)pt;.*", "$1").trim())
                .filter(StringUtils::isNotEmpty).map(BigDecimal::new)
                .map(BigDecimal::intValue)
                .orElse(0);
    }
}
