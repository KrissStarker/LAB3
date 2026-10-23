INSERT INTO users (name, email, role, created_at)
SELECT 'Иван Отправитель', 'ivan@example.com', 'SENDER', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'ivan@example.com');

INSERT INTO users (name, email, role, created_at)
SELECT 'Мария Курьер', 'maria@example.com', 'COURIER', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'maria@example.com');

INSERT INTO routes (route_date, max_weight, status, created_at)
SELECT DATE '2026-10-20', 15.000, 'PLANNED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM routes LIMIT 1);

INSERT INTO shipments (address, weight, status, sender_id, created_at)
SELECT 'ул. Ленина, 10', 3.500, 'CREATED', u.id, CURRENT_TIMESTAMP
FROM users u
WHERE u.email = 'ivan@example.com'
  AND NOT EXISTS (SELECT 1 FROM shipments WHERE address = 'ул. Ленина, 10');

INSERT INTO shipments (address, weight, status, sender_id, created_at)
SELECT 'пр. Мира, 5', 2.000, 'CREATED', u.id, CURRENT_TIMESTAMP
FROM users u
WHERE u.email = 'ivan@example.com'
  AND NOT EXISTS (SELECT 1 FROM shipments WHERE address = 'пр. Мира, 5');

INSERT INTO shipments (address, weight, status, sender_id, created_at)
SELECT 'ул. Садовая, 3', 12.000, 'CREATED', u.id, CURRENT_TIMESTAMP
FROM users u
WHERE u.email = 'ivan@example.com'
  AND NOT EXISTS (SELECT 1 FROM shipments WHERE address = 'ул. Садовая, 3');

INSERT INTO stops (address, sequence_number, route_id, created_at)
SELECT 'ул. Ленина, 10', 1, r.id, CURRENT_TIMESTAMP
FROM routes r
WHERE NOT EXISTS (SELECT 1 FROM stops WHERE sequence_number = 1 AND route_id = r.id)
ORDER BY r.id
LIMIT 1;

INSERT INTO stops (address, sequence_number, route_id, created_at)
SELECT 'пр. Мира, 5', 2, r.id, CURRENT_TIMESTAMP
FROM routes r
WHERE NOT EXISTS (SELECT 1 FROM stops WHERE sequence_number = 2 AND route_id = r.id)
ORDER BY r.id
LIMIT 1;
