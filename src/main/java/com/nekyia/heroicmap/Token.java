package com.nekyia.heroicmap;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Das Token für den Kartendownload, Byte für Byte wie in docs/plugin.md, „Token“, des Renderers.
 * Siehe docs/download.md, „Token“.
 */
final class Token {

    static final int GEHEIMNIS = 32;
    static final int ZUFALL = 16;
    private static final Pattern BAUM = Pattern.compile("[a-z0-9-]{1,64}");
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private Token() {}

    /** Ablauf und Deckel als u64; ein negativer long steht für Werte ab 2^63. */
    static String stelleAus(byte[] geheimnis, UUID spieler, long ablauf, long deckel, int stufe, byte[] zufall, String baum) {
        if (geheimnis.length != GEHEIMNIS || zufall.length != ZUFALL || stufe < 0 || stufe > 255
                || !BAUM.matcher(baum).matches()) {
            throw new IllegalArgumentException("Token: Geheimnis, Zufall, Stufe oder Baum passt nicht");
        }
        byte[] b = baum.getBytes(StandardCharsets.US_ASCII);
        byte[] inhalt = ByteBuffer.allocate(51 + b.length)
                .put((byte) 1)
                .putLong(spieler.getMostSignificantBits()).putLong(spieler.getLeastSignificantBits())
                .putLong(ablauf).putLong(deckel)
                .put((byte) stufe).put(zufall)
                .put((byte) b.length).put(b)
                .array();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(geheimnis, "HmacSHA256"));
            return BASE64URL.encodeToString(inhalt) + "." + BASE64URL.encodeToString(mac.doFinal(inhalt));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 fehlt in diesem Java", e);
        }
    }
}
