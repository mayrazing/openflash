package openflash_core.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.entity.PracticeModeOption;
import openflash_core.service.SettingsService;
import openflash_core.service.TypeRegistryService;

class SettingsControllerTest {

    @Test
    void reviewLoadProfilesNotExposedFromGlobalSettingsNamespace() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SettingsController(mock(SettingsService.class), mock(TypeRegistryService.class)))
            .build();

        mvc.perform(get("/api/settings/review-load-profiles"))
            .andExpect(status().isNotFound());
    }

    @Test
    void languagesExposedFromSettingsNamespace() throws Exception {
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(typeRegistryService.getEnabledLanguageOptions())
            .thenReturn(List.of(new PracticeModeOption("en", "English")));
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SettingsController(mock(SettingsService.class), typeRegistryService))
            .build();

        mvc.perform(get("/api/settings/languages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("en"))
            .andExpect(jsonPath("$.data[0].label").value("English"));
    }
}
