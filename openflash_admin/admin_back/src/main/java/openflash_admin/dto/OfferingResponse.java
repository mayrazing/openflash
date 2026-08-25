package openflash_admin.dto;

public record OfferingResponse(
    String offeringKey,
    String source,
    String modelKey,
    boolean enabled,
    boolean defaultAccess,
    int sortOrder,
    String runtimeStatus
) {
}
