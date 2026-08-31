package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.PrintAsset;
import com.lin.distribution.mapper.PrintAssetMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.PrintTemplateVersionMapper;
import com.lin.distribution.service.support.TemplateContentGovernor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印资源服务测试（W0-6：上传与引用保护）
 */
@ExtendWith(MockitoExtension.class)
class PrintAssetServiceImplTest {

    @Mock
    private PrintAssetMapper printAssetMapper;
    @Mock
    private PrintTemplateMapper printTemplateMapper;
    @Mock
    private PrintTemplateVersionMapper printTemplateVersionMapper;

    @InjectMocks
    private PrintAssetServiceImpl printAssetService;

    /** upload() 内部 new TemplateContentGovernor？——否，是构造注入，@InjectMocks 无法注入 final 字段 governor。
     *  改用直接构造。 */
    private PrintAssetServiceImpl realService;

    @BeforeEach
    void setUp() {
        realService = new PrintAssetServiceImpl(
                printAssetMapper, printTemplateMapper, printTemplateVersionMapper, new TemplateContentGovernor());
    }

    @Test
    void 被引用资源应禁止删除() {
        PrintAsset asset = new PrintAsset();
        asset.setId(1L);
        asset.setUrl("/profile/print/assets/a.png");
        asset.setStoragePath("print/assets/a.png");
        when(printAssetMapper.selectPrintAssetById(1L)).thenReturn(asset);
        when(printTemplateMapper.countContentLike("/profile/print/assets/a.png")).thenReturn(1);

        ServiceException ex = assertThrows(ServiceException.class, () -> realService.deletePrintAssetById(1L));
        assertTrue(ex.getMessage().contains("历史引用资源"));
        verify(printAssetMapper, org.mockito.Mockito.never()).deletePrintAssetById(any(Long.class));
    }

    @Test
    void 无引用资源应软删并物理删除文件() {
        PrintAsset asset = new PrintAsset();
        asset.setId(1L);
        asset.setUrl("/profile/print/assets/none.png");
        asset.setStoragePath("print/assets/none.png");
        when(printAssetMapper.selectPrintAssetById(1L)).thenReturn(asset);
        when(printTemplateMapper.countContentLike(any(String.class))).thenReturn(0);
        when(printTemplateVersionMapper.countContentLike(any(String.class))).thenReturn(0);
        when(printAssetMapper.deletePrintAssetById(1L)).thenReturn(1);

        // 物理文件不存在时不抛错（仅警告）
        int rows = realService.deletePrintAssetById(1L);
        assertEquals(1, rows);
        verify(printAssetMapper).deletePrintAssetById(1L);
    }

    @Test
    void 非图片扩展名应拒绝上传() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.gif", "image/gif", new byte[]{1, 2});
        ServiceException ex = assertThrows(ServiceException.class, () -> realService.upload(file));
        assertTrue(ex.getMessage().contains("PNG/JPG"));
    }

    @Test
    void 超过5MB应拒绝上传() {
        byte[] big = new byte[(int) TemplateContentGovernor.ASSET_MAX_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", big);
        assertThrows(ServiceException.class, () -> realService.upload(file));
    }

    private static void assertTrue(boolean cond) {
        org.junit.jupiter.api.Assertions.assertTrue(cond);
    }
}
