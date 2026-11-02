import type { AuthError, Session } from '@supabase/supabase-js';

import { normalizeEmail, normalizeUsername } from '@/features/auth/validation';
import { supabase } from '@/services/api/supabase';
import type { Profile, SignInInput, SignUpInput } from '@/types/profile';

export class AuthServiceError extends Error {
  constructor(message: string, readonly code?: string) {
    super(message);
    this.name = 'AuthServiceError';
  }
}

export type SignUpResult =
  | { status: 'signed-in'; session: Session }
  // Returned when "Confirm email" is enabled in the Supabase project.
  | { status: 'confirmation-required' };

const PROFILE_COLUMNS = 'id, username, full_name, avatar_url, created_at, updated_at';

function toServiceError(error: AuthError): AuthServiceError {
  switch (error.code) {
    case 'invalid_credentials':
      return new AuthServiceError('Wrong email or password.', error.code);
    case 'email_not_confirmed':
      return new AuthServiceError('Confirm your email before logging in.', error.code);
    case 'user_already_exists':
    case 'email_exists':
      return new AuthServiceError('An account with this email already exists.', error.code);
    case 'weak_password':
      return new AuthServiceError('The password is too weak.', error.code);
    case 'over_request_rate_limit':
    case 'over_email_send_rate_limit':
      return new AuthServiceError('Too many attempts. Try again in a moment.', error.code);
    default:
      if (error.name === 'AuthRetryableFetchError') {
        return new AuthServiceError('No connection to the server. Try again.', 'network');
      }
      return new AuthServiceError(error.message, error.code);
  }
}

export async function isUsernameAvailable(username: string): Promise<boolean> {
  const { data, error } = await supabase.rpc('is_username_available', {
    p_username: normalizeUsername(username),
  });
  if (error) {
    throw new AuthServiceError('Could not check the username. Try again.', error.code);
  }
  return data === true;
}

/**
 * Creates the auth user. The profile row is created by the
 * `handle_new_user` trigger from the metadata passed here, so it is
 * stored even when email confirmation means there is no session yet.
 */
export async function signUp(input: SignUpInput): Promise<SignUpResult> {
  if (!(await isUsernameAvailable(input.username))) {
    throw new AuthServiceError('This username is already taken.', 'username_taken');
  }

  const { data, error } = await supabase.auth.signUp({
    email: normalizeEmail(input.email),
    password: input.password,
    options: {
      data: {
        username: normalizeUsername(input.username),
        full_name: input.fullName.trim(),
      },
    },
  });
  if (error) {
    throw toServiceError(error);
  }

  // With email confirmation on, Supabase hides existing accounts by
  // returning a user without identities instead of an error.
  if (data.user && data.user.identities?.length === 0) {
    throw new AuthServiceError('An account with this email already exists.', 'user_already_exists');
  }

  return data.session
    ? { status: 'signed-in', session: data.session }
    : { status: 'confirmation-required' };
}

export async function signIn(input: SignInInput): Promise<Session> {
  const { data, error } = await supabase.auth.signInWithPassword({
    email: normalizeEmail(input.email),
    password: input.password,
  });
  if (error) {
    throw toServiceError(error);
  }
  return data.session;
}

export async function signOut(): Promise<void> {
  const { error } = await supabase.auth.signOut();
  if (error) {
    throw toServiceError(error);
  }
}

export async function fetchProfile(userId: string): Promise<Profile> {
  const { data, error } = await supabase
    .from('profiles')
    .select(PROFILE_COLUMNS)
    .eq('id', userId)
    .maybeSingle<Profile>();

  if (error) {
    throw new AuthServiceError('Could not load your profile.', error.code);
  }
  if (!data) {
    throw new AuthServiceError('Your profile was not found.', 'profile_not_found');
  }
  return data;
}
