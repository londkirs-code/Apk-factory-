-- Add independent archiving for TaskHub lists without changing task rows or existing RLS policies.
ALTER TABLE public.task_lists
    ADD COLUMN IF NOT EXISTS is_archived boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS task_lists_archived_created_at_idx
    ON public.task_lists (is_archived, created_at);
