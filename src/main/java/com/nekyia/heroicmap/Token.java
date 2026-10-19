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
 * Die Token für den Kartendownload und die Anmeldung auf der Webkarte, Byte für Byte wie in docs/plugin.md,
 * „Token“ und „Anmeldung und Sitzung“, des Renderers. Siehe docs/download.md, „Token“, und docs/anmeldung.md.
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
        return unterschrieben(geheimnis, inhalt);
    }

    /** Die Anmeldung, Fassung 2: 45 Byte Inhalt; {@code zaehler} als u32. */
    static String anmeldung(byte[] geheimnis, UUID spieler, long ablauf, long zaehler, byte[] zufall) {
        if (geheimnis.length != GEHEIMNIS || zufall.length != ZUFALL || zaehler < 0 || zaehler > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Token: Geheimnis, Zufall oder Zähler passt nicht");
        }
        byte[] inhalt = ByteBuffer.allocate(45)
                .put((byte) 2)
                .putLong(spieler.getMostSignificantBits()).putLong(spieler.getLeastSignificantBits())
                .putLong(ablauf).putInt((int) zaehler)
                .put(zufall)
                .array();
        return unterschrieben(geheimnis, inhalt);
    }

    private static String unterschrieben(byte[] geheimnis, byte[] inhalt) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(geheimnis, "HmacSHA256"));
            return BASE64URL.encodeToString(inhalt) + "." + BASE64URL.encodeToString(mac.doFinal(inhalt));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 fehlt in diesem Java", e);
        }
    }
}
