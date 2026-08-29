import Link from "next/link";
import { redirect } from "next/navigation";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ListingCard } from "@/components/listing-card";
import { myListings } from "@/lib/api";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";
import type { Listing } from "@/types/api";
import { markSold, relist, removeListing } from "./actions";

/**
 * Somewhere to see what you have posted.
 *
 * The API had served /api/listings/mine from the start, and the client had a
 * myListings() for it, and no page ever called either — so posting something
 * dropped it into the feed and that was the last you saw of it. Editing and
 * deleting were in the same state: enforced, tested, and unreachable.
 */
export default async function MyListingsPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;

  if (!isSupabaseConfigured()) {
    redirect("/auth/login");
  }

  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session) {
    redirect("/auth/login?next=/browse/mine");
  }

  let listings: Listing[] = [];
  let failed = false;
  try {
    listings = (await myListings(session.access_token, 0, 50)).content;
  } catch {
    failed = true;
  }

  return (
    <main className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold tracking-tight">My listings</h1>
          <p className="text-muted-foreground mt-1">
            Everything you have posted, and what you can do with it.
          </p>
        </div>

        {error && (
          <div className="border-destructive/40 bg-destructive/5 text-destructive mb-6 rounded-lg border px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {failed ? (
          <div className="flex min-h-[300px] items-center justify-center rounded-lg border-2 border-dashed">
            <div className="max-w-md text-center">
              <h2 className="text-lg font-semibold">
                Your listings could not be loaded
              </h2>
              <p className="text-muted-foreground mt-2 text-pretty">
                The marketplace service did not respond. This does not mean your
                listings are gone — nothing could be fetched at all.
              </p>
            </div>
          </div>
        ) : listings.length === 0 ? (
          <div className="flex min-h-[300px] items-center justify-center rounded-lg border-2 border-dashed">
            <div className="text-center">
              <h2 className="text-lg font-semibold">Nothing posted yet</h2>
              <p className="text-muted-foreground mt-2">
                Sell a textbook, a desk, or an hour of tutoring.
              </p>
              <Button asChild className="mt-4">
                <Link href="/browse/new">
                  <Plus className="size-5" />
                  Post a listing
                </Link>
              </Button>
            </div>
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {listings.map((listing) => (
              <div key={listing.id} className="flex flex-col gap-2">
                <ListingCard listing={listing} />
                <div className="flex gap-2">
                  {listing.status === "available" ? (
                    <form action={markSold} className="flex-1">
                      <input type="hidden" name="id" value={listing.id} />
                      <Button
                        type="submit"
                        variant="secondary"
                        size="sm"
                        className="w-full"
                      >
                        Mark sold
                      </Button>
                    </form>
                  ) : (
                    <form action={relist} className="flex-1">
                      <input type="hidden" name="id" value={listing.id} />
                      <Button
                        type="submit"
                        variant="secondary"
                        size="sm"
                        className="w-full"
                      >
                        Relist
                      </Button>
                    </form>
                  )}
                  <form action={removeListing}>
                    <input type="hidden" name="id" value={listing.id} />
                    <Button type="submit" variant="ghost" size="sm">
                      Delete
                    </Button>
                  </form>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
