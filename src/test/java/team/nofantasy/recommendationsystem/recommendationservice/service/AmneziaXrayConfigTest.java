package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.junit.jupiter.api.Test;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmneziaXrayConfigTest {
    private static final String OUTBOUND = """
            {"protocol":"vless","settings":{"vnext":[{"address":"vps.example","port":443,
              "users":[{"id":"00000000-0000-0000-0000-000000000001","encryption":"none"}]}]},
             "streamSettings":{"network":"xhttp","security":"reality",
               "xhttpSettings":{"path":"/course"},"realitySettings":{"serverName":"example.org","publicKey":"test"}}}
            """;

    @Test
    void createsLoopbackSocksWithOnlyTheVlessOutbound() throws IOException {
        AmneziaXrayConfig config = AmneziaXrayConfig.fromShareLink(shareLink(OUTBOUND), 23_456, TestFixtures.JSON);
        JsonNode nativeConfig = TestFixtures.JSON.readTree(config.json());

        assertThat(nativeConfig.get("inbounds").get(0).get("listen").asString()).isEqualTo("127.0.0.1");
        assertThat(nativeConfig.get("inbounds").get(0).get("port").asInt()).isEqualTo(23_456);
        assertThat(nativeConfig.get("inbounds").get(0).get("protocol").asString()).isEqualTo("socks");
        assertThat(nativeConfig.get("outbounds")).hasSize(1);
        assertThat(nativeConfig.get("outbounds").get(0).get("protocol").asString()).isEqualTo("vless");
        assertThat(nativeConfig.get("outbounds").get(0).get("streamSettings").get("network").asString())
                .isEqualTo("xhttp");
        assertThat(nativeConfig.has("routing")).isFalse();
    }

    @Test
    void rejectsAnOutboundThatWouldBypassTheTunnel() throws IOException {
        assertThatThrownBy(() -> AmneziaXrayConfig.fromShareLink(
                shareLink(OUTBOUND.replace("\"vless\"", "\"freedom\"")), 23_456, TestFixtures.JSON
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Amnezia XRay configuration");
    }

    @Test
    void rejectsInvalidShareLinksWithoutDisclosingTheirContents() {
        assertThatThrownBy(() -> AmneziaXrayConfig.fromShareLink("vpn://invalid", 23_456, TestFixtures.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Amnezia XRay configuration");
    }

    private static String shareLink(String outbound) throws IOException {
        ObjectNode root = TestFixtures.JSON.createObjectNode();
        root.putArray("containers").addObject().putObject("xray")
                .put("last_config", "{\"outbounds\":[" + outbound + "]}");
        byte[] json = TestFixtures.JSON.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(ByteBuffer.allocate(Integer.BYTES).putInt(json.length).array());
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(bytes)) {
            compressed.write(json);
        }
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }
}
