package openflash_core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.PracticeModeOption;
import openflash_core.entity.ResponseTimeConfig;
import openflash_core.service.PracticeService;
import openflash_core.service.SystemConfigService;

class PracticeControllerTest {

    private PracticeController controller(PracticeService ps, SystemConfigService cs) {
        return new PracticeController(ps, cs);
    }

    /**
     * 验证页面能拿到后端当前启用的练习模式按钮。
     */
    @Test
    void listPracticeModesReturnsEnabledModes() {
        PracticeService practiceService = mock(PracticeService.class);
        when(practiceService.listPracticeModes()).thenReturn(List.of(
            new PracticeModeOption("a2b", "A面→B面"),
            new PracticeModeOption("random", "随机双向")
        ));
        PracticeController ctrl = controller(practiceService, mock(SystemConfigService.class));

        ApiResponse<List<PracticeModeOption>> response = ctrl.listPracticeModes();

        assertEquals(200, response.getCode());
        assertEquals(2, response.getData().size());
        assertEquals("a2b", response.getData().get(0).getValue());
        assertEquals("A面→B面", response.getData().get(0).getLabel());
        assertEquals("random", response.getData().get(1).getValue());
        assertEquals("随机双向", response.getData().get(1).getLabel());
    }

    /**
     * 验证 response-time-config 端点从 SystemConfigService 读取并返回三个阈值。
     */
    @Test
    void getResponseTimeConfigReturnsDatabaseValues() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getInt("practice.response-time.timeout-seconds", 60)).thenReturn(90);
        when(configService.getInt("practice.response-time.grade3-slow-threshold-seconds", 5)).thenReturn(4);
        when(configService.getInt("practice.response-time.grade2-slow-threshold-seconds", 10)).thenReturn(8);
        PracticeController ctrl = controller(mock(PracticeService.class), configService);

        ApiResponse<ResponseTimeConfig> response = ctrl.getResponseTimeConfig();

        assertEquals(200, response.getCode());
        assertEquals(90, response.getData().timeoutSeconds());
        assertEquals(4, response.getData().grade3SlowThresholdSeconds());
        assertEquals(8, response.getData().grade2SlowThresholdSeconds());
    }

    /**
     * 验证 DB 缺失时端点回退到默认值（60 / 5 / 10 秒）。
     */
    @Test
    void getResponseTimeConfigFallsBackToDefaults() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getInt("practice.response-time.timeout-seconds", 60)).thenReturn(60);
        when(configService.getInt("practice.response-time.grade3-slow-threshold-seconds", 5)).thenReturn(5);
        when(configService.getInt("practice.response-time.grade2-slow-threshold-seconds", 10)).thenReturn(10);
        PracticeController ctrl = controller(mock(PracticeService.class), configService);

        ApiResponse<ResponseTimeConfig> response = ctrl.getResponseTimeConfig();

        assertEquals(60, response.getData().timeoutSeconds());
        assertEquals(5, response.getData().grade3SlowThresholdSeconds());
        assertEquals(10, response.getData().grade2SlowThresholdSeconds());
    }
}
