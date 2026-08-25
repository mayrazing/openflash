package openflash_admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.core.io.ClassPathResource;

class AdminPlatformAiMapperXmlTest {

    @Test
    void myBatisParsesEveryPlatformMapperStatement() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = new ClassPathResource("mapper/AdminPlatformAiMapper.xml")
                .getInputStream()) {
            new XMLMapperBuilder(
                input,
                configuration,
                "mapper/AdminPlatformAiMapper.xml",
                configuration.getSqlFragments()).parse();
        }

        String namespace = AdminPlatformAiMapper.class.getName() + ".";
        assertThat(configuration.hasStatement(namespace + "findCatalogRows")).isTrue();
        assertThat(configuration.hasStatement(namespace + "findEnabledOfferings")).isTrue();
        assertThat(configuration.hasStatement(namespace + "findUserAccessOverrides")).isTrue();
        assertThat(configuration.hasStatement(namespace + "findEnabledOfferingByKey")).isTrue();
        assertThat(configuration.hasStatement(namespace + "findCliOffering")).isTrue();
    }

    @Test
    void legacyFeatureMapperIsRemovedAfterPlatformReplacement() {
        assertThat(Files.exists(Path.of(
            "src/main/java/openflash_admin/feature/AdminFeatureFlagMapper.java"))).isFalse();
        assertThat(Files.exists(Path.of(
            "src/main/resources/mapper/AdminFeatureFlagMapper.xml"))).isFalse();
    }

    @Test
    void mapperReadsOnlySafeCatalogAndAccessTablesUsingV59Columns() throws IOException {
        String xml = resource();
        String forbiddenSecretTable = "pw_platform_ai_" + "secret";

        assertThat(xml).contains(
            "pw_platform_ai_connection",
            "pw_platform_ai_offering",
            "pw_platform_ai_user_access",
            "c.config -&gt;&gt; 'baseurl'");
        assertThat(xml).doesNotContain(
            forbiddenSecretTable, "secret_enc", "pw_feature_flag",
            "pw_user_feature_flag");
    }

    @Test
    void catalogQueryKeepsConnectionsWithoutOfferingsAndUsesStableDatabaseOrder()
            throws IOException {
        String sql = statement("findCatalogRows");

        assertThat(sql).contains(
            "from pw_platform_ai_connection c",
            "left join pw_platform_ai_offering o on o.connection_id = c.id",
            "order by c.sort_order, c.id, o.sort_order, o.id");
    }

    @Test
    void userPageUsesOneEnabledOfferingQueryAndOneBoundedOverrideQuery()
            throws IOException {
        String offerings = statement("findEnabledOfferings");
        String overrides = statement("findUserAccessOverrides");

        assertThat(offerings).contains(
            "join pw_platform_ai_connection c on c.id = o.connection_id",
            "where c.enabled = 1 and o.enabled = 1",
            "order by c.sort_order, o.sort_order, o.id");
        assertThat(overrides).contains(
            "from pw_platform_ai_user_access a",
            "join pw_platform_ai_offering o on o.id = a.offering_id",
            "join pw_platform_ai_connection c on c.id = o.connection_id",
            "where c.enabled = 1 and o.enabled = 1",
            "a.user_id in",
            "<foreach collection=\"userids\" item=\"userid\" open=\"(\" separator=\",\" close=\")\">",
            "#{userid}");
    }

    @Test
    void pointReadsRejectDisabledOfferingsAndSelectOnlyDynamicCliOffering()
            throws IOException {
        String enabledOffering = statement("findEnabledOfferingByKey");
        String cliOffering = statement("findCliOffering");

        assertThat(enabledOffering).contains(
            "where o.offering_key = #{offeringkey}",
            "and c.enabled = 1",
            "and o.enabled = 1");
        assertThat(cliOffering).contains(
            "where c.cli_key = #{clikey}",
            "and o.model_key is null",
            "limit 1");
    }

    private static String resource() throws IOException {
        return normalize(new ClassPathResource("mapper/AdminPlatformAiMapper.xml")
            .getContentAsString(StandardCharsets.UTF_8));
    }

    private static String statement(String id) throws IOException {
        String xml = resource();
        int idStart = xml.indexOf("id=\"" + id.toLowerCase(Locale.ROOT) + "\"");
        assertThat(idStart).as("mapper statement %s", id).isGreaterThanOrEqualTo(0);
        int bodyStart = xml.indexOf('>', idStart) + 1;
        int bodyEnd = xml.indexOf("</select>", bodyStart);
        assertThat(bodyEnd).as("mapper select closing tag %s", id).isGreaterThan(bodyStart);
        return xml.substring(bodyStart, bodyEnd);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
