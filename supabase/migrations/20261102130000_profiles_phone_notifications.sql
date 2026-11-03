-- Fields edited on the Profile screen.
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS phone text,
  ADD COLUMN IF NOT EXISTS notifications_enabled boolean NOT NULL DEFAULT true;

-- Same rule as validatePhone() in the app: optional "+", then 6-15 digits.
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_phone_format
  CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{6,15}$');
