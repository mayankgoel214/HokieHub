import Image from "next/image";

/**
 * A listing's picture.
 *
 * Every cover is served from this app rather than an image host, so a card can
 * never render as a broken box because somebody else's CDN went down or hotlink
 * protection kicked in. Listings without a picture of their own fall back to a
 * cover chosen from the category, which is a real answer rather than a grey
 * rectangle with the word "image" in it.
 */
const BY_CATEGORY: Array<[RegExp, string]> = [
  [
    /textbook|engineering|business|science|mathematic|liberal arts|school suppl/i,
    "textbook",
  ],
  [/bike|bicycle|scooter|transport/i, "bike"],
  [/laptop|computer|monitor|phone|tablet|camera|gaming|electronic/i, "monitor"],
  [/audio|headphone|speaker/i, "headphones"],
  [/desk|chair|bed|mattress|furniture|storage/i, "desk"],
  [/kitchen|dining|appliance|coffee/i, "coffee"],
  [/ticket|event/i, "tickets"],
  [/tutor|service|lesson/i, "tutoring"],
  [/lamp|decor|light/i, "lamp"],
  [/cloth|apparel|shoe|accessor/i, "clothing"],
  [/sport|recreation|outdoor/i, "bike"],
];

export function coverFor(categoryName: string, title: string): string {
  const haystack = `${categoryName} ${title}`;
  for (const [pattern, name] of BY_CATEGORY) {
    if (pattern.test(haystack)) return `/covers/${name}.svg`;
  }
  return "/covers/generic.svg";
}

export function ListingCover({
  src,
  categoryName,
  title,
  priority = false,
  className = "",
}: {
  src?: string | null;
  categoryName: string;
  title: string;
  priority?: boolean;
  className?: string;
}) {
  const resolved =
    src && src.trim() !== "" ? src : coverFor(categoryName, title);

  return (
    <div
      className={`bg-muted relative aspect-[4/3] w-full overflow-hidden ${className}`}
    >
      <Image
        src={resolved}
        alt={title}
        fill
        // Cards sit in a four-up grid on desktop and full width on a phone;
        // telling the browser that up front stops it fetching a 400px-wide
        // image to paint 200 points of it.
        sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
        className="object-cover transition-transform duration-300 group-hover:scale-[1.03]"
        priority={priority}
      />
    </div>
  );
}
