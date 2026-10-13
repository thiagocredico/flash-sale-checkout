package br.com.ada.flashsale.processor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-09-29T12:00:00");

    @Test
    void createsOrderInProcessingStatus() {
        var order = Order.from(validEvent());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getDataHoraCriacao()).isEqualTo(CREATED_AT);
        assertThat(order.getDataHoraAtualizacao()).isEqualTo(CREATED_AT);
    }

    @Test
    void confirmationIsIdempotent() {
        var order = Order.from(validEvent());

        order.confirm();
        var updatedAt = order.getDataHoraAtualizacao();
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getDataHoraAtualizacao()).isEqualTo(updatedAt);
    }

    @Test
    void doesNotAllowChangingOneTerminalStatusToAnother() {
        var order = Order.from(validEvent());
        order.confirm();

        assertThatIllegalStateException().isThrownBy(order::cancel);
    }

    private OrderCreatedEvent validEvent() {
        return new OrderCreatedEvent(
                "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
                "PROD-001",
                2,
                "50329291076",
                "Fernando Augusto",
                CREATED_AT);
    }
}