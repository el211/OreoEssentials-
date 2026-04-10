package fr.elias.oreoessentialsweb.dto.plugin;

public record PluginSyncResponse(
        boolean accepted,
        String message
) {}
