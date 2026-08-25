package openflash_core.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Iterator;
import java.nio.file.Files;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Card;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_core.service.FeatureFlagService;
import openflash_core.service.RemoteImageDownloader;
import openflash_core.service.UserUploadRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 浏览器插件导入服务：收口功能开关、图片转存和导入建卡。 */
@Service
public class BrowserImportServiceImpl {

    public static final String FEATURE_KEY = "feature.browser-import";
    static final int MAX_IMAGE_DIMENSION = 4000;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final FeatureFlagService featureFlagService;
    private final CurrentUserService currentUserService;
    private final CardService cardService;
    private final RemoteImageDownloader imageDownloader;
    private final UserUploadRegistry uploadRegistry;
    private final Path uploadDir;
    private final Supplier<UUID> uuidSupplier;

    /** 生产注入构造器。 */
    @Autowired
    public BrowserImportServiceImpl(
        FeatureFlagService featureFlagService,
        CurrentUserService currentUserService,
        CardService cardService,
        RemoteImageDownloader imageDownloader,
        UserUploadRegistry uploadRegistry
    ) {
        this(featureFlagService, currentUserService, cardService, imageDownloader,
            uploadRegistry, Paths.get("uploads"));
    }

    /** 测试用构造器，可注入临时上传目录。 */
    BrowserImportServiceImpl(
        FeatureFlagService featureFlagService,
        CurrentUserService currentUserService,
        CardService cardService,
        RemoteImageDownloader imageDownloader,
        UserUploadRegistry uploadRegistry,
        Path uploadDir
    ) {
        this(featureFlagService, currentUserService, cardService, imageDownloader,
            uploadRegistry, uploadDir, UUID::randomUUID);
    }

    BrowserImportServiceImpl(
        FeatureFlagService featureFlagService,
        CurrentUserService currentUserService,
        CardService cardService,
        RemoteImageDownloader imageDownloader,
        UserUploadRegistry uploadRegistry,
        Path uploadDir,
        Supplier<UUID> uuidSupplier
    ) {
        this.featureFlagService = featureFlagService;
        this.currentUserService = currentUserService;
        this.cardService = cardService;
        this.imageDownloader = imageDownloader;
        this.uploadRegistry = uploadRegistry;
        this.uploadDir = uploadDir;
        this.uuidSupplier = uuidSupplier;
    }

    /** 转存一组远程图片 URL，结果顺序与输入顺序一致。 */
    public TransferImagesResponse transferImages(TransferImagesRequest request) {
        ensureEnabled();
        Long userId = currentUserService.getCurrentUserId();
        List<String> urls = request == null || request.urls() == null ? List.of() : request.urls();
        List<TransferImageResult> results = new ArrayList<>();
        for (String url : urls) {
            results.add(transferOne(url, userId));
        }
        return new TransferImagesResponse(results);
    }

