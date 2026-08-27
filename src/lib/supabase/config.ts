/**
 * Whether a Supabase project is configured for this deployment.
 *
 * Browsing HokieHub is public and goes straight to the Spring API, so the
 * marketplace is fully usable with no identity provider at all. The middleware
 * used to build a Supabase client unconditionally, which threw on every request
 * when the keys were absent and took down every page — including the ones that
 * never needed a session.
 *
 * Missing configuration means nobody can be signed in. It does not mean anyone
 * gets in: the callers below fail closed, sending anything that requires an
 * account to the login page, which says plainly that sign-in is unavailable.
 */
export const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
export const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export function isSupabaseConfigured(): boolean {
  return Boolean(
    supabaseUrl &&
    supabaseAnonKey &&
    // The committed placeholders count as unconfigured; treating them as real
    // just moves the failure to a confusing 401 from Supabase itself.
    !supabaseUrl.includes("placeholder") &&
    !supabaseAnonKey.includes("placeholder"),
  );
}
