package cn.alini.trueuuid.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Guards actionable premium-session denial messages in both locale sources. */
class PremiumReauthenticationMessageTest {
    private static final String LOCALE = "assets/trueuuid/lang/en_us.json";

    @Test
    void forgeLoginScreenExplainsReauthenticationWithoutOfflineBypass() throws Exception {
        try (InputStream data = getClass().getClassLoader().getResourceAsStream(LOCALE)) {
            assertNotNull(data, "Forge login language file must be packaged");
            JsonObject lang = JsonParser.parseReader(
                    new InputStreamReader(data, StandardCharsets.UTF_8)).getAsJsonObject();
            assertPremiumGuidance(lang);
        }
    }

    @Test
    void sharedLocaleAlsoExplainsReauthenticationWithoutOfflineBypass() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path shared = null;
        for (Path directory = root; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("platform/common/assets/src/main/resources").resolve(LOCALE);
            if (Files.isRegularFile(candidate)) {
                shared = candidate;
                break;
            }
        }
        assertNotNull(shared, "shared locale source must exist in the repository");
        JsonObject lang = JsonParser.parseString(Files.readString(shared, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertPremiumGuidance(lang);
    }

    private static void assertPremiumGuidance(JsonObject lang) throws IOException {
        assertTrue(lang.has("trueuuid.disconnect.bound_premium"), "missing bound premium message");
        String bound = lang.get("trueuuid.disconnect.bound_premium").getAsString();
        assertTrue(bound.contains("sign out"), "Explain how to refresh an expired session");
        assertTrue(bound.contains("sign in again"), "Explain how to refresh an expired session");
        assertTrue(bound.contains("Offline login is blocked"), "Never suggest a premium UUID bypass");

        assertTrue(lang.has("trueuuid.disconnect.auth_denied"), "missing auth denial message");
        String denied = lang.get("trueuuid.disconnect.auth_denied").getAsString();
        assertTrue(denied.contains("reauthenticate"));
        assertTrue(denied.contains("Offline login cannot replace a verified premium identity"));
    }
}
