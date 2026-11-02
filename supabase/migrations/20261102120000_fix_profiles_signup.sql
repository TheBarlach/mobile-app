-- Fix profile creation on sign-up and tighten profile permissions.
--
-- Problems in 20261026121540_remote_schema.sql:
--   1. handle_new_user() only copied full_name, so username was never stored.
--   2. A duplicate or empty-string username made the trigger fail, which
--      aborts the whole sign-up ("Database error saving new user").
--   3. updated_at was never updated.
--   4. anon/authenticated had TRUNCATE/DELETE/etc. on profiles. TRUNCATE is
--      not covered by RLS, so anyone with the publishable key could empty it.
--   5. Users created before the trigger existed have no profile row.

-- 1 + 2: store all metadata sent from the app, normalised.
CREATE OR REPLACE FUNCTION public.handle_new_user()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO ''
  AS $function$
declare
  v_username text := nullif(lower(trim(new.raw_user_meta_data ->> 'username')), '');
begin
  -- Never block sign-up because of a username clash; the app checks
  -- availability first, so this only happens on a race.
  if v_username is not null
     and exists (select 1 from public.profiles p where p.username = v_username) then
    v_username := null;
  end if;

  insert into public.profiles (id, username, full_name, avatar_url)
  values (
    new.id,
    v_username,
    nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
    nullif(trim(new.raw_user_meta_data ->> 'avatar_url'), '')
  )
  on conflict (id) do nothing;

  return new;
end;
$function$;

-- Trigger functions must not be callable through the API.
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;

-- Usernames are stored lowercase so uniqueness is case-insensitive.
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_username_format
  CHECK (username IS NULL OR username ~ '^[a-z0-9_]{3,30}$') NOT VALID;

-- Lets the registration screen check a username before signing up.
CREATE OR REPLACE FUNCTION public.is_username_available(p_username text)
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path TO ''
  AS $function$
  select not exists (
    select 1 from public.profiles
    where username = lower(trim(p_username))
  );
$function$;

REVOKE ALL ON FUNCTION public.is_username_available(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_username_available(text) TO anon, authenticated;

-- 3: keep updated_at current.
CREATE OR REPLACE FUNCTION public.set_updated_at()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SET search_path TO ''
  AS $function$
begin
  new.updated_at := now();
  return new;
end;
$function$;

REVOKE ALL ON FUNCTION public.set_updated_at() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS profiles_set_updated_at ON public.profiles;
CREATE TRIGGER profiles_set_updated_at
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.set_updated_at();

-- 4: least-privilege grants. RLS policies still restrict rows to the owner.
REVOKE ALL ON TABLE public.profiles FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.profiles TO authenticated;

-- 5: backfill profiles for existing users.
INSERT INTO public.profiles (id, full_name)
SELECT u.id, nullif(trim(u.raw_user_meta_data ->> 'full_name'), '')
FROM auth.users u
WHERE NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = u.id);
