"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import { acceptBid, placeBid, withdrawBid } from "@/lib/api";

async function accessToken(): Promise<string | null> {
  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  return session?.access_token ?? null;
}

/**
 * A form action resolves to void, so a refusal cannot be returned. It goes back
 * to the listing as a query parameter and is rendered there — swallowing it
 * would make a rejected offer look exactly like an accepted one.
 */
function back(listingId: string, error?: string, done?: string): never {
  if (error) {
    redirect(`/listings/${listingId}?error=${encodeURIComponent(error)}`);
  }
  redirect(`/listings/${listingId}?done=${done ?? "placed"}`);
}

function describe(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

export async function submitBid(formData: FormData) {
  const listingId = formData.get("listingId") as string;
  const token = await accessToken();
  if (!token) redirect(`/auth/login?next=/listings/${listingId}`);

  const amount = Number(formData.get("amount"));
  if (!Number.isFinite(amount) || amount <= 0) {
    back(listingId, "Enter an offer greater than zero.");
  }

  try {
    await placeBid(
      listingId,
      { amount, message: (formData.get("message") as string) || undefined },
      token,
    );
  } catch (e) {
    back(listingId, describe(e, "That offer could not be placed."));
  }

  revalidatePath(`/listings/${listingId}`);
  revalidatePath("/browse");
  back(listingId, undefined, "placed");
}

export async function retractBid(formData: FormData) {
  const listingId = formData.get("listingId") as string;
  const token = await accessToken();
  if (!token) redirect(`/auth/login?next=/listings/${listingId}`);

  try {
    await withdrawBid(listingId, token);
  } catch (e) {
    back(listingId, describe(e, "That offer could not be withdrawn."));
  }

  revalidatePath(`/listings/${listingId}`);
  revalidatePath("/browse");
  back(listingId, undefined, "withdrawn");
}

export async function takeOffer(formData: FormData) {
  const listingId = formData.get("listingId") as string;
  const bidId = formData.get("bidId") as string;
  const token = await accessToken();
  if (!token) redirect(`/auth/login?next=/listings/${listingId}`);

  try {
    await acceptBid(listingId, bidId, token);
  } catch (e) {
    back(listingId, describe(e, "That offer could not be accepted."));
  }

  revalidatePath(`/listings/${listingId}`);
  revalidatePath("/browse");
  revalidatePath("/browse/mine");
  back(listingId, undefined, "accepted");
}
