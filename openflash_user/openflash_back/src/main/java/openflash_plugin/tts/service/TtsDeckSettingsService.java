package openflash_plugin.tts.service;

import java.util.List;
import openflash_plugin.tts.dto.TtsDeckSettingsUpdateCommand;
import openflash_plugin.tts.entity.TtsDeckSettings;

public interface TtsDeckSettingsService {

    TtsDeckSettings getForCurrentUser(Long deckId);

    TtsDeckSettings saveForCurrentUser(Long deckId, TtsDeckSettingsUpdateCommand command);

    List<String> getEnabledEngines();
}
