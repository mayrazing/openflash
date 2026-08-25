package openflash_ai_runtime.controller;

import openflash_ai_runtime.support.CodexLoginCoordinator;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.common.RuntimeErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@RestController
@RequestMapping("/api/internal/admin/clis")
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class AiRuntimeCliAdminController {

    private static final String CLI_KEY = "codex";
    private static final String CONNECTION_KEY = "platform-codex";
    private static final String OFFERING_KEY = "platform-codex-cli";

    private final CodexRuntimeService codexRuntimeService;

    public AiRuntimeCliAdminController(CodexRuntimeService codexRuntimeService) {
        this.codexRuntimeService = codexRuntimeService;
    }

    @GetMapping
    public List<CliSnapshot> list() {
        return List.of(snapshot());
    }

    @GetMapping("/{cliKey}")
    public CliAdminSnapshot detail(@PathVariable String cliKey) {
        requireCodex(cliKey);
        return new CliAdminSnapshot(snapshot(), login(codexRuntimeService.loginSnapshot()));
    }

    @PostMapping("/{cliKey}/login")
    public CompletionStage<LoginSnapshot> startLogin(@PathVariable String cliKey) {
        requireCodex(cliKey);
        return safeStage(
            codexRuntimeService.startLogin(), AiRuntimeCliAdminController::login);
    }

    @DeleteMapping("/{cliKey}/login")
    public CompletionStage<LoginSnapshot> cancelLogin(@PathVariable String cliKey) {
        requireCodex(cliKey);
        return safeStage(
            codexRuntimeService.cancelLogin(), AiRuntimeCliAdminController::login);
    }

    @DeleteMapping("/{cliKey}/account")
    public CompletionStage<AccountLogoutResponse> logoutAccount(@PathVariable String cliKey) {
        requireCodex(cliKey);
        return safeStage(
            codexRuntimeService.logoutAccount(), AccountLogoutResponse::new);
    }

    private CliSnapshot snapshot() {
        return new CliSnapshot(
            CLI_KEY,
            CONNECTION_KEY,
            OFFERING_KEY,
            codexRuntimeService.status().status().name());
    }

    private static void requireCodex(String cliKey) {
        if (!CLI_KEY.equals(cliKey)) {
            throw new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.NOT_FOUND);
        }
    }

    private static LoginSnapshot login(CodexLoginCoordinator.LoginSnapshot snapshot) {
        return new LoginSnapshot(
            snapshot.state().name(), snapshot.verificationUrl(), snapshot.userCode());
    }

    private static <T, R> CompletionStage<R> safeStage(
            CompletionStage<T> source, Function<T, R> mapper) {
        return source.handle((value, failure) -> {
            if (failure != null) throw unavailable(failure);
            return mapper.apply(value);
        });
    }

    private static openflash_ai_runtime.common.RuntimeException unavailable(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
    }

    public record CliSnapshot(
        String cliKey,
        String connectionKey,
        String offeringKey,
        String runtimeStatus) {
    }

    public record CliAdminSnapshot(CliSnapshot cli, LoginSnapshot login) {
    }

    public record LoginSnapshot(String state, String verificationUrl, String userCode) {
    }

    public record AccountLogoutResponse(boolean loggedOut) {
    }
}
