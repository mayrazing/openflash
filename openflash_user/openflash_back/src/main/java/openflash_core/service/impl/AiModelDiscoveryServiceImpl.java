package openflash_core.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openflash_core.common.AiErrorCode;
import openflash_core.common.AppException;
import openflash_core.security.OutboundUrlValidator;
import openflash_core.security.OutboundUrlValidator.ResolvedTarget;
import org.springframework.stereotype.Service;

/**
 * AI 模型发现服务：按候选顺序探测上游 /models 列表接口，过滤无效响应。
 * <p>候选 1：Anthropic 风格 {@code <baseUrl>/v1/models}（{@link AuthStyle#ANTHROPIC}）。
 * <br>后续候选：当 baseUrl 命中已知兼容后缀时，先对同一 URL 尝试 Bearer，
 * 再剥离后缀后依次拼 {@code /v1/models}、{@code /models}（{@link AuthStyle#BEARER}）。
 * <p>所有候选 URL 在请求前都过 {@link OutboundUrlValidator}，校验失败直接报
 * {@link AiErrorCode#AI_MODEL_DISCOVERY_INVALID_URL}；候选全部失败报
 * {@link AiErrorCode#AI_MODEL_DISCOVERY_FAILED}。
 */
@Service
public class AiModelDiscoveryServiceImpl {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 已知的协议兼容子路径；按长度降序，避免短后缀抢先匹配长后缀。 */
    private static final List<String> KNOWN_COMPAT_SUFFIXES = List.of(
            "/api/claudecode",
            "/api/anthropic",
            "/apps/anthropic",
            "/api/coding",
            "/claudecode",
            "/anthropic",
            "/step_plan",
            "/coding",
            "/claude");

    private final OutboundUrlValidator validator;
    private final ModelListTransport transport;

    public AiModelDiscoveryServiceImpl(
            OutboundUrlValidator validator,
            ModelListTransport transport) {
        this.validator = validator;
        this.transport = transport;
    }

    /** 探测并返回模型列表；任一候选成功则返回，全失败抛 {@link AppException}。 */
    public List<ModelOption> discover(String baseUrl, String apiKey) {
        List<Candidate> candidates = buildCandidates(baseUrl);
        for (Candidate candidate : candidates) {
            ResolvedTarget target;
            try {
                target = validator.resolve(candidate.url());
            } catch (IllegalArgumentException ex) {
                // baseUrl 结构非法或解析到内网/非 https：用户输入问题，立刻报错不再尝试其它候选。
                throw new AppException(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL);
            }
            try {
                String body = transport.get(target, apiKey, candidate.authStyle());
                List<ModelOption> models = parse(body);
                if (!models.isEmpty()) return models;
            } catch (UpstreamAttemptFailed | JsonProcessingException ex) {
                // 当前候选失败（HTTP/解析）：尝试同源下一种标准模型接口；全部失败由循环外汇报。
            }
        }
        throw new AppException(AiErrorCode.AI_MODEL_DISCOVERY_FAILED);
    }

    /** 按候选顺序生成模型端点，并去掉重复的 URL 与鉴权组合。 */
    private List<Candidate> buildCandidates(String baseUrl) {
        List<Candidate> list = new ArrayList<>(4);
        String primaryModelsUrl = anthropicModelsUrl(baseUrl);
        list.add(new Candidate(primaryModelsUrl, AuthStyle.ANTHROPIC));
        String compatibilitySuffix = findCompatibilitySuffix(baseUrl);
        if (compatibilitySuffix != null) {
            addCandidateIfAbsent(list, new Candidate(primaryModelsUrl, AuthStyle.BEARER));
            String root = removeCompatibilitySuffix(baseUrl, compatibilitySuffix);
            addCandidateIfAbsent(list, new Candidate(root + "/v1/models", AuthStyle.BEARER));
            addCandidateIfAbsent(list, new Candidate(root + "/models", AuthStyle.BEARER));
        }
        return list;
    }

