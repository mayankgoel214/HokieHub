import Link from "next/link";
import { Plus, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { listListings } from "@/lib/api";
import type { Listing } from "@/types/api";

/**
 * An unreachable API and an empty marketplace are different things, and this
 * used to render both as "No listings found" — an outage looked exactly like a
 * marketplace nobody had posted to yet.
 */
async function getListings(
  q: string,
): Promise<
  { listings: Listing[]; failed: false } | { listings: null; failed: true }
> {
  try {
    // Browsing is public, so this needs no token. The API pages its results;
    // the dashboard shows the first page.
    const page = await listListings({ size: 24, q });
    return { listings: page.content, failed: false };
  } catch (error) {
    console.error("Error fetching listings:", error);
    return { listings: null, failed: true };
  }
}

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q = "" } = await searchParams;
  const result = await getListings(q);
  const listings = result.listings ?? [];

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-8">
        {/* The header already carries "Post a listing"; a second button for
            the same action beside the title was just noise. */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold tracking-tight">
            Browse the marketplace
          </h1>
          <p className="text-muted-foreground mt-1">
            {q
              ? `Results for "${q}"`
              : "Everything students are selling right now"}
          </p>
        </div>

        {/* A plain GET form: the query lives in the URL, so a search is
            shareable and needs no client-side JavaScript. */}
        <form action="/browse" method="get" className="mb-8 flex gap-2">
          <div className="relative flex-1">
            <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
            <Input
              type="search"
              name="q"
              defaultValue={q}
              placeholder="Search listings by title or description"
              className="pl-9"
              aria-label="Search listings"
            />
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>

        {result.failed ? (
          <div className="flex min-h-[400px] items-center justify-center rounded-lg border-2 border-dashed">
            <div className="max-w-md text-center">
              <h3 className="text-lg font-semibold">
                The marketplace is unreachable
              </h3>
              <p className="text-muted-foreground mt-2 text-pretty">
                The listings service did not respond. This is not an empty
                marketplace — nothing could be loaded at all. Try again shortly.
              </p>
            </div>
          </div>
        ) : listings.length === 0 ? (
          <div className="flex min-h-[400px] items-center justify-center rounded-lg border-2 border-dashed">
            <div className="text-center">
              <h3 className="text-lg font-semibold">
                {q ? `Nothing matches "${q}"` : "No listings found"}
              </h3>
              <p className="text-muted-foreground mt-2">
                {q
                  ? "Try a different word, or clear the search to see everything."
                  : "Get started by creating your first listing"}
              </p>
              {q ? (
                <Link href="/browse">
                  <Button variant="outline" className="mt-4">
                    Clear search
                  </Button>
                </Link>
              ) : (
                <Link href="/browse/new">
                  <Button className="mt-4">
                    <Plus className="size-5" />
                    Create Listing
                  </Button>
                </Link>
              )}
            </div>
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {listings.map((listing) => (
              <Link
                key={listing.id}
                href={`/listings/${listing.id}`}
                className="focus-visible:ring-ring rounded-xl focus-visible:ring-2 focus-visible:outline-none"
              >
                <Card className="flex h-full flex-col transition-shadow hover:shadow-md">
                  <CardHeader>
                    <div className="mb-3 flex items-center justify-between gap-2">
                      <div className="flex items-baseline gap-2">
                        <span className="text-sm font-semibold">
                          ${Number(listing.price).toFixed(2)}
                        </span>
                        {listing.listingType === "service" && (
                          <span className="text-muted-foreground text-xs">
                            /hr
                          </span>
                        )}
                      </div>
                      <Badge>{listing.status}</Badge>
                    </div>
                    <CardTitle className="line-clamp-2 text-xl wrap-anywhere">
                      {listing.title}
                    </CardTitle>
                    <CardDescription className="line-clamp-3 text-base wrap-anywhere">
                      {listing.description}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="flex-1">
                    <div className="space-y-2">
                      {listing.condition && (
                        <div className="text-sm">
                          <span className="text-muted-foreground">
                            Condition:
                          </span>{" "}
                          <span className="font-medium capitalize">
                            {listing.condition.replace("_", " ")}
                          </span>
                        </div>
                      )}
                      {listing.location && (
                        <div className="text-muted-foreground text-sm">
                          {listing.location}
                        </div>
                      )}
                    </div>
                  </CardContent>
                  <CardFooter className="border-t">
                    <div className="flex w-full items-center justify-between text-xs text-muted-foreground">
                      <span>{listing.viewsCount} views</span>
                      <span>
                        {new Date(listing.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </CardFooter>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
