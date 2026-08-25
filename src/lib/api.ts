import type {
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
