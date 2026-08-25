package openflash_plugin.mask_mode.service.impl;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.service.CurrentUserService;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;
import openflash_plugin.mask_mode.service.MaskModeDeckSettingsService;
import openflash_plugin.mask_mode.dto.MaskModeDeckSettingsUpdateCommand;
import openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper;

@Service
public class MaskModeDeckSettingsServiceImpl implements MaskModeDeckSettingsService {

    /** 合法遮蔽模式常量集中在此，controller 与 service 不重复判断合法性。 */
    public static final Set<String> ALLOWED_MODES = Set.of("random", "full");

    /** 缺插件设置行时回退的默认遮蔽模式，与 V45 表默认值保持一致。 */
    public static final String DEFAULT_MODE = "random";

    /** 缺插件设置行时回退的默认总开关，与 V47 表默认值保持一致：新装卡包默认开启。 */
    public static final boolean DEFAULT_ENABLED = true;

    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;
    private final MaskModeDeckSettingsMapper maskModeDeckSettingsMapper;

    /** 注入当前用户、卡包归属和卡包设置读写入口。 */
    public MaskModeDeckSettingsServiceImpl(
        CurrentUserService currentUserService,
        DeckMapper deckMapper,
        MaskModeDeckSettingsMapper maskModeDeckSettingsMapper
    ) {
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
        this.maskModeDeckSettingsMapper = maskModeDeckSettingsMapper;
    }

    /**
     * 返回当前用户拥有卡包的遮蔽模式设置。
     * DB 行的 mode 不在白名单时（历史 migration 或手工改产生的脏值），回退 DEFAULT_MODE，
     * 避免设置页 radio 全部未选；脏 mode 回退保留 DB 已存的 enabled，
     * 避免顺手把用户关闭的总开关复位。
     */
    @Override
    public MaskModeDeckSettings getForCurrentUser(Long deckId) {
        requireDeckOwnership(deckId);
        MaskModeDeckSettings settings = maskModeDeckSettingsMapper.findByDeckId(deckId);
        if (settings == null) {
            return defaultSettings(deckId);
        }
        if (settings.mode() == null || !ALLOWED_MODES.contains(settings.mode())) {
            return new MaskModeDeckSettings(deckId, DEFAULT_MODE, settings.enabled());
        }
        return settings;
    }

    /**
     * 保存当前用户拥有卡包的遮蔽模式设置，只写插件自有设置表。
     */
    @Override
    @Transactional
    public MaskModeDeckSettings saveForCurrentUser(Long deckId, MaskModeDeckSettingsUpdateCommand command) {
        requireDeckOwnership(deckId);
        requireValidCommand(command);
        MaskModeDeckSettings next = new MaskModeDeckSettings(deckId, command.mode(), command.enabled());
        maskModeDeckSettingsMapper.upsert(next);
        return next;
    }

    /**
     * 校验当前用户拥有这个卡包，防止未拥有卡包读取或保存遮蔽模式设置。
     */
    private void requireDeckOwnership(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
    }

    /**
     * 校验保存命令合法：mode 须在白名单内，enabled 不能为 null。
     * 先判 command/mode null 再调 contains，避免 Set.of 不容 null 触发 NPE。
     */
    private void requireValidCommand(MaskModeDeckSettingsUpdateCommand command) {
        if (command == null
            || command.mode() == null
            || !ALLOWED_MODES.contains(command.mode())
            || command.enabled() == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
    }

    /**
     * 返回遮蔽模式默认值，缺插件设置行时页面仍显示稳定的随机遮蔽状态、开关默认开启。
     */
    private MaskModeDeckSettings defaultSettings(Long deckId) {
        return new MaskModeDeckSettings(deckId, DEFAULT_MODE, DEFAULT_ENABLED);
    }
}
