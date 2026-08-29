import type {
  Bid,
  DefectInput,
  PriceCheck,
  PriceCheckStatusInfo,
  BidSummary,
  Category,
  CreateListingBody,
  Listing,
  Page,
  ProblemDetail,
} from "@/types/api";

/**
 * Client for the Spring Boot API.
 *
 * The API is a separate service, so requests carry the Supabase access token as
 * a bearer rather than relying on the session cookie — the cookie is scoped to
 * the Next.js origin and would not reach it.
 */
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(
  path: string,
  options: RequestInit & { token?: string } = {},
): Promise<T> {
  const { token, headers, ...rest } = options;

  const response = await fetch(`${API_URL}${path}`, {
    ...rest,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    const problem = body as ProblemDetail | null;
    // Field-level validation failures arrive under `errors`; surface the first
    // one, since that is what a form needs to show.
    const firstFieldError = problem?.errors
      ? Object.values(problem.errors)[0]
      : undefined;
    throw new ApiError(
      firstFieldError ??
        problem?.detail ??
        `Request failed (${response.status})`,
      response.status,
      problem?.errors,
    );
  }

  return body as T;
}

export function listListings(
  params: {
    page?: number;
    size?: number;
    categoryId?: number;
    status?: string;
    listingType?: string;
    q?: string;
  } = {},
): Promise<Page<Listing>> {
  const query = new URLSearchParams(
    Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => [k, String(v)]),
  );
  const suffix = query.toString() ? `?${query}` : "";
  return request<Page<Listing>>(`/api/listings${suffix}`);
}

export function getListing(id: string): Promise<Listing> {
  return request<Listing>(`/api/listings/${id}`);
}

export function listCategories(): Promise<Category[]> {
  return request<Category[]>("/api/categories");
}

export function createListing(
  body: CreateListingBody,
  token: string,
): Promise<Listing> {
  return request<Listing>("/api/listings", {
    method: "POST",
    body: JSON.stringify(body),
    token,
  });
}

export function updateListing(
  id: string,
  body: Partial<CreateListingBody> & {
    status?: string;
    defects?: DefectInput[];
  },
  token: string,
): Promise<Listing> {
  return request<Listing>(`/api/listings/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
    token,
  });
}

export function deleteListingImage(
  listingId: string,
  imageId: number,
  token: string,
): Promise<void> {
  return request<void>(`/api/listings/${listingId}/images/${imageId}`, {
    method: "DELETE",
    token,
  });
}

export function deleteListing(id: string, token: string): Promise<void> {
  return request<void>(`/api/listings/${id}`, { method: "DELETE", token });
}

export function placeBid(
  listingId: string,
  body: { amount: number; message?: string },
  token: string,
): Promise<Bid> {
  return request<Bid>(`/api/listings/${listingId}/bids`, {
    method: "PUT",
    body: JSON.stringify(body),
    token,
  });
}

export function withdrawBid(listingId: string, token: string): Promise<void> {
  return request<void>(`/api/listings/${listingId}/bids`, {
    method: "DELETE",
    token,
  });
}

/** Seller only. Everyone else gets the count via the listing itself. */
export function listBids(listingId: string, token: string): Promise<Bid[]> {
  return request<Bid[]>(`/api/listings/${listingId}/bids`, { token });
}

export function acceptBid(
  listingId: string,
  bidId: string,
  token: string,
): Promise<Bid> {
  return request<Bid>(`/api/listings/${listingId}/bids/${bidId}/accept`, {
    method: "POST",
    token,
  });
}

export function bidSummary(listingId: string): Promise<BidSummary> {
  return request<BidSummary>(`/api/listings/${listingId}/bids/summary`);
}

/**
 * Uploads a photograph. Multipart, so this bypasses the JSON `request` helper
 * above — setting Content-Type by hand on a FormData body would strip the
 * multipart boundary the browser generates, and the upload would arrive
 * unparseable.
 */
export async function uploadListingImage(
  listingId: string,
  file: File,
  token: string,
): Promise<{ id: number; url: string; isPrimary: boolean }> {
  const body = new FormData();
  body.append("file", file);

  const response = await fetch(`${API_URL}/api/listings/${listingId}/images`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body,
  });

  const parsed = await response.json().catch(() => null);
  if (!response.ok) {
    const problem = parsed as ProblemDetail | null;
    throw new ApiError(
      problem?.detail ?? `Upload failed (${response.status})`,
      response.status,
    );
  }
  return parsed;
}

export function priceCheckStatus(
  listingId: string,
  token?: string,
): Promise<PriceCheckStatusInfo> {
  return request<PriceCheckStatusInfo>(
    `/api/listings/${listingId}/price-check/status`,
    { token },
  );
}

export function unlockPriceCheck(
  listingId: string,
  token: string,
): Promise<void> {
  return request<void>(`/api/listings/${listingId}/price-check/unlock`, {
    method: "POST",
    token,
  });
}

export function getPriceCheck(
  listingId: string,
  token: string,
): Promise<PriceCheck> {
  return request<PriceCheck>(`/api/listings/${listingId}/price-check`, {
    token,
  });
}

export function myListings(
  token: string,
  page = 0,
  size = 20,
): Promise<Page<Listing>> {
  return request<Page<Listing>>(
    `/api/listings/mine?page=${page}&size=${size}`,
    { token },
  );
}
