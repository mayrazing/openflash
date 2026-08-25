package openflash_plugin.mask_mode.service;

import openflash_plugin.mask_mode.dto.MaskModeDeckSettingsUpdateCommand;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;

public interface MaskModeDeckSettingsService {

    /**
     * 返回当前用户拥有的卡包遮蔽模式设置。
     */
    MaskModeDeckSettings getForCurrentUser(Long deckId);

    /**
     * 保存当前用户拥有的卡包遮蔽模式设置。
     */
    MaskModeDeckSettings saveForCurrentUser(Long deckId, MaskModeDeckSettingsUpdateCommand command);
}
