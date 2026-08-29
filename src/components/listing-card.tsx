import Link from "next/link";
import { Gavel, MapPin, TriangleAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { ListingCover } from "@/components/listing-cover";
import { relativeTime } from "@/lib/time";
import type { Listing } from "@/types/api";

/**
 * One listing in the grid. Extracted from the browse page because "my listings"
 * needs the same card and a marketplace where the same item looks like two
 * different things depending on which page you found it on reads as unfinished.
 */
export function ListingCard({
  listing,
  priority = false,
}: {
  listing: Listing;
  priority?: boolean;
}) {
  const isService = listing.listingType === "service";
  const defectCount = listing.defects?.length ?? 0;
  const bids = listing.bids;

  return (
    <Link
      href={`/listings/${listing.id}`}
      className="focus-visible:ring-ring group rounded-xl focus-visible:ring-2 focus-visible:outline-none"
    >
      <Card className="flex h-full flex-col overflow-hidden pt-0 transition-shadow hover:shadow-md">
        <ListingCover
          src={listing.primaryImageUrl}
          categoryName={listing.category.name}
          title={listing.title}
          priority={priority}
        />

        <CardContent className="flex flex-1 flex-col gap-2">
          <div className="flex items-start justify-between gap-2">
            <span className="text-lg font-semibold">
              ${Number(listing.price).toFixed(2)}
              {isService && (
                <span className="text-muted-foreground text-xs font-normal">
                  {" "}
                  /hr
                </span>
              )}
            </span>
            {listing.status !== "available" && (
              <Badge variant="secondary">{listing.status}</Badge>
            )}
          </div>

          <h3 className="line-clamp-2 font-medium wrap-anywhere">
            {listing.title}
          </h3>

          <p className="text-muted-foreground line-clamp-2 text-sm wrap-anywhere">
            {listing.description}
          </p>

          <div className="text-muted-foreground mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 pt-1 text-xs">
            <span>{listing.category.name}</span>
            {listing.condition && (
              <span className="capitalize">
                {listing.condition.replace("_", " ")}
              </span>
            )}
            {listing.location && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3" />
                {listing.location}
              </span>
            )}
          </div>
        </CardContent>

        <CardFooter className="text-muted-foreground gap-3 border-t text-xs">
          {bids && bids.count > 0 ? (
            <span className="text-foreground inline-flex items-center gap-1 font-medium">
              <Gavel className="size-3.5" />
              {bids.count} {bids.count === 1 ? "offer" : "offers"}
              {bids.highest != null &&
                ` · high $${Number(bids.highest).toFixed(0)}`}
            </span>
          ) : (
            <span>No offers yet</span>
          )}
          {defectCount > 0 && (
            <span className="inline-flex items-center gap-1">
              <TriangleAlert className="size-3.5" />
              {defectCount}
            </span>
          )}
          <span className="ml-auto">{relativeTime(listing.createdAt)}</span>
        </CardFooter>
      </Card>
    </Link>
  );
}