    /** 通过浏览器导入专用入口创建卡片，不影响主站普通建卡。 */
    public Card createImportedCard(Long deckId, ImportCardRequest request) {
        ensureEnabled();
        String sideA = request == null || request.sideA() == null ? "" : request.sideA().trim();
        String sideB = request == null || request.sideB() == null ? "" : request.sideB().trim();
        List<String> sideAImage = request == null || request.sideAImage() == null ? List.of() : request.sideAImage();
        List<String> sideBImage = request == null || request.sideBImage() == null ? List.of() : request.sideBImage();
        if (sideA.isBlank() && sideB.isBlank() && sideAImage.isEmpty() && sideBImage.isEmpty()) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_EMPTY_CONTENT);
        }
        return cardService.createCard(deckId, sideA, sideB, sideAImage, sideBImage);
    }

    private void ensureEnabled() {
        if (!featureFlagService.isEnabled(FEATURE_KEY)) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }

    /** 转存单张图片，失败时返回失败项而不中断整批。 */
    private TransferImageResult transferOne(String url, Long userId) {
        RemoteImageDownloader.ResolvedImageUrl resolvedUrl = resolveHttpUrl(url);
        if (resolvedUrl == null) {
            return TransferImageResult.failure(url, ErrorCode.BROWSER_IMPORT_INVALID_IMAGE_URL.value());
        }
        try {
            RemoteImageDownloader.DownloadedImage image = imageDownloader.download(resolvedUrl);
            String storedUrl = storeAsUpload(userId, image.bytes());
            return TransferImageResult.success(url, storedUrl);
        } catch (AppException ex) {
            return TransferImageResult.failure(url, ex.getErrorCode().value());
        }
    }

    /** 只允许浏览器导入转存 http/https 图片。 */
    private RemoteImageDownloader.ResolvedImageUrl resolveHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null) {
                return null;
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return null;
            }
            if (host.equalsIgnoreCase("localhost")) {
                return null;
            }
            InetAddress selectedAddress = null;
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    return null;
                }
                if (selectedAddress == null) {
                    selectedAddress = address;
                }
            }
            return selectedAddress == null ? null : new RemoteImageDownloader.ResolvedImageUrl(uri, selectedAddress);
        } catch (IOException | URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** 拒绝本机、内网、链路本地、多播和未指定地址，避免把后端变成内网探测器。 */
    private boolean isBlockedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return true;
    }

    /** 把远程图片规范化保存到 uploads，返回可访问相对路径。 */
    private String storeAsUpload(Long userId, byte[] bytes) {
        try {
            ensureImageDimensionsAllowed(bytes);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
            }
            Files.createDirectories(uploadDir);
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            Path dest = writeNewJpeg(rgb);
            String filename = dest.getFileName().toString();
            String relativePath = "/uploads/" + filename;
            try {
                uploadRegistry.record(userId, relativePath);
            } catch (RuntimeException exception) {
                deleteAfterRegistrationFailure(dest, exception);
                throw exception;
            }
            return relativePath;
        } catch (IOException ex) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        }
    }

    private Path writeNewJpeg(BufferedImage image) throws IOException {
        while (true) {
            Path dest = uploadDir.resolve(uuidSupplier.get() + ".jpg");
            OutputStream output;
            try {
                output = Files.newOutputStream(dest,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            try (output) {
                if (!ImageIO.write(image, "jpg", output)) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
                return dest;
            } catch (IOException | RuntimeException writeFailure) {
                deleteAfterWriteFailure(dest, writeFailure);
                throw writeFailure;
            }
        }
    }

    private void deleteAfterWriteFailure(Path dest, Exception writeFailure) {
        try {
            Files.deleteIfExists(dest);
        } catch (IOException cleanupFailure) {
            writeFailure.addSuppressed(cleanupFailure);
        }
    }

    private void deleteAfterRegistrationFailure(Path dest, RuntimeException registrationFailure) {
        try {
            Files.deleteIfExists(dest);
        } catch (IOException cleanupFailure) {
            registrationFailure.addSuppressed(cleanupFailure);
        }
    }

    /** 解码像素前先读取图片宽高，拒绝超大尺寸图片。 */
    private void ensureImageDimensionsAllowed(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > MAX_IMAGE_DIMENSION || reader.getHeight(0) > MAX_IMAGE_DIMENSION) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    public record TransferImagesRequest(List<String> urls) {}
    public record TransferImagesResponse(List<TransferImageResult> results) {}
    public record TransferImageResult(String sourceUrl, Boolean success, String url, Integer code) {
        public static TransferImageResult success(String sourceUrl, String url) {
            return new TransferImageResult(sourceUrl, true, url, null);
        }
        public static TransferImageResult failure(String sourceUrl, Integer code) {
            return new TransferImageResult(sourceUrl, false, null, code);
        }
    }
    public record ImportCardRequest(String sideA, List<String> sideAImage, String sideB, List<String> sideBImage) {}
}
