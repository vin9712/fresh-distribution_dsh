package com.lin.distribution.service.impl;

import com.lin.common.config.RuoYiConfig;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.common.utils.uuid.IdUtils;
import com.lin.distribution.domain.PrintAsset;
import com.lin.distribution.mapper.PrintAssetMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.PrintTemplateVersionMapper;
import com.lin.distribution.service.PrintAssetService;
import com.lin.distribution.service.support.TemplateContentGovernor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * 打印资源 Service 实现（W0-6）
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintAssetServiceImpl implements PrintAssetService {

    /** 资源存储子目录（相对 profile）：print/assets */
    private static final String ASSET_SUB_DIR = "print/assets";
    /** 资源对外访问前缀（RuoYi 静态资源映射 profile → /profile） */
    private static final String ASSET_URL_PREFIX = "/profile/print/assets/";

    private final PrintAssetMapper printAssetMapper;
    private final PrintTemplateMapper printTemplateMapper;
    private final PrintTemplateVersionMapper printTemplateVersionMapper;
    private final TemplateContentGovernor governor;

    @Override
    public PrintAsset selectPrintAssetById(Long id) {
        return printAssetMapper.selectPrintAssetById(id);
    }

    @Override
    public List<PrintAsset> selectPrintAssetList(PrintAsset printAsset) {
        return printAssetMapper.selectPrintAssetList(printAsset);
    }

    /**
     * 上传资源（蓝图 36：PNG/JPG 5MB；37：图片使用本机文件目录）。
     * 同内容（同 size + 同原文件名）已存在则复用，避免重复落盘。
     */
    @Override
    @Transactional
    public PrintAsset upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originName = Objects.requireNonNull(file.getOriginalFilename());
        governor.assertAssetAllowed(originName, file.getSize());

        String ext = extOf(originName);
        String uuid = IdUtils.fastUUID().replace("-", "");
        String fileName = uuid + "." + ext;
        String storagePath = ASSET_SUB_DIR + "/" + fileName;
        String url = ASSET_URL_PREFIX + fileName;

        // 落盘到本机文件目录（profile/print/assets）；用绝对路径避免 transferTo 相对路径问题
        Path dir = resolveAssetDir();
        Path target = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new ServiceException("资源保存失败：" + e.getMessage());
        }

        PrintAsset asset = PrintAsset.builder()
                .assetKey(uuid)
                .fileName(fileName)
                .originName(originName)
                .contentType(detectContentType(ext))
                .sizeBytes(file.getSize())
                .storagePath(storagePath)
                .url(url)
                .refCount(0)
                .isDeleted(false)
                .build();
        asset.setCreateBy(resolveOperator());
        asset.setCreateTime(DateUtils.getNowDate());
        printAssetMapper.insertPrintAsset(asset);
        log.info("[print asset] 资源上传：{} → {}（{}字节，操作者：{}）",
                originName, url, file.getSize(), asset.getCreateBy());
        return asset;
    }

    /**
     * 物理软删：先扫描模板/版本是否引用该 URL，被引用则禁止删除（蓝图 37：历史引用资源不可物理删除）。
     */
    @Override
    @Transactional
    public int deletePrintAssetById(Long id) {
        PrintAsset exist = printAssetMapper.selectPrintAssetById(id);
        if (exist == null) {
            throw new ServiceException("资源不存在");
        }
        if (isAssetReferenced(exist.getUrl())) {
            throw new ServiceException("该资源仍被模板/历史版本引用，不可删除（蓝图：历史引用资源不可物理删除）");
        }
        // 无引用：软删 DB 记录，并尽力删除本机文件（目录未配置/删除失败不影响软删结果）
        int rows = printAssetMapper.deletePrintAssetById(id);
        String profile = RuoYiConfig.getProfile();
        if (profile != null && !profile.isBlank() && exist.getStoragePath() != null) {
            try {
                File f = Paths.get(profile, exist.getStoragePath()).toAbsolutePath().normalize().toFile();
                if (f.exists() && !f.delete()) {
                    log.warn("[print asset] 物理文件删除失败：{}", f.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("[print asset] 物理文件删除异常：{}", e.getMessage());
            }
        }
        return rows;
    }

    /**
     * 解析资源存储绝对目录（profile 未配置时报错而非 NPE）。
     */
    private Path resolveAssetDir() {
        String profile = RuoYiConfig.getProfile();
        if (profile == null || profile.isBlank()) {
            throw new ServiceException("未配置文件上传根目录（ruoyi.profile），无法存取打印资源");
        }
        return Paths.get(profile, ASSET_SUB_DIR).toAbsolutePath().normalize();
    }

    /**
     * 扫描模板 content 与版本 content 是否引用该 URL（LIKE 模糊匹配）。
     */
    @Override
    public boolean isAssetReferenced(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (printTemplateMapper.countContentLike(url) > 0) {
            return true;
        }
        return printTemplateVersionMapper.countContentLike(url) > 0;
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    private String extOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1).toLowerCase() : "";
    }

    private String detectContentType(String ext) {
        switch (ext) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            default: return "application/octet-stream";
        }
    }
}
