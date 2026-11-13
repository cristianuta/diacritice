package ro.fastdiacritics.web;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DiacriticsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void addsDiacritics() throws Exception {
        mvc().perform(post("/api/diacritice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Buna ziua, Uta! Cred ca maine mergem la Brasov.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Bună ziua, Uță! Cred că mâine mergem la Brașov."))
                .andExpect(jsonPath("$.modificari").value(5));
    }

    @Test
    void rejectsMissingText() throws Exception {
        mvc().perform(post("/api/diacritice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesFrontEnd() throws Exception {
        mvc().perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("Adaugă diacritice"));
    }
}
