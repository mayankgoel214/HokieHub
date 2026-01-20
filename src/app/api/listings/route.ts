import { NextRequest, NextResponse } from 'next/server'
import { getAuthenticatedUser } from '@/lib/auth'
import { createListing, getAllListings } from '@/lib/listings'
import { CreateListingRequest } from '@/types/listings'
import { ensureUserExists } from '@/lib/users'

export async function POST(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser()
    if (!user) {
      return NextResponse.json(
        { error: 'Unauthorized. Please sign in.' },
        { status: 401 }
      )
    }

    await ensureUserExists(user)

    const body: CreateListingRequest = await request.json()
    const listing = await createListing(user.id, body)

    return NextResponse.json(
      {
        success: true,
        listing,
        message: 'Listing created successfully',
      },
      { status: 201 }
    )
  } catch (error) {
    console.error('Error creating listing:', error)
    return NextResponse.json(
      {
        error: 'Failed to create listing',
        details: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 500 }
    )
  }
}

export async function GET(request: NextRequest) {
  try {
    const listings = await getAllListings()
    return NextResponse.json({ success: true, listings })
  } catch (error) {
    console.error('Error fetching listings:', error)
    return NextResponse.json(
      {
        error: 'Failed to fetch listings',
        details: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 500 }
    )
  }
}
