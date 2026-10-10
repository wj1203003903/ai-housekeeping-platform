INSERT INTO visits VALUES (1, 7, '2013-01-01', 'rabies shot');
INSERT INTO visits VALUES (2, 8, '2013-01-02', 'rabies shot');
INSERT INTO visits VALUES (3, 8, '2013-01-03', 'neutered');
INSERT INTO visits VALUES (4, 7, '2013-01-04', 'spayed');

INSERT INTO service_bookings
  (id, booking_number, customer_id, address_id, service_item_id, housekeeper_id, service_date,
   start_time, end_time, quantity, unit_price, total_price, status, customer_remark, created_at, updated_at)
VALUES (1, 'HS202607160001', 1, 1, 1, 1, '2026-07-18', '09:00:00', '11:00:00',
        2.00, 50.00, 100.00, 'ASSIGNED', '家中有猫，请进门时注意', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO service_bookings
  (id, booking_number, customer_id, address_id, service_item_id, housekeeper_id, service_date,
   start_time, end_time, quantity, unit_price, total_price, status, customer_remark, created_at, updated_at)
VALUES (2, 'HS202607160002', 2, 3, 3, 2, '2026-07-19', '14:00:00', '15:00:00',
        1.00, 120.00, 120.00, 'ACCEPTED', '清洗客厅挂机', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
