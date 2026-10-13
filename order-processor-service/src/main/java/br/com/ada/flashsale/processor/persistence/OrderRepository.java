package br.com.ada.flashsale.processor.persistence;

import br.com.ada.flashsale.processor.domain.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}