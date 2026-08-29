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
  redirect("/browse");
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

  const { data, error } = await supabase.auth.signUp({
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

  // Whether a session comes back depends on the project's "Confirm email"
  // setting, and this used to ignore that: it always told the new account to go
  // and check their email. With confirmation off, Supabase signs them in on the
  // spot, so that message sent every new user to wait for mail that would never
  // arrive — while they were, in fact, already signed in.
  if (data.session) {
    revalidatePath("/", "layout");
    redirect("/browse");
  }

  return { success: "Check your email to confirm your account." };
}

/**
 * Signs the visitor in as a shared demo account.
 *
 * Posting and bidding require a Virginia Tech address that has been confirmed
 * by a link, which is the correct rule and also means somebody evaluating this
 * from outside the university cannot try any of it. This is the way through: a
 * real account, on the record, that anyone can borrow.
 *
 * The credentials live in the server environment, so the password never reaches
 * the browser. It is a demonstration account and is labelled as one everywhere
 * it appears — it is not a back door around the email rule, it is one account
 * that already satisfied it.
 */
export async function signInAsDemo() {
  // A form action resolves to void, so a refusal goes back as a query parameter
  // and the login page renders it.
  const fail = (why: string): never => {
    redirect(`/auth/login?error=${encodeURIComponent(why)}`);
  };

  if (!isSupabaseConfigured()) fail(NOT_CONFIGURED);

  const email = process.env.DEMO_ACCOUNT_EMAIL;
  const password = process.env.DEMO_ACCOUNT_PASSWORD;
  if (!email || !password) {
    fail(
      "The demo account is not set up on this deployment. Sign up with a @vt.edu address instead.",
    );
  }

  const supabase = await createClient();
  const { error } = await supabase.auth.signInWithPassword({
    email: email!,
    password: password!,
  });
  if (error) fail(`The demo account could not sign in: ${error.message}`);

  revalidatePath("/", "layout");
  redirect("/browse");
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
