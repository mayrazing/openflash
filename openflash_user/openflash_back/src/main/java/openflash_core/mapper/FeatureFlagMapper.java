package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询功能开关配置。
 */
@Mapper
public interface FeatureFlagMapper {

    /**
     * 查询全局功能开关是否启用。
     */
    Boolean findGlobalEnabled(@Param("featureKey") String featureKey);
}
