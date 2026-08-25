package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.UserAiConfig;

@Mapper
public interface UserAiConfigMapper {
    /** 查当前激活的 provider 行。 */
    UserAiConfig findActiveByUserId(Long userId);

    /** 查用户全部 provider 行。 */
    List<UserAiConfig> findAllByUserId(Long userId);

    /** 按 provider 查某行。 */
    UserAiConfig findByUserIdAndProvider(Long userId, String provider);

    /** 插入或更新某 provider 的 config。 */
    int upsert(UserAiConfig config);

    /** 删除用户自己的某个 provider。 */
    int deleteByUserIdAndProvider(Long userId, String provider);

    int deleteByUserId(@Param("userId") Long userId);
}
