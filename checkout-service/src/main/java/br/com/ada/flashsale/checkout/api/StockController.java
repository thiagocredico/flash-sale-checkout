package br.com.ada.flashsale.checkout.api;

import br.com.ada.flashsale.checkout.stock.InitialStockProperties;
import br.com.ada.flashsale.checkout.stock.RedisStockService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stock")
public class StockController {

    private final InitialStockProperties initialStockProperties;
    private final RedisStockService stockService;

    public StockController(InitialStockProperties initialStockProperties, RedisStockService stockService) {
        this.initialStockProperties = initialStockProperties;
        this.stockService = stockService;
    }

    @GetMapping
    public List<StockItemResponse> listStock() {
        return initialStockProperties.initial().stream()
                .map(product -> new StockItemResponse(
                        product.productId(), stockService.currentStock(product.productId())))
                .toList();
    }
}
