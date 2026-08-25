package openflash_core.controller;

import openflash_core.service.impl.BrowserImportServiceImpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import openflash_core.entity.Card;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BrowserImportControllerTest {

    /** 图片转存接口返回按序结果。 */
    @Test
    void transferImagesReturnsOrderedResults() throws Exception {
        BrowserImportServiceImpl service = mock(BrowserImportServiceImpl.class);
        when(service.transferImages(any())).thenReturn(new BrowserImportServiceImpl.TransferImagesResponse(List.of(
            BrowserImportServiceImpl.TransferImageResult.success("https://a.test/a.png", "/uploads/a.jpg"),
            BrowserImportServiceImpl.TransferImageResult.failure("https://b.test/b.png", 40092)
        )));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BrowserImportController(service)).build();

        mvc.perform(post("/api/browser-import/images/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"urls\":[\"https://a.test/a.png\",\"https://b.test/b.png\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[0].url").value("/uploads/a.jpg"))
            .andExpect(jsonPath("$.data.results[1].code").value(40092));
    }

    /** 浏览器导入建卡接口返回创建后的卡片。 */
    @Test
    void createImportedCardReturnsCard() throws Exception {
        BrowserImportServiceImpl service = mock(BrowserImportServiceImpl.class);
        Card card = new Card();
        card.setId(12L);
        when(service.createImportedCard(any(), any())).thenReturn(card);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BrowserImportController(service)).build();

        mvc.perform(post("/api/browser-import/decks/7/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sideA\":\"hello\",\"sideAImage\":[\"/uploads/a.jpg\"],\"sideB\":\"world\",\"sideBImage\":[\"/uploads/b.jpg\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(12));
    }
}
