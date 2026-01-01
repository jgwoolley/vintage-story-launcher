package com.yelloowstone.vslauncher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ClientSettingsFile {

    private Map<String, String> stringSettings;
    private Map<String, Integer> intSettings;
    private Map<String, Boolean> boolSettings;
    private Map<String, String> floatSettings;
    private Map<String, List<String>> stringListSettings;

    @JsonCreator
    public ClientSettingsFile(
            @JsonProperty("stringSettings") Map<String, String> stringSettings,
            @JsonProperty("intSettings") Map<String, Integer> intSettings,
            @JsonProperty("boolSettings") Map<String, Boolean> boolSettings,
            @JsonProperty("floatSettings") Map<String, String> floatSettings,
            @JsonProperty("stringListSettings") Map<String, List<String>> stringListSettings) {
        this.stringSettings = stringSettings;
        this.intSettings = intSettings;
        this.boolSettings = boolSettings;
        this.floatSettings = floatSettings;
        this.stringListSettings = stringListSettings;
    }

    public Map<String, String> getStringSettings() {
        return stringSettings;
    }

    public Map<String, Integer> getIntSettings() {
        return intSettings;
    }

    public Map<String, Boolean> getBoolSettings() {
        return boolSettings;
    }

    public Map<String, String> getFloatSettings() {
        return floatSettings;
    }

    public Map<String, List<String>> getStringListSettings() {
        return stringListSettings;
    }
}
