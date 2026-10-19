package com.yelloowstone.vslauncher.mods;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Minimal client for the Vintage Story mod database (https://mods.vintagestory.at). */
public class ModDbClient {
    private static final String API_MOD_URL = "https://mods.vintagestory.at/api/mod/";
    private static final String USER_AGENT = "vs-launcher";

    /** One release entry of a mod. */
    public record Release(String version, String mainFile, String fileName) {
    }

    /** A mod's name and newest release (null when nothing is published). */
    public record ModRecord(String name, Release latest) {
    }

    public static class ModDbException extends Exception {
        private static final long serialVersionUID = 1L;

        public ModDbException(final String message) {
            super(message);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public ModRecord fetch(final String modid) throws ModDbException {
        final String url = API_MOD_URL + URLEncoder.encode(modid, StandardCharsets.UTF_8);
        final HttpResponse<String> response;
        try {
            response = http.send(request(url), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ModDbException("network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModDbException("interrupted");
        }
        if (response.statusCode() == 404) {
            throw new ModDbException("not found on moddb");
        }
        if (response.statusCode() != 200) {
            throw new ModDbException("http error " + response.statusCode());
        }

        final JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw new ModDbException("unreadable moddb response");
        }
        final String status = root.path("statuscode").asString("");
        if ("404".equals(status)) {
            throw new ModDbException("not found on moddb");
        }
        if (!"200".equals(status)) {
            throw new ModDbException("api returned status " + status);
        }

        final JsonNode mod = root.path("mod");
        final String name = mod.path("name").asString(null);
        final JsonNode releases = mod.path("releases");
        if (!releases.isArray() || releases.size() == 0) {
            return new ModRecord(name, null);
        }
        final JsonNode newest = releases.get(0);
        return new ModRecord(name, new Release(
                newest.path("modversion").asString(null),
                newest.path("mainfile").asString(null),
                newest.path("filename").asString(null)));
    }

    public byte[] download(final String url) throws IOException {
        try {
            final HttpResponse<byte[]> response = http.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("http error " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    private static HttpRequest request(final String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
