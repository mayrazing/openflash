package openflash_ai_runtime.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.Function;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 解析并准备 OpenFlash 专用且与默认 Codex home 隔离的目录. */
@Component
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public final class CodexHome {
    static final String CONFIG_KEY = "ai.codex-home";
    static final String DEFAULT_VALUE = "~/.local/share/openflash/codex-home";

    private final Function<String, String> loader;
    private final Path userHome;

    @Autowired
    public CodexHome(RuntimeSystemConfigService systemConfigService) {
        this(key -> systemConfigService.getString(key, DEFAULT_VALUE),
                Path.of(System.getProperty("user.home")));
    }

    public CodexHome(Function<String, String> loader, Path userHome) {
        this.loader = loader;
        this.userHome = userHome.toAbsolutePath().normalize();
    }

    /** 返回可写的隔离目录, 无法证明与默认 Codex home 隔离时拒绝创建. */
    public Path prepare() throws IOException {
        Path canonicalUserHome = userHome.toRealPath();
        if (!Files.isDirectory(canonicalUserHome, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Operating system user home is not a directory");
        }
        String configured = loader.apply(CONFIG_KEY);
        String value = configured == null || configured.isBlank() ? DEFAULT_VALUE : configured.trim();
        Path candidate = value.equals("~")
                ? canonicalUserHome
                : value.startsWith("~/")
                        ? canonicalUserHome.resolve(value.substring(2))
                        : Path.of(value);
        Path normalized = candidate.toAbsolutePath().normalize();
        Path defaultCodexHome = canonicalUserHome.resolve(".codex").normalize();
        if (normalized.startsWith(defaultCodexHome) || normalized.equals(canonicalUserHome)
                || normalized.getParent() == null) {
            throw new IOException("OpenFlash Codex home is not isolated");
        }
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("OpenFlash Codex home must not be a symbolic link");
        }
        Path existingAncestor = normalized;
        while (existingAncestor != null
                && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IOException("OpenFlash Codex home has no existing ancestor");
        }
        Path resolvedDestination = existingAncestor.toRealPath()
                .resolve(existingAncestor.relativize(normalized))
                .normalize();
        Path resolvedDefaultCodexHome;
        if (Files.isSymbolicLink(defaultCodexHome)) {
            try {
                resolvedDefaultCodexHome = defaultCodexHome.toRealPath();
            } catch (IOException brokenDefaultLink) {
                throw new IOException("Default Codex home symlink cannot be resolved");
            }
        } else if (Files.exists(defaultCodexHome, LinkOption.NOFOLLOW_LINKS)) {
            resolvedDefaultCodexHome = defaultCodexHome.toRealPath();
        } else {
            resolvedDefaultCodexHome = defaultCodexHome;
        }
        if (resolvedDestination.startsWith(resolvedDefaultCodexHome)) {
            throw new IOException("OpenFlash Codex home resolves into the default Codex home");
        }
        if (resolvedDestination.equals(canonicalUserHome)
                || resolvedDestination.getParent() == null) {
            throw new IOException("OpenFlash Codex home is not isolated");
        }
        Files.createDirectories(resolvedDestination);
        if (Files.isSymbolicLink(resolvedDestination)
                || !Files.isDirectory(resolvedDestination, LinkOption.NOFOLLOW_LINKS)
                || !Files.isWritable(resolvedDestination)) {
            throw new IOException("OpenFlash Codex home is not a writable directory");
        }
        if (Files.getFileStore(resolvedDestination).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    resolvedDestination, PosixFilePermissions.fromString("rwx------"));
        }
        Path realHome = resolvedDestination.toRealPath();
        if (!realHome.equals(resolvedDestination)
                || realHome.startsWith(resolvedDefaultCodexHome)) {
            throw new IOException("OpenFlash Codex home resolves into the default Codex home");
        }
        Path agents = realHome.resolve("AGENTS.md");
        if (Files.isRegularFile(agents) && Files.size(agents) > 0L) {
            throw new IOException("OpenFlash Codex home contains AGENTS.md instructions");
        }
        return realHome;
    }
}
