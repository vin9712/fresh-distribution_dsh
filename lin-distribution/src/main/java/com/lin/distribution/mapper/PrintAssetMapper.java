package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.PrintAsset;

/**
 * 打印资源登记 Mapper（W0-6）
 *
 * @author dsh
 */
public interface PrintAssetMapper {

    /** 按主键查询 */
    PrintAsset selectPrintAssetById(Long id);

    /** 按资源键查询 */
    PrintAsset selectPrintAssetByKey(String assetKey);

    /** 按存储路径查询（用于重复上传复用） */
    PrintAsset selectPrintAssetByStoragePath(String storagePath);

    /** 列表查询 */
    List<PrintAsset> selectPrintAssetList(PrintAsset printAsset);

    /** 新增 */
    int insertPrintAsset(PrintAsset printAsset);

    /** 修改 */
    int updatePrintAsset(PrintAsset printAsset);

    /** 软删 */
    int deletePrintAssetById(Long id);

    /** 批量软删 */
    int deletePrintAssetByIds(Long[] ids);
}
