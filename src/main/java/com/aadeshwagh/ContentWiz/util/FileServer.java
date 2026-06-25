package com.aadeshwagh.ContentWiz.util;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

@Component
public class FileServer {

    @Value("${ngrok.auth.token}")
    private String authToken;

    @Value("${content.root-directory}")
    private String contentRootDir;

    @Value("${content.file-server.port:8765}")
    private int fileServerPort;

    private NgrokClient ngrokClient;
    private HttpServer httpServer;
    private String publicBaseUrl;

    /**
     * @return ngrok public base URL
     * @throws IOException              if file server fails to start
     * @throws IllegalStateException    if contentRootDir does not exist
     */
    public synchronized String getPublicBaseUrl() throws IOException {
        if (publicBaseUrl != null) {
            return publicBaseUrl;
        }

        startFileServer();
        startNgrokTunnel();

        return publicBaseUrl;
    }

    private void startFileServer() throws IOException {
        Path rootPath = Paths.get(contentRootDir).toAbsolutePath().normalize();

        if (!Files.exists(rootPath)) {
            throw new IllegalStateException("Content root directory does not exist: " + rootPath);
        }

        httpServer = HttpServer.create(new InetSocketAddress(fileServerPort), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());

        httpServer.createContext("/", exchange -> {
            String requestedPath = exchange.getRequestURI().getPath().replaceFirst("^/", "");
            Path filePath = rootPath.resolve(requestedPath).normalize();

            if (!filePath.startsWith(rootPath)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        httpServer.start();
    }

    private void startNgrokTunnel() {
        JavaNgrokConfig config = new JavaNgrokConfig.Builder()
                .withAuthToken(authToken)
                .build();

        ngrokClient = new NgrokClient.Builder()
                .withJavaNgrokConfig(config)
                .build();

        Tunnel tunnel = ngrokClient.connect(new CreateTunnel.Builder()
                .withAddr(fileServerPort)
                .build());

        publicBaseUrl = tunnel.getPublicUrl();
    }

    @PreDestroy
    public void shutdown() {
        if (httpServer != null) httpServer.stop(0);
        if (ngrokClient != null) ngrokClient.kill();
    }
}