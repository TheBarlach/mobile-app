/** Row in the `public.profiles` table. One per `auth.users` row. */
export interface Profile {
  id: string;
  username: string | null;
  full_name: string | null;
  avatar_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface SignUpInput {
  email: string;
  password: string;
  username: string;
  fullName: string;
}

export interface SignInInput {
  email: string;
  password: string;
}
