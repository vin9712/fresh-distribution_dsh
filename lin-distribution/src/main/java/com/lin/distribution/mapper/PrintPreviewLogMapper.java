package com.lin.distribution.mapper;

import com.lin.distribution.domain.PrintPreviewLog;

/**
 * 打印预览记录Mapper接口（W0-4.4：正式打印前必须有预览记录）
 *
 * @author dsh
 */
public interface PrintPreviewLogMapper {

    /**
     * 新增预览记录
     *
     * @param log 预览记录
     * @return 结果
     */
    int insert(PrintPreviewLog log);
}
