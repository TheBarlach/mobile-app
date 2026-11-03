-- Groups, members, expenses, payments and reminders.
--
-- Only facts are stored (who paid what and how it was split). Balances are calculated in the
-- app from these rows by the pure functions in domain/balances, so they can never drift.
--
-- Amounts are bigint in øre. Every write goes through a function below, which validates the
-- input and is idempotent on its client-generated id: a retry after a network failure cannot
-- create a duplicate. The tables themselves are read-only for app users.

-- Tables -------------------------------------------------------------------------------------

CREATE TABLE public.groups (
  id         uuid PRIMARY KEY,
  name       text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
  created_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.group_members (
  group_id  uuid NOT NULL REFERENCES public.groups (id) ON DELETE CASCADE,
  user_id   uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, user_id)
);

CREATE INDEX group_members_user_id_idx ON public.group_members (user_id);

CREATE TABLE public.expenses (
  id          uuid PRIMARY KEY,
  group_id    uuid NOT NULL REFERENCES public.groups (id) ON DELETE CASCADE,
  paid_by     uuid NOT NULL REFERENCES public.profiles (id) ON DELETE RESTRICT,
  description text NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 200),
  amount      bigint NOT NULL CHECK (amount >= 0),
  created_by  uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX expenses_group_id_idx ON public.expenses (group_id);

