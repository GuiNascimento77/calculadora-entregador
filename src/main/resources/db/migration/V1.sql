CREATE TABLE vehicles (
 id INTEGER PRIMARY KEY,
 name TEXT NOT NULL,
 type TEXT NOT NULL CHECK(type IN ('MOTO','BIKE','CARRO')),
 odometer TEXT NOT NULL,
 consumption TEXT NOT NULL,
 fuel_price TEXT NOT NULL,
 daily_fixed TEXT NOT NULL
);
CREATE TABLE shifts (
 id INTEGER PRIMARY KEY,
 vehicle_id INTEGER NOT NULL REFERENCES vehicles(id),
 day TEXT NOT NULL,
 started_at TEXT NOT NULL,
 ended_at TEXT,
 km_start TEXT NOT NULL,
 km_end TEXT,
 fuel_per_km TEXT NOT NULL,
 maintenance_per_km TEXT NOT NULL,
 daily_fixed TEXT NOT NULL,
 UNIQUE(vehicle_id, day),
 CHECK((ended_at IS NULL AND km_end IS NULL) OR (ended_at IS NOT NULL AND km_end IS NOT NULL))
);
CREATE TABLE rides (
 id INTEGER PRIMARY KEY,
 shift_id INTEGER NOT NULL REFERENCES shifts(id),
 platform TEXT NOT NULL,
 amount TEXT NOT NULL,
 km TEXT NOT NULL,
 minutes INTEGER NOT NULL CHECK(minutes > 0)
);
CREATE TABLE expenses (
 id INTEGER PRIMARY KEY,
 vehicle_id INTEGER NOT NULL REFERENCES vehicles(id),
 shift_id INTEGER REFERENCES shifts(id),
 day TEXT NOT NULL,
 category TEXT NOT NULL CHECK(category IN ('COMBUSTIVEL','MANUTENCAO','FIXO','EXTRA')),
 amount TEXT NOT NULL,
 description TEXT NOT NULL,
 CHECK(category != 'EXTRA' OR shift_id IS NOT NULL)
);
CREATE TABLE maintenance_plans (
 id INTEGER PRIMARY KEY,
 vehicle_id INTEGER NOT NULL REFERENCES vehicles(id),
 item TEXT NOT NULL,
 interval_km TEXT NOT NULL,
 cost TEXT NOT NULL,
 last_km TEXT NOT NULL,
 UNIQUE(vehicle_id, item)
);
CREATE TABLE maintenance_history (
 id INTEGER PRIMARY KEY,
 plan_id INTEGER NOT NULL REFERENCES maintenance_plans(id),
 day TEXT NOT NULL,
 odometer TEXT NOT NULL,
 amount TEXT NOT NULL,
 expense_id INTEGER NOT NULL UNIQUE REFERENCES expenses(id)
);
CREATE TABLE goals (
 vehicle_id INTEGER NOT NULL REFERENCES vehicles(id),
 day TEXT NOT NULL,
 amount TEXT NOT NULL,
 PRIMARY KEY(vehicle_id, day)
);