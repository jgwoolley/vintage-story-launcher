package com.yelloowstone.vslauncher.mods;

import java.util.Collections;
import java.util.Map;

/** A mod found in a Mods folder, with metadata parsed from its modinfo.json. */
public class LocalMod {
    private final String source;
    private final String modid;
    private final String version;
    private final String name;
    private final String description;
    private final byte[] icon;
    private final String error;
    private final Map<String, String> dependencies;

    LocalMod(final String source, final String modid, final String version, final String name,
            final String description, final byte[] icon, final Map<String, String> dependencies) {
        this.source = source;
        this.modid = modid;
        this.version = version;
        this.name = name != null ? name : modid;
        this.description = description;
        this.icon = icon;
        this.error = null;
        this.dependencies = dependencies;
    }

    LocalMod(final String source, final String error) {
        this.source = source;
        this.modid = null;
        this.version = null;
        this.name = source;
        this.description = null;
        this.icon = null;
        this.error = error;
        this.dependencies = Collections.emptyMap();
    }

    /** File or folder name inside the Mods folder. */
    public String getSource() {
        return source;
    }

    public String getModid() {
        return modid;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** PNG bytes of the mod's modicon.png, or null. */
    public byte[] getIcon() {
        return icon;
    }

    public String getError() {
        return error;
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }
}
