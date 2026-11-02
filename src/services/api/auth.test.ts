import { beforeEach, describe, expect, it, jest } from '@jest/globals';

import { AuthServiceError, fetchProfile, signIn, signUp } from './auth';
import { supabase } from './supabase';

jest.mock('./supabase', () => ({
  supabase: {
    rpc: jest.fn(),
    from: jest.fn(),
    auth: {
      signUp: jest.fn(),
      signInWithPassword: jest.fn(),
      signOut: jest.fn(),
    },
  },
}));

type AsyncMock = jest.Mock<(...args: unknown[]) => Promise<unknown>>;

// Only the client calls used by the auth service are faked.
const mockSupabase = supabase as unknown as {
  rpc: AsyncMock;
  from: jest.Mock<(table: string) => unknown>;
  auth: { signUp: AsyncMock; signInWithPassword: AsyncMock; signOut: AsyncMock };
};

const input = {
  fullName: '  Anna Hansen ',
  username: ' Anna_H ',
  email: ' Anna@Example.com ',
  password: 'secret123',
};

const session = { access_token: 'token', user: { id: 'user-1' } };

beforeEach(() => {
  jest.clearAllMocks();
  mockSupabase.rpc.mockResolvedValue({ data: true, error: null });
});

describe('signUp', () => {
  it('sends normalized profile data as metadata for the database trigger', async () => {
    mockSupabase.auth.signUp.mockResolvedValue({
      data: { user: { id: 'user-1', identities: [{}] }, session },
      error: null,
    });

    await expect(signUp(input)).resolves.toEqual({ status: 'signed-in', session });

    expect(mockSupabase.rpc).toHaveBeenCalledWith('is_username_available', { p_username: 'anna_h' });
    expect(mockSupabase.auth.signUp).toHaveBeenCalledWith({
      email: 'anna@example.com',
      password: 'secret123',
      options: { data: { username: 'anna_h', full_name: 'Anna Hansen' } },
    });
  });

  it('does not create the user when the username is taken', async () => {
    mockSupabase.rpc.mockResolvedValue({ data: false, error: null });

    await expect(signUp(input)).rejects.toMatchObject({ code: 'username_taken' });
    expect(mockSupabase.auth.signUp).not.toHaveBeenCalled();
  });

  it('reports when email confirmation is required', async () => {
    mockSupabase.auth.signUp.mockResolvedValue({
      data: { user: { id: 'user-1', identities: [{}] }, session: null },
      error: null,
    });

    await expect(signUp(input)).resolves.toEqual({ status: 'confirmation-required' });
  });

  it('detects an existing email hidden by Supabase', async () => {
    mockSupabase.auth.signUp.mockResolvedValue({
      data: { user: { id: 'user-1', identities: [] }, session: null },
      error: null,
    });

    await expect(signUp(input)).rejects.toMatchObject({ code: 'user_already_exists' });
  });
});

describe('signIn', () => {
  it('returns the session for valid credentials', async () => {
    mockSupabase.auth.signInWithPassword.mockResolvedValue({ data: { session }, error: null });

    await expect(signIn({ email: ' Anna@Example.com', password: 'secret123' })).resolves.toBe(session);
    expect(mockSupabase.auth.signInWithPassword).toHaveBeenCalledWith({
      email: 'anna@example.com',
      password: 'secret123',
    });
  });

  it('maps wrong credentials to a readable error', async () => {
    mockSupabase.auth.signInWithPassword.mockResolvedValue({
      data: { session: null },
      error: { name: 'AuthApiError', code: 'invalid_credentials', message: 'Invalid login credentials' },
    });

    await expect(signIn({ email: 'a@b.dk', password: 'wrong' })).rejects.toEqual(
      new AuthServiceError('Wrong email or password.', 'invalid_credentials'),
    );
  });

  it('maps network failures to a readable error', async () => {
    mockSupabase.auth.signInWithPassword.mockResolvedValue({
      data: { session: null },
      error: { name: 'AuthRetryableFetchError', message: 'Failed to fetch' },
    });

    await expect(signIn({ email: 'a@b.dk', password: 'x' })).rejects.toMatchObject({ code: 'network' });
  });
});

describe('fetchProfile', () => {
  function mockProfileQuery(result: { data: unknown; error: unknown }) {
    const query = {
      select: jest.fn().mockReturnThis(),
      eq: jest.fn().mockReturnThis(),
      maybeSingle: jest.fn(() => Promise.resolve(result)),
    };
    mockSupabase.from.mockReturnValue(query);
    return query;
  }

  it('loads the profile row for the logged-in user', async () => {
    const profile = { id: 'user-1', username: 'anna_h', full_name: 'Anna Hansen' };
    const query = mockProfileQuery({ data: profile, error: null });

    await expect(fetchProfile('user-1')).resolves.toBe(profile);
    expect(mockSupabase.from).toHaveBeenCalledWith('profiles');
    expect(query.eq).toHaveBeenCalledWith('id', 'user-1');
  });

  it('fails clearly when the profile row is missing', async () => {
    mockProfileQuery({ data: null, error: null });

    await expect(fetchProfile('user-1')).rejects.toMatchObject({ code: 'profile_not_found' });
  });
});
