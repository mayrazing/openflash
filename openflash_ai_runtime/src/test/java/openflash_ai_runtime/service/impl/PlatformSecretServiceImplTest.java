package openflash_ai_runtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import openflash_ai_runtime.entity.PlatformAiConnection;
import openflash_ai_runtime.entity.PlatformAiSecret;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.PlatformAiSecretMapper;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PlatformSecretServiceImplTest {

    private static final String STRONG_PASSWORD = base64Bytes(32, 11);
    private static final String STRONG_SALT = base64Bytes(16, 73);

    @Test
    void replaceEncryptsAndMarksCredentialsInsideOneTransaction() throws Exception {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());
        PlatformSecretService service = service(
                connections, secrets, STRONG_PASSWORD, STRONG_SALT);

        service.replace("platform-api", "plain-api-key");

        verify(secrets).upsert(org.mockito.ArgumentMatchers.argThat(secret ->
                secret.connectionId() == 3L
                        && !secret.secretEnc().equals("plain-api-key")
                        && !secret.secretEnc().contains("plain-api-key")));
        verify(connections).setCredentialsConfigured("platform-api", true);
        assertThat(PlatformSecretServiceImpl.class
                .getMethod("replace", String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void decryptsOnlyInsideRuntimeAndNeverReturnsCiphertext() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());
        PlatformSecretService service = service(
                connections, secrets, STRONG_PASSWORD, STRONG_SALT);
        service.replace("platform-api", "plain-api-key");
        org.mockito.ArgumentCaptor<PlatformAiSecret> saved =
                org.mockito.ArgumentCaptor.forClass(PlatformAiSecret.class);
        verify(secrets).upsert(saved.capture());
        when(secrets.findByConnectionId(3L)).thenReturn(saved.getValue());

        assertThat(service.requirePlaintext(3L)).isEqualTo("plain-api-key");
    }

    @Test
    void authenticatedEncryptionUsesRandomNonceAndRejectsAnyCiphertextTampering() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());
        PlatformSecretService service = service(
                connections, secrets, STRONG_PASSWORD, STRONG_SALT);
        List<PlatformAiSecret> saved = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return 1;
        }).when(secrets).upsert(org.mockito.ArgumentMatchers.any());

        service.replace("platform-api", "same-plain-secret");
        service.replace("platform-api", "same-plain-secret");

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).secretEnc()).isNotEqualTo(saved.get(1).secretEnc());
        when(secrets.findByConnectionId(3L)).thenReturn(saved.get(0));
        assertThat(service.requirePlaintext(3L)).isEqualTo("same-plain-secret");

        String ciphertext = saved.get(0).secretEnc();
        int last = ciphertext.length() - 1;
        char changed = ciphertext.charAt(last) == '0' ? '1' : '0';
        PlatformAiSecret tampered = new PlatformAiSecret(
                3L, ciphertext.substring(0, last) + changed);
        when(secrets.findByConnectionId(3L)).thenReturn(tampered);
        assertThatThrownBy(() -> service.requirePlaintext(3L))
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .hasMessageNotContaining("same-plain-secret")
                .hasMessageNotContaining(ciphertext);
    }

    @Test
    void missingEncryptionConfigAndBlankKeysFailClosedWithoutWriting() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());

        assertCode(() -> service(connections, secrets, "", STRONG_SALT)
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, STRONG_PASSWORD, "")
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, base64Bytes(31, 9), STRONG_SALT)
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, STRONG_PASSWORD, base64Bytes(15, 9))
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, STRONG_PASSWORD, STRONG_SALT)
                .replace("platform-api", " "), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        verifyNoInteractions(secrets);
    }

    @Test
    void encryptionConfigMustBeStandardBase64WithStrongDecodedLengths() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());

        assertCode(() -> service(connections, secrets, "not-base64!", STRONG_SALT)
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, STRONG_PASSWORD, "not-base64!")
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, "   ", STRONG_SALT)
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        assertCode(() -> service(connections, secrets, STRONG_PASSWORD, "\t\n")
                .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        verifyNoInteractions(secrets);
    }

    @Test
    void encryptionConfigRejectsEveryNonCanonicalBase64Spelling() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        when(connections.findByKey("platform-api")).thenReturn(connection());

        String urlSafePassword = Base64.getUrlEncoder().encodeToString(
                repeatedBytes(32, (byte) 0xff));
        String urlSafeSalt = Base64.getUrlEncoder().encodeToString(
                repeatedBytes(16, (byte) 0xff));
        assertNonZeroPaddingBitsAreAcceptedByJavaDecoder(STRONG_PASSWORD);
        assertNonZeroPaddingBitsAreAcceptedByJavaDecoder(STRONG_SALT);
        for (String nonCanonicalPassword : List.of(
                withoutPadding(STRONG_PASSWORD),
                withNonZeroPaddingBits(STRONG_PASSWORD),
                withEmbeddedWhitespace(STRONG_PASSWORD),
                urlSafePassword)) {
            assertCode(() -> service(
                            connections, secrets, nonCanonicalPassword, STRONG_SALT)
                    .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        }
        for (String nonCanonicalSalt : List.of(
                withoutPadding(STRONG_SALT),
                withNonZeroPaddingBits(STRONG_SALT),
                withEmbeddedWhitespace(STRONG_SALT),
                urlSafeSalt)) {
            assertCode(() -> service(
                            connections, secrets, STRONG_PASSWORD, nonCanonicalSalt)
                    .replace("platform-api", "plain-api-key"), RuntimeErrorCode.UNAVAILABLE);
        }

        verifyNoInteractions(secrets);
    }

    @Test
    void missingConnectionOrSecretReturnsOnlySafeErrorCodes() {
        PlatformAiConnectionMapper connections = mock(PlatformAiConnectionMapper.class);
        PlatformAiSecretMapper secrets = mock(PlatformAiSecretMapper.class);
        PlatformSecretService service = service(
                connections, secrets, STRONG_PASSWORD, STRONG_SALT);

        assertCode(() -> service.replace("missing-secret-key", "plain-secret"),
                RuntimeErrorCode.NOT_FOUND);
        assertCode(() -> service.requirePlaintext(99L), RuntimeErrorCode.UNAVAILABLE);
        assertThatThrownBy(() -> service.replace("missing-secret-key", "plain-secret"))
                .hasMessageNotContaining("plain-secret")
                .hasMessageNotContaining("missing-secret-key");
        verify(secrets, org.mockito.Mockito.never()).upsert(
                org.mockito.ArgumentMatchers.any());
    }

    private static PlatformSecretService service(
            PlatformAiConnectionMapper connections,
            PlatformAiSecretMapper secrets,
            String password,
            String salt) {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getPlatformSecret().setPassword(password);
        properties.getPlatformSecret().setSalt(salt);
        return new PlatformSecretServiceImpl(connections, secrets, properties);
    }

    private static PlatformAiConnection connection() {
        return new PlatformAiConnection(
                3L, "platform-api", "API", "ANTHROPIC", null,
                "https://api.example.test", false, true, 0);
    }

    private static String base64Bytes(int length, int seed) {
        byte[] material = new byte[length];
        for (int index = 0; index < length; index++) {
            material[index] = (byte) (seed + index * 13);
        }
        return Base64.getEncoder().encodeToString(material);
    }

    private static byte[] repeatedBytes(int length, byte value) {
        byte[] material = new byte[length];
        java.util.Arrays.fill(material, value);
        return material;
    }

    private static String withoutPadding(String canonical) {
        return canonical.substring(0, canonical.indexOf('='));
    }

    private static String withEmbeddedWhitespace(String canonical) {
        return canonical.substring(0, 4) + "\n" + canonical.substring(4);
    }

    private static String withNonZeroPaddingBits(String canonical) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int paddingStart = canonical.indexOf('=');
        int lastDataIndex = paddingStart - 1;
        int canonicalValue = alphabet.indexOf(canonical.charAt(lastDataIndex));
        return canonical.substring(0, lastDataIndex)
                + alphabet.charAt(canonicalValue + 1)
                + canonical.substring(paddingStart);
    }

    private static void assertNonZeroPaddingBitsAreAcceptedByJavaDecoder(String canonical) {
        String nonCanonical = withNonZeroPaddingBits(canonical);
        byte[] decoded = Base64.getDecoder().decode(nonCanonical);
        assertThat(decoded).containsExactly(Base64.getDecoder().decode(canonical));
        assertThat(Base64.getEncoder().encodeToString(decoded)).isEqualTo(canonical);
    }

    private static void assertCode(Runnable action, RuntimeErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(code);
    }
}
