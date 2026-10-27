CREATE UNIQUE INDEX one_open_shift_per_vehicle ON shifts(vehicle_id) WHERE ended_at IS NULL;
CREATE INDEX shifts_period ON shifts(day, vehicle_id);
CREATE INDEX expenses_period ON expenses(day, vehicle_id);
CREATE INDEX rides_shift ON rides(shift_id);
CREATE INDEX history_plan ON maintenance_history(plan_id, day);