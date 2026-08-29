"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import { deleteListing, updateListing } from "@/lib/api";

/**
 * The API is a separate service, so the Supabase session cookie does not reach
 * it — the access token has to be read here and sent explicitly.
 */
async function accessToken(): Promise<string | null> {
  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  return session?.access_token ?? null;
}

/**
 * A form action has to resolve to void, so a failure cannot simply be returned.
 * It goes back to the page as a query parameter and is rendered there — the
 * alternative was swallowing it, which would leave a delete that silently did
 * nothing looking exactly like one that worked.
 */
function failed(message: string): never {
  redirect(`/browse/mine?error=${encodeURIComponent(message)}`);
}

function describe(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

async function setStatus(id: string, status: "sold" | "available") {
  const token = await accessToken();
  if (!token) failed("Your session has expired. Sign in again.");

  try {
    await updateListing(id, { status }, token);
  } catch (e) {
    failed(describe(e, "That listing could not be updated."));
  }

  revalidatePath("/browse/mine");
  revalidatePath("/browse");
}

export async function markSold(formData: FormData) {
  await setStatus(formData.get("id") as string, "sold");
}

export async function relist(formData: FormData) {
  await setStatus(formData.get("id") as string, "available");
}

export async function removeListing(formData: FormData) {
  const token = await accessToken();
  if (!token) failed("Your session has expired. Sign in again.");

  try {
    await deleteListing(formData.get("id") as string, token);
  } catch (e) {
    failed(describe(e, "That listing could not be deleted."));
  }

  revalidatePath("/browse/mine");
  revalidatePath("/browse");
}
