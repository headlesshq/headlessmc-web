package io.github.headlesshq.web.mods.icon;

import io.github.headlesshq.headlessmc.config.Holder;
import io.github.headlesshq.headlessmc.net.NetConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up the icons of Modrinth projects. HeadlessMc's {@code RemoteMod}s do not carry the icon,
 * so the projects found by a search are fetched with one request to
 * <a href=https://docs.modrinth.com/api/operations/getprojects/>/v2/projects</a>, which accepts slugs.
 */
@ApplicationScoped
public class ModrinthIconService {
    private static final Logger LOG = Logger.getLogger(ModrinthIconService.class);
    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // slug -> icon url, empty if the project has no icon
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(TIMEOUT)
        .build();

    private final Holder<NetConfig> netConfig;
    private final String apiUrl;

    @Inject
    public ModrinthIconService(
        Holder<NetConfig> netConfig,
        @ConfigProperty(name = "hmc.web.modrinth.api-url", defaultValue = "https://api.modrinth.com") String apiUrl
    ) {
        this.netConfig = netConfig;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    /**
     * @param slugs the slugs (or ids) of Modrinth projects.
     * @return the icon urls of the projects that have one, by slug.
     * Empty if the request fails, icons are not worth failing a search for.
     */
    public Map<String, String> getIcons(Collection<String> slugs) {
        List<String> missing = slugs.stream().distinct().filter(slug -> !cache.containsKey(slug)).toList();
        if (!missing.isEmpty()) {
            try {
                fetch(missing);
            } catch (IOException | RuntimeException e) {
                LOG.debugf(e, "Failed to fetch Modrinth icons of %s", missing);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Map<String, String> result = new HashMap<>();
        for (String slug : slugs) {
            Optional<String> icon = cache.get(slug);
            if (icon != null && icon.isPresent()) {
                result.put(slug, icon.get());
            }
        }

        return result;
    }

    private void fetch(List<String> slugs) throws IOException, InterruptedException {
        String ids = MAPPER.writeValueAsString(slugs);
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/v2/projects?ids=" + URLEncoder.encode(ids, StandardCharsets.UTF_8)))
            .header("User-Agent", netConfig.get().userAgent())
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth responded with " + response.statusCode() + ": " + response.body());
        }

        for (JsonNode project : MAPPER.readTree(response.body())) {
            JsonNode icon = project.get("icon_url");
            Optional<String> url = icon != null && icon.isString() && !icon.asString().isBlank()
                ? Optional.of(icon.asString())
                : Optional.empty();
            // the search returns slugs, but accept ids too
            for (String key : List.of(project.path("slug").asString(""), project.path("id").asString(""))) {
                if (!key.isEmpty()) {
                    cache.put(key, url);
                }
            }
        }
    }

}
