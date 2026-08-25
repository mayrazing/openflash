package openflash_plugin.ai_card.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 删除 AI Card 插件持有的用户异步任务。 */
@Mapper
public interface AiCardUserTaskCleanupMapper {

    int deleteByUserId(@Param("userId") Long userId);
}
