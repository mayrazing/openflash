package openflash_core.service;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.UserUploadMapper;
import org.springframework.stereotype.Service;

/** 锁定并校验卡片即将引用的本地上传文件归属。 */
@Service
public class UserUploadAccessGuard {

    private final UserUploadMapper userUploadMapper;

    public UserUploadAccessGuard(UserUploadMapper userUploadMapper) {
        this.userUploadMapper = userUploadMapper;
    }

    /** 允许远程 URL; 本地上传路径必须合法且归当前用户所有。 */
    public void requireMediaUrlsOwnedBy(Long userId, List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return;
        }
        TreeSet<String> localPaths = new TreeSet<>();
        for (String mediaUrl : mediaUrls) {
            String normalized = mediaUrl == null ? null : mediaUrl.trim();
            if (normalized == null || normalized.isEmpty()
                    || !UploadPathPolicy.isUploadReference(normalized)) {
                continue;
            }
            if (!UploadPathPolicy.isDirectUploadPath(normalized)) {
                throw denied();
            }
            localPaths.add(normalized);
        }
        for (String localPath : localPaths) {
            Long ownerUserId = userUploadMapper.lockOwnerIdByPath(localPath);
            if (!Objects.equals(userId, ownerUserId)) {
                throw denied();
            }
        }
    }

    private AppException denied() {
        return new AppException(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED);
    }
}
