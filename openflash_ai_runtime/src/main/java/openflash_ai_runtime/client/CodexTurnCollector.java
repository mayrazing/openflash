package openflash_ai_runtime.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;

/** 只归并一个 process generation 内一个 ephemeral thread 的单 turn 输出. */
final class CodexTurnCollector {

    private static final Set<String> SAFE_ITEM_TYPES = Set.of(
            "userMessage",
            "hookPrompt",
            "agentMessage",
            "plan",
            "reasoning",
            "enteredReviewMode",
            "exitedReviewMode",
            "contextCompaction");

    private final long generation;
    private final String threadId;
    private final InterruptRequester interruptRequester;
    private final CompletableFuture<String> result = new CompletableFuture<>();
    private final List<AgentMessage> completedMessages = new ArrayList<>();
    private final Map<String, AgentMessage> completedById = new HashMap<>();
    private final StringBuilder deltaFallback = new StringBuilder();

    private String provisionalTurnId;
    private String turnId;
    private String terminalText;
    private CodexAppException terminalFailure;
    private boolean terminal;
    private boolean interruptRequested;

    CodexTurnCollector(
            long generation, String threadId, InterruptRequester interruptRequester) {
        this.generation = generation;
        this.threadId = requireText(threadId, "thread id");
        this.interruptRequester = interruptRequester;
    }

    /** 将 turn/start response 绑定到已按 thread 注册的 collector. */
    synchronized void bindTurn(String responseThreadId, String responseTurnId) {
        if (!threadId.equals(responseThreadId) || isBlank(responseTurnId)) {
            failProtocol(responseTurnId);
            return;
        }
        if (turnId != null) {
            if (!turnId.equals(responseTurnId)) failProtocol(responseTurnId);
            return;
        }
        turnId = responseTurnId;
        if (provisionalTurnId != null && !provisionalTurnId.equals(turnId)) {
            terminal = true;
            terminalText = null;
            terminalFailure = new CodexAppException(
                    AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE);
            requestInterrupt(turnId);
            completeIfBound();
            return;
        }
        completeIfBound();
    }

    /** 接收 app-server notification; 非本 thread/turn 事件不改变状态. */
    synchronized void accept(String method, JsonNode params) {
        if (terminal || params == null || !params.isObject()) return;
        switch (method) {
            case "item/agentMessage/delta" -> acceptDelta(params);
            case "item/started" -> acceptItemStarted(params);
            case "item/completed" -> acceptItemCompleted(params);
            case "turn/completed" -> acceptTurnCompleted(params);
            default -> {
                // Forward-compatible notification methods do not affect this turn.
            }
        }
    }

    CompletableFuture<String> result() {
        return result;
    }

    /** 连接级失败必须原子结束仍等待 terminal 的 collector. */
    synchronized void failConnection() {
        fail(AiErrorCode.AI_CODEX_RUNTIME_FAILED, false, turnId);
    }

    private void acceptDelta(JsonNode params) {
        Target target = target(params, params.get("turnId"));
        if (target == Target.OTHER) return;
        JsonNode itemId = params.get("itemId");
        JsonNode delta = params.get("delta");
        if (target == Target.MALFORMED
                || !isText(itemId)
                || !isTextual(delta)) {
            failProtocol(targetTurn(params));
            return;
        }
        deltaFallback.append(delta.textValue());
    }

    private void acceptItemCompleted(JsonNode params) {
        Target target = target(params, params.get("turnId"));
        if (target == Target.OTHER) return;
        JsonNode item = params.get("item");
        JsonNode type = item == null ? null : item.get("type");
        JsonNode id = item == null ? null : item.get("id");
        if (target == Target.MALFORMED || item == null || !item.isObject()
                || !isText(type) || !isText(id)) {
            failProtocol(targetTurn(params));
            return;
        }
        String itemType = type.textValue();
        if (isToolItem(itemType)) {
            fail(AiErrorCode.AI_CODEX_TOOL_BLOCKED, true, params.path("turnId").asText(null));
            return;
        }
        if (!"agentMessage".equals(itemType)) return;
        JsonNode text = item.get("text");
        if (!isTextual(text)) {
            failProtocol(params.path("turnId").asText(null));
            return;
        }
        String itemId = id.textValue();
        if (completedById.containsKey(itemId)) return;
        AgentMessage message = new AgentMessage(itemId, text.textValue());
        completedById.put(itemId, message);
        completedMessages.add(message);
    }

    private void acceptItemStarted(JsonNode params) {
        Target target = target(params, params.get("turnId"));
        if (target == Target.OTHER) return;
        JsonNode item = params.get("item");
        JsonNode type = item == null ? null : item.get("type");
        JsonNode id = item == null ? null : item.get("id");
        if (target == Target.MALFORMED || item == null || !item.isObject()
                || !isText(type) || !isText(id)) {
            failProtocol(targetTurn(params));
            return;
        }
        if (isToolItem(type.textValue())) {
            fail(AiErrorCode.AI_CODEX_TOOL_BLOCKED, true, params.path("turnId").asText(null));
        }
    }

