CREATE DATABASE IF NOT EXISTS housekeeping;

USE housekeeping;

CREATE TABLE IF NOT EXISTS visits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED NOT NULL,
  visit_date DATE,
  description VARCHAR(8192),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;
CREATE TABLE IF NOT EXISTS service_bookings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(80) UNIQUE,
  reservation_key VARCHAR(80) UNIQUE,
  booking_number VARCHAR(30),
  customer_id INT NOT NULL,
  address_id INT NOT NULL,
  service_item_id INT NOT NULL,
  housekeeper_id INT,
  service_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  total_price DECIMAL(10,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  customer_remark VARCHAR(500),
  created_at DATETIME,
  updated_at DATETIME
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS service_reviews (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT UNSIGNED NOT NULL,
  customer_id INT NOT NULL,
  housekeeper_id INT NOT NULL,
  score INT NOT NULL,
  content VARCHAR(1000),
  created_at DATETIME,
  FOREIGN KEY (booking_id) REFERENCES service_bookings(id),
  UNIQUE (booking_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS domain_event_outbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, aggregate_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(80) NOT NULL, payload VARCHAR(4000) NOT NULL, created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP NULL
) engine=InnoDB;
