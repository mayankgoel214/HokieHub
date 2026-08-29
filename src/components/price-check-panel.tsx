import { ExternalLink, Lock, ScanSearch, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { buyPriceCheck } from "@/app/listings/[id]/price-check-actions";
import type { PriceCheck, PriceCheckStatusInfo } from "@/types/api";

/**
 * What the item is actually worth, according to comparable ones that were
 * really listed.
 *
 * The sources are shown next to the number rather than tucked away, because the
 * number is only worth anything if the buyer can check it. When the search finds
 * nothing there is no number at all — the panel says so, which is a useful
 * answer, and a good deal more honest than a confident guess.
 */
const VERDICT_COPY: Record<string, { label: string; className: string }> = {
  below_market: {
    label: "Below the going rate",
    className: "bg-green-600/10 text-green-800 border-green-600/30",
  },
  fair: {
    label: "About right",
    className: "bg-muted text-foreground border-border",
  },
  above_market: {
    label: "Above the going rate",
    className: "bg-amber-500/10 text-amber-800 border-amber-500/30",
  },
};

export function PriceCheckPanel({
  listingId,
  askingPrice,
  isSeller,
  signedIn,
  status,
  check,
}: {
  listingId: string;
  askingPrice: number;
  isSeller: boolean;
  signedIn: boolean;
  status: PriceCheckStatusInfo | null;
  check: PriceCheck | null;
}) {
  // Not configured on this deployment — say nothing rather than offer something
  // that cannot work.
  if (!status?.available) return null;

  const price = (status.priceCents / 100).toFixed(2);

  return (
    <div id="price-check" className="rounded-xl border p-5">
      <div className="flex items-start gap-2">
        <ScanSearch className="text-primary mt-0.5 size-5 shrink-0" />
        <div className="min-w-0">
          <h2 className="font-semibold">What is it actually worth?</h2>
          <p className="text-muted-foreground mt-1 text-sm text-pretty">
            An independent read from the photographs and comparable used
            listings — not from the seller.
          </p>
        </div>
      </div>

      {isSeller ? (
        <p className="text-muted-foreground mt-4 border-t pt-4 text-sm">
          This is for buyers. You set the price on this listing.
        </p>
      ) : !status.unlocked ? (
        <div className="mt-4 border-t pt-4">
          <ul className="text-muted-foreground space-y-1.5 text-sm">
            <li>· What the item is, read from the photographs</li>
            <li>· A fair second-hand range for it</li>
            <li>· The comparable listings behind that range</li>
          </ul>

          {signedIn ? (
            <form action={buyPriceCheck} className="mt-4">
              <input type="hidden" name="listingId" value={listingId} />
              <Button type="submit" className="w-full">
                <Lock className="size-4" />
                Unlock for ${price}
              </Button>
            </form>
          ) : (
            <Button asChild className="mt-4 w-full">
              <a href={`/auth/login?next=/listings/${listingId}`}>
                Sign in to unlock
              </a>
            </Button>
          )}

          <p className="text-muted-foreground mt-2 text-center text-xs">
            No payment is taken — this build charges nothing.
          </p>
        </div>
      ) : check === null ? (
        <p className="text-muted-foreground mt-4 border-t pt-4 text-sm">
          Working out a price for this one…
        </p>
      ) : check.status === "ready" ? (
        <div className="mt-4 space-y-4 border-t pt-4">
          {check.identifiedItem && (
            <div>
              <p className="text-muted-foreground text-xs tracking-wide uppercase">
                Identified as
              </p>
              <p className="mt-1 text-sm font-medium wrap-anywhere">
                {check.identifiedItem}
              </p>
            </div>
          )}

          <div>
            <p className="text-muted-foreground text-xs tracking-wide uppercase">
              Typical second-hand price
            </p>
            <p className="mt-1 text-2xl font-semibold">
              ${Number(check.estimatedLow).toFixed(0)} – $
              {Number(check.estimatedHigh).toFixed(0)}
            </p>
            {check.verdict && (
              <span
                className={`mt-2 inline-block rounded-md border px-2 py-1 text-xs font-medium ${
                  VERDICT_COPY[check.verdict]?.className ?? ""
                }`}
              >
                Asking ${askingPrice.toFixed(2)} ·{" "}
                {VERDICT_COPY[check.verdict]?.label ?? check.verdict}
              </span>
            )}
          </div>

          {check.summary && (
            <p className="text-sm leading-relaxed text-pretty wrap-anywhere">
              {check.summary}
            </p>
          )}

          {check.sources.length > 0 && (
            <div>
              <p className="text-muted-foreground text-xs tracking-wide uppercase">
                Comparable listings found
              </p>
              <ul className="mt-2 space-y-2">
                {check.sources.map((source, i) => (
                  <li key={i} className="text-sm">
                    <div className="flex items-baseline justify-between gap-3">
                      {source.url ? (
                        <a
                          href={source.url}
                          target="_blank"
                          rel="noreferrer nofollow"
                          className="hover:text-primary inline-flex min-w-0 items-center gap-1 underline underline-offset-2"
                        >
                          <span className="truncate">{source.title}</span>
                          <ExternalLink className="size-3 shrink-0" />
                        </a>
                      ) : (
                        <span className="truncate">{source.title}</span>
                      )}
                      {source.price != null && (
                        <span className="shrink-0 font-medium">
                          ${Number(source.price).toFixed(0)}
                        </span>
                      )}
                    </div>
                    {source.note && (
                      <p className="text-muted-foreground text-xs">
                        {source.note}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <p className="text-muted-foreground border-t pt-3 text-xs text-pretty">
            Estimated by {check.model} from the listings above. It is a guide,
            not an appraisal.
          </p>
        </div>
      ) : check.status === "no_comparables" ? (
        <div className="mt-4 space-y-3 border-t pt-4">
          <div className="flex items-start gap-2 text-sm">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" />
            <p className="text-pretty">
              No comparable sales were found for this one, so there is no price
              to quote. We would rather say that than show a number with nothing
              behind it.
            </p>
          </div>
          {check.identifiedItem && (
            <p className="text-muted-foreground text-sm">
              Identified as {check.identifiedItem}.
            </p>
          )}
          {check.summary && (
            <p className="text-sm leading-relaxed text-pretty">
              {check.summary}
            </p>
          )}
        </div>
      ) : (
        <div className="mt-4 border-t pt-4 text-sm">
          <p className="text-pretty">
            The valuation could not be completed. Nothing was charged.
          </p>
          {check.failureReason && (
            <p className="text-muted-foreground mt-1 text-xs">
              {check.failureReason}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
