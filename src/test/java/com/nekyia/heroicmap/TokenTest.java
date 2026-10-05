package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Gegen die Testvektoren des Renderers, eine Kopie von renderer/tests/fixtures/token.json; die CI vergleicht sie. */
class TokenTest {

    private static JsonObject vektoren() throws Exception {
        var datei = Objects.requireNonNull(TokenTest.class.getResourceAsStream("/token.json"));
        try (var r = new InputStreamReader(datei, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void stellt_jedes_gueltige_token_der_vektoren_aus() throws Exception {
        var v = vektoren();
        byte[] geheimnis = HexFormat.of().parseHex(v.get("geheimnis_hex").getAsString());
        var gueltig = v.getAsJsonArray("gueltig");
        assertEquals(4, gueltig.size());
        for (var e : gueltig) {
            var t = e.getAsJsonObject();
            String token = Token.stelleAus(geheimnis,
                    UUID.fromString(t.get("uuid").getAsString()),
                    new BigInteger(t.get("ablauf").getAsString()).longValue(),
                    new BigInteger(t.get("deckel").getAsString()).longValue(),
                    t.get("stufe").getAsInt(),
                    HexFormat.of().parseHex(t.get("zufall_hex").getAsString()),
                    t.get("baum").getAsString());
            assertEquals(t.get("token").getAsString(), token, t.get("name").getAsString());
        }
    }

    @Test
    void lehnt_ab_was_kein_gueltiges_token_ergaebe() {
        byte[] g = new byte[Token.GEHEIMNIS];
        byte[] z = new byte[Token.ZUFALL];
        var u = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(new byte[31], u, 1, 1, 9, z, "top-north-s"));
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(g, u, 1, 1, 9, new byte[15], "top-north-s"));
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(g, u, 1, 1, 256, z, "top-north-s"));
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(g, u, 1, 1, 9, z, "../x"));
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(g, u, 1, 1, 9, z, ""));
        assertThrows(IllegalArgumentException.class, () -> Token.stelleAus(g, u, 1, 1, 9, z, "a".repeat(65)));
    }
}
