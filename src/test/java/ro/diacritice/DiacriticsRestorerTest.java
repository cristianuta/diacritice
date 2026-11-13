package ro.diacritice;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiacriticsRestorerTest {

    private static DiacriticsRestorer restorer;

    @BeforeAll
    static void loadDictionary() {
        restorer = DiacriticsRestorer.withDefaultDictionary();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            si                                                            | și
            Uta                                                           | Uță
            UTA                                                           | UȚĂ
            Buna ziua! Astazi mergem in oras sa cumparam paine si branza. | Bună ziua! Astăzi mergem în oraș să cumpărăm pâine și brânză.
            Cred ca este o idee buna.                                     | Cred că este o idee bună.
            Este la fel ca tine.                                          | Este la fel ca tine.
            Am venit pentru ca m-ai chemat.                               | Am venit pentru că m-ai chemat.
            Stam in fata casei.                                           | Stăm în fața casei.
            Fata a intrat in casa.                                        | Fata a intrat în casa.
            informatie, solutiile, situatiei, nationala                   | informație, soluțiile, situației, națională
            Voi ati facut asta?                                           | Voi ați făcut asta?
            El citeste si vorbeste.                                       | El citește și vorbește.
            Este peste poveste.                                           | Este peste poveste.
            SI ESTI ROMAN                                                 | ȘI EȘTI ROMÂN
            Intr-o zi, Romania.                                           | Într-o zi, România.
            Bucuresti, Iasi si Timisoara                                  | București, Iași și Timișoara
            A doua zi au venit doua fete.                                 | A doua zi au venit două fete.
            Ea va pleca sau va ramane.                                    | Ea va pleca sau va rămâne.
            Masina a ramas in parcare pana tarziu.                        | Mașina a rămas în parcare până târziu.
            Ministerul Sanatatii a anuntat masuri noi.                    | Ministerul Sănătății a anunțat măsuri noi.
            """)
    void addsDiacritics(String input, String expected) {
        assertThat(restorer.restore(input)).isEqualTo(expected);
    }

    @Test
    void normalizesCedillaToComma() {
        assertThat(restorer.restore("Deja în oraş şi ţară")).isEqualTo("Deja în oraș și țară");
    }

    @Test
    void leavesWordsWithDiacriticsUntouched() {
        assertThat(restorer.restore("orașul are deja diacritice")).isEqualTo("orașul are deja diacritice");
    }

    @Test
    void countsChangedWords() {
        DiacriticsRestorer.Result result = restorer.restoreWithStats("Uta si Ion sunt in oras.");
        assertThat(result.text()).isEqualTo("Uță și Ion sunt în oraș.");
        assertThat(result.changedWords()).isEqualTo(4);
    }

    @Test
    void handlesEmptyText() {
        assertThat(restorer.restore("")).isEmpty();
    }
}
