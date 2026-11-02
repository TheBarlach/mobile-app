import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PrimaryButton } from '@/components/primary-button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { hasErrors, validateSignIn, type FieldErrors } from '@/features/auth/validation';
import { signIn } from '@/services/api/auth';
import type { SignInInput } from '@/types/profile';

export default function LoginScreen() {
  const [form, setForm] = useState<SignInInput>({ email: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors<SignInInput>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const update = (field: keyof SignInInput) => (value: string) =>
    setForm((current) => ({ ...current, [field]: value }));

  async function handleSubmit() {
    const errors = validateSignIn(form);
    setFieldErrors(errors);
    setSubmitError(null);
    if (hasErrors(errors)) {
      return;
    }

    setIsSubmitting(true);
    try {
      // On success the root layout sees the new session and shows the app.
      await signIn(form);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Login failed.');
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
            <ThemedText type="title">Log in</ThemedText>

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
              autoComplete="current-password"
              textContentType="password"
              onSubmitEditing={handleSubmit}
            />

            {submitError ? (
              <ThemedText type="small" style={styles.error} accessibilityRole="alert">
                {submitError}
              </ThemedText>
            ) : null}

            <PrimaryButton title="Log in" onPress={handleSubmit} loading={isSubmitting} />

            <Link href="/register" replace>
              <ThemedText type="linkPrimary">No account? Create one</ThemedText>
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
