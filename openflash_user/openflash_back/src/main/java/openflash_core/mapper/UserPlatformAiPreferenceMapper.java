package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.UserPlatformAiPreference;

@Mapper
public interface UserPlatformAiPreferenceMapper {
    UserPlatformAiPreference find(
            @Param("userId") Long userId, @Param("offeringId") Long offeringId);

    int upsert(UserPlatformAiPreference preference);
}
