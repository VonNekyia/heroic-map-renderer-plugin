import com.nekyia.heroicmap.xz.XZInputStream;
import java.io.BufferedInputStream;
import java.util.zip.ZipFile;

/**
 * Packt einen Eintrag des Jars nach stdout aus, mit dem Decoder, den das Jar selbst mitbringt. Für pruefe-jar.sh:
 * java -cp <jar> .github/Auspacken.java <jar> <eintrag>. Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
 */
class Auspacken {
    public static void main(String[] a) throws Exception {
        try (var jar = new ZipFile(a[0]);
                var in = new XZInputStream(new BufferedInputStream(jar.getInputStream(jar.getEntry(a[1]))))) {
            in.transferTo(System.out);
        }
        System.out.flush();
    }
}
