package ro.fastdiacritics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Adaugă diacriticele românești (ă, â, î, ș, ț) într-un text scris fără ele.
 *
 * <p>Strategia, în ordinea priorității, pentru fiecare cuvânt:
 * <ol>
 *   <li>cuvintele care au deja diacritice rămân neschimbate;</li>
 *   <li>reguli de context pentru cuvinte ambigue (ex. „ca” / „că”);</li>
 *   <li>dicționarul complet (~650.000 de forme, generat din Hunspell ro_RO + frecvențe din corpus):
 *       forma fără diacritice → forma corectă;</li>
 *   <li>reguli de sufix pentru cuvintele necunoscute (ex. „-tie” → „-ție”).</li>
 * </ol>
 * Majusculele din textul original sunt păstrate.
 */
public final class DiacriticsRestorer {

    private static final Locale RO = Locale.of("ro");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    /** Sufixe aplicate cuvintelor care nu sunt în dicționar (primul care se potrivește câștigă). */
    private static final String[][] SUFFIX_RULES = {
            {"tiilor", "țiilor"},
            {"tiile", "țiile"},
            {"atatea", "ătatea"},
            {"atate", "ătate"},
            {"ionala", "ională"},
            {"easca", "ească"},
            {"tiei", "ției"},
            {"tia", "ția"},
            {"tie", "ție"},
            {"tii", "ții"},
            {"isti", "iști"},
            {"esti", "ești"},
            {"este", "ește"},
            {"inta", "ință"},
            {"eti", "eți"},
            {"ati", "ați"},
            {"iti", "iți"},
            {"and", "ând"},
    };

    /** Cuvinte care se termină într-un sufix de mai sus, dar NU primesc diacritice. */
    private static final Set<String> SUFFIX_EXCEPTIONS = Set.of(
            "este", "peste", "poveste", "veste", "teste", "proteste", "manifeste", "modeste", "celeste",
            "brand", "grand", "stand", "comand", "recomand", "expand",
            "tinta", "pinta");

    /** Cuvinte după care „ca” devine „că” (ex. „cred că”, „pentru că”). */
    private static final Set<String> BEFORE_CA = Set.of(
            "pentru", "cred", "crezi", "crede", "credem", "credeti", "spune", "spun", "spus", "zice", "zis",
            "stiu", "stii", "stie", "stim", "stiti", "sper", "speram", "pare", "parea", "faptul", "vezi",
            "vedem", "vad", "sigur", "sigura", "clar", "evident", "adevarat", "posibil", "aflat", "afla",
            "declarat", "anuntat", "observat", "considera", "consider", "simt", "simte", "recunosc",
            "recunoaste", "uitat", "amintesc", "arata", "aratat", "dovedit", "explicat", "inseamna",
            "dat", "decat", "incat", "asa", "doar", "numai", "desi", "macar", "fiindca", "ca");

    /** Dicționarul complet, generat de {@code tools/build_dictionary.py}. */
    public static final String DEFAULT_DICTIONARY = "/dictionar/ro.txt.gz";
    /** Suprascrieri manuale, cu prioritate față de dicționarul complet. */
    public static final String OVERRIDES = "/dictionar/suplimentar.txt";

    private final Map<String, String> dictionary;

    public DiacriticsRestorer(Map<String, String> dictionary) {
        this.dictionary = Map.copyOf(dictionary);
    }

    /** Creează un restaurator cu suprascrierile manuale + dicționarul complet din classpath. */
    public static DiacriticsRestorer withDefaultDictionary() {
        Map<String, String> map = new HashMap<>(1 << 20);
        loadResource(OVERRIDES, map);
        loadResource(DEFAULT_DICTIONARY, map);
        return new DiacriticsRestorer(map);
    }

    private static void loadResource(String path, Map<String, String> into) {
        try (InputStream raw = DiacriticsRestorer.class.getResourceAsStream(path)) {
            if (raw == null) {
                throw new IllegalStateException(path + " nu a fost găsit în classpath");
            }
            loadDictionary(path.endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw, into);
        } catch (IOException e) {
            throw new IllegalStateException("Nu pot citi " + path, e);
        }
    }