    private void acceptTurnCompleted(JsonNode params) {
        JsonNode turn = params.get("turn");
        JsonNode eventTurnId = turn == null ? null : turn.get("id");
        Target target = target(params, eventTurnId);
        if (target == Target.OTHER) return;
        JsonNode status = turn == null ? null : turn.get("status");
        JsonNode items = turn == null ? null : turn.get("items");
        if (target == Target.MALFORMED || turn == null || !turn.isObject()
                || !isText(status) || items == null || !items.isArray()) {
            failProtocol(eventTurnId == null ? null : eventTurnId.asText(null));
            return;
        }
        if (containsTool(items)) {
            fail(AiErrorCode.AI_CODEX_TOOL_BLOCKED, true, eventTurnId.textValue());
            return;
        }
        switch (status.textValue()) {
            case "completed" -> completeText(items);
            case "failed" -> fail(AiErrorCode.AI_CODEX_RUNTIME_FAILED, false, eventTurnId.textValue());
            case "interrupted" -> fail(AiErrorCode.AI_INTERRUPTED, false, eventTurnId.textValue());
            default -> failProtocol(eventTurnId.textValue());
        }
    }

    private void completeText(JsonNode terminalItems) {
        List<AgentMessage> fallback = terminalAgentMessages(terminalItems);
        if (terminal) return;
        if (terminalFailure != null) return;

        List<AgentMessage> output = completedMessages;
        if (fallback.stream().anyMatch(message -> !completedById.containsKey(message.id()))) {
            output = mergeTerminalFallback(fallback);
        }
        String text = joinNonBlank(output);
        if (output.isEmpty()) text = deltaFallback.toString();
        if (text.isBlank()) {
            fail(AiErrorCode.AI_EMPTY_RESPONSE, false, turnId);
            return;
        }
        terminal = true;
        terminalText = text;
        completeIfBound();
    }

    private List<AgentMessage> terminalAgentMessages(JsonNode items) {
        List<AgentMessage> messages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            if (!item.isObject() || !isText(item.get("type")) || !isText(item.get("id"))) {
                failProtocol(turnId);
                return List.of();
            }
            if (!"agentMessage".equals(item.path("type").textValue())) continue;
            if (!isTextual(item.get("text"))) {
                failProtocol(turnId);
                return List.of();
            }
            String id = item.path("id").textValue();
            if (seen.add(id)) messages.add(new AgentMessage(id, item.path("text").textValue()));
        }
        return messages;
    }

    private List<AgentMessage> mergeTerminalFallback(List<AgentMessage> fallback) {
        List<AgentMessage> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AgentMessage message : fallback) {
            AgentMessage canonical = completedById.getOrDefault(message.id(), message);
            merged.add(canonical);
            seen.add(message.id());
        }
        for (AgentMessage message : completedMessages) {
            if (seen.add(message.id())) merged.add(message);
        }
        return merged;
    }

    private boolean containsTool(JsonNode items) {
        for (JsonNode item : items) {
            JsonNode type = item.get("type");
            if (isText(type) && isToolItem(type.textValue())) return true;
        }
        return false;
    }

    private Target target(JsonNode params, JsonNode eventTurnId) {
        JsonNode eventThreadId = params.get("threadId");
        if (!isText(eventThreadId)) return Target.OTHER;
        if (!threadId.equals(eventThreadId.textValue())) return Target.OTHER;
        if (!isText(eventTurnId)) return Target.MALFORMED;
        String candidate = eventTurnId.textValue();
        if (turnId != null) return turnId.equals(candidate) ? Target.THIS : Target.OTHER;
        if (provisionalTurnId == null) provisionalTurnId = candidate;
        return provisionalTurnId.equals(candidate) ? Target.THIS : Target.OTHER;
    }

    private void failProtocol(String candidateTurnId) {
        fail(AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE, true, candidateTurnId);
    }

    private void fail(AiErrorCode errorCode, boolean interrupt, String candidateTurnId) {
        if (terminal) return;
        terminal = true;
        terminalFailure = new CodexAppException(errorCode);
        if (interrupt) requestInterrupt(candidateTurnId);
        completeIfBound();
    }

    private void requestInterrupt(String candidateTurnId) {
        String targetTurnId = turnId != null ? turnId : candidateTurnId;
        if (interruptRequested || isBlank(targetTurnId)) return;
        interruptRequested = true;
        interruptRequester.request(generation, threadId, targetTurnId);
    }

    private void completeIfBound() {
        if (turnId == null || !terminal || result.isDone()) return;
        if (terminalFailure != null) result.completeExceptionally(terminalFailure);
        else result.complete(terminalText);
    }

    private static String joinNonBlank(List<AgentMessage> messages) {
        return messages.stream()
                .map(AgentMessage::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static boolean isToolItem(String type) {
        return !SAFE_ITEM_TYPES.contains(type);
    }

    private static String targetTurn(JsonNode params) {
        JsonNode value = params.get("turnId");
        return value == null ? null : value.asText(null);
    }

    private static String requireText(String value, String label) {
        if (isBlank(value)) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    private static boolean isText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank();
    }

    private static boolean isTextual(JsonNode node) {
        return node != null && node.isTextual();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    interface InterruptRequester {
        void request(long generation, String threadId, String turnId);
    }

    private record AgentMessage(String id, String text) {}

    private enum Target {
        THIS,
        OTHER,
        MALFORMED
    }
}
