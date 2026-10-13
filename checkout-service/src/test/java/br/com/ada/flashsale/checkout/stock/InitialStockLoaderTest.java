package br.com.ada.flashsale.checkout.stock;

import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class InitialStockLoaderTest {

    @Mock
    private RedisStockService stockService;

    @Test
    void initializesConfiguredProductsWithoutReplacingExistingStock() {
        var properties = new InitialStockProperties(List.of(
                new InitialStockProperties.ProductStock("PROD-001", 100),
                new InitialStockProperties.ProductStock("PROD-002", 25)));
        var loader = new InitialStockLoader(properties, stockService);

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(stockService).initializeIfAbsent("PROD-001", 100);
        verify(stockService).initializeIfAbsent("PROD-002", 25);
    }
}