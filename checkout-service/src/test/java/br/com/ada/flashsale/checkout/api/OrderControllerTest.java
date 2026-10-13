package br.com.ada.flashsale.checkout.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(CheckoutExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderService createOrderService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void returnsAcceptedWithSnakeCaseResponse() throws Exception {
        var orderId = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
        when(createOrderService.create(any())).thenReturn(
                new CreateOrderResponse(orderId, OrderStatus.PROCESSING, LocalDateTime.parse("2026-09-29T12:00:00")));

        mockMvc.perform(post("/v1/orders")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pedido_id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data_hora_requisicao").value("2026-09-29T12:00:00"));
    }

    @Test
    void returnsBadRequestForInvalidRequest() throws Exception {
        mockMvc.perform(post("/v1/orders")
                        .contentType("application/json")
                        .content("""
                                {
                                  "produto_id": " ",
                                  "quantidade": 0,
                                  "cliente_cpf": "",
                                  "cliente_nome": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.length()").value(4));

        verify(createOrderService, never()).create(any());
    }

    @Test
    void returnsConflictWhenStockIsInsufficient() throws Exception {
        when(createOrderService.create(any())).thenThrow(new InsufficientStockException("PROD-001"));

        mockMvc.perform(post("/v1/orders")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Estoque insuficiente para o produto PROD-001"));
    }

    @Test
    void returnsServiceUnavailableWhenSubmissionFails() throws Exception {
        when(createOrderService.create(any()))
                .thenThrow(new OrderSubmissionException(new IllegalStateException("database unavailable")));

        mockMvc.perform(post("/v1/orders")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível registrar o pedido"));
    }

    @Test
    void returnsBadRequestWhenOrderIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(get("/v1/orders/{orderId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Parâmetro inválido: orderId"));

        verify(orderQueryService, never()).find(any());
    }

    @Test
    void returnsBadRequestWhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/v1/orders")
                        .contentType("application/json")
                        .content("{\"quantidade\": \"not-a-number\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisição inválido ou malformado"));

        verify(createOrderService, never()).create(any());
    }

                @Test
                void returnsOrderWhenFound() throws Exception {
              var orderId = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
              when(orderQueryService.find(orderId)).thenReturn(new OrderResponse(
                orderId,
                "PROD-001",
                2,
                "50329291076",
                "Fernando Augusto",
                OrderStatus.CONFIRMED,
                LocalDateTime.parse("2026-09-29T12:04:10")));

              mockMvc.perform(get("/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido_id").value(orderId.toString()))
                .andExpect(jsonPath("$.produto_id").value("PROD-001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data_hora_atualizacao").value("2026-09-29T12:04:10"));
                }

                @Test
                void returnsNotFoundWhenOrderIsMissing() throws Exception {
              var orderId = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
              when(orderQueryService.find(orderId)).thenThrow(new OrderNotFoundException(orderId));

              mockMvc.perform(get("/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido não encontrado"));
                }

    private String validRequestJson() {
        return """
                {
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "cliente_cpf": "50329291076",
                  "cliente_nome": "Fernando Augusto"
                }
                """;
    }
}