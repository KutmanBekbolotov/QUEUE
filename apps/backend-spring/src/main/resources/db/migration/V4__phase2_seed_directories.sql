INSERT INTO regions (code, name, active) VALUES
  ('CHUY', 'Чуйская область', true),
  ('ISSYK_KUL', 'Иссык-Кульская область', true),
  ('NARYN', 'Нарынская область', true),
  ('TALAS', 'Таласская область', true),
  ('OSH_REGION', 'Ошская область', true),
  ('JALAL_ABAD', 'Джалал-Абадская область', true),
  ('BATKEN', 'Баткенская область', true),
  ('BISHKEK', 'Бишкек', true),
  ('OSH_CITY', 'Ош', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO service_categories (code, name, ticket_prefix, active) VALUES
  ('TS', 'ТС', 'ТС', true),
  ('VU', 'ВУ', 'ВУ', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO services (category_id, code, name, default_duration_minutes, active)
SELECT c.id, v.code, v.name, 15, true
FROM service_categories c
JOIN (VALUES
  ('TS', 'TS_STS_LOST_REPLACE', 'Утеря/замена СТС'),
  ('TS', 'TS_PLATE_LOST_REPLACE', 'Утеря/замена госномера'),
  ('TS', 'TS_TEMP_REGISTRATION_STOP', 'Временное прекращение регистрации'),
  ('TS', 'TS_SALE_CONTRACT_REGISTRATION', 'Постановка на учет по ДКП'),
  ('TS', 'TS_RE_EQUIPMENT', 'Переоборудование ТС'),
  ('TS', 'TS_REGISTRATION_RESTORE', 'Возобновление регистрации'),
  ('TS', 'TS_PRIMARY_REGISTRATION', 'Первичная регистрация'),
  ('TS', 'TS_DEREGISTRATION', 'Снятие с учета'),
  ('TS', 'TS_TINT_PERMIT', 'Разрешение на тонировку ТС'),
  ('TS', 'TS_DISPOSAL', 'Выбраковка'),
  ('VU', 'VU_DIGITIZATION', 'Оцифровка'),
  ('VU', 'VU_ISSUE', 'Выдача'),
  ('VU', 'VU_PRIMARY_ISSUE', 'Первичная выдача ВУ'),
  ('VU', 'VU_INTERNATIONAL_ISSUE', 'Выдача ВУ международного образца'),
  ('VU', 'VU_DEPRIVATION', 'Лишение ВУ'),
  ('VU', 'VU_RETRAINING', 'Переподготовка'),
  ('VU', 'VU_LOST_REPLACE', 'Утеря/замена ВУ')
) AS v(category_code, code, name) ON v.category_code = c.code
ON CONFLICT (code) DO NOTHING;

INSERT INTO cancellation_reasons (code, name, active) VALUES
  ('CLIENT_REQUEST', 'Отмена по просьбе клиента', true),
  ('DOCUMENTS_MISSING', 'Недостаточно документов', true),
  ('DEPARTMENT_CLOSED', 'Отдел закрыт', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO pause_reasons (code, name, active) VALUES
  ('TECHNICAL_BREAK', 'Технический перерыв', true),
  ('DOCUMENT_CHECK', 'Проверка документов', true),
  ('CLIENT_WAITING', 'Ожидание клиента', true)
ON CONFLICT (code) DO NOTHING;

