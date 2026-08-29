import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Eye, Gavel, MapPin } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ListingCover } from "@/components/listing-cover";
import { DefectList } from "@/components/defect-list";
import { relativeTime } from "@/lib/time";
import {
  ApiError,
  getListing,
  getPriceCheck,
  listBids,
  priceCheckStatus,
} from "@/lib/api";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";
import { PriceCheckPanel } from "@/components/price-check-panel";
import type {
  Bid,
  Listing,
  PriceCheck,
  PriceCheckStatusInfo,
} from "@/types/api";
import { retractBid, submitBid, takeOffer } from "./bid-actions";

/**
 * The listing a browse card links to. Reading it also bumps the view counter,
 * which the API does in the same request.
 */
export default async function ListingPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ error?: string; done?: string }>;
}) {
  const { id } = await params;
  const { error, done } = await searchParams;

  let listing: Listing;
  try {
    listing = await getListing(id);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      notFound();
    }
    // Anything else is a real failure, and pretending it is a missing listing
    // would hide an outage behind a 404.
    throw e;
  }

  // Who is looking decides what they can do: the seller sees the offers, a
  // signed-in stranger can make one, a visitor is invited to sign in.
  let viewerId: string | null = null;
  let accessToken: string | null = null;
  let offers: Bid[] = [];
  if (isSupabaseConfigured()) {
    const supabase = await createClient();
    const {
      data: { session },
    } = await supabase.auth.getSession();
    viewerId = session?.user?.id ?? null;
    accessToken = session?.access_token ?? null;

    if (session && listing.seller.id === viewerId) {
      try {
        offers = await listBids(id, session.access_token);
      } catch {
        offers = [];
      }
    }
  }

  // The price check: whether it exists here, whether this viewer has bought it,
  // and if so the analysis itself. Fetched server-side so an unbought analysis
  // never reaches the browser at all.
  let pcStatus: PriceCheckStatusInfo | null = null;
  let priceCheck: PriceCheck | null = null;
  try {
    pcStatus = await priceCheckStatus(id, accessToken ?? undefined);
    if (pcStatus.unlocked && listing.seller.id !== viewerId && accessToken) {
      priceCheck = await getPriceCheck(id, accessToken);
    }
  } catch {
    // A price check that cannot be reached must not take the listing down with
    // it; the panel simply does not appear.
    pcStatus = null;
  }

  const isSeller = viewerId !== null && viewerId === listing.seller.id;
  const isService = listing.listingType === "service";
  const takingOffers = listing.status === "available";

  return (
    <main className="min-h-screen bg-background">
      <div className="container mx-auto max-w-5xl px-4 py-8">
        <Link
          href="/browse"
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-sm"
        >
          <ArrowLeft className="size-4" />
          Back to listings
        </Link>

        {error && (
          <div className="border-destructive/40 bg-destructive/5 text-destructive mt-4 rounded-lg border px-4 py-3 text-sm">
            {error}
          </div>
        )}
        {done && !error && (
          <div className="mt-4 rounded-lg border border-green-600/30 bg-green-600/5 px-4 py-3 text-sm text-green-800">
            {done === "accepted"
              ? "Offer accepted. The listing is on hold and the other bidders have been declined — arrange the handover with the buyer below."
              : done === "withdrawn"
                ? "Your offer has been withdrawn."
                : done === "unlocked"
                  ? "Price check unlocked. Nothing was charged."
                  : "Your offer has been sent to the seller."}
          </div>
        )}

        <div className="mt-6 grid gap-8 md:grid-cols-[1.2fr_1fr]">
          <div>
            <ListingCover
              src={listing.primaryImageUrl}
              categoryName={listing.category.name}
              title={listing.title}
              priority
              className="rounded-xl border"
            />

            <h1 className="mt-6 text-3xl font-bold tracking-tight text-balance wrap-anywhere">
              {listing.title}
            </h1>
            <p className="text-muted-foreground mt-1.5 text-sm">
              {listing.category.name} · listed {relativeTime(listing.createdAt)}
            </p>

            <p className="mt-5 leading-relaxed text-pretty wrap-anywhere">
              {listing.description}
            </p>

            <div className="mt-8 border-t pt-6">
              <h2 className="font-semibold">Condition notes</h2>
              <p className="text-muted-foreground mt-1 mb-3 text-sm">
                Faults the seller has declared up front.
              </p>
              <DefectList defects={listing.defects} />
            </div>

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
          </div>

          <aside className="space-y-6">
            <div className="rounded-xl border p-5">
              <div className="flex items-baseline justify-between gap-3">
                <div className="text-3xl font-semibold">
                  ${Number(listing.price).toFixed(2)}
                  {isService && (
                    <span className="text-muted-foreground text-base font-normal">
                      /hr
                    </span>
                  )}
                </div>
                <Badge
                  variant={takingOffers ? "default" : "secondary"}
                  className="capitalize"
                >
                  {listing.status}
                </Badge>
              </div>

              <p className="text-muted-foreground mt-2 inline-flex items-center gap-1.5 text-sm">
                <Gavel className="size-4" />
                {listing.bids.count === 0
                  ? "No offers yet"
                  : `${listing.bids.count} ${listing.bids.count === 1 ? "offer" : "offers"}${
                      listing.bids.highest != null
                        ? ` · highest $${Number(listing.bids.highest).toFixed(2)}`
                        : ""
                    }`}
              </p>

              <dl className="mt-5 grid gap-4 border-t pt-5 sm:grid-cols-2">
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

              <div className="mt-5 border-t pt-5">
                {isSeller ? (
                  <p className="text-muted-foreground text-sm">
                    This is your listing. Offers appear below.
                  </p>
                ) : !takingOffers ? (
                  <p className="text-muted-foreground text-sm">
                    This listing is {listing.status} and is not taking offers.
                  </p>
                ) : viewerId ? (
                  <form action={submitBid} className="space-y-3">
                    <input type="hidden" name="listingId" value={listing.id} />
                    <label
                      htmlFor="amount"
                      className="block text-sm font-medium"
                    >
                      Make an offer
                    </label>
                    <Input
                      id="amount"
                      name="amount"
                      type="number"
                      step="0.01"
                      min="0.01"
                      required
                      placeholder={Number(listing.price).toFixed(2)}
                    />
                    <Textarea
                      name="message"
                      rows={2}
                      maxLength={500}
                      placeholder="Optional note — when you could collect, for instance"
                    />
                    <div className="flex gap-2">
                      <Button type="submit" className="flex-1">
                        Send offer
                      </Button>
                    </div>
                  </form>
                ) : (
                  <Button asChild className="w-full">
                    <Link href={`/auth/login?next=/listings/${listing.id}`}>
                      Sign in to make an offer
                    </Link>
                  </Button>
                )}

                {!isSeller && viewerId && takingOffers && (
                  <form action={retractBid} className="mt-2">
                    <input type="hidden" name="listingId" value={listing.id} />
                    <Button
                      type="submit"
                      variant="ghost"
                      size="sm"
                      className="w-full"
                    >
                      Withdraw my offer
                    </Button>
                  </form>
                )}
              </div>

              <div className="mt-5 border-t pt-5">
                <Button asChild variant="outline" className="w-full">
                  <a
                    href={`mailto:${listing.seller.email}?subject=${encodeURIComponent(`HokieHub: ${listing.title}`)}`}
                  >
                    Email the seller
                  </a>
                </Button>
              </div>
            </div>

            <PriceCheckPanel
              listingId={listing.id}
              askingPrice={Number(listing.price)}
              isSeller={isSeller}
              signedIn={viewerId !== null}
              status={pcStatus}
              check={priceCheck}
            />

            {isSeller && (
              <div className="rounded-xl border p-5">
                <h2 className="font-semibold">Offers</h2>
                <p className="text-muted-foreground mt-1 text-sm">
                  Only you can see who has bid.
                </p>

                {offers.length === 0 ? (
                  <p className="text-muted-foreground mt-4 text-sm">
                    Nobody has made an offer yet.
                  </p>
                ) : (
                  <ul className="mt-4 space-y-3">
                    {offers.map((offer) => (
                      <li key={offer.id} className="rounded-lg border p-3">
                        <div className="flex items-baseline justify-between gap-2">
                          <span className="font-semibold">
                            ${Number(offer.amount).toFixed(2)}
                            {offer.status === "accepted" && (
                              <Badge className="ml-2 align-middle">
                                accepted
                              </Badge>
                            )}
                          </span>
                          <span className="text-muted-foreground text-xs">
                            {relativeTime(offer.updatedAt)}
                          </span>
                        </div>
                        <p className="text-muted-foreground mt-0.5 truncate text-sm">
                          {offer.bidder.fullName}
                        </p>
                        {offer.message && (
                          <p className="mt-2 text-sm wrap-anywhere">
                            {offer.message}
                          </p>
                        )}
                        {offer.status === "accepted" ? (
                          <a
                            className="text-primary mt-3 inline-block text-sm underline"
                            href={`mailto:${offer.bidder.email}?subject=${encodeURIComponent(`HokieHub: ${listing.title}`)}`}
                          >
                            Email {offer.bidder.fullName.split(" ")[0]} to
                            arrange the handover
                          </a>
                        ) : (
                          <form action={takeOffer} className="mt-3">
                            <input
                              type="hidden"
                              name="listingId"
                              value={listing.id}
                            />
                            <input
                              type="hidden"
                              name="bidId"
                              value={offer.id}
                            />
                            <Button
                              type="submit"
                              size="sm"
                              variant="secondary"
                              className="w-full"
                            >
                              Accept this offer
                            </Button>
                          </form>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </aside>
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
      <dt className="text-muted-foreground text-xs tracking-wide uppercase">
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
