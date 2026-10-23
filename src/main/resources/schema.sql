CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS routes (
    id BIGSERIAL PRIMARY KEY,
    route_date DATE NOT NULL,
    max_weight NUMERIC(12, 3) NOT NULL CHECK (max_weight > 0),
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(500) NOT NULL,
    weight NUMERIC(12, 3) NOT NULL CHECK (weight >= 0),
    status VARCHAR(64) NOT NULL,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS stops (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(id),
    address VARCHAR(500) NOT NULL,
    sequence_number INT NOT NULL CHECK (sequence_number > 0),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (route_id, sequence_number)
);
CREATE TABLE IF NOT EXISTS shipment_assignments (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL UNIQUE REFERENCES shipments(id),
    stop_id BIGINT NOT NULL REFERENCES stops(id),
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
