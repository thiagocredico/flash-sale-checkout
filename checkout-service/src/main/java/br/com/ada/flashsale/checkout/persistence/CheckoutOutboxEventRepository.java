package br.com.ada.flashsale.checkout.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutOutboxEventRepository extends JpaRepository<CheckoutOutboxEvent, UUID> {

    List<CheckoutOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}