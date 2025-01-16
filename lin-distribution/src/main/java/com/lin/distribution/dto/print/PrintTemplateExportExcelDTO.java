package com.lin.distribution.dto.print;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintTemplateExportExcelDTO implements Serializable {
    /**
     * 文本元素
     * 第一个为主标题
     * 第二个为副标题
     * 往后的都是普通文本
     */
    private List<String> textElements;
    /**
     * 是否为多级表头
     */
    private Boolean multiTable;
    /**
     * 表头数据
     */
    private List<List<PrintTemplateExcelCell>> tableHeadList;
    /**
     * 表身数据
     */
    private List<List<PrintTemplateExcelCell>> tableBodyList;
    /**
     * 表尾数据
     */
    private List<List<PrintTemplateExcelCell>> tableFootList;
    /**
     * 分组表尾行
     */
    private String tableGridFooter;

}
