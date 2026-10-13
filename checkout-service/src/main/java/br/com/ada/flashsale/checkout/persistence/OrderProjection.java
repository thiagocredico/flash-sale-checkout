package br.com.ada.flashsale.checkout.persistence;

import br.com.ada.flashsale.checkout.api.CreateOrderRequest;
import br.com.ada.flashsale.contracts.OrderStatus;
import br.com.ada.flashsale.contracts.OrderStatusUpdatedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_projection")
public class OrderProjection {

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

    protected OrderProjection() {
    }

    private OrderProjection(UUID pedidoId, CreateOrderRequest request, LocalDateTime now) {
        this.pedidoId = pedidoId;
        produtoId = request.produtoId();
        quantidade = request.quantidade();
        clienteCpf = request.clienteCpf();
        clienteNome = request.clienteNome();
        status = OrderStatus.PROCESSING;
        dataHoraCriacao = now;
        dataHoraAtualizacao = now;
    }

    public static OrderProjection processing(UUID pedidoId, CreateOrderRequest request, LocalDateTime now) {
        return new OrderProjection(pedidoId, request, now);
    }

    public void applyStatus(OrderStatusUpdatedEvent event) {
        if (status == event.status()) {
            return;
        }
        if (status != OrderStatus.PROCESSING) {
            throw new IllegalStateException("Cannot change a terminal order status");
        }
        status = event.status();
        dataHoraAtualizacao = event.dataHoraAtualizacao();
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