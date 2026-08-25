package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemConfigMapper {

    /**
     * 按配置 key 读取数据库中的配置 value。
     *
     * @param key 配置 key
     * @return 配置 value
     */
    String findValueByKey(@Param("key") String key);
}