    /** URL 与鉴权组合未出现过时追加候选，保留同 URL 的不同鉴权尝试。 */
    private static void addCandidateIfAbsent(List<Candidate> candidates, Candidate candidate) {
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    /**
     * 拼接 anthropic 标准 /models 接口：
     * <ul>
     *   <li>路径已以 {@code /v1} 段结尾 → 仅追加 {@code /models}；
     *   <li>否则追加 {@code /v1/models}。
     * </ul>
     * 末尾 {@code /} 在拼接前 strip 掉，避免出现 {@code //}。
     */
    static String anthropicModelsUrl(String baseUrl) {
        String trimmed = stripTrailingSlash(safeTrim(baseUrl));
        if (pathEndsWithV1(trimmed)) return trimmed + "/models";
        return trimmed + "/v1/models";
    }

    /** 返回路径命中的最长兼容后缀；未命中返回 {@code null}。 */
    static String findCompatibilitySuffix(String baseUrl) {
        String path = safePath(baseUrl);
        for (String suffix : KNOWN_COMPAT_SUFFIXES) {
            if (path.endsWith(suffix)) return suffix;
        }
        return null;
    }

    /** 仅去掉路径末尾的已知兼容后缀，保留 scheme、host、port、前缀路径和 query。 */
    static String removeCompatibilitySuffix(String baseUrl, String suffix) {
        try {
            URI uri = new URI(stripTrailingSlash(safeTrim(baseUrl)));
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (!path.endsWith(suffix)) return baseUrl;
            String newPath = path.substring(0, path.length() - suffix.length());
            return new URI(
                            uri.getScheme(),
                            uri.getRawUserInfo(),
                            uri.getHost(),
                            uri.getPort(),
                            newPath.isEmpty() ? null : newPath,
                            uri.getRawQuery(),
                            null)
                    .toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid base url", ex);
        }
    }

    /** 解析响应：必须有 data 数组；保持上游顺序，去重相同 id；非字符串/空 id 跳过。 */
    private List<ModelOption> parse(String body) throws JsonProcessingException {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) return List.of();
        Map<String, ModelOption> models = new LinkedHashMap<>();
        for (JsonNode element : data) {
            JsonNode idNode = element.get("id");
            if (idNode == null || !idNode.isTextual()) continue;
            String id = idNode.asText();
            if (id.isEmpty()) continue;
            models.putIfAbsent(id, new ModelOption(id, id, supportedReasoningEfforts(element)));
        }
        return List.copyOf(models.values());
    }

    /** 读取 Anthropic Models API 为当前模型声明的 effort 档位. */
    private static List<String> supportedReasoningEfforts(JsonNode model) {
        JsonNode effort = model.path("capabilities").path("effort");
        if (!effort.isObject() || !effort.path("supported").asBoolean(false)) return List.of();
        List<String> supported = new ArrayList<>();
        for (String level : List.of("low", "medium", "high", "xhigh", "max")) {
            if (effort.path(level).path("supported").asBoolean(false)) supported.add(level);
        }
        return List.copyOf(supported);
    }

    private static boolean pathEndsWithV1(String url) {
        String path = safePath(url);
        return path.endsWith("/v1");
    }

    private static String safePath(String url) {
        try {
            String p = new URI(stripTrailingSlash(safeTrim(url))).getRawPath();
            return p == null ? "" : p;
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** 候选 endpoint：URL + 鉴权风格。 */
    private record Candidate(String url, AuthStyle authStyle) {}

    /** 模型选项 DTO：id 即上游模型标识，name 暂同 id（前端可改名展示）。 */
    public record ModelOption(String id, String name, List<String> supportedReasoningEfforts) {
        public ModelOption(String id, String name) {
            this(id, name, List.of());
        }
    }

    /** 上游候选鉴权风格。 */
    public enum AuthStyle {
        ANTHROPIC,
        BEARER
    }

    /** 单次 HTTP 拉取抽象，便于单元测试替换 transport，不命中真实网络。 */
    public interface ModelListTransport {
        String get(ResolvedTarget target, String apiKey, AuthStyle authStyle)
                throws UpstreamAttemptFailed;
    }

    /** 当前候选请求失败（连接/状态码/解析体积）。包内 unchecked，外层用于跳到下一个候选。 */
    public static final class UpstreamAttemptFailed extends RuntimeException {
        public UpstreamAttemptFailed() {
            super();
        }

        public UpstreamAttemptFailed(Throwable cause) {
            super(cause);
        }
    }
}
