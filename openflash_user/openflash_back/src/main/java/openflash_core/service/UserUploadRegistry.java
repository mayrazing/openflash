package openflash_core.service;

import openflash_core.mapper.UserUploadMapper;
import org.springframework.stereotype.Service;

/** 记录新上传文件的用户归属。 */
@Service
public class UserUploadRegistry {

    private final UserUploadMapper userUploadMapper;

    public UserUploadRegistry(UserUploadMapper userUploadMapper) {
        this.userUploadMapper = userUploadMapper;
    }

    public void record(Long userId, String relativePath) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        UploadPathPolicy.requireDirectUploadPath(relativePath);
        if (userUploadMapper.insert(userId, relativePath) != 1) {
            throw new IllegalStateException("upload ownership was not recorded");
        }
    }
}
