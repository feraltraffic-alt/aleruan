-- When the customer said yes, and when the status last changed: analytics counts agreed deals by the
-- day they were won, and lists recently completed ones. Older deals fall back to the day they were created.
ALTER TABLE deal ADD COLUMN won_at timestamptz;
ALTER TABLE deal ADD COLUMN status_changed_at timestamptz;
