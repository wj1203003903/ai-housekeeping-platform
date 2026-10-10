DROP TABLE visits IF EXISTS;
DROP TABLE service_reviews IF EXISTS;
DROP TABLE service_bookings IF EXISTS;

CREATE TABLE visits (
  id          INTEGER IDENTITY PRIMARY KEY,
  pet_id      INTEGER NOT NULL,
  visit_date  DATE,
  description VARCHAR(8192)
);

CREATE INDEX visits_pet_id ON visits (pet_id);

CREATE TABLE service_bookings (
  id INTEGER IDENTITY PRIMARY KEY,
  version BIGINT DEFAULT 0 NOT NULL,
  idempotency_key VARCHAR(80) UNIQUE,
  reservation_key VARCHAR(80) UNIQUE,
  booking_number VARCHAR(30),
  customer_id INTEGER NOT NULL,
  address_id INTEGER NOT NULL,
  service_item_id INTEGER NOT NULL,
  housekeeper_id INTEGER,
  service_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  total_price DECIMAL(10,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  customer_remark VARCHAR(500),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE service_reviews (
  id INTEGER IDENTITY PRIMARY KEY,
  booking_id INTEGER NOT NULL,
  customer_id INTEGER NOT NULL,
  housekeeper_id INTEGER NOT NULL,
  score INTEGER NOT NULL,
  content VARCHAR(1000),
  created_at TIMESTAMP,
  FOREIGN KEY (booking_id) REFERENCES service_bookings(id),
  UNIQUE (booking_id)
);

CREATE TABLE domain_event_outbox (
  id BIGINT IDENTITY PRIMARY KEY, aggregate_id VARCHAR(80) NOT NULL, event_type VARCHAR(80) NOT NULL,
  payload VARCHAR(4000) NOT NULL, created_at TIMESTAMP NOT NULL, published_at TIMESTAMP
);
