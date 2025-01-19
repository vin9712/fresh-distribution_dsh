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
     * 表格头文本元素
     */
    private List<String> tableHeadTextList;
    /**
     * 是否为多级表头
     */
    private Boolean multiTable;
    /**
     * 表头列数
     */
    private Integer tableHeadColNum;
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
    /**
     * 表格尾文本元素
     */
    private List<String> tableFootTextList;

}
