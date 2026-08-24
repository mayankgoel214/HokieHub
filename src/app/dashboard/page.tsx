import Link from 'next/link'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { listListings } from '@/lib/api'
import type { Listing } from '@/types/api'

async function getListings(): Promise<Listing[]> {
  try {
    // Browsing is public, so this needs no token. The API pages its results;
    // the dashboard shows the first page.
    const page = await listListings({ size: 24 })
    return page.content
  } catch (error) {
    console.error('Error fetching listings:', error)
    return []
  }
}

export default async function DashboardPage() {
  const listings = await getListings()

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
            <p className="text-muted-foreground mt-1">
              Browse all available listings
            </p>
          </div>
          <Link href="/dashboard/create">
            <Button size="lg">
              <Plus className="size-5" />
              Create Listing
            </Button>
          </Link>
        </div>

        {listings.length === 0 ? (
          <div className="flex min-h-[400px] items-center justify-center rounded-lg border-2 border-dashed">
            <div className="text-center">
              <h3 className="text-lg font-semibold">No listings found</h3>
              <p className="text-muted-foreground mt-2">
                Get started by creating your first listing
              </p>
              <Link href="/dashboard/create">
                <Button className="mt-4">
                  <Plus className="size-5" />
                  Create Listing
                </Button>
              </Link>
            </div>
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {listings.map((listing) => (
              <Card key={listing.id} className="flex flex-col">
                <CardHeader>
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <div className="flex items-baseline gap-2">
                      <span className="text-sm font-semibold">
                        ${Number(listing.price).toFixed(2)}
                      </span>
                      {listing.listingType === 'service' && (
                        <span className="text-muted-foreground text-xs">
                          /hr
                        </span>
                      )}
                    </div>
                    <Badge>
                      {listing.status}
                    </Badge>
                  </div>
                  <CardTitle className="line-clamp-2 text-xl">
                    {listing.title}
                  </CardTitle>
                  <CardDescription className="line-clamp-3 text-base">
                    {listing.description}
                  </CardDescription>
                </CardHeader>
                <CardContent className="flex-1">
                  <div className="space-y-2">
                    {listing.condition && (
                      <div className="text-sm">
                        <span className="text-muted-foreground">
                          Condition:
                        </span>{' '}
                        <span className="font-medium capitalize">
                          {listing.condition.replace('_', ' ')}
                        </span>
                      </div>
                    )}
                    {listing.location && (
                      <div className="text-muted-foreground text-sm">
                        {listing.location}
                      </div>
                    )}
                  </div>
                </CardContent>
                <CardFooter className="border-t">
                  <div className="flex w-full items-center justify-between text-xs text-muted-foreground">
                    <span>{listing.viewsCount} views</span>
                    <span>
                      {new Date(listing.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </CardFooter>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
