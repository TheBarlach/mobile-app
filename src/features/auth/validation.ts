import type { SignInInput, SignUpInput } from '@/types/profile';

export const MIN_PASSWORD_LENGTH = 8;

// Must match the profiles_username_format constraint in the database.
const USERNAME_PATTERN = /^[a-z0-9_]{3,30}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type FieldErrors<T> = Partial<Record<keyof T, string>>;

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

export function normalizeUsername(username: string): string {
  return username.trim().toLowerCase();
}

export function validateSignIn(input: SignInInput): FieldErrors<SignInInput> {
  const errors: FieldErrors<SignInInput> = {};

  if (!EMAIL_PATTERN.test(normalizeEmail(input.email))) {
    errors.email = 'Enter a valid email address.';
  }
  if (input.password.length === 0) {
    errors.password = 'Enter your password.';
  }

  return errors;
}

export function validateSignUp(input: SignUpInput): FieldErrors<SignUpInput> {
  const errors: FieldErrors<SignUpInput> = {};

  if (input.fullName.trim().length === 0) {
    errors.fullName = 'Enter your name.';
  }
  if (!USERNAME_PATTERN.test(normalizeUsername(input.username))) {
    errors.username = 'Use 3-30 letters, numbers or underscores.';
  }
  if (!EMAIL_PATTERN.test(normalizeEmail(input.email))) {
    errors.email = 'Enter a valid email address.';
  }
  if (input.password.length < MIN_PASSWORD_LENGTH) {
    errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
  }

  return errors;
}

export function hasErrors<T>(errors: FieldErrors<T>): boolean {
  return Object.keys(errors).length > 0;
}
