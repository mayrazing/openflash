package openflash_admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminStartupDocumentationTest {

    @Test
    void adminReadmeDocumentsThreeProcessLauncherAndIndependentManualStarts() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));

        assertContains(
            readme,
            "admin_start.sh starts only admin_back and admin_front. "
                + "It does not start PostgreSQL, openflash_back, or openflash_ai_runtime."
        );
        assertOrdered(
            readme,
            List.of(
                "cd openflash_ai_runtime",
                "cd openflash_admin/admin_back",
                "cd openflash_admin/admin_front",
                "\ncd openflash_admin\n",
                "./admin_start.sh"
            )
        );
        assertContains(readme, "cd openflash_user/openflash_back");
        assertContains(readme, "./mvnw spring-boot:run");
    }

    @Test
    void adminReadmeNamesEverySecretWithoutSharingRuntimeScopes() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));

        for (String name : List.of(
                "OPENFLASH_ADMIN_INTERNAL_TOKEN",
                "OPENFLASH_AI_RUNTIME_ADMIN_TOKEN",
                "OPENFLASH_AI_RUNTIME_CORE_TOKEN",
                "OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD",
                "OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT")) {
            assertContains(readme, name);
        }
        assertContains(
            readme,
            "OPENFLASH_AI_RUNTIME_ADMIN_TOKEN and OPENFLASH_AI_RUNTIME_CORE_TOKEN must use "
                + "different non-empty values."
        );
        assertContains(readme, "Never print any token or encryption value.");
    }

    @Test
    void adminReadmeDocumentsFlywayOwnershipAndDegradedOperation() throws IOException {
        assertOperationalContract(Files.readString(Path.of("../README.md")));
    }

    @Test
    void rootReadmeDocumentsTheSameOperationalContractInBothLanguages() throws IOException {
        String readme = Files.readString(Path.of("../../README.md"));

        assertOperationalContract(readme);
        assertContains(
            readme,
            "openflash_back 是唯一的 Flyway owner. 新数据库必须先成功启动一次 openflash_back, "
                + "admin_back 和 openflash_ai_runtime 才能使用它."
        );
        assertContains(
            readme,
            "完成初始化后, openflash_back 离线时 admin_back 和 openflash_ai_runtime 仍可运行."
        );
        assertContains(
            readme,
            "openflash_back 离线时永久删除用户不可用, admin_back 会报告\"用户服务未启动\"."
        );
        assertContains(
            readme,
            "openflash_ai_runtime 离线时个人 AI 仍可用, 因为它仍保存在 pw_user_ai_config "
                + "并由 openflash_back 处理."
        );
    }

    private static void assertOperationalContract(String source) {
        assertContains(
            source,
            "openflash_back is the only Flyway owner. A fresh database needs one successful "
                + "openflash_back startup before admin_back or openflash_ai_runtime can use it."
        );
        assertContains(
            source,
            "After that initialization, admin_back and openflash_ai_runtime can run while "
                + "openflash_back is offline."
        );
        assertContains(
            source,
            "Permanent user deletion remains unavailable while openflash_back is offline; "
                + "admin_back reports \"User service is not running\"."
        );
        assertContains(
            source,
            "Personal AI remains available when openflash_ai_runtime is offline because it stays "
                + "in pw_user_ai_config and is still handled by openflash_back."
        );
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Missing documentation contract: " + expected);
    }

    private static void assertOrdered(String source, List<String> values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value);
            assertTrue(current >= 0, () -> "Missing documentation value: " + value);
            assertTrue(current > previous, () -> "Documentation value is out of order: " + value);
            previous = current;
        }
    }
}