    /**
     * Citește un dicționar: cuvinte scrise corect, separate prin spații/linii noi, comentarii cu „#”.
     * Cheia este cuvântul fără diacritice; dacă o cheie există deja în {@code into}, se păstrează
     * valoarea existentă (deci prima sursă / primul cuvânt din fișier câștigă).
     */
    public static void loadDictionary(InputStream in, Map<String, String> into) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        String line;
        while ((line = reader.readLine()) != null) {
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            for (String token : line.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                String word = normalizeCedilla(token).toLowerCase(RO);
                String key = strip(word);
                into.putIfAbsent(key, key.equals(word) ? key : word);
            }
        }
    }

    /** Adaugă diacriticele în text. */
    public String restore(String text) {
        return restoreWithStats(text).text();
    }

    /** Adaugă diacriticele și numără câte cuvinte au fost modificate. */
    public Result restoreWithStats(String text) {
        if (text == null || text.isEmpty()) {
            return new Result("", 0);
        }
        String normalized = normalizeCedilla(text);
        List<int[]> spans = WORD.matcher(normalized).results()
                .map(m -> new int[]{m.start(), m.end()})
                .toList();

        StringBuilder out = new StringBuilder(normalized);
        int changed = 0;
        for (int i = 0; i < spans.size(); i++) {
            String original = normalized.substring(spans.get(i)[0], spans.get(i)[1]);
            String previous = i > 0 ? lower(normalized.substring(spans.get(i - 1)[0], spans.get(i - 1)[1])) : "";
            String next = i + 1 < spans.size()
                    ? lower(normalized.substring(spans.get(i + 1)[0], spans.get(i + 1)[1])) : "";
            String restored = restoreWord(original, strip(previous), strip(next));
            if (!restored.equals(original)) {
                // Lungimea se păstrează (fiecare diacritică înlocuiește exact o literă).
                out.replace(spans.get(i)[0], spans.get(i)[1], restored);
                changed++;
            }
        }
        return new Result(out.toString(), changed);
    }

    private String restoreWord(String original, String previous, String next) {
        String lower = lower(original);
        if (!strip(lower).equals(lower)) {
            return original; // are deja diacritice – îl lăsăm cum a scris utilizatorul
        }
        String candidate = contextual(lower, previous, next);
        if (candidate == null) {
            candidate = dictionary.get(lower);
        }
        if (candidate == null) {
            candidate = applySuffixRules(lower);
        }
        return candidate.length() == original.length() ? matchCase(original, candidate) : original;
    }

    private static String contextual(String word, String previous, String next) {
        return switch (word) {
            case "ca" -> BEFORE_CA.contains(previous) && !previous.equals("ca") ? "că" : "ca";
            case "fata" -> next.equals("de") ? "față"                       // „față de”
                    : previous.equals("in") || previous.equals("din") ? "fața" // „în fața”, „din fața”
                    : "fata";
            case "doua" -> previous.equals("a") ? "doua" : "două"; // „a doua zi” vs. „două zile”
            default -> null;
        };
    }

    private static String applySuffixRules(String word) {
        String result = word;
        if (result.length() > 5 && result.contains("tion")) {
            result = result.replace("tion", "țion");
        }
        if (word.length() < 5 || SUFFIX_EXCEPTIONS.contains(word)) {
            return result;
        }
        for (String[] rule : SUFFIX_RULES) {
            if (result.endsWith(rule[0])) {
                if (rule[0].equals("tii") && result.endsWith("stii")) {
                    break;
                }
                return result.substring(0, result.length() - rule[0].length()) + rule[1];
            }
        }
        return result;
    }

    /** Aplică diacriticele din {@code target} păstrând majusculele din {@code original}. */
    private static String matchCase(String original, String target) {
        StringBuilder sb = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char c = target.charAt(i);
            sb.append(Character.isUpperCase(original.charAt(i)) ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String lower(String s) {
        return s.toLowerCase(RO);
    }

    /** Elimină diacriticele românești (ă â î ș ț → a a i s t). */
    public static String strip(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(switch (c) {
                case 'ă', 'â' -> 'a';
                case 'Ă', 'Â' -> 'A';
                case 'î' -> 'i';
                case 'Î' -> 'I';
                case 'ș', 'ş' -> 's';
                case 'Ș', 'Ş' -> 'S';
                case 'ț', 'ţ' -> 't';
                case 'Ț', 'Ţ' -> 'T';
                default -> c;
            });
        }
        return sb.toString();
    }

    /** Înlocuiește ş/ţ (cu sedilă, greșit) cu ș/ț (cu virgulă, corect). */
    public static String normalizeCedilla(String s) {
        return s.replace('ş', 'ș').replace('Ş', 'Ș').replace('ţ', 'ț').replace('Ţ', 'Ț');
    }

    public record Result(String text, int changedWords) {
    }
}
