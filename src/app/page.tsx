import Link from 'next/link'
import { ArrowRight, BadgeCheck, BookOpen, Sofa, GraduationCap } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { listListings } from '@/lib/api'

/**
 * The first thing a visitor sees. Previously this redirected straight to the
 * login form, which meant nobody could tell what HokieHub was without an
 * account — and nobody makes an account for something they cannot see.
 */
export default async function Home() {
  let liveCount = 0
  try {
    liveCount = (await listListings({ size: 1 })).totalElements
  } catch {
    // The marketplace copy stands on its own if the API is unreachable.
  }

  return (
    <main className="min-h-screen bg-background">
      <section className="container mx-auto px-4 py-20 sm:py-28">
        <div className="mx-auto max-w-2xl text-center">
          <div className="text-muted-foreground mb-5 inline-flex items-center gap-2 rounded-full border px-4 py-1.5 text-sm">
            <BadgeCheck className="size-4 shrink-0" />
            <span className="min-w-0">Verified @vt.edu accounts only</span>
          </div>

          <h1 className="text-4xl font-bold tracking-tight text-balance sm:text-5xl">
            Buy and sell within Virginia Tech.
          </h1>

          <p className="text-muted-foreground mx-auto mt-5 max-w-xl text-lg text-pretty">
            Textbooks, dorm furniture, monitors, tutoring — traded between students
            on the same campus, instead of with strangers on Facebook Marketplace.
          </p>

          <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Link href="/browse">
              <Button size="lg" className="w-full sm:w-auto">
                Browse listings
                <ArrowRight className="size-5" />
              </Button>
            </Link>
            <Link href="/auth/signup">
              <Button size="lg" variant="outline" className="w-full sm:w-auto">
                Sell something
              </Button>
            </Link>
          </div>

          {liveCount > 0 && (
            <p className="text-muted-foreground mt-5 text-sm">
              {liveCount} {liveCount === 1 ? 'listing' : 'listings'} on the marketplace
            </p>
          )}
        </div>
      </section>

      <section className="border-t bg-muted/30">
        <div className="container mx-auto grid gap-8 px-4 py-16 sm:grid-cols-3">
          {[
            {
              icon: BookOpen,
              title: 'Course materials',
              body: 'Textbooks priced by the students who just finished the class, not the bookstore.',
            },
            {
              icon: Sofa,
              title: 'Move-out season',
              body: 'Furniture and appliances that would otherwise go to a dumpster in May.',
            },
            {
              icon: GraduationCap,
              title: 'Services, not just stuff',
              body: 'Tutoring and other help, with subjects, availability and an hourly rate.',
            },
          ].map(({ icon: Icon, title, body }) => (
            <div key={title} className="min-w-0">
              <Icon className="text-muted-foreground size-6" />
              <h2 className="mt-3 font-semibold">{title}</h2>
              <p className="text-muted-foreground mt-1.5 text-sm text-pretty">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="border-t">
        <div className="text-muted-foreground container mx-auto px-4 py-8 text-sm">
          Every account is verified against an @vt.edu address, so you are trading
          with someone who is actually on campus.
        </div>
      </footer>
    </main>
  )
}
