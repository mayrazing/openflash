package openflash_core.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import openflash_core.dto.ApiResponse;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserUploadRegistry;

/**
 * 处理文件上传，把图片存到本地 uploads 目录并返回可访问路径。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    private final CurrentUserService currentUserService;
    private final UserUploadRegistry uploadRegistry;
    private final Path uploadDir;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public UploadController(CurrentUserService currentUserService, UserUploadRegistry uploadRegistry) {
        this(currentUserService, uploadRegistry, Paths.get("uploads"));
    }

    UploadController(
        CurrentUserService currentUserService,
        UserUploadRegistry uploadRegistry,
        Path uploadDir
    ) {
        this(currentUserService, uploadRegistry, uploadDir, UUID::randomUUID);
    }

    UploadController(
        CurrentUserService currentUserService,
        UserUploadRegistry uploadRegistry,
        Path uploadDir,
        Supplier<UUID> uuidSupplier
    ) {
        this.currentUserService = currentUserService;
        this.uploadRegistry = uploadRegistry;
        this.uploadDir = uploadDir;
        this.uuidSupplier = uuidSupplier;
    }

    /**
     * 接收图片文件，存到 uploads 目录，返回可访问的相对路径。
     */
    @PostMapping("/upload")
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.UPLOAD_FILE_MISSING);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException(ErrorCode.UPLOAD_FILE_NOT_IMAGE);
        }

        Long userId = currentUserService.getCurrentUserId();
        try {
            Files.createDirectories(uploadDir);
            Path dest = writeNewUpload(file);
            String filename = dest.getFileName().toString();
            String relativePath = "/uploads/" + filename;
            try {
                uploadRegistry.record(userId, relativePath);
            } catch (RuntimeException exception) {
                deleteAfterRegistrationFailure(dest, exception);
                throw exception;
            }
            return ApiResponse.success(new UploadResponse(relativePath));
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }

    private Path writeNewUpload(MultipartFile file) throws IOException {
        while (true) {
            Path dest = uploadDir.resolve(uuidSupplier.get() + ".jpg");
            OutputStream output;
            try {
                output = Files.newOutputStream(dest,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            try (output; InputStream input = file.getInputStream()) {
                input.transferTo(output);
                return dest;
            } catch (IOException writeFailure) {
                deleteAfterWriteFailure(dest, writeFailure);
                throw writeFailure;
            }
        }
    }

    private void deleteAfterWriteFailure(Path dest, IOException writeFailure) {
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

    public record UploadResponse(String url) {}
}
