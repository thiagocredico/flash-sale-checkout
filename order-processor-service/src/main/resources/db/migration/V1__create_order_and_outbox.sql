CREATE TABLE orders (
    pedido_id UUID PRIMARY KEY,
    produto_id VARCHAR(100) NOT NULL,
    quantidade INTEGER NOT NULL CHECK (quantidade > 0),
    cliente_cpf VARCHAR(20) NOT NULL,
    cliente_nome VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    data_hora_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    data_hora_atualizacao TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;