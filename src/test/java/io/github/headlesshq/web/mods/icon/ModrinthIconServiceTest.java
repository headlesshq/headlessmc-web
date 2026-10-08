package io.github.headlesshq.web.mods.icon;

import com.sun.net.httpserver.HttpServer;
import io.github.headlesshq.headlessmc.net.NetConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModrinthIconServiceTest {
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ModrinthIconService service;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/projects", exchange -> {
            requests.add(exchange.getRequestHeaders().getFirst("User-Agent") + " "
                + URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] body = """
                [{"id": "AANobbMI", "slug": "sodium", "icon_url": "https://cdn.modrinth.com/sodium.png"},
                 {"id": "P7dR8mSH", "slug": "fabric-api", "icon_url": null}]
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        NetConfig config = (NetConfig) Proxy.newProxyInstance(
            NetConfig.class.getClassLoader(),
            new Class<?>[]{NetConfig.class},
            (proxy, method, args) -> "userAgent".equals(method.getName()) ? "HeadlessMc/test" : null
        );
        service = new ModrinthIconService(() -> config, "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesIconsOnceInOneRequest() {
        Map<String, String> expected = Map.of("sodium", "https://cdn.modrinth.com/sodium.png");
        assertEquals(expected, service.getIcons(List.of("sodium", "fabric-api")));
        assertEquals(List.of("HeadlessMc/test ids=[\"sodium\",\"fabric-api\"]"), requests);

        // cached, also projects without an icon
        assertEquals(expected, service.getIcons(List.of("fabric-api", "sodium")));
        assertEquals(1, requests.size());
    }

    @Test
    void returnsNoIconsIfModrinthIsUnreachable() {
        server.stop(0);
        assertEquals(Map.of(), service.getIcons(List.of("sodium")));
    }

}
