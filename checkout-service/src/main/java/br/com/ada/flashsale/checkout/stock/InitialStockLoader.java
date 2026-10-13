package br.com.ada.flashsale.checkout.stock;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InitialStockLoader implements ApplicationRunner {

    private final InitialStockProperties properties;
    private final RedisStockService stockService;

    public InitialStockLoader(InitialStockProperties properties, RedisStockService stockService) {
        this.properties = properties;
        this.stockService = stockService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        properties.initial().forEach(product ->
                stockService.initializeIfAbsent(product.productId(), product.quantity()));
    }
}