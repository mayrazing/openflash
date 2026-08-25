package openflash_plugin.mask_mode.mapper;

import org.apache.ibatis.annotations.Mapper;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;

@Mapper
public interface MaskModeDeckSettingsMapper {

    /** 查询某个卡包的遮蔽模式设置。 */
    MaskModeDeckSettings findByDeckId(Long deckId);

    /** 插入或更新某个卡包的遮蔽模式设置。 */
    int upsert(MaskModeDeckSettings settings);

    /** 删除某个卡包的遮蔽模式设置。 */
    int deleteByDeckId(Long deckId);
}
