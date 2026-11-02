import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PrimaryButton } from '@/components/primary-button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { hasErrors, validateSignUp, type FieldErrors } from '@/features/auth/validation';
import { signUp } from '@/services/api/auth';
import type { SignUpInput } from '@/types/profile';

const EMPTY_FORM: SignUpInput = { fullName: '', username: '', email: '', password: '' };

export default function RegisterScreen() {
  const [form, setForm] = useState<SignUpInput>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors<SignUpInput>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [needsConfirmation, setNeedsConfirmation] = useState(false);

  const update = (field: keyof SignUpInput) => (value: string) =>
    setForm((current) => ({ ...current, [field]: value }));

  async function handleSubmit() {
    const errors = validateSignUp(form);
    setFieldErrors(errors);
    setSubmitError(null);
    if (hasErrors(errors)) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await signUp(form);
      // 'signed-in' is handled by the root layout, which shows the app.
      if (result.status === 'confirmation-required') {
        setNeedsConfirmation(true);
        setIsSubmitting(false);
      }
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Could not create the account.');
      setIsSubmitting(false);
    }
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
            <ThemedText type="title">Create account</ThemedText>

            {needsConfirmation ? (
              <ThemedText>
                Check your email and confirm your account, then log in.
              </ThemedText>
            ) : (
              <>
                <TextField
                  label="Full name"
                  value={form.fullName}
                  onChangeText={update('fullName')}
                  error={fieldErrors.fullName}
                  autoComplete="name"
                  textContentType="name"
                />
                <TextField
                  label="Username"
                  value={form.username}
                  onChangeText={update('username')}
                  error={fieldErrors.username}
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="username"
                  textContentType="username"
                />
                <TextField
                  label="Email"
                  value={form.email}
                  onChangeText={update('email')}
                  error={fieldErrors.email}
                  autoCapitalize="none"
                  autoComplete="email"
                  keyboardType="email-address"
                  textContentType="emailAddress"
                />
                <TextField
                  label="Password"
                  value={form.password}
                  onChangeText={update('password')}
                  error={fieldErrors.password}
                  secureTextEntry
                  autoComplete="new-password"
                  textContentType="newPassword"
                  onSubmitEditing={handleSubmit}
                />

                {submitError ? (
                  <ThemedText type="small" style={styles.error} accessibilityRole="alert">
                    {submitError}
                  </ThemedText>
                ) : null}

                <PrimaryButton
                  title="Create account"
                  onPress={handleSubmit}
                  loading={isSubmitting}
                />
              </>
            )}

            <Link href="/login" replace>
              <ThemedText type="linkPrimary">Already have an account? Log in</ThemedText>
            </Link>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
  },
  safeArea: {
    flex: 1,
    maxWidth: MaxContentWidth,
  },
  flex: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    gap: Spacing.three,
    padding: Spacing.four,
  },
  error: {
    color: '#D93025',
  },
});
