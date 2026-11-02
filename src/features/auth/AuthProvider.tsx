import type { Session } from '@supabase/supabase-js';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { fetchProfile } from '@/services/api/auth';
import { supabase } from '@/services/api/supabase';
import type { Profile } from '@/types/profile';

interface AuthContextValue {
  session: Session | null;
  profile: Profile | null;
  /** True until the stored session has been read on app start. */
  isLoading: boolean;
  profileError: string | null;
  reloadProfile: () => Promise<void>;
}

interface ProfileState {
  userId: string;
  profile: Profile | null;
  error: string | null;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  // Tagged with the user id so a previous user's profile is never shown.
  const [profileState, setProfileState] = useState<ProfileState | null>(null);

  useEffect(() => {
    supabase.auth
      .getSession()
      .then(({ data }) => setSession(data.session))
      .finally(() => setIsLoading(false));

    // Supabase warns against awaiting other Supabase calls inside this
    // callback, so the profile is loaded in a separate effect.
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession);
    });
    return () => data.subscription.unsubscribe();
  }, []);

  const userId = session?.user.id;

  const loadProfile = useCallback((id: string, isCancelled: () => boolean = () => false) => {
    return fetchProfile(id).then(
      (profile) => {
        if (!isCancelled()) {
          setProfileState({ userId: id, profile, error: null });
        }
      },
      (error: unknown) => {
        if (isCancelled()) {
          return;
        }
        const message = error instanceof Error ? error.message : 'Could not load your profile.';
        setProfileState((current) => ({
          userId: id,
          profile: current?.userId === id ? current.profile : null,
          error: message,
        }));
      },
    );
  }, []);

  useEffect(() => {
    if (!userId) {
      return;
    }
    let cancelled = false;
    void loadProfile(userId, () => cancelled);
    return () => {
      cancelled = true;
    };
  }, [userId, loadProfile]);

  const current = profileState && profileState.userId === userId ? profileState : null;
  const profile = current?.profile ?? null;
  const profileError = current?.error ?? null;

  const reloadProfile = useCallback(async () => {
    if (userId) {
      await loadProfile(userId);
    }
  }, [userId, loadProfile]);

  const value = useMemo(
    () => ({ session, profile, isLoading, profileError, reloadProfile }),
    [session, profile, isLoading, profileError, reloadProfile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
