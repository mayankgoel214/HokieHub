import pool from '@/lib/db'
import { CreateListingRequest, Listing, UpdateListingRequest } from '@/types/listings'

export async function createListing(
  sellerId: string,
  data: CreateListingRequest
): Promise<Listing> {
  const client = await pool.connect()

  try {
    await client.query('BEGIN')

    const listingResult = await client.query(
      `INSERT INTO listings (
        seller_id, category_id, title, description, price,
        condition, listing_type, location, expires_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      RETURNING *`,
      [
        sellerId,
        data.category_id,
        data.title,
        data.description,
        data.price,
        data.condition || null,
        data.listing_type,
        data.location || null,
        data.expires_at || null,
      ]
    )

    const listing = listingResult.rows[0]

    if (data.listing_type === 'service' && data.service_details) {
      await client.query(
        `INSERT INTO service_details (
          listing_id, subjects, availability, hourly_rate, experience_level
        ) VALUES ($1, $2, $3, $4, $5)`,
        [
          listing.id,
          data.service_details.subjects || null,
          data.service_details.availability || null,
          data.service_details.hourly_rate || null,
          data.service_details.experience_level || null,
        ]
      )
    }

    if (data.images && data.images.length > 0) {
      for (const [index, image] of data.images.entries()) {
        await client.query(
          `INSERT INTO listing_images (
            listing_id, image_url, is_primary, display_order
          ) VALUES ($1, $2, $3, $4)`,
          [
            listing.id,
            image.image_url,
            image.is_primary ?? (index === 0),
            image.display_order ?? index,
          ]
        )
      }
    }

    await client.query('COMMIT')
    return listing
  } catch (error) {
    await client.query('ROLLBACK')
    throw error
  } finally {
    client.release()
  }
}

export async function getListingById(
  listingId: string
): Promise<Listing | null> {
  const result = await pool.query(
    `SELECT l.* FROM listings l WHERE l.id = $1`,
    [listingId]
  )

  if (result.rows.length === 0) {
    return null
  }

  return result.rows[0]
}

export async function getAllListings(): Promise<Listing[]> {
  const result = await pool.query(
    `SELECT l.* FROM listings l ORDER BY l.created_at DESC`
  )

  return result.rows
}

export async function updateListing(
  listingId: string,
  sellerId: string,
  data: UpdateListingRequest
): Promise<Listing | null> {
  const fields: string[] = []
  const values: any[] = []
  let paramCount = 1

  if (data.category_id !== undefined) {
    fields.push(`category_id = $${paramCount++}`)
    values.push(data.category_id)
  }
  if (data.title !== undefined) {
    fields.push(`title = $${paramCount++}`)
    values.push(data.title)
  }
  if (data.description !== undefined) {
    fields.push(`description = $${paramCount++}`)
    values.push(data.description)
  }
  if (data.price !== undefined) {
    fields.push(`price = $${paramCount++}`)
    values.push(data.price)
  }
  if (data.condition !== undefined) {
    fields.push(`condition = $${paramCount++}`)
    values.push(data.condition)
  }
  if (data.status !== undefined) {
    fields.push(`status = $${paramCount++}`)
    values.push(data.status)
  }
  if (data.location !== undefined) {
    fields.push(`location = $${paramCount++}`)
    values.push(data.location)
  }
  if (data.expires_at !== undefined) {
    fields.push(`expires_at = $${paramCount++}`)
    values.push(data.expires_at)
  }

  if (fields.length === 0) {
    const listing = await getListingById(listingId)
    return listing
  }

  fields.push(`updated_at = NOW()`)
  values.push(listingId, sellerId)

  const result = await pool.query(
    `UPDATE listings
     SET ${fields.join(', ')}
     WHERE id = $${paramCount} AND seller_id = $${paramCount + 1}
     RETURNING *`,
    values
  )

  if (result.rows.length === 0) {
    return null
  }

  return result.rows[0]
}

export async function deleteListing(
  listingId: string,
  sellerId: string
): Promise<boolean> {
  const result = await pool.query(
    `DELETE FROM listings WHERE id = $1 AND seller_id = $2 RETURNING id`,
    [listingId, sellerId]
  )

  return result.rows.length > 0
}
