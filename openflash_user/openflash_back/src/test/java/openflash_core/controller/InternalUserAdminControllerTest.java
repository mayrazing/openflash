package openflash_core.controller;

import openflash_core.security.InternalAdminTokenGuard;
import openflash_core.security.InternalAdminTokenInterceptor;
import openflash_core.service.impl.InternalUserAdminServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InternalUserAdminControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InternalAdminTokenGuard tokenGuard;
    private InternalUserAdminServiceImpl service;
    private InternalUserAdminController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tokenGuard = mock(InternalAdminTokenGuard.class);
        service = mock(InternalUserAdminServiceImpl.class);
        controller = new InternalUserAdminController(tokenGuard, service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addInterceptors(new InternalAdminTokenInterceptor(tokenGuard))
            .build();
    }

    @Test
    void missingPutBodyRunsTokenGuardThenReturnsInvalidRequestEnvelope() throws Exception {
        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40093))
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(tokenGuard, times(2)).requireValid("token");
        verifyNoInteractions(service);
    }

    @Test
    void missingDeleteActorRunsTokenGuardThenReturnsInvalidRequestEnvelope() throws Exception {
        mvc.perform(delete("/api/internal/admin/users/8")
                .header("X-OpenFlash-Admin-Token", "token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40093))
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(tokenGuard, times(2)).requireValid("token");
        verifyNoInteractions(service);
    }

    @Test
    void invalidTokenWinsWhenPutBodyIsMissingOrMalformed() throws Exception {
        doThrow(new AppException(ErrorCode.FORBIDDEN))
            .when(tokenGuard).requireValid("bad");

        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "bad")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(service);
    }

    @Test
    void validTokenWithTruncatedJsonReturnsInternalInvalidRequest() throws Exception {
        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorUserId\":7,"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40093));

        verifyNoInteractions(service);
    }

    @Test
    void invalidOrMissingTokenWinsOverTruncatedJson() throws Exception {
        doThrow(new AppException(ErrorCode.FORBIDDEN))
            .when(tokenGuard).requireValid("bad");
        doThrow(new AppException(ErrorCode.FORBIDDEN))
            .when(tokenGuard).requireValid(null);

        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorUserId\":7,"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(put("/api/internal/admin/users/8/banned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorUserId\":7,"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(service);
    }

    @Test
    void invalidTokenWinsWhenDeleteActorIsMissingOrInvalid() throws Exception {
        doThrow(new AppException(ErrorCode.FORBIDDEN))
            .when(tokenGuard).requireValid("bad");

        mvc.perform(delete("/api/internal/admin/users/8")
                .header("X-OpenFlash-Admin-Token", "bad"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/internal/admin/users/8")
                .header("X-OpenFlash-Admin-Token", "bad")
                .queryParam("actorUserId", "0"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(service);
    }

    @Test
    void validHttpRequestsCallServiceAndReturnSuccessBodies() throws Exception {
        mvc.perform(put("/api/internal/admin/users/8/banned")
                .header("X-OpenFlash-Admin-Token", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorUserId\":7,\"banned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true));
        mvc.perform(delete("/api/internal/admin/users/8")
                .header("X-OpenFlash-Admin-Token", "token")
                .queryParam("actorUserId", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

        verify(service).setBanned(7L, 8L, true);
        verify(service).deleteUser(7L, 8L);
    }

    @Test
    void setBannedGuardsTokenBeforeCallingService() throws Exception {
        InternalUserAdminController.UpdateResponse response = controller.setBanned(
            "token", 8L, json("{\"actorUserId\":7,\"banned\":true}"));

        assertEquals(new InternalUserAdminController.UpdateResponse(true), response);
        InOrder order = inOrder(tokenGuard, service);
        order.verify(tokenGuard).requireValid("token");
        order.verify(service).setBanned(7L, 8L, true);
    }

    @Test
    void tokenGuardRunsBeforeMalformedBodyValidation() throws Exception {
        doThrow(new AppException(ErrorCode.FORBIDDEN))
            .when(tokenGuard).requireValid("bad");

        assertError(ErrorCode.FORBIDDEN,
            () -> controller.setBanned("bad", 8L, json("{}")));

        verifyNoInteractions(service);
    }

    @Test
    void setBannedRejectsAnyBodyExceptExactTwoTypedFields() throws Exception {
        for (String body : new String[] {
                "{}",
                "[]",
                "null",
                "{\"actorUserId\":7}",
                "{\"banned\":true}",
                "{\"actorUserId\":\"7\",\"banned\":true}",
                "{\"actorUserId\":7,\"banned\":\"true\"}",
                "{\"actorUserId\":7,\"banned\":true,\"extra\":1}"}) {
            assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
                () -> controller.setBanned("token", 8L, json(body)));
        }

        verifyNoInteractions(service);
    }

    @Test
    void setBannedRejectsNonPositiveActorAndTargetIds() throws Exception {
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.setBanned("token", 8L,
                json("{\"actorUserId\":0,\"banned\":true}")));
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.setBanned("token", 0L,
                json("{\"actorUserId\":7,\"banned\":true}")));
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.setBanned("token", null,
                json("{\"actorUserId\":7,\"banned\":true}")));

        verifyNoInteractions(service);
    }

    @Test
    void deleteGuardsTokenThenCallsService() {
        InternalUserAdminController.DeleteResponse response = controller.delete(
            "token", 8L, 7L);

        assertEquals(new InternalUserAdminController.DeleteResponse(true), response);
        InOrder order = inOrder(tokenGuard, service);
        order.verify(tokenGuard).requireValid("token");
        order.verify(service).deleteUser(7L, 8L);
    }

    @Test
    void deleteRejectsNonPositiveActorOrTargetIds() {
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.delete("token", 8L, null));
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.delete("token", 8L, 0L));
        assertError(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID,
            () -> controller.delete("token", -1L, 7L));

        verifyNoInteractions(service);
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        AppException error = assertThrows(AppException.class, action::run);
        assertEquals(expected, error.getErrorCode());
    }
}
