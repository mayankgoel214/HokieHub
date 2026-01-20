export type ListingType = 'item' | 'service'
export type ListingStatus = 'available' | 'pending' | 'sold' | 'unavailable'
export type ItemCondition = 'new' | 'like_new' | 'good' | 'fair' | 'poor'

export interface Listing {
  id: string
  seller_id: string
  category_id: number
  title: string
  description: string
  price: number
  condition?: ItemCondition
  listing_type: ListingType
  status: ListingStatus
  location?: string
  views_count: number
  created_at: string
  updated_at: string
  expires_at?: string
}

export interface CreateListingRequest {
  category_id: number
  title: string
  description: string
  price: number
  condition?: ItemCondition
  listing_type: ListingType
  location?: string
  expires_at?: string

  service_details?: {
    subjects?: string[]
    availability?: string
    hourly_rate?: number
    experience_level?: string
  }

  images?: {
    image_url: string
    is_primary?: boolean
    display_order?: number
  }[]
}

export interface UpdateListingRequest {
  category_id?: number
  title?: string
  description?: string
  price?: number
  condition?: ItemCondition
  status?: ListingStatus
  location?: string
  expires_at?: string
}
