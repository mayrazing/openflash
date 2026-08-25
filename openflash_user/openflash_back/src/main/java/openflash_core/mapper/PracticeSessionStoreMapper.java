package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PracticeSessionStoreMapper {

    /**
     * 读取指定卡包的练习断点数据，返回 JSON 字符串，不存在时返回 null。
     */
    String findData(
        @Param("userId") Long userId,
        @Param("deckId") Long deckId
    );

    /**
     * 写入或覆盖练习断点数据。
     */
    void upsert(
        @Param("userId") Long userId,
        @Param("deckId") Long deckId,
        @Param("data") String data
    );

    /**
     * 删除指定类型的练习断点数据。
     */
    void delete(
        @Param("userId") Long userId,
        @Param("deckId") Long deckId
    );

    /** 删除指定卡包的全部练习断点。 */
    int deleteByDeckId(@Param("deckId") Long deckId);

    /** 删除指定用户的全部练习断点。 */
    int deleteByUserId(@Param("userId") Long userId);
}
