INSERT IGNORE INTO vets VALUES (1, 'James', 'Carter');
INSERT IGNORE INTO vets VALUES (2, 'Helen', 'Leary');
INSERT IGNORE INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT IGNORE INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT IGNORE INTO vets VALUES (5, 'Henry', 'Stevens');
INSERT IGNORE INTO vets VALUES (6, 'Sharon', 'Jenkins');

INSERT IGNORE INTO specialties VALUES (1, 'radiology');
INSERT IGNORE INTO specialties VALUES (2, 'surgery');
INSERT IGNORE INTO specialties VALUES (3, 'dentistry');

INSERT IGNORE INTO vet_specialties VALUES (2, 1);
INSERT IGNORE INTO vet_specialties VALUES (3, 2);
INSERT IGNORE INTO vet_specialties VALUES (3, 3);
INSERT IGNORE INTO vet_specialties VALUES (4, 2);
INSERT IGNORE INTO vet_specialties VALUES (5, 1);
INSERT IGNORE INTO service_skills (id, name) VALUES
  (1, '日常保洁'), (2, '深度保洁'), (3, '家电清洗'), (4, '收纳整理');

INSERT IGNORE INTO housekeepers
  (id, name, telephone, introduction, experience_years, rating, status, service_district)
VALUES
  (1, '王秀兰', '13900001111', '擅长家庭日常保洁，服务细致。', 8, 4.9, 'AVAILABLE', '浦东新区,徐汇区'),
  (2, '李建国', '13900002222', '深度保洁和家电清洗经验丰富。', 6, 4.8, 'AVAILABLE', '静安区,长宁区'),
  (3, '陈阿姨', '13900003333', '擅长收纳整理和日常保洁。', 10, 5.0, 'AVAILABLE', '全市');

INSERT IGNORE INTO housekeeper_skills (housekeeper_id, skill_id) VALUES
  (1, 1), (1, 2), (2, 2), (2, 3), (3, 1), (3, 4);

INSERT IGNORE INTO service_items
  (id, name, description, skill_id, pricing_type, unit_price, minimum_units, estimated_minutes, enabled)
VALUES
  (1, '日常保洁', '地面、桌面、厨房和卫生间基础清洁', 1, 'PER_HOUR', 50.00, 2.00, 120, TRUE),
  (2, '全屋深度保洁', '全屋除尘、油污和卫生死角深度清洁', 2, 'PER_AREA', 8.00, 50.00, 300, TRUE),
  (3, '空调清洗', '挂机空调拆洗、消毒和滤网清洁', 3, 'PER_ITEM', 120.00, 1.00, 60, TRUE),
  (4, '家庭收纳整理', '衣柜、厨房或全屋空间规划与整理', 4, 'PER_HOUR', 80.00, 3.00, 180, TRUE);

INSERT IGNORE INTO worker_schedules
  (id, housekeeper_id, service_date, start_time, end_time, status)
VALUES (1, 1, '2026-07-18', '13:00:00', '16:00:00', 'BOOKED');
