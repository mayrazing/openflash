package openflash_ai_runtime.transport;

import java.util.List;
import java.util.UUID;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;

/** 一个已注册平台 API 协议的发现、生成和精确取消边界. */
public interface PlatformAiTransport {

    String protocol();

    List<String> discoverModels(ConnectionTarget target);

    String generate(GenerateCommand command);

    String generate(GenerateCommand command, RequestState requestState);

    boolean cancel(UUID requestId);

    record ConnectionTarget(String baseUrl, String apiKey) {

        @Override
        public String toString() {
            return "ConnectionTarget[baseUrl=" + baseUrl + ", apiKey=<redacted>]";
        }
    }

    record GenerateCommand(
            UUID requestId,
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            String systemPrompt,
            Double temperature) {

        @Override
        public String toString() {
            return "GenerateCommand[requestId=" + requestId
                    + ", baseUrl=" + baseUrl
                    + ", apiKey=<redacted>, model=" + model
                    + ", prompt=<redacted>, systemPrompt=<redacted>, temperature="
                    + temperature + "]";
        }
    }
}
