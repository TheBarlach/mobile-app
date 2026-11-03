-- Checks for 20261103120000_groups_expenses.sql. Run against a local database that has the
-- migrations applied (never production), e.g.:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" -v ON_ERROR_STOP=1 -f supabase/sql-tests/groups_expenses_test.sql
-- Everything runs in one transaction that is rolled back. A failed check raises an exception.

begin;

-- Test users: Anna (a), Bo (b) and Carl (c), who is not in the group.
insert into auth.users (id, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', '{"username": "anna_t", "full_name": "Anna Test"}'),
  ('00000000-0000-0000-0000-00000000000b', '{"username": "bo_t", "full_name": "Bo Test"}'),
  ('00000000-0000-0000-0000-00000000000c', '{"username": "carl_t", "full_name": "Carl Test"}');

create function pg_temp.sign_in(p_user text) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
$$;

create function pg_temp.expect_error(p_sql text, p_expected_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  raise exception 'Expected % from: %', p_expected_state, p_sql;
exception
  when others then
    if sqlstate <> p_expected_state then
      raise exception 'Expected % but got % (%) from: %', p_expected_state, sqlstate, sqlerrm, p_sql;
    end if;
end;
$$;

grant execute on all functions in schema pg_temp to authenticated;

set local role authenticated;

-- Anna creates a group (twice, like a retry) and adds Bo.
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.create_group('10000000-0000-0000-0000-000000000001', ' Summer Trip ');
select public.create_group('10000000-0000-0000-0000-000000000001', 'Summer Trip');
select public.add_group_member('10000000-0000-0000-0000-000000000001', 'BO_T');

do $$ begin
  assert (select count(*) from public.groups) = 1, 'retrying create_group must not duplicate the group';
  assert (select name from public.groups) = 'Summer Trip', 'group name is trimmed';
  assert (select count(*) from public.group_members) = 2, 'Anna and Bo are members';
end $$;

-- Anna pays 100.00 DKK split 50/50. The retry with the same id is ignored.
select public.create_expense(
  '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Dinner',
  '00000000-0000-0000-0000-00000000000a', 10000,
  '{"00000000-0000-0000-0000-00000000000a": 5000, "00000000-0000-0000-0000-00000000000b": 5000}');
select public.create_expense(
  '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Dinner',
  '00000000-0000-0000-0000-00000000000a', 10000,
  '{"00000000-0000-0000-0000-00000000000a": 5000, "00000000-0000-0000-0000-00000000000b": 5000}');

do $$ begin
  assert (select count(*) from public.expenses) = 1, 'retrying create_expense must not duplicate it';
  assert (select sum(amount) from public.expense_shares) = 10000, 'shares are stored';
end $$;

-- Shares that do not add up are rejected and nothing is saved.
select pg_temp.expect_error($sql$
  select public.create_expense('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
    'Taxi', '00000000-0000-0000-0000-00000000000a', 10000,
    '{"00000000-0000-0000-0000-00000000000a": 5000, "00000000-0000-0000-0000-00000000000b": 4000}')
$sql$, '22023');
-- Participants must be members.
select pg_temp.expect_error($sql$
  select public.create_expense('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
    'Taxi', '00000000-0000-0000-0000-00000000000a', 100, '{"00000000-0000-0000-0000-00000000000c": 100}')
$sql$, '22023');
-- Negative amounts are rejected.
select pg_temp.expect_error($sql$
  select public.create_expense('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001',
    'Refund', '00000000-0000-0000-0000-00000000000a', -100, '{"00000000-0000-0000-0000-00000000000a": -100}')
$sql$, '22023');

do $$ begin
  assert (select count(*) from public.expenses) = 1, 'rejected expenses leave no rows behind';
end $$;

-- App users cannot write to the tables directly.
select pg_temp.expect_error($sql$
  insert into public.payments (id, group_id, from_user, to_user, amount) values (gen_random_uuid(),
    '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000b',
    '00000000-0000-0000-0000-00000000000a', 100)
$sql$, '42501');

-- Bo pays Anna back (with a retry) and Anna reminds Bo.
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000b');
select public.record_payment('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000a', 5000);
select public.record_payment('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000a', 5000);
select pg_temp.expect_error($sql$
  select public.record_payment(gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-00000000000a', 0)
$sql$, '23514');

do $$ begin
  assert (select count(*) from public.payments) = 1, 'retrying record_payment must not duplicate it';
  assert (select from_user from public.payments) = '00000000-0000-0000-0000-00000000000b',
    'the payment is recorded as coming from the caller';
  -- Bo can see Anna's name because they share a group.
  assert (select count(*) from public.profiles) = 2, 'members see their own and group mates profiles';
end $$;

select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.send_reminder('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000b');
select public.send_reminder('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000b');

do $$ begin
  assert (select count(*) from public.reminders) = 1, 'retrying send_reminder must not duplicate it';
end $$;

-- Carl is not a member: he sees nothing and cannot write to the group.
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000c');

do $$ begin
  assert (select count(*) from public.groups) = 0, 'non-members cannot see the group';
  assert (select count(*) from public.group_members) = 0, 'non-members cannot see members';
  assert (select count(*) from public.expenses) = 0, 'non-members cannot see expenses';
  assert (select count(*) from public.expense_shares) = 0, 'non-members cannot see shares';
  assert (select count(*) from public.payments) = 0, 'non-members cannot see payments';
  assert (select count(*) from public.reminders) = 0, 'others cannot see reminders';
  assert (select count(*) from public.profiles) = 1, 'non-members only see their own profile';
end $$;

select pg_temp.expect_error($sql$
  select public.record_payment(gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-00000000000a', 100)
$sql$, '42501');
select pg_temp.expect_error($sql$
  select public.add_group_member('10000000-0000-0000-0000-000000000001', 'carl_t')
$sql$, '42501');
-- Reusing someone else's group id does not make Carl a member.
select public.create_group('10000000-0000-0000-0000-000000000001', 'Hijack');

do $$ begin
  assert (select count(*) from public.groups) = 0, 'a reused group id must not grant membership';
end $$;

reset role;
select 'All groups/expenses checks passed' as result;

rollback;
