import { Platform, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AnimatedIcon } from '@/components/animated-icon';
import { HintRow } from '@/components/hint-row';
import { PrimaryButton } from '@/components/primary-button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { WebBadge } from '@/components/web-badge';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/AuthProvider';
import { signOut } from '@/services/api/auth';

export default function HomeScreen() {
  const { session, profile, profileError, reloadProfile } = useAuth();
  const displayName = profile?.full_name ?? profile?.username ?? session?.user.email;

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ThemedView style={styles.heroSection}>
          <AnimatedIcon />
          <ThemedText type="title" style={styles.title}>
            {displayName ? `Welcome, ${displayName}` : 'Welcome'}
          </ThemedText>
        </ThemedView>

        <ThemedView type="backgroundElement" style={styles.stepContainer}>
          {profile ? (
            <>
              <HintRow title="Name" hint={<ThemedText type="small">{profile.full_name ?? '-'}</ThemedText>} />
              <HintRow
                title="Username"
                hint={<ThemedText type="small">{profile.username ? `@${profile.username}` : '-'}</ThemedText>}
              />
              <HintRow title="Email" hint={<ThemedText type="small">{session?.user.email ?? '-'}</ThemedText>} />
            </>
          ) : profileError ? (
            <>
              <ThemedText type="small">{profileError}</ThemedText>
              <PrimaryButton title="Try again" onPress={reloadProfile} />
            </>
          ) : (
            <ThemedText type="small">Loading profile…</ThemedText>
          )}
        </ThemedView>

        <PrimaryButton title="Log out" onPress={() => void signOut().catch(() => undefined)} />

        {Platform.OS === 'web' && <WebBadge />}
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    flexDirection: 'row',
  },
  safeArea: {
    flex: 1,
    paddingHorizontal: Spacing.four,
    alignItems: 'center',
    gap: Spacing.three,
    paddingBottom: BottomTabInset + Spacing.three,
    maxWidth: MaxContentWidth,
  },
  heroSection: {
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
    paddingHorizontal: Spacing.four,
    gap: Spacing.four,
  },
  title: {
    textAlign: 'center',
  },
  stepContainer: {
    gap: Spacing.three,
    alignSelf: 'stretch',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.four,
    borderRadius: Spacing.four,
  },
});
