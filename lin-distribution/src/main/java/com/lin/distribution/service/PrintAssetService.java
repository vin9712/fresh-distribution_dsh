package com.lin.distribution.service;

import com.lin.distribution.domain.PrintAsset;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 打印资源 Service（W0-6）：本机文件目录资源管理，按模板版本固化，历史引用资源不可物理删除。
 *
 * @author dsh
 */
public interface PrintAssetService {

    /** 按主键查询 */
    PrintAsset selectPrintAssetById(Long id);

    /** 列表查询 */
    List<PrintAsset> selectPrintAssetList(PrintAsset printAsset);

    /**
     * 上传资源（蓝图 36：PNG/JPG 5MB；蓝图 37：图片使用本机文件目录并与数据库同批备份）
     *
     * @param file 上传文件
     * @return 资源登记记录（含 url，content 可直接引用）
     */
    PrintAsset upload(MultipartFile file);

    /**
     * 物理软删资源（蓝图 37：历史引用资源不可物理删除；被任意模板/版本引用则拒绝删除）。
     *
     * @param id 资源主键
     * @return 结果
     */
    int deletePrintAssetById(Long id);

    /**
     * 检查某资源 URL 是否被模板/版本引用（删除前校验用）。
     *
     * @param url 资源 URL
     * @return 是否被引用
     */
    boolean isAssetReferenced(String url);
}
