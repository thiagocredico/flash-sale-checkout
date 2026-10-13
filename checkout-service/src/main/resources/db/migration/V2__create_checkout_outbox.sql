CREATE TABLE checkout_outbox_event (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_checkout_outbox_unpublished
    ON checkout_outbox_event (created_at)
    WHERE published_at IS NULL;