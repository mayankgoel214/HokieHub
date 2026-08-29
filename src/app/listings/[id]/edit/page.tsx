import { notFound, redirect } from "next/navigation";
import { getListing } from "@/lib/api";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";
import { EditListingForm } from "./edit-form";
import type { Listing } from "@/types/api";

/**
 * Editing a listing you own.
 *
 * The API had supported this from the start, with the ownership check and the
 * tests to go with it, and the only way to change anything through the site was
 * to mark it sold or delete it. A seller who mistyped a price had to take the
 * listing down and post it again, losing its offers with it.
 *
 * Ownership is checked here as well as by the API. The API is what enforces it;
 * this is so that someone who is not the owner gets sent away rather than shown
 * a form that will refuse them at the end.
 */
export default async function EditListingPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  if (!isSupabaseConfigured()) redirect("/auth/login");

  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) redirect(`/auth/login?next=/listings/${id}/edit`);

  let listing: Listing;
  try {
    listing = await getListing(id);
  } catch {
    notFound();
  }

  if (listing.seller.id !== session.user.id) {
    redirect(`/listings/${id}`);
  }

  return <EditListingForm listing={listing} />;
}
