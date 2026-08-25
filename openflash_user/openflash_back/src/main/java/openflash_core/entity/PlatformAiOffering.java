package openflash_core.entity;

public record PlatformAiOffering(
        long id,
        String offeringKey,
        String modelKey,
        boolean enabled,
        boolean defaultAccess,
        int sortOrder,
        String kind,
        String protocol,
        boolean connectionEnabled,
        boolean accessGranted) {

    public boolean usable() {
        return enabled && connectionEnabled && accessGranted;
    }
}
