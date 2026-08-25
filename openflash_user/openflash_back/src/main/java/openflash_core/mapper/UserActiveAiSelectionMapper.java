package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.UserActiveAiSelection;

@Mapper
public interface UserActiveAiSelectionMapper {
    UserActiveAiSelection findByUserId(@Param("userId") Long userId);

    int upsert(UserActiveAiSelection selection);

    int deleteByUserId(@Param("userId") Long userId);

    int deleteUserProviderSelection(
            @Param("userId") Long userId,
            @Param("providerKey") String providerKey);
}
