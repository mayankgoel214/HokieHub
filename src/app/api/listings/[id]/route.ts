import { NextRequest, NextResponse } from 'next/server'
import { getListingById, updateListing, deleteListing } from '@/lib/listings'
import { getAuthenticatedUser } from '@/lib/auth'
import { UpdateListingRequest } from '@/types/listings'

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params
    const listing = await getListingById(id)

    if (!listing) {
      return NextResponse.json(
        { error: 'Listing not found' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      success: true,
      listing,
    })
  } catch (error) {
    console.error('Error fetching listing:', error)
    return NextResponse.json(
      {
        error: 'Failed to fetch listing',
        details: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 500 }
    )
  }
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const user = await getAuthenticatedUser()
    if (!user) {
      return NextResponse.json(
        { error: 'Unauthorized. Please sign in.' },
        { status: 401 }
      )
    }

    const { id } = await params
    const body: UpdateListingRequest = await request.json()
    const listing = await updateListing(id, user.id, body)

    if (!listing) {
      return NextResponse.json(
        { error: 'Listing not found or you do not have permission to update it' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      success: true,
      listing,
      message: 'Listing updated successfully',
    })
  } catch (error) {
    console.error('Error updating listing:', error)
    return NextResponse.json(
      {
        error: 'Failed to update listing',
        details: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 500 }
    )
  }
}

export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const user = await getAuthenticatedUser()
    if (!user) {
      return NextResponse.json(
        { error: 'Unauthorized. Please sign in.' },
        { status: 401 }
      )
    }

    const { id } = await params
    const deleted = await deleteListing(id, user.id)

    if (!deleted) {
      return NextResponse.json(
        { error: 'Listing not found or you do not have permission to delete it' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      success: true,
      message: 'Listing deleted successfully',
    })
  } catch (error) {
    console.error('Error deleting listing:', error)
    return NextResponse.json(
      {
        error: 'Failed to delete listing',
        details: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 500 }
    )
  }
}
