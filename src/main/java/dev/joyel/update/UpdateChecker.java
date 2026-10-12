package dev.joyel.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class UpdateChecker {

    private static final String RELEASES_URL = "https://github.com/constnulled/JByteMod-ReForked/releases";
    private static final String RELEASES_API = "https://api.github.com/repos/constnulled/JByteMod-ReForked/releases?per_page=30";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private UpdateChecker() {}

    public static UpdateRelease findUpdate(String currentVersionStr, String responseBody) {
        SemanticVersion current = SemanticVersion.parse(currentVersionStr);
        if (current == null || responseBody == null || responseBody.isBlank()) return null;

        JsonArray releases;
        try {
            JsonElement el = JsonParser.parseString(responseBody);
            if (!el.isJsonArray()) return null;
            releases = el.getAsJsonArray();
        } catch (Exception e) {
            return null;
        }

        boolean includePrereleases = current.isPrerelease();
        List<Candidate> candidates = new ArrayList<>();

        for (JsonElement el : releases) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            boolean isDraft = obj.has("draft") && obj.get("draft").getAsBoolean();
            if (isDraft) continue;

            boolean isPrerelease = obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
            String tagName = obj.has("tag_name") ? obj.get("tag_name").getAsString() : null;
            String htmlUrl = obj.has("html_url") ? obj.get("html_url").getAsString() : RELEASES_URL;
            String body = obj.has("body") ? obj.get("body").getAsString() : "";

            SemanticVersion version = SemanticVersion.parse(tagName);
            if (version == null) continue;
            if (!includePrereleases && (isPrerelease || version.isPrerelease())) continue;
            if (version.compareTo(current) > 0) {
                candidates.add(new Candidate(version, htmlUrl, body));
            }
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparing(c -> c.version));
        Candidate best = candidates.getLast();
        String url = (best.url == null || best.url.isBlank()) ? RELEASES_URL : best.url;
        return new UpdateRelease(best.version.toString(), url, best.changelog);
    }

    public static UpdateRelease checkForUpdate(String currentVersion) {
        String body = fetchReleasesJson();
        if (body == null) return null;
        return findUpdate(currentVersion, body);
    }

    private static String fetchReleasesJson() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_API))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "JByteMod-ReForked-Update-Checker")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return null;
        }
    }

    private record Candidate(SemanticVersion version, String url, String changelog) {}
}
