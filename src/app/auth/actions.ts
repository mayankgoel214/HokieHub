"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

export async function login(formData: FormData) {
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
  const supabase = await createClient();
  await supabase.auth.signOut();
  revalidatePath("/", "layout");
  redirect("/auth/login");
}
