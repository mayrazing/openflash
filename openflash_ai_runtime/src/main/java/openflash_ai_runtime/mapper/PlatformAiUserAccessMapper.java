package openflash_ai_runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAiUserAccessMapper {

    int upsert(
            @Param("userId") long userId,
            @Param("offeringId") long offeringId,
            @Param("enabled") boolean enabled);

    int delete(
            @Param("userId") long userId,
            @Param("offeringId") long offeringId);
}
