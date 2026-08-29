/**
 * The HokieHub API contract, as served by the Spring Boot service.
 *
 * These are camelCase and nest the seller and category, unlike the flat
 * snake_case rows the old Next.js route handlers returned straight from SQL.
 */

export type ListingType = "item" | "service";
export type ListingStatus = "available" | "pending" | "sold" | "unavailable";
export type ItemCondition = "new" | "like_new" | "good" | "fair" | "poor";

export interface SellerSummary {
  id: string;
  fullName: string;
  email: string;
}

export interface CategorySummary {
  id: number;
  name: string;
  parentId: number | null;
}

export interface ListingImage {
  id: number;
  imageUrl: string;
  isPrimary: boolean;
  displayOrder: number;
}

export interface ServiceDetails {
  subjects: string[] | null;
  availability: string | null;
  hourlyRate: number | null;
  experienceLevel: string | null;
}

export type DefectSeverity = 'minor' | 'moderate' | 'major'

export interface Defect {
  id: number
  description: string
  severity: DefectSeverity
}

/** How much interest a listing has, without saying who from. */
export interface BidSummary {
  count: number
  highest: number | null
}

export interface Bid {
  id: string
  bidder: SellerSummary
  amount: number
  message: string | null
  status: 'active' | 'withdrawn' | 'accepted' | 'declined'
  createdAt: string
  updatedAt: string
}

export type PriceCheckStatus = "ready" | "no_comparables" | "failed";

export interface PriceCheckSource {
  title: string;
  url: string | null;
  price: number | null;
  note: string | null;
}

export interface PriceCheck {
  status: PriceCheckStatus;
  identifiedItem: string | null;
  estimatedLow: number | null;
  estimatedTypical: number | null;
  estimatedHigh: number | null;
  verdict: "below_market" | "fair" | "above_market" | null;
  summary: string | null;
  failureReason: string | null;
  /** True only when Google Search actually returned results behind the estimate. */
  grounded: boolean;
  model: string | null;
  sources: PriceCheckSource[];
  createdAt: string;
}

export interface PriceCheckStatusInfo {
  available: boolean;
  priceCents: number;
  unlocked: boolean;
}

export interface Listing {
  id: string;
  seller: SellerSummary;
  category: CategorySummary;
  title: string;
  description: string;
  price: number;
  condition: ItemCondition | null;
  listingType: ListingType;
  status: ListingStatus;
  location: string | null;
  viewsCount: number;
  serviceDetails: ServiceDetails | null;
  images: ListingImage[];
  primaryImageUrl: string | null;
  defects: Defect[];
  bids: BidSummary;
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
}

/** Every list endpoint returns this envelope rather than a bare array. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: number;
  name: string;
  description: string | null;
  icon: string | null;
  children: Category[];
}

export interface DefectInput {
  description: string
  severity: DefectSeverity
}

export interface CreateListingBody {
  categoryId: number;
  title: string;
  description: string;
  price: number;
  condition?: ItemCondition;
  listingType: ListingType;
  location?: string;
  expiresAt?: string;
  images?: { imageUrl: string; isPrimary?: boolean; displayOrder?: number }[];
  defects?: DefectInput[];
}

/** RFC 7807 problem+json, which is what the API returns for every error. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
