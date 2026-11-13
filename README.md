# Diacritice

Aplicație web Spring Boot care adaugă diacriticele românești (ă, â, î, ș, ț) unui text scris fără ele.
Lipești textul într-un câmp, apeși **„Adaugă diacritice”** și primești textul corectat.

```
Buna ziua, Uta! Ministerul Sanatatii a anuntat ca masurile raman in vigoare pana in decembrie.
→ Bună ziua, Uță! Ministerul Sănătății a anunțat că măsurile rămân în vigoare până în decembrie.
```

Stack: Java 25, Spring Boot 4.1, Maven (prin Maven Wrapper — nu trebuie instalat).

## Pornire

```powershell
.\mvnw.cmd spring-boot:run        # http://localhost:8080
.\mvnw.cmd test                   # teste
.\mvnw.cmd package                # target/diacritice-*.jar
java -jar target\diacritice-0.1.0-SNAPSHOT.jar --server.port=9000
```

Din IntelliJ: deschide `pom.xml` ca proiect Maven și rulează `DiacriticeApplication`.

## Structură

```
src/main/java/ro/diacritice/
  DiacriticeApplication.java       aplicația Spring Boot + bean-ul DiacriticsRestorer
  DiacriticsRestorer.java          motorul (Java pur, fără dependențe de Spring)
  web/DiacriticsController.java    API REST
src/main/resources/
  static/index.html                interfața: textarea + butoane
  dictionar/ro.txt.gz              dicționarul complet (generat, ~650.000 de chei)
  dictionar/suplimentar.txt        suprascrieri manuale (au prioritate)
  application.properties
tools/build_dictionary.py          regenerează ro.txt.gz
```

## API

`POST /api/diacritice`

```bash
curl -X POST http://localhost:8080/api/diacritice \
     -H "Content-Type: application/json" \
     -d '{"text": "Buna ziua, ce mai faci?"}'
# {"text":"Bună ziua, ce mai faci?","modificari":1}
```

Textul e limitat la 200.000 de caractere; erorile sunt returnate ca Problem Details (RFC 9457).

## Cum funcționează

Pentru fiecare cuvânt, în ordine:

1. dacă are deja diacritice, rămâne neschimbat (ş/ţ cu sedilă sunt convertite în ș/ț corecte);
2. **reguli de context** pentru omografe care depind de vecini — `ca`/`că` („cred că”, „pentru că”),
   `fata`/`fața`/`față` („în fața”, „față de”), `doua`/`două` („a doua zi”);
3. **dicționarul** — forma fără diacritice → forma corectă (`uta` → `uță`, `masina` → `mașina`);
4. **reguli de sufix** pentru cuvintele necunoscute — `-tie → -ție`, `-easca → -ească` etc.

Majusculele originale sunt păstrate (`UTA` → `UȚĂ`, `Romania` → `România`).

## Dicționarul

`ro.txt.gz` conține o linie per cheie fără diacritice: forma câștigătoare. E generat din:

- **Hunspell ro_RO** ([LibreOffice/dictionaries](https://github.com/LibreOffice/dictionaries/tree/master/ro), GPL/LGPL/MPL) —
  toate formele flexionare valide (~766.000), inclusiv nume proprii;
- **corpusul Leipzig `ron_news_2022_1M`** ([wortschatz-leipzig.de](https://wortschatz.uni-leipzig.de/en/download/Romanian), CC BY) —
  frecvențe folosite doar pentru a alege între formele valide (ex. `casa` vs. `casă`). Pentru că multe texte
  sunt scrise fără diacritice, frecvența formelor fără diacritice e penalizată.

Regenerare:

```powershell
curl -O https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ro/ro_RO.dic
curl -O https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ro/ro_RO.aff
curl -O https://downloads.wortschatz-leipzig.de/corpora/ron_news_2022_1M.tar.gz   # ~250 MB
tar -xzf ron_news_2022_1M.tar.gz
python tools/build_dictionary.py ro_RO.dic ro_RO.aff ron_news_2022_1M/ron_news_2022_1M-words.txt src/main/resources/dictionar/ro.txt.gz
```

Corecturi punctuale: adaugă cuvântul în `suplimentar.txt` (ex. `pește` ca „peste” să devină mereu „pește”).

## Limitări

Omografele dintre care ambele forme sunt corecte (`casa`/`casă`, `mama`/`mamă`, `sa`/`să`) primesc forma
cea mai frecventă, indiferent de context. Următorul pas ar fi un model pe n-grame (bigrame din corpus) sau
un model ML în `DiacriticsRestorer` — interfața și API-ul rămân neschimbate.
