package br.com.ada.flashsale.checkout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ada.flashsale.checkout.stock.InitialStockProperties;
import br.com.ada.flashsale.checkout.stock.RedisStockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InitialStockProperties initialStockProperties;

    @MockitoBean
    private RedisStockService stockService;

    @Test
    void returnsConfiguredProductsWithCurrentStockInSnakeCase() throws Exception {
        when(initialStockProperties.initial()).thenReturn(List.of(
                new InitialStockProperties.ProductStock("PROD-001", 100)));
        when(stockService.currentStock("PROD-001")).thenReturn(37);

        mockMvc.perform(get("/v1/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].produto_id").value("PROD-001"))
                .andExpect(jsonPath("$[0].quantidade_disponivel").value(37));
    }

    @Test
    void returnsEmptyListWhenNoProductIsConfigured() throws Exception {
        when(initialStockProperties.initial()).thenReturn(List.of());

        mockMvc.perform(get("/v1/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