CREATE TABLE public.expense_shares (
  expense_id uuid NOT NULL REFERENCES public.expenses (id) ON DELETE CASCADE,
  user_id    uuid NOT NULL REFERENCES public.profiles (id) ON DELETE RESTRICT,
  amount     bigint NOT NULL CHECK (amount >= 0),
  PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE public.payments (
  id         uuid PRIMARY KEY,
  group_id   uuid NOT NULL REFERENCES public.groups (id) ON DELETE CASCADE,
  from_user  uuid NOT NULL REFERENCES public.profiles (id) ON DELETE RESTRICT,
  to_user    uuid NOT NULL REFERENCES public.profiles (id) ON DELETE RESTRICT,
  amount     bigint NOT NULL CHECK (amount > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (from_user <> to_user)
);

CREATE INDEX payments_group_id_idx ON public.payments (group_id);

-- Read by the notifications feature later.
CREATE TABLE public.reminders (
  id         uuid PRIMARY KEY,
  group_id   uuid NOT NULL REFERENCES public.groups (id) ON DELETE CASCADE,
  from_user  uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  to_user    uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (from_user <> to_user)
);

CREATE INDEX reminders_to_user_idx ON public.reminders (to_user);

-- Membership helpers -------------------------------------------------------------------------
-- SECURITY DEFINER so policies can use them without recursing into group_members' own policy.

CREATE FUNCTION public.is_group_member(p_group_id uuid, p_user_id uuid DEFAULT auth.uid())
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
  select exists (
    select 1 from public.group_members m
    where m.group_id = p_group_id and m.user_id = p_user_id
  );
$$;

CREATE FUNCTION public.shares_group_with(p_user_id uuid)
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
  select exists (
    select 1
    from public.group_members mine
    join public.group_members theirs on theirs.group_id = mine.group_id
    where mine.user_id = auth.uid() and theirs.user_id = p_user_id
  );
$$;

-- Row level security: members can read their groups' data ------------------------------------

ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expense_shares ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reminders ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Members read their groups" ON public.groups
  FOR SELECT TO authenticated USING (public.is_group_member(id));

CREATE POLICY "Members read group members" ON public.group_members
  FOR SELECT TO authenticated USING (public.is_group_member(group_id));

CREATE POLICY "Members read expenses" ON public.expenses
  FOR SELECT TO authenticated USING (public.is_group_member(group_id));

CREATE POLICY "Members read expense shares" ON public.expense_shares
  FOR SELECT TO authenticated
  USING (exists (
    select 1 from public.expenses e
    where e.id = expense_id and public.is_group_member(e.group_id)
  ));

CREATE POLICY "Members read payments" ON public.payments
  FOR SELECT TO authenticated USING (public.is_group_member(group_id));

CREATE POLICY "Sender and receiver read reminders" ON public.reminders
  FOR SELECT TO authenticated
  USING ((select auth.uid()) IN (from_user, to_user));

-- Group mates must see each other's names on the balances screen.
CREATE POLICY "Read profiles of group mates" ON public.profiles
  FOR SELECT TO authenticated USING (public.shares_group_with(id));

-- Supabase grants everything on new tables to anon/authenticated by default; reads only.
REVOKE ALL ON TABLE
  public.groups, public.group_members, public.expenses, public.expense_shares, public.payments, public.reminders
  FROM anon, authenticated;
GRANT SELECT ON TABLE
  public.groups, public.group_members, public.expenses, public.expense_shares, public.payments, public.reminders
  TO authenticated;

-- Write functions ----------------------------------------------------------------------------
-- Each checks that the caller is signed in and a member, and does nothing if the id exists.

CREATE FUNCTION public.create_group(p_id uuid, p_name text)
  RETURNS void
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;

  insert into public.groups (id, name, created_by)
  values (p_id, trim(p_name), v_user)
  on conflict (id) do nothing;

  -- Only the creator of this id becomes a member, so a reused id cannot join someone's group.
  if exists (select 1 from public.groups g where g.id = p_id and g.created_by = v_user) then
    insert into public.group_members (group_id, user_id)
    values (p_id, v_user)
    on conflict do nothing;
  end if;
end;
$$;

CREATE FUNCTION public.add_group_member(p_group_id uuid, p_username text)
  RETURNS uuid
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
declare
  v_member uuid;
begin
  if not public.is_group_member(p_group_id) then
    raise exception 'Not a member of this group' using errcode = '42501';
  end if;

  select p.id into v_member from public.profiles p where p.username = lower(trim(p_username));
  if v_member is null then
    raise exception 'No user with that username' using errcode = 'P0002';
  end if;

  insert into public.group_members (group_id, user_id)
  values (p_group_id, v_member)
  on conflict do nothing;

  return v_member;
end;
$$;

-- p_shares is a JSON object mapping user id to share in øre, e.g. {"<uuid>": 3334, ...}.
CREATE FUNCTION public.create_expense(
  p_id uuid,
  p_group_id uuid,
  p_description text,
  p_paid_by uuid,
  p_amount bigint,
  p_shares jsonb
)
  RETURNS void
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $$
declare
  v_share record;
  v_total bigint := 0;
begin
  if not public.is_group_member(p_group_id) then
    raise exception 'Not a member of this group' using errcode = '42501';
  end if;

  -- Idempotent retry: the expense was already saved.
  if exists (select 1 from public.expenses e where e.id = p_id) then
    return;
  end if;

  if not public.is_group_member(p_group_id, p_paid_by) then
    raise exception 'The payer is not a member of this group' using errcode = '22023';
  end if;
  if p_amount is null or p_amount < 0 then
    raise exception 'Amount must not be negative' using errcode = '22023';
  end if;
  if jsonb_typeof(p_shares) <> 'object' or p_shares = '{}'::jsonb then
    raise exception 'An expense needs at least one participant' using errcode = '22023';
  end if;

  insert into public.expenses (id, group_id, paid_by, description, amount, created_by)
  values (p_id, p_group_id, p_paid_by, trim(p_description), p_amount, auth.uid());

  for v_share in select key::uuid as user_id, value::bigint as amount from jsonb_each_text(p_shares) loop
    if not public.is_group_member(p_group_id, v_share.user_id) then
      raise exception 'A participant is not a member of this group' using errcode = '22023';
    end if;
    if v_share.amount < 0 then
      raise exception 'Shares must not be negative' using errcode = '22023';
    end if;
    insert into public.expense_shares (expense_id, user_id, amount)
    values (p_id, v_share.user_id, v_share.amount);
    v_total := v_total + v_share.amount;
  end loop;

  -- Raising here rolls back the expense row as well.
  if v_total <> p_amount then
    raise exception 'Shares add up to %, expected %', v_total, p_amount using errcode = '22023';
  end if;
end;
$$;

-- Records that the caller paid p_to_user.
CREATE FUNCTION public.record_payment(p_id uuid, p_group_id uuid, p_to_user uuid, p_amount bigint)
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

  insert into public.payments (id, group_id, from_user, to_user, amount)
  values (p_id, p_group_id, auth.uid(), p_to_user, p_amount)
  on conflict (id) do nothing;
end;
$$;

-- Asks p_to_user to pay the caller back.
CREATE FUNCTION public.send_reminder(p_id uuid, p_group_id uuid, p_to_user uuid)
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

  insert into public.reminders (id, group_id, from_user, to_user)
  values (p_id, p_group_id, auth.uid(), p_to_user)
  on conflict (id) do nothing;
end;
$$;

REVOKE ALL ON FUNCTION
  public.is_group_member(uuid, uuid),
  public.shares_group_with(uuid),
  public.create_group(uuid, text),
  public.add_group_member(uuid, text),
  public.create_expense(uuid, uuid, text, uuid, bigint, jsonb),
  public.record_payment(uuid, uuid, uuid, bigint),
  public.send_reminder(uuid, uuid, uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION
  public.is_group_member(uuid, uuid),
  public.shares_group_with(uuid),
  public.create_group(uuid, text),
  public.add_group_member(uuid, text),
  public.create_expense(uuid, uuid, text, uuid, bigint, jsonb),
  public.record_payment(uuid, uuid, uuid, bigint),
  public.send_reminder(uuid, uuid, uuid)
  TO authenticated;
