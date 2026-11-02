import { describe, expect, it } from '@jest/globals';

import { hasErrors, normalizeEmail, normalizeUsername, validateSignIn, validateSignUp } from './validation';

const validSignUp = {
  fullName: 'Anna Hansen',
  username: 'anna_h',
  email: 'anna@example.com',
  password: 'secret123',
};

describe('normalizers', () => {
  it('trims and lowercases email and username', () => {
    expect(normalizeEmail('  Anna@Example.COM ')).toBe('anna@example.com');
    expect(normalizeUsername(' Anna_H ')).toBe('anna_h');
  });
});

describe('validateSignUp', () => {
  it('accepts valid input', () => {
    expect(hasErrors(validateSignUp(validSignUp))).toBe(false);
  });

  it('accepts a mixed-case username because it is stored lowercase', () => {
    expect(validateSignUp({ ...validSignUp, username: 'Anna_H' }).username).toBeUndefined();
  });

  it.each(['ab', 'has space', 'æøå', 'a'.repeat(31)])('rejects username %p', (username) => {
    expect(validateSignUp({ ...validSignUp, username }).username).toBeDefined();
  });

  it('rejects empty name, invalid email and short password', () => {
    const errors = validateSignUp({ fullName: '  ', username: 'anna', email: 'anna@', password: 'short' });
    expect(Object.keys(errors).sort()).toEqual(['email', 'fullName', 'password']);
  });
});

describe('validateSignIn', () => {
  it('requires email and password', () => {
    const errors = validateSignIn({ email: '', password: '' });
    expect(Object.keys(errors).sort()).toEqual(['email', 'password']);
  });

  it('accepts valid input', () => {
    expect(hasErrors(validateSignIn({ email: 'anna@example.com', password: 'x' }))).toBe(false);
  });
});
