package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAiUserAccessMapper {
    Boolean findOverride(
            @Param("userId") Long userId, @Param("offeringId") Long offeringId);
}
