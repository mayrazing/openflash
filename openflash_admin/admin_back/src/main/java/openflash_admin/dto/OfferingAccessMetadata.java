package openflash_admin.dto;

public record OfferingAccessMetadata(
    String offeringKey,
    String source,
    String connectionKey,
    String kind,
    String protocol,
    String cliKey,
    String modelKey,
    boolean defaultAccess
) {
}
