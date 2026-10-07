create table sites (
  id int generated always as identity primary key,
  name varchar(100) not null,
  url text not null unique,
  created_at timestamptz not null default now()
);

create table check_results (
  id bigint generated always as identity primary key,
  site_id int not null references sites(id) on delete cascade,
  up boolean not null,
  status_code int ,
  response_time_ms int,
  error text,
  checked_at timestamptz not null default now()
);

create index check_results_site_id_checked_at_idx on check_results (site_id, checked_at DESC);

