package ro.fastdiacritics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FastDiacriticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastDiacriticsApplication.class, args);
    }

    /** Dicționarul (~650.000 de forme) se încarcă o singură dată, la pornire. */
    @Bean
    public DiacriticsRestorer diacriticsRestorer() {
        return DiacriticsRestorer.withDefaultDictionary();
    }
}
