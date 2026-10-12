BEGIN;

INSERT INTO sites (name, url) VALUES
  ('Always up',     'https://seed-up.example.com'),
  ('Went down',     'https://seed-down.example.com'),
  ('Server error',  'https://seed-500.example.com'),
  ('Slow',          'https://seed-slow.example.com'),
  ('Recovered',     'https://seed-recovered.example.com'),
  ('Never checked', 'https://seed-pending.example.com');

INSERT INTO check_results (site_id, up, status_code, response_time_ms, error, checked_at)
SELECT s.id, c.up, c.status_code, c.response_time_ms, c.error,
       date_bin('10 minutes', now() - interval '10 minutes', timestamptz '2000-01-01') + interval '5 minutes' - c.age
FROM sites s
JOIN (VALUES
  ('https://seed-up.example.com',        true,  200,  120,  NULL,                interval '3 minutes'),
  ('https://seed-up.example.com',        true,  200,  135,  NULL,                interval '2 minutes'),
  ('https://seed-up.example.com',        true,  200,  110,  NULL,                interval '1 minute'),
  ('https://seed-down.example.com',      true,  200,  150,  NULL,                interval '3 minutes'),
  ('https://seed-down.example.com',      true,  200,  160,  NULL,                interval '2 minutes'),
  ('https://seed-down.example.com',      false, NULL, 5000, 'connect timed out', interval '1 minute'),
  ('https://seed-500.example.com',       false, 503,  80,   NULL,                interval '2 minutes'),
  ('https://seed-500.example.com',       false, 500,  95,   NULL,                interval '1 minute'),
  ('https://seed-slow.example.com',      true,  200,  400,  NULL,                interval '2 minutes'),
  ('https://seed-slow.example.com',      true,  200,  3200, NULL,                interval '1 minute'),
  ('https://seed-recovered.example.com', false, NULL, 5000, 'UnknownHostException', interval '3 minutes'),
  ('https://seed-recovered.example.com', false, 502,  300,  NULL,                interval '2 minutes'),
  ('https://seed-recovered.example.com', true,  200,  210,  NULL,                interval '1 minute')
) AS c(url, up, status_code, response_time_ms, error, age) ON c.url = s.url;

COMMIT;
