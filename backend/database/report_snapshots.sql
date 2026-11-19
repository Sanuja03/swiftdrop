-- Thenuri's saved delivery reports. Review before applying to shared Supabase.
-- Existing tables and records are preserved.
CREATE TABLE IF NOT EXISTS public.report_snapshots (
    id UUID PRIMARY KEY,
    created_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    data_json TEXT NOT NULL,
    CONSTRAINT report_snapshots_date_order CHECK (start_date <= end_date)
);

CREATE INDEX IF NOT EXISTS idx_report_snapshots_created
    ON public.report_snapshots (created_at DESC, id DESC);

-- Reports are accessed through the Spring API's Admin role checks.
-- The database owner used by the backend can access the table; browser database
-- clients receive no access through Supabase's public API without an RLS policy.
ALTER TABLE public.report_snapshots ENABLE ROW LEVEL SECURITY;
