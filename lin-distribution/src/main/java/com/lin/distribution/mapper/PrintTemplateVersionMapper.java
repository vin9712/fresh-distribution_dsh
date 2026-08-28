package com.lin.distribution.mapper;

import com.lin.distribution.domain.PrintTemplateVersion;

import java.util.List;

/**
 * 打印模板版本快照Mapper接口（W0-4.4）
 *
 * @author dsh
 */
public interface PrintTemplateVersionMapper {

    /**
     * 查询某模板的版本列表（按版本号倒序）
     *
     * @param templateId 模板ID
     * @return 版本集合
     */
    List<PrintTemplateVersion> selectListByTemplateId(Long templateId);

    /**
     * 根据ID查询版本
     *
     * @param id 版本ID
     * @return 版本
     */
    PrintTemplateVersion selectById(Long id);

    /**
     * 查询某模板的当前最新版本号（无则返回 0）
     *
     * @param templateId 模板ID
     * @return 最大版本号
     */
    Integer selectMaxVersionNo(Long templateId);

    /**
     * 新增版本快照
     *
     * @param version 版本
     * @return 结果
     */
    int insert(PrintTemplateVersion version);
}
