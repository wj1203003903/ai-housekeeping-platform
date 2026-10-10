INSERT INTO vets VALUES (1, 'James', 'Carter');
INSERT INTO vets VALUES (2, 'Helen', 'Leary');
INSERT INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT INTO vets VALUES (5, 'Henry', 'Stevens');
INSERT INTO vets VALUES (6, 'Sharon', 'Jenkins');

INSERT INTO specialties VALUES (1, 'radiology');
INSERT INTO specialties VALUES (2, 'surgery');
INSERT INTO specialties VALUES (3, 'dentistry');

INSERT INTO vet_specialties VALUES (2, 1);
INSERT INTO vet_specialties VALUES (3, 2);
INSERT INTO vet_specialties VALUES (3, 3);
INSERT INTO vet_specialties VALUES (4, 2);
INSERT INTO vet_specialties VALUES (5, 1);

INSERT INTO service_skills VALUES (1, '日常保洁');
INSERT INTO service_skills VALUES (2, '深度保洁');
INSERT INTO service_skills VALUES (3, '家电清洗');
INSERT INTO service_skills VALUES (4, '收纳整理');

INSERT INTO housekeepers VALUES (1, '王秀兰', '13900001111', '擅长家庭日常保洁，服务细致。', 8, 4.9, 'AVAILABLE', '浦东新区,徐汇区');
INSERT INTO housekeepers VALUES (2, '李建国', '13900002222', '深度保洁和家电清洗经验丰富。', 6, 4.8, 'AVAILABLE', '静安区,长宁区');
INSERT INTO housekeepers VALUES (3, '陈阿姨', '13900003333', '擅长收纳整理和日常保洁。', 10, 5.0, 'AVAILABLE', '全市');

INSERT INTO housekeeper_skills VALUES (1, 1);
INSERT INTO housekeeper_skills VALUES (1, 2);
INSERT INTO housekeeper_skills VALUES (2, 2);
INSERT INTO housekeeper_skills VALUES (2, 3);
INSERT INTO housekeeper_skills VALUES (3, 1);
INSERT INTO housekeeper_skills VALUES (3, 4);

INSERT INTO service_items VALUES (1, '日常保洁', '地面、桌面、厨房和卫生间基础清洁', 1, 'PER_HOUR', 50.00, 2.00, 120, TRUE);
INSERT INTO service_items VALUES (2, '全屋深度保洁', '全屋除尘、油污和卫生死角深度清洁', 2, 'PER_AREA', 8.00, 50.00, 300, TRUE);
INSERT INTO service_items VALUES (3, '空调清洗', '挂机空调拆洗、消毒和滤网清洁', 3, 'PER_ITEM', 120.00, 1.00, 60, TRUE);
INSERT INTO service_items VALUES (4, '家庭收纳整理', '衣柜、厨房或全屋空间规划与整理', 4, 'PER_HOUR', 80.00, 3.00, 180, TRUE);

INSERT INTO worker_schedules VALUES (1, 1, '2026-07-18', '13:00:00', '16:00:00', 'BOOKED', NULL);
