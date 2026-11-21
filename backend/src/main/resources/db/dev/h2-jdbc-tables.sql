-- Dev profile (H2) only. Hibernate creates the tables that have JPA entities; this adds the
-- ones the backend reads with plain JDBC. Keep in step with schema.sql.
CREATE TABLE IF NOT EXISTS report_snapshots (
    id         UUID PRIMARY KEY,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    data_json  TEXT NOT NULL,
    CONSTRAINT report_snapshots_date_order CHECK (start_date <= end_date)
);
