"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";

/**
 * Said once, plainly, rather than letting the Supabase client throw a stack
 * trace at someone trying to sign in.
 */
const NOT_CONFIGURED =
  "Accounts are not available on this deployment yet — no identity provider is configured. Browsing the marketplace works without one.";

export async function login(formData: FormData) {
  if (!isSupabaseConfigured()) {
    return { error: NOT_CONFIGURED };
  }

  const supabase = await createClient();

  const email = formData.get("email") as string;
  const password = formData.get("password") as string;

  if (!email.endsWith("@vt.edu")) {
    return { error: "Please use your Virginia Tech email (@vt.edu)" };
  }

  const { error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });

  if (error) {
    return { error: error.message };
  }

  revalidatePath("/", "layout");
  redirect("/");
}

export async function signup(formData: FormData) {
  if (!isSupabaseConfigured()) {
    return { error: NOT_CONFIGURED };
  }

  const supabase = await createClient();

  const email = formData.get("email") as string;
  const password = formData.get("password") as string;

  if (!email.endsWith("@vt.edu")) {
    return { error: "Please use your Virginia Tech email (@vt.edu)" };
  }

  const { error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      // The callback route lives in this Next.js app, not in the Spring API —
      // pointing the confirmation link at NEXT_PUBLIC_API_URL sent every new
      // account to a service that has no such route.
      emailRedirectTo: `${process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"}/auth/callback`,
    },
  });

  if (error) {
    return { error: error.message };
  }

  return { success: "Check your email to confirm your account!" };
}

export async function signout() {
  if (!isSupabaseConfigured()) {
    redirect("/auth/login");
  }

  const supabase = await createClient();
  await supabase.auth.signOut();
  revalidatePath("/", "layout");
  redirect("/auth/login");
}
