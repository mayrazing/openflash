package openflash_core.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import openflash_core.common.AppLog;
import openflash_core.common.ErrorCode;
import openflash_core.entity.CardMedia;
import openflash_core.service.UploadPathPolicy;

/**
 * 删除 uploads 目录下的图片文件，供各 Service 复用。
 */
@Component
public class UploadFileDeleter {

    private static final Logger log = LoggerFactory.getLogger(UploadFileDeleter.class);

    private static final String UPLOAD_PREFIX = "/uploads/";

    private final Path uploadRoot;

    public UploadFileDeleter() {
        this(Paths.get("uploads"));
    }

    UploadFileDeleter(Path uploadRoot) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
    }

    /**
     * 按媒体记录删除对应的磁盘文件，失败只记日志，不中断主流程。
     */
    public void delete(List<CardMedia> mediaList) {
        for (CardMedia media : mediaList) {
            String url = media.getMediaUrl();
            if (url == null) {
                continue;
            }
            String filename = extractFilename(url);
            if (filename == null) {
                continue;
            }
            Path file = uploadRoot.resolve(filename);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                AppLog.warn(log, ErrorCode.UPLOAD_FILE_DELETE_FAILED, "删除上传图片失败: {}", file, e);
            }
        }
    }

    /** 删除 uploads 根目录直属文件，非法路径和 IO 失败都向调用方抛出。 */
    public void deleteRequired(String relativePath) {
        String normalized = requireDirectUploadPath(relativePath);
        String filename = normalized.substring(UPLOAD_PREFIX.length());
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!uploadRoot.equals(resolved.getParent())) {
            throw new UploadFileDeletionRejectedException("文件删除路径不在 uploads 根目录");
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException exception) {
            openflash_core.common.AppException failure =
                new openflash_core.common.AppException(ErrorCode.UPLOAD_FILE_DELETE_FAILED);
            failure.initCause(exception);
            throw failure;
        }
    }

    static String requireDirectUploadPath(String relativePath) {
        try {
            return UploadPathPolicy.requireDirectUploadPath(relativePath);
        } catch (IllegalArgumentException exception) {
            throw new UploadFileDeletionRejectedException("文件删除路径无效", exception);
        }
    }

    /** 从 exact 直属上传路径提取文件名，远程 URL 和非法路径返回 null。 */
    private String extractFilename(String url) {
        if (!UploadPathPolicy.isUploadReference(url)
                || !UploadPathPolicy.isDirectUploadPath(url)) {
            return null;
        }
        return url.substring(UPLOAD_PREFIX.length());
    }
}
