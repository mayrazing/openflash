package openflash_admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminSystemConfigMapper {

    String findValueByKey(@Param("configKey") String configKey);
}
