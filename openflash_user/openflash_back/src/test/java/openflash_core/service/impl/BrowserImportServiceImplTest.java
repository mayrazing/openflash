package openflash_core.service.impl;

import openflash_core.service.RemoteImageDownloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Card;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_core.service.FeatureFlagService;
import openflash_core.service.UserUploadRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrowserImportServiceImplTest {

    @TempDir
    Path tempDir;

    /** 功能关闭时，浏览器导入建卡被拒绝。 */
    @Test
    void createImportedCardRejectsWhenFeatureDisabled() {
        BrowserImportServiceImpl service = serviceWith(false, null, mock(CardService.class));

        AppException ex = assertThrows(AppException.class,
            () -> service.createImportedCard(7L, new BrowserImportServiceImpl.ImportCardRequest("word", List.of(), "", List.of())));

        assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
    }

    /** A/B 全空且都没有图片时，不创建空卡。 */
    @Test
    void createImportedCardRejectsEmptyContentAcrossBothSides() {
        BrowserImportServiceImpl service = serviceWith(true, null, mock(CardService.class));

        AppException ex = assertThrows(AppException.class,
            () -> service.createImportedCard(7L,
                new BrowserImportServiceImpl.ImportCardRequest("  ", List.of(), " ", List.of())));

        assertEquals(ErrorCode.BROWSER_IMPORT_EMPTY_CONTENT, ex.getErrorCode());
    }

    /** 只有 B 面有文字时也能建卡。 */
    @Test
    void createImportedCardAllowsOnlySideBText() {
        CardService cardService = mock(CardService.class);
        Card created = new Card();
        created.setId(101L);
        when(cardService.createCard(7L, "", "answer", List.of(), List.of())).thenReturn(created);
        BrowserImportServiceImpl service = serviceWith(true, null, cardService);

        Card result = service.createImportedCard(7L,
            new BrowserImportServiceImpl.ImportCardRequest(" ", List.of(), " answer ", List.of()));

        assertEquals(101L, result.getId());
        verify(cardService).createCard(7L, "", "answer", List.of(), List.of());
    }

    /** 创建导入卡时复用现有 CardService，保留 A/B 文本和多图顺序。 */
    @Test
    void createImportedCardDelegatesBothSidesToCardService() {
        CardService cardService = mock(CardService.class);
        Card created = new Card();
        created.setId(99L);
        List<String> sideAImage = List.of("/uploads/a1.jpg", "/uploads/a2.jpg");
        List<String> sideBImage = List.of("/uploads/b1.jpg");
        when(cardService.createCard(7L, "hello", "world", sideAImage, sideBImage)).thenReturn(created);
        BrowserImportServiceImpl service = serviceWith(true, null, cardService);

        Card result = service.createImportedCard(7L,
            new BrowserImportServiceImpl.ImportCardRequest(" hello ", sideAImage, " world ", sideBImage));

        assertEquals(99L, result.getId());
        verify(cardService).createCard(7L, "hello", "world", sideAImage, sideBImage);
    }

    /** 图片 URL 转存保持输入顺序，并返回成功路径与失败原因。 */
    @Test
    void transferImagesKeepsInputOrder() {
        RemoteImageDownloader downloader = target -> {
            if (target.uri().toString().equals("http://93.184.216.34/a.png")) {
                return new RemoteImageDownloader.DownloadedImage("image/png", onePixelPng());
            }
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        };
        BrowserImportServiceImpl service = serviceWith(true, downloader, mock(CardService.class));

        BrowserImportServiceImpl.TransferImagesResponse response = service.transferImages(
            new BrowserImportServiceImpl.TransferImagesRequest(List.of("http://93.184.216.34/a.png", "http://93.184.216.35/b.png")));

        assertEquals(2, response.results().size());
        assertEquals("http://93.184.216.34/a.png", response.results().get(0).sourceUrl());
        assertEquals("/uploads/", response.results().get(0).url().substring(0, 9));
        assertEquals("http://93.184.216.35/b.png", response.results().get(1).sourceUrl());
        assertEquals(false, response.results().get(1).success());
    }

    @Test
    void transferImagesRecordsCreatedFileForCurrentUser() throws Exception {
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        BrowserImportServiceImpl service = serviceWith(true,
            target -> new RemoteImageDownloader.DownloadedImage("image/png", onePixelPng()),
            mock(CardService.class), tempDir, null, uploadRegistry);

        BrowserImportServiceImpl.TransferImagesResponse response = service.transferImages(
            new BrowserImportServiceImpl.TransferImagesRequest(List.of("http://93.184.216.34/a.png")));

        String relativePath = response.results().get(0).url();
        verify(uploadRegistry).record(1L, relativePath);
        assertEquals(true, Files.exists(tempDir.resolve(Path.of(relativePath).getFileName())));
    }

    @Test
    void transferImagesDeletesNewFileWhenOwnershipInsertFails() throws Exception {
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        RuntimeException insertFailure = new RuntimeException("owner insert failed");
        doThrow(insertFailure).when(uploadRegistry).record(eq(1L), anyString());
        BrowserImportServiceImpl service = serviceWith(true,
            target -> new RemoteImageDownloader.DownloadedImage("image/png", onePixelPng()),
            mock(CardService.class), tempDir, null, uploadRegistry);

        RuntimeException actual = assertThrows(RuntimeException.class,
            () -> service.transferImages(
                new BrowserImportServiceImpl.TransferImagesRequest(List.of("http://93.184.216.34/a.png"))));

        assertSame(insertFailure, actual);
        try (var files = Files.list(tempDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void transferCollisionAndRegistryFailurePreserveExistingFile() throws Exception {
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID newId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Path existingFile = tempDir.resolve(existingId + ".jpg");
        Path newFile = tempDir.resolve(newId + ".jpg");
        Files.writeString(existingFile, "sentinel");
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        RuntimeException insertFailure = new RuntimeException("owner insert failed");
        doThrow(insertFailure).when(uploadRegistry).record(eq(1L), anyString());
        ArrayDeque<UUID> ids = new ArrayDeque<>(List.of(existingId, newId));
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.isEnabled(eq(BrowserImportServiceImpl.FEATURE_KEY))).thenReturn(true);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        BrowserImportServiceImpl service = new BrowserImportServiceImpl(
            featureFlagService,
            currentUserService,
            mock(CardService.class),
            target -> new RemoteImageDownloader.DownloadedImage("image/png", onePixelPng()),
            uploadRegistry,
            tempDir,
            ids::removeFirst
        );

        RuntimeException actual = assertThrows(RuntimeException.class,
            () -> service.transferImages(
                new BrowserImportServiceImpl.TransferImagesRequest(List.of("http://93.184.216.34/a.png"))));

        assertSame(insertFailure, actual);
        assertEquals(true, Files.exists(existingFile));
        assertEquals("sentinel", Files.readString(existingFile));
        assertFalse(Files.exists(newFile));
    }

    /** 未登录用户不能触发后端远程图片下载。 */
    @Test
    void transferImagesRejectsWhenNotLoggedIn() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserId()).thenThrow(new AppException(ErrorCode.UNAUTHORIZED));
        BrowserImportServiceImpl service = serviceWith(true, url -> {
            throw new AssertionError("downloader must not be called");
        }, mock(CardService.class), tempDir, currentUserService);

        AppException ex = assertThrows(AppException.class,
            () -> service.transferImages(new BrowserImportServiceImpl.TransferImagesRequest(List.of("http://93.184.216.34/a.png"))));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    /** 非 http/https 图片地址不交给下载器。 */
    @Test
    void transferImagesRejectsUnsupportedScheme() {
        BrowserImportServiceImpl service = serviceWith(true, url -> {
            throw new AssertionError("downloader must not be called");
        }, mock(CardService.class));

        BrowserImportServiceImpl.TransferImagesResponse response = service.transferImages(
            new BrowserImportServiceImpl.TransferImagesRequest(List.of("file:///etc/passwd")));

        assertEquals(false, response.results().get(0).success());
        assertEquals(ErrorCode.BROWSER_IMPORT_INVALID_IMAGE_URL.value(), response.results().get(0).code());
    }

    /** 本机地址不能被浏览器导入转存接口请求，避免内网探测。 */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/image.png",
        "http://127.0.0.1/image.png",
        "http://10.0.0.1/image.png",
        "http://172.16.0.1/image.png",
        "http://192.168.0.1/image.png",
        "http://169.254.0.1/image.png",
        "http://0.0.0.0/image.png",
        "http://224.0.0.1/image.png",
        "http://[::1]/image.png",
        "http://[fc00::1]/image.png"
    })
    void transferImagesRejectsUnsafeHosts(String imageUrl) {
        BrowserImportServiceImpl service = serviceWith(true, target -> {
            throw new AssertionError("downloader must not be called");
        }, mock(CardService.class));

        BrowserImportServiceImpl.TransferImagesResponse response = service.transferImages(
            new BrowserImportServiceImpl.TransferImagesRequest(List.of(imageUrl)));

        assertEquals(false, response.results().get(0).success());
        assertEquals(ErrorCode.BROWSER_IMPORT_INVALID_IMAGE_URL.value(), response.results().get(0).code());
    }

    private BrowserImportServiceImpl serviceWith(
        boolean enabled,
        RemoteImageDownloader downloader,
        CardService cardService
    ) {
        return serviceWith(enabled, downloader, cardService, tempDir, null, mock(UserUploadRegistry.class));
    }

    private BrowserImportServiceImpl serviceWith(
        boolean enabled,
        RemoteImageDownloader downloader,
        CardService cardService,
        Path uploadDir,
        CurrentUserService currentUserService
    ) {
        return serviceWith(enabled, downloader, cardService, uploadDir, currentUserService,
            mock(UserUploadRegistry.class));
    }

    private BrowserImportServiceImpl serviceWith(
        boolean enabled,
        RemoteImageDownloader downloader,
        CardService cardService,
        Path uploadDir,
        CurrentUserService currentUserService,
        UserUploadRegistry uploadRegistry
    ) {
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.isEnabled(eq(BrowserImportServiceImpl.FEATURE_KEY))).thenReturn(enabled);
        CurrentUserService userService = currentUserService;
        if (userService == null) {
            userService = mock(CurrentUserService.class);
            when(userService.getCurrentUserId()).thenReturn(1L);
        }
        return new BrowserImportServiceImpl(
            featureFlagService, userService, cardService, downloader, uploadRegistry, uploadDir);
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    }
}
