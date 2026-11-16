package team.nofantasy.recommendationsystem.recommendationservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!test")
@Slf4j
public class XrayTunnelService {
    private static final String LOOPBACK = "127.0.0.1";
    private static final int START_TIMEOUT_SECONDS = 5;

    private final String executable;
    private final Path configFile;
    private final int socksPort;
    private final Proxy proxy;
    private final Counter startFailures;
    private volatile Process process;
    private volatile boolean closed;

    public XrayTunnelService(
            @Value("${AMNEZIA_XRAY_CONFIGURATION:}") String shareLink,
            @Value("${spring.ai.anthropic.api-key:}") String apiKey,
            @Value("${app.xray.executable:xray}") String executable,
            JsonMapper mapper,
            MeterRegistry registry
    ) {
        this.executable = executable;
        this.startFailures = registry.counter("recommendation.xray.start.failures");

        Path prepared = null;
        int selectedPort = 10_808;
        if (StringUtils.hasText(apiKey) && StringUtils.hasText(shareLink)) {
            try {
                selectedPort = availablePort();
                prepared = secretFile(AmneziaXrayConfig.fromShareLink(shareLink, selectedPort, mapper).json());
            } catch (IOException | IllegalArgumentException error) {
                startFailures.increment();
                log.atWarn().setMessage("XRay tunnel configuration is invalid; Claude requests are disabled").log();
            }
        } else if (StringUtils.hasText(apiKey)) {
            log.atWarn().setMessage("AMNEZIA_XRAY_CONFIGURATION is missing; Claude requests are disabled").log();
        }

        this.socksPort = selectedPort;
        this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(LOOPBACK, selectedPort));
        this.configFile = prepared;
        Gauge.builder("recommendation.xray.ready", this, tunnel -> tunnel.isReady() ? 1 : 0)
                .description("Whether the local XRay SOCKS tunnel is accepting connections")
                .register(registry);

        if (configFile != null) {
            start();
        }
    }

    public Proxy proxy() {
        return proxy;
    }

    public boolean isReady() {
        Process current = process;
        if (current == null || !current.isAlive()) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK, socksPort), 200);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public synchronized void restartIfNeeded() {
        if (configFile != null && !closed && !isReady()) {
            stopProcess();
            start();
        }
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        stopProcess();
        if (configFile != null) {
            try {
                Files.deleteIfExists(configFile);
            } catch (IOException error) {
                log.atWarn().setMessage("Temporary XRay configuration could not be removed").log();
            }
        }
    }

    private synchronized void start() {
        if (closed || configFile == null) {
            return;
        }
        try {
            Process started = new ProcessBuilder(executable, "run", "-c", configFile.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process = started;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline && started.isAlive()) {
                if (isReady()) {
                    log.atInfo().setMessage("XRay tunnel is accepting local SOCKS connections").log();
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(50);
            }
            stopProcess();
            startFailures.increment();
            log.atWarn().setMessage("XRay tunnel did not start; Claude requests are disabled").log();
        } catch (IOException error) {
            startFailures.increment();
            log.atWarn().setMessage("XRay executable could not be started; Claude requests are disabled").log();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            stopProcess();
            startFailures.increment();
            log.atWarn().setMessage("XRay startup was interrupted; Claude requests are disabled").log();
        }
    }

    private void stopProcess() {
        Process current = process;
        process = null;
        if (current == null || !current.isAlive()) {
            return;
        }
        current.destroy();
        try {
            if (!current.waitFor(2, TimeUnit.SECONDS)) {
                current.destroyForcibly();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(LOOPBACK, 0));
            return socket.getLocalPort();
        }
    }

    private static Path secretFile(String config) throws IOException {
        Path file = Files.createTempFile("recommendation-xray-", ".json");
        try {
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
            }
            Files.writeString(file, config, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            return file;
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(file);
            throw error;
        }
    }
}
