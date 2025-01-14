package com.lin.distribution.dto.print;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

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

    public static List<PrintTemplateExcelCell> from(Elements elements){
        if (CollectionUtils.isEmpty(elements)) {
            return new ArrayList<>();
        }

        return elements.stream()
                .map(it ->{
                    return  PrintTemplateExcelCell.builder()
                            .text(it.text())
                            .build();
                })
                .toList();
    }
}
