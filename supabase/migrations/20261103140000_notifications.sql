-- In-app notifications ("Alerts" tab).
--
-- Notifications are created by the database, not by the app: a trigger on each table writes one
-- row per recipient when an expense, reminder, payment or new member is inserted. That way a
-- notification is never lost because the sender's phone went offline after its write succeeded,
-- and a retried write cannot notify twice (the write functions skip ids that already exist, and
-- an AFTER INSERT trigger does not fire for a skipped row).
--
-- Names, the group name and the amount are copied into the row when it is created, so the text
-- stays readable after someone leaves the group, and the app needs no extra joins to show it.

-- Reminders carry the amount the sender saw on the balances screen. Balances are simplified
-- across the whole group in the app (domain/balances), so the database cannot recompute the
-- amount one member owes another.
ALTER TABLE public.reminders
  ADD COLUMN amount bigint CHECK (amount IS NULL OR amount > 0);

DROP FUNCTION public.send_reminder(uuid, uuid, uuid);

-- p_amount is optional so app versions that do not send it keep working.
CREATE FUNCTION public.send_reminder(p_id uuid, p_group_id uuid, p_to_user uuid, p_amount bigint DEFAULT NULL)
  RETURNS void
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
begin
  if not public.is_group_member(p_group_id) then
    raise exception 'Not a member of this group' using errcode = '42501';
  end if;
  if not public.is_group_member(p_group_id, p_to_user) then
    raise exception 'The receiver is not a member of this group' using errcode = '22023';
  end if;
  if p_amount is not null and p_amount <= 0 then
    raise exception 'Amount must be positive' using errcode = '22023';
  end if;

  insert into public.reminders (id, group_id, from_user, to_user, amount)
  values (p_id, p_group_id, auth.uid(), p_to_user, p_amount)
  on conflict (id) do nothing;
end;
$$;

REVOKE ALL ON FUNCTION public.send_reminder(uuid, uuid, uuid, bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.send_reminder(uuid, uuid, uuid, bigint) TO authenticated;

-- Table --------------------------------------------------------------------------------------

CREATE TABLE public.notifications (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  type        text NOT NULL CHECK (
                type IN ('expense_added', 'payment_reminder', 'payment_received', 'member_joined', 'added_to_group')
              ),
  group_id    uuid REFERENCES public.groups (id) ON DELETE CASCADE,
  group_name  text,
  -- Who caused the notification: the member who added the expense, sent the reminder, paid or joined.
  actor_id    uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  actor_name  text,
  -- The expense description, for expense_added.
  description text,
  amount      bigint CHECK (amount IS NULL OR amount >= 0),
  read_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX notifications_user_id_created_at_idx ON public.notifications (user_id, created_at DESC);

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Read own notifications" ON public.notifications
  FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);

-- Read only for app users; rows are written by the triggers and mark_notifications_read.
REVOKE ALL ON TABLE public.notifications FROM anon, authenticated;
GRANT SELECT ON TABLE public.notifications TO authenticated;

-- Marking as read ----------------------------------------------------------------------------

-- Marks the caller's notifications in p_ids as read. Ids of other users' notifications are
-- ignored. Calling it again has no further effect, so the app can safely retry it.
CREATE FUNCTION public.mark_notifications_read(p_ids uuid[])
  RETURNS void
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;

  update public.notifications
  set read_at = now()
  where user_id = auth.uid() and id = any (p_ids) and read_at is null;
end;
$$;

REVOKE ALL ON FUNCTION public.mark_notifications_read(uuid[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_notifications_read(uuid[]) TO authenticated;

-- Triggers -----------------------------------------------------------------------------------

-- The name shown for a user: full name, else username.
CREATE FUNCTION public.notification_display_name(p_user_id uuid)
  RETURNS text
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
  select coalesce(nullif(trim(p.full_name), ''), p.username)
  from public.profiles p
  where p.id = p_user_id;
$$;

-- Every other member hears about a new expense.
CREATE FUNCTION public.notify_expense_added()
  RETURNS trigger
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
declare
  v_actor uuid := coalesce(new.created_by, new.paid_by);
begin
  insert into public.notifications (user_id, type, group_id, group_name, actor_id, actor_name, description, amount)
  select m.user_id, 'expense_added', new.group_id, g.name, v_actor,
         public.notification_display_name(v_actor), new.description, new.amount
  from public.group_members m
  join public.groups g on g.id = m.group_id
  where m.group_id = new.group_id and m.user_id <> v_actor;
  return null;
end;
$$;

-- The member who is asked to pay.
CREATE FUNCTION public.notify_payment_reminder()
  RETURNS trigger
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
begin
  insert into public.notifications (user_id, type, group_id, group_name, actor_id, actor_name, amount)
  select new.to_user, 'payment_reminder', new.group_id, g.name, new.from_user,
         public.notification_display_name(new.from_user), new.amount
  from public.groups g
  where g.id = new.group_id;
  return null;
end;
$$;

-- The member who was paid.
CREATE FUNCTION public.notify_payment_received()
  RETURNS trigger
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
begin
  insert into public.notifications (user_id, type, group_id, group_name, actor_id, actor_name, amount)
  select new.to_user, 'payment_received', new.group_id, g.name, new.from_user,
         public.notification_display_name(new.from_user), new.amount
  from public.groups g
  where g.id = new.group_id;
  return null;
end;
$$;

-- The other members hear that someone joined; the new member hears who added them.
-- The creator joining their own new group (create_group) notifies nobody.
CREATE FUNCTION public.notify_member_joined()
  RETURNS trigger
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
declare
  v_adder uuid := auth.uid();
begin
  insert into public.notifications (user_id, type, group_id, group_name, actor_id, actor_name)
  select m.user_id, 'member_joined', new.group_id, g.name, new.user_id,
         public.notification_display_name(new.user_id)
  from public.group_members m
  join public.groups g on g.id = m.group_id
  where m.group_id = new.group_id
    and m.user_id <> new.user_id
    -- The member who added them already knows.
    and m.user_id is distinct from v_adder;

  if v_adder is not null and v_adder <> new.user_id then
    insert into public.notifications (user_id, type, group_id, group_name, actor_id, actor_name)
    select new.user_id, 'added_to_group', new.group_id, g.name, v_adder,
           public.notification_display_name(v_adder)
    from public.groups g
    where g.id = new.group_id;
  end if;
  return null;
end;
$$;

CREATE TRIGGER expenses_notify
  AFTER INSERT ON public.expenses
  FOR EACH ROW EXECUTE FUNCTION public.notify_expense_added();

CREATE TRIGGER reminders_notify
  AFTER INSERT ON public.reminders
  FOR EACH ROW EXECUTE FUNCTION public.notify_payment_reminder();

CREATE TRIGGER payments_notify
  AFTER INSERT ON public.payments
  FOR EACH ROW EXECUTE FUNCTION public.notify_payment_received();

CREATE TRIGGER group_members_notify
  AFTER INSERT ON public.group_members
  FOR EACH ROW EXECUTE FUNCTION public.notify_member_joined();

-- Helpers and trigger functions must not be callable through the API.
REVOKE ALL ON FUNCTION
  public.notification_display_name(uuid),
  public.notify_expense_added(),
  public.notify_payment_reminder(),
  public.notify_payment_received(),
  public.notify_member_joined()
  FROM PUBLIC, anon, authenticated;
