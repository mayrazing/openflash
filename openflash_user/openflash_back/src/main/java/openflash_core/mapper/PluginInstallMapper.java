package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 插件安装关系数据访问：按卡包查已装、装/卸、删卡包清理。 */
@Mapper
public interface PluginInstallMapper {

    /** 查某用户某卡包已安装的插件 ID 列表。 */
    List<String> findPluginIdsByDeck(@Param("userId") Long userId, @Param("deckId") Long deckId);

    /** 安装：插入一行，已存在则忽略。 */
    int insert(@Param("userId") Long userId, @Param("deckId") Long deckId, @Param("pluginId") String pluginId);

    /** 卸载：删除指定行。 */
    int delete(@Param("userId") Long userId, @Param("deckId") Long deckId, @Param("pluginId") String pluginId);

    /** 删卡包级联：清掉该卡包的全部安装记录，返回删除行数。 */
    int deleteByDeckId(@Param("deckId") Long deckId);

    int deleteByUserId(@Param("userId") Long userId);

    /** 判断某卡包是否已安装某插件（按 deck 索引，无需 userId）。 */
    boolean existsByDeckAndPlugin(@Param("deckId") Long deckId, @Param("pluginId") String pluginId);
}
