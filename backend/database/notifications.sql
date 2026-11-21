-- SwiftDrop Notifications
-- Review before executing against shared Supabase database.

CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES public.users(id) ON DELETE CASCADE,

    event_id UUID UNIQUE,

    parcel_id UUID
        REFERENCES public.parcels(id) ON DELETE SET NULL,

    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,

    notification_type VARCHAR(50) NOT NULL
        DEFAULT 'PARCEL_STATUS',

    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON public.notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON public.notifications (user_id, is_read);

-- For databases where the notifications table already exists:
ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS event_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_event_id
    ON public.notifications (event_id)
    WHERE event_id IS NOT NULL;