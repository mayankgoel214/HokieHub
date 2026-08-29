import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Eye, MapPin } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiError, getListing } from "@/lib/api";
import type { Listing } from "@/types/api";

/**
 * The listing a browse card links to. Reading it also bumps the view counter,
 * which the API does in the same request.
 */
export default async function ListingPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let listing: Listing;
  try {
    listing = await getListing(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    // Anything else is a real failure, and pretending it is a missing listing
    // would hide an outage behind a 404.
    throw error;
  }

  const isService = listing.listingType === "service";

  return (
    <main className="min-h-screen bg-background">
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Link
          href="/browse"
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-sm"
        >
          <ArrowLeft className="size-4" />
          Back to listings
        </Link>

        <div className="mt-6 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-3xl font-bold tracking-tight text-balance wrap-anywhere">
              {listing.title}
            </h1>
            <p className="text-muted-foreground mt-1.5 text-sm">
              {listing.category.name} · listed{" "}
              {new Date(listing.createdAt).toLocaleDateString()}
            </p>
          </div>
          <div className="text-right">
            <div className="text-2xl font-semibold">
              ${Number(listing.price).toFixed(2)}
              {isService && (
                <span className="text-muted-foreground text-base font-normal">
                  /hr
                </span>
              )}
            </div>
            <Badge className="mt-1.5">{listing.status}</Badge>
          </div>
        </div>

        <p className="mt-6 text-pretty leading-relaxed wrap-anywhere">
          {listing.description}
        </p>

        <dl className="mt-8 grid gap-4 border-t pt-6 sm:grid-cols-2">
          <Detail label="Seller" value={listing.seller.fullName} />
          <Detail label="Contact" value={listing.seller.email} />
          {listing.condition && (
            <Detail
              label="Condition"
              value={listing.condition.replace("_", " ")}
              capitalize
            />
          )}
          {listing.location && (
            <Detail
              label="Location"
              value={listing.location}
              icon={<MapPin className="size-3.5" />}
            />
          )}
          <Detail
            label="Views"
            value={String(listing.viewsCount)}
            icon={<Eye className="size-3.5" />}
          />
        </dl>

        {listing.serviceDetails && (
          <div className="mt-8 border-t pt-6">
            <h2 className="font-semibold">Service details</h2>
            <dl className="mt-4 grid gap-4 sm:grid-cols-2">
              {listing.serviceDetails.subjects?.length ? (
                <Detail
                  label="Subjects"
                  value={listing.serviceDetails.subjects.join(", ")}
                />
              ) : null}
              {listing.serviceDetails.availability && (
                <Detail
                  label="Availability"
                  value={listing.serviceDetails.availability}
                />
              )}
              {listing.serviceDetails.hourlyRate != null && (
                <Detail
                  label="Hourly rate"
                  value={`$${Number(listing.serviceDetails.hourlyRate).toFixed(2)}`}
                />
              )}
              {listing.serviceDetails.experienceLevel && (
                <Detail
                  label="Experience"
                  value={listing.serviceDetails.experienceLevel}
                  capitalize
                />
              )}
            </dl>
          </div>
        )}

        <div className="mt-10 border-t pt-6">
          <Button asChild>
            <a
              href={`mailto:${listing.seller.email}?subject=${encodeURIComponent(`HokieHub: ${listing.title}`)}`}
            >
              Email the seller
            </a>
          </Button>
        </div>
      </div>
    </main>
  );
}

function Detail({
  label,
  value,
  icon,
  capitalize,
}: {
  label: string;
  value: string;
  icon?: React.ReactNode;
  capitalize?: boolean;
}) {
  return (
    <div className="min-w-0">
      <dt className="text-muted-foreground text-xs uppercase tracking-wide">
        {label}
      </dt>
      <dd
        className={`mt-1 flex items-center gap-1.5 text-sm font-medium ${
          capitalize ? "capitalize" : ""
        }`}
      >
        {icon}
        <span className="truncate">{value}</span>
      </dd>
    </div>
  );
}
