CREATE TABLE order_projection (
    pedido_id UUID PRIMARY KEY,
    produto_id VARCHAR(100) NOT NULL,
    quantidade INTEGER NOT NULL CHECK (quantidade > 0),
    cliente_cpf VARCHAR(20) NOT NULL,
    cliente_nome VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    data_hora_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    data_hora_atualizacao TIMESTAMP WITHOUT TIME ZONE NOT NULL
);