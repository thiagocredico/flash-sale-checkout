package br.com.ada.flashsale.processor.domain;

import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "pedido_id", nullable = false, updatable = false)
    private UUID pedidoId;

    @Column(name = "produto_id", nullable = false, updatable = false)
    private String produtoId;

    @Column(nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "cliente_cpf", nullable = false, updatable = false)
    private String clienteCpf;

    @Column(name = "cliente_nome", nullable = false, updatable = false)
    private String clienteNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "data_hora_criacao", nullable = false, updatable = false)
    private LocalDateTime dataHoraCriacao;

    @Column(name = "data_hora_atualizacao", nullable = false)
    private LocalDateTime dataHoraAtualizacao;

    protected Order() {
    }

    private Order(OrderCreatedEvent event) {
        pedidoId = UUID.fromString(event.pedidoId());
        produtoId = event.produtoId();
        quantidade = event.quantidade();
        clienteCpf = event.clienteCpf();
        clienteNome = event.clienteNome();
        status = OrderStatus.PROCESSING;
        dataHoraCriacao = event.dataHoraCriacao();
        dataHoraAtualizacao = event.dataHoraCriacao();
    }

    public static Order from(OrderCreatedEvent event) {
        return new Order(event);
    }

    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    private void transitionTo(OrderStatus targetStatus) {
        if (status == targetStatus) {
            return;
        }
        if (status != OrderStatus.PROCESSING) {
            throw new IllegalStateException("Cannot change a terminal order status");
        }
        status = targetStatus;
        dataHoraAtualizacao = LocalDateTime.now();
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public String getClienteCpf() {
        return clienteCpf;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getDataHoraCriacao() {
        return dataHoraCriacao;
    }

    public LocalDateTime getDataHoraAtualizacao() {
        return dataHoraAtualizacao;
    }
}