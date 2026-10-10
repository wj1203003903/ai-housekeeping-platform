DROP TABLE pets IF EXISTS;
DROP TABLE types IF EXISTS;
DROP TABLE owners IF EXISTS;
DROP TABLE service_addresses IF EXISTS;
DROP TABLE customers IF EXISTS;

CREATE TABLE types (
  id   INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80)
);
CREATE INDEX types_name ON types (name);

CREATE TABLE owners (
  id         INTEGER IDENTITY PRIMARY KEY,
  first_name VARCHAR(30),
  last_name  VARCHAR(30),
  address    VARCHAR(255),
  city       VARCHAR(80),
  telephone  VARCHAR(12)
);
CREATE INDEX owners_last_name ON owners (last_name);

CREATE TABLE pets (
  id         INTEGER IDENTITY PRIMARY KEY,
  name       VARCHAR(30),
  birth_date DATE,
  type_id    INTEGER NOT NULL,
  owner_id   INTEGER NOT NULL
);
ALTER TABLE pets ADD CONSTRAINT fk_pets_owners FOREIGN KEY (owner_id) REFERENCES owners (id);
ALTER TABLE pets ADD CONSTRAINT fk_pets_types FOREIGN KEY (type_id) REFERENCES types (id);
CREATE INDEX pets_name ON pets (name);

CREATE TABLE customers (
  id        INTEGER IDENTITY PRIMARY KEY,
  name      VARCHAR(80) NOT NULL,
  telephone VARCHAR(20) NOT NULL,
  email     VARCHAR(120)
);

CREATE TABLE service_addresses (
  id                 INTEGER IDENTITY PRIMARY KEY,
  customer_id        INTEGER NOT NULL,
  contact_name       VARCHAR(80) NOT NULL,
  telephone          VARCHAR(20) NOT NULL,
  city               VARCHAR(80) NOT NULL,
  district           VARCHAR(80) NOT NULL,
  detail_address     VARCHAR(255) NOT NULL,
  access_instruction VARCHAR(500),
  default_address    BOOLEAN DEFAULT FALSE
);
ALTER TABLE service_addresses ADD CONSTRAINT fk_service_address_customer
  FOREIGN KEY (customer_id) REFERENCES customers (id);
