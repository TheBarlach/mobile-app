-- Checks for 20261103140000_notifications.sql. Run against a local database that has the
-- migrations applied (never production), e.g.:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" -v ON_ERROR_STOP=1 -f supabase/sql-tests/notifications_test.sql
-- Everything runs in one transaction that is rolled back. A failed check raises an exception.

begin;

-- Test users: Anna (a), Bo (b), Carl (c) and Dina (d), who is never in the group.
insert into auth.users (id, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', '{"username": "anna_t", "full_name": "Anna Test"}'),
  ('00000000-0000-0000-0000-00000000000b', '{"username": "bo_t", "full_name": "Bo Test"}'),
  ('00000000-0000-0000-0000-00000000000c', '{"username": "carl_t"}'),
  ('00000000-0000-0000-0000-00000000000d', '{"username": "dina_t", "full_name": "Dina Test"}');

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

-- Notifications of one user, as the test (superuser) sees them.
create function pg_temp.notifications_of(p_user text)
  returns setof public.notifications language sql as $$
  select * from public.notifications where user_id = p_user::uuid order by created_at, type;
$$;

grant execute on all functions in schema pg_temp to authenticated;

-- Group setup ---------------------------------------------------------------------------------

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.create_group('10000000-0000-0000-0000-000000000001', 'Summer Trip');
reset role;

do $$ begin
  assert (select count(*) from public.notifications) = 0, 'creating a group notifies nobody';
end $$;

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.add_group_member('10000000-0000-0000-0000-000000000001', 'bo_t');
select public.add_group_member('10000000-0000-0000-0000-000000000001', 'carl_t');
-- Adding the same member again (a retry) must not notify again.
select public.add_group_member('10000000-0000-0000-0000-000000000001', 'carl_t');
reset role;

do $$ begin
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')) = 0,
    'the member who adds someone is not notified';
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')) = 2,
    'Bo hears he was added and that Carl joined';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')
    where type = 'added_to_group' and actor_name = 'Anna Test' and group_name = 'Summer Trip'),
    'Bo hears that Anna added him';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')
    where type = 'member_joined' and actor_name = 'carl_t'),
    'Bo hears that Carl joined, named by username because Carl has no full name';
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000c')) = 1,
    'Carl only hears that he was added, once';
end $$;

-- Expenses ------------------------------------------------------------------------------------

delete from public.notifications;

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.create_expense(
  '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Dinner',
  '00000000-0000-0000-0000-00000000000a', 60000,
  '{"00000000-0000-0000-0000-00000000000a": 20000, "00000000-0000-0000-0000-00000000000b": 20000, "00000000-0000-0000-0000-00000000000c": 20000}');
-- Retry with the same id.
select public.create_expense(
  '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Dinner',
  '00000000-0000-0000-0000-00000000000a', 60000,
  '{"00000000-0000-0000-0000-00000000000a": 20000, "00000000-0000-0000-0000-00000000000b": 20000, "00000000-0000-0000-0000-00000000000c": 20000}');
reset role;

do $$ begin
  assert (select count(*) from public.notifications) = 2, 'one expense notification each for Bo and Carl, despite the retry';
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')) = 0,
    'the member who added the expense is not notified';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')
    where type = 'expense_added' and actor_name = 'Anna Test' and description = 'Dinner'
      and amount = 60000 and group_name = 'Summer Trip' and read_at is null),
    'Bo sees who added what, and for how much';
end $$;

-- An expense rejected by validation creates no notification.
set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect_error($sql$
  select public.create_expense(gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 'Taxi',
    '00000000-0000-0000-0000-00000000000a', 100, '{"00000000-0000-0000-0000-00000000000b": 99}')
$sql$, '22023');
reset role;

do $$ begin
  assert (select count(*) from public.notifications) = 2, 'a rolled back expense notifies nobody';
end $$;

-- Reminders and payments ----------------------------------------------------------------------

delete from public.notifications;

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000a');
select public.send_reminder('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000b', 20000);
select public.send_reminder('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000b', 20000);
-- Older app versions send no amount.
select public.send_reminder('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000c');
select pg_temp.expect_error($sql$
  select public.send_reminder(gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-00000000000b', 0)
$sql$, '22023');

select pg_temp.sign_in('00000000-0000-0000-0000-00000000000b');
select public.record_payment('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000a', 20000);
select public.record_payment('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-00000000000a', 20000);
reset role;

do $$ begin
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')) = 1,
    'Bo gets one reminder, despite the retry';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000b')
    where type = 'payment_reminder' and actor_name = 'Anna Test' and amount = 20000),
    'the reminder says who asked and how much';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000c')
    where type = 'payment_reminder' and amount is null),
    'a reminder without an amount still notifies';
  assert (select count(*) from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')) = 1,
    'Anna is told once that Bo paid her';
  assert exists (
    select 1 from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')
    where type = 'payment_received' and actor_name = 'Bo Test' and amount = 20000),
    'the payment notification says who paid how much';
end $$;

-- Access --------------------------------------------------------------------------------------

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000b');

do $$ begin
  assert (select count(*) from public.notifications) = 1, 'Bo only reads his own notifications';
  assert (select count(*) from public.notifications where user_id <> auth.uid()) = 0,
    'no notification of another user is visible';
end $$;

select pg_temp.expect_error($sql$
  insert into public.notifications (user_id, type) values (auth.uid(), 'expense_added')
$sql$, '42501');
select pg_temp.expect_error($sql$
  update public.notifications set read_at = now()
$sql$, '42501');
select pg_temp.expect_error($sql$
  delete from public.notifications
$sql$, '42501');
select pg_temp.expect_error($sql$
  select public.notify_expense_added()
$sql$, '42501');

-- Marking as read: Bo marks his reminder and Anna's payment notification. Only his own changes.
select public.mark_notifications_read(array(
  select id from public.notifications
  union all
  select '00000000-0000-0000-0000-0000000000ff'::uuid
));
-- Retry.
select public.mark_notifications_read(array(select id from public.notifications));

do $$ begin
  assert (select count(*) from public.notifications where read_at is null) = 0, 'Bo''s reminder is read';
end $$;

reset role;

do $$ begin
  assert (select read_at from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')) is null,
    'marking cannot touch another user''s notifications';
end $$;

set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000b');
-- Passing someone else's id does nothing.
select public.mark_notifications_read(array(
  select id from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')));
reset role;

do $$ begin
  assert (select read_at from pg_temp.notifications_of('00000000-0000-0000-0000-00000000000a')) is null,
    'another user''s id is ignored';
end $$;

-- Dina, outside the group, sees nothing and cannot remind anyone in it.
set local role authenticated;
select pg_temp.sign_in('00000000-0000-0000-0000-00000000000d');

do $$ begin
  assert (select count(*) from public.notifications) = 0, 'Dina has no notifications';
end $$;

select pg_temp.expect_error($sql$
  select public.send_reminder(gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-00000000000b', 100)
$sql$, '42501');

reset role;
select 'All notifications checks passed' as result;

rollback;
