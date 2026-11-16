package team.nofantasy.recommendationsystem.recommendationservice.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

record AmneziaXrayConfig(String json) {
    private static final int MAX_CONFIG_BYTES = 1_048_576;

    static AmneziaXrayConfig fromShareLink(String shareLink, int socksPort, JsonMapper mapper) {
        try {
            if (shareLink == null || !shareLink.startsWith("vpn://") || socksPort < 1 || socksPort > 65_535) {
                throw new IllegalArgumentException();
            }

            byte[] binary = Base64.getUrlDecoder().decode(shareLink.substring("vpn://".length()).strip());
            if (binary.length < 6) {
                throw new IllegalArgumentException();
            }

            int expectedLength = ByteBuffer.wrap(binary, 0, Integer.BYTES).getInt();
            if (expectedLength < 1 || expectedLength > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException();
            }

            byte[] decoded;
            try (InflaterInputStream input = new InflaterInputStream(
                    new ByteArrayInputStream(binary, Integer.BYTES, binary.length - Integer.BYTES))) {
                decoded = input.readNBytes(MAX_CONFIG_BYTES + 1);
                if (decoded.length != expectedLength || input.read() != -1) {
                    throw new IllegalArgumentException();
                }
            }

            JsonNode shared = mapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode containers = shared.get("containers");
            if (containers == null || !containers.isArray()) {
                throw new IllegalArgumentException();
            }

            JsonNode amneziaXray = null;
            for (JsonNode container : containers) {
                JsonNode candidate = container.get("xray");
                if (candidate != null) {
                    if (amneziaXray != null) {
                        throw new IllegalArgumentException();
                    }
                    amneziaXray = candidate;
                }
            }
            if (amneziaXray == null) {
                throw new IllegalArgumentException();
            }

            JsonNode nativeConfigValue = amneziaXray.get("last_config");
            if (nativeConfigValue == null || !nativeConfigValue.isString()) {
                throw new IllegalArgumentException();
            }
            JsonNode nativeConfig = mapper.readTree(nativeConfigValue.asString());
            JsonNode outbounds = nativeConfig.get("outbounds");
            if (outbounds == null || !outbounds.isArray() || outbounds.size() != 1) {
                throw new IllegalArgumentException();
            }

            JsonNode outbound = outbounds.get(0);
            JsonNode stream = outbound.get("streamSettings");
            if (!is(outbound, "protocol", "vless")
                    || stream == null
                    || !is(stream, "network", "xhttp")
                    || !is(stream, "security", "reality")
                    || stream.get("xhttpSettings") == null
                    || stream.get("realitySettings") == null) {
                throw new IllegalArgumentException();
            }

            ObjectNode local = mapper.createObjectNode();
            ObjectNode inbound = local.putArray("inbounds").addObject();
            inbound.put("listen", "127.0.0.1");
            inbound.put("port", socksPort);
            inbound.put("protocol", "socks");
            inbound.putObject("settings").put("udp", false);
            local.putArray("outbounds").add(outbound.deepCopy());
            local.putObject("log").put("loglevel", "error");
            return new AmneziaXrayConfig(mapper.writeValueAsString(local));
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("Invalid Amnezia XRay configuration");
        }
    }

    private static boolean is(JsonNode object, String field, String expected) {
        JsonNode value = object.get(field);
        return value != null && value.isString() && expected.equals(value.asString());
    }
}
