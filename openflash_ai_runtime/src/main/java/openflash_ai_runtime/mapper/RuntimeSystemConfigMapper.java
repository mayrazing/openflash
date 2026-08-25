package openflash_ai_runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RuntimeSystemConfigMapper {

    String findValueByKey(@Param("key") String key);
}
