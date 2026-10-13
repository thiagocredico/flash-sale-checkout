package br.com.ada.flashsale.checkout.stock;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flash-sale.stock")
public record InitialStockProperties(List<ProductStock> initial) {

    public InitialStockProperties {
        initial = initial == null ? List.of() : List.copyOf(initial);
    }

    public record ProductStock(String productId, int quantity) {
    }
}