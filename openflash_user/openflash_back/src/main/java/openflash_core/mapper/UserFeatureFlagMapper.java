package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 查询功能开关的用户级覆盖配置。 */
@Mapper
public interface UserFeatureFlagMapper {

    String findRolloutType(@Param("featureKey") String featureKey);

    Boolean findUserEnabled(
        @Param("featureKey") String featureKey,
        @Param("userId") Long userId
    );

    int upsertUserEnabled(
        @Param("featureKey") String featureKey,
        @Param("userId") Long userId,
        @Param("enabled") boolean enabled
    );

    int deleteByUserId(@Param("userId") Long userId);

    /** 返回不提供用户覆盖的实现，供旧的一参数服务构造器使用。 */
    static UserFeatureFlagMapper noOverrides() {
        return new UserFeatureFlagMapper() {
            @Override
            public String findRolloutType(String featureKey) {
                return null;
            }

            @Override
            public Boolean findUserEnabled(String featureKey, Long userId) {
                return null;
            }

            @Override
            public int upsertUserEnabled(String featureKey, Long userId, boolean enabled) {
                return 0;
            }

            @Override
            public int deleteByUserId(Long userId) {
                return 0;
            }
        };
    }
}
