package cn.alini.trueuuid.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Prevents the premium-session failure screen from regressing to generic network advice. */
class PremiumReauthenticationMessageTest {
    @Test
    void expiredPremiumSessionExplainsReauthenticationWithoutOfferingOfflineBypass() throws Exception {
        try (InputStream data = getClass().getClassLoader()
                .getResourceAsStream("assets/trueuuid/lang/en_us.json")) {
            assertNotNull(data, "Forge login language file must be packaged");
            JsonObject lang = JsonParser.parseReader(
                    new InputStreamReader(data, StandardCharsets.UTF_8)).getAsJsonObject();

            String bound = lang.get("trueuuid.disconnect.bound_premium").getAsString();
            assertTrue(bound.contains("sign out"), "Explain how to refresh an expired session");
            assertTrue(bound.contains("sign in again"), "Explain how to refresh an expired session");
            assertTrue(bound.contains("Offline login is blocked"), "Never suggest a premium-UUID bypass");

            String denied = lang.get("trueuuid.disconnect.auth_denied").getAsString();
            assertTrue(denied.contains("reauthenticate"));
            assertTrue(denied.contains("Offline login cannot replace a verified premium identity"));
        }
    }
}
