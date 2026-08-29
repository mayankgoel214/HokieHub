"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import { unlockPriceCheck } from "@/lib/api";

/**
 * Buying the price check.
 *
 * No money moves: the API records what it would have cost and charges nothing.
 * The button says so, because a paywall that pretends to take payment is a
 * different thing from one that is honestly a demonstration.
 */
export async function buyPriceCheck(formData: FormData) {
  const listingId = formData.get("listingId") as string;

  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) redirect(`/auth/login?next=/listings/${listingId}`);

  try {
    await unlockPriceCheck(listingId, session.access_token);
  } catch (e) {
    const message =
      e instanceof Error && e.message
        ? e.message
        : "The price check could not be unlocked.";
    redirect(`/listings/${listingId}?error=${encodeURIComponent(message)}`);
  }

  revalidatePath(`/listings/${listingId}`);
  redirect(`/listings/${listingId}?done=unlocked#price-check`);
}
