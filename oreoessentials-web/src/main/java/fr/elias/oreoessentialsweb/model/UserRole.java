package fr.elias.oreoessentialsweb.model;

/**
 * Roles available on the web panel.
 *
 * PLAYER — a Minecraft player who registered to view their own data.
 * OWNER  — a server/network owner who registered their server and configured
 *           their shared MongoDB URI (or enabled the plugin API sync).
 * ADMIN  — platform-level administrator (OreoEssentials team).
 */
public enum UserRole {
    PLAYER,
    OWNER,
    ADMIN
}
