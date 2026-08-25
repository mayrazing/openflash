package openflash_core.service;

import java.util.List;
import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.service.impl.PluginRegistry;

/** 管理插件按卡包的安装关系，并把「已装」收敛为「已装 ∩ 全局启用」。 */
@Service
public class PluginInstallService {

    private final PluginInstallMapper mapper;
    private final PluginRegistry registry;
    private final DeckMapper deckMapper;

    public PluginInstallService(PluginInstallMapper mapper, PluginRegistry registry, DeckMapper deckMapper) {
        this.mapper = mapper;
        this.registry = registry;
        this.deckMapper = deckMapper;
    }

    /** 返回某卡包对用户可见的已装插件：已装且全局开启。 */
    public List<String> installedPluginIds(Long userId, Long deckId) {
        requireDeckOwnership(userId, deckId);
        List<String> active = registry.activePluginIds();
        return mapper.findPluginIdsByDeck(userId, deckId).stream()
            .filter(active::contains)
            .toList();
    }

    /** 给某卡包安装插件（幂等）。校验卡包归属与插件合法性，防越权与脏数据。 */
    public void install(Long userId, Long deckId, String pluginId) {
        requireDeckOwnership(userId, deckId);
        requireSupportedPlugin(pluginId);
        mapper.insert(userId, deckId, pluginId);
    }

    /** 从某卡包卸载插件（幂等）。仅校验卡包归属，允许清掉已下线插件的残留行。 */
    public void uninstall(Long userId, Long deckId, String pluginId) {
        requireDeckOwnership(userId, deckId);
        mapper.delete(userId, deckId, pluginId);
    }

    /**
     * 判断某卡包是否已安装某插件。纯内部只读，不做归属校验、不抛异常；
     * 全局启用与否由调用方（如 AiCardFeatureGuard）另行判断，此处只看安装行是否存在。
     */
    public boolean isInstalledOnDeck(Long deckId, String pluginId) {
        return mapper.existsByDeckAndPlugin(deckId, pluginId);
    }

    /** 校验卡包归属当前用户，非本人卡包按「不存在」处理，防越权写入与信息泄露。 */
    private void requireDeckOwnership(Long userId, Long deckId) {
        if (deckMapper.findByIdAndUserId(deckId, userId) == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
    }

    /** 校验插件已注册且全局启用，过滤未注册/不支持的 pluginId，防 DB 配错或伪造写入脏行。 */
    private void requireSupportedPlugin(String pluginId) {
        if (!registry.activePluginIds().contains(pluginId)) {
            throw new AppException(ErrorCode.PLUGIN_NOT_SUPPORTED);
        }
    }
}
