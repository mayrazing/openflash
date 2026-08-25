package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.UserSettings;

@Mapper
public interface UserSettingsMapper {

    UserSettings findByUserId(Long userId);

    int insert(UserSettings userSettings);

    int update(UserSettings userSettings);

    int deleteByUserId(@Param("userId") Long userId);
}
