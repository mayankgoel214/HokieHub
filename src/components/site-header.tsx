import Link from "next/link";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { signout } from "@/app/auth/actions";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";

/**
 * Every page had been an island: no logo, no way back to the marketplace, no
 * route to signing in. A visitor who landed on a listing from a shared link had
 * nowhere to go but the back button.
 *
 * It also has to know who is signed in. It previously showed "Sign in" to
 * everyone, including people who already were, and offered no way to sign out
 * at all — the action existed and nothing ever called it.
 */
export async function SiteHeader() {
  let email: string | null = null;

  if (isSupabaseConfigured()) {
    const supabase = await createClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    email = user?.email ?? null;
  }

  return (
    <header className="bg-background/80 sticky top-0 z-50 border-b backdrop-blur">
      <div className="container mx-auto flex h-14 items-center gap-4 px-4">
        <Link href="/" className="flex shrink-0 items-center gap-2">
          {/* The VT-maroon monogram doubles as the favicon mark. */}
          <span className="bg-primary text-primary-foreground grid size-7 place-items-center rounded-md text-sm font-bold">
            H
          </span>
          <span className="hidden font-semibold tracking-tight sm:inline">
            HokieHub
          </span>
        </Link>

        <nav className="text-muted-foreground flex items-center gap-4 text-sm">
          <Link
            href="/browse"
            className="hover:text-foreground transition-colors"
          >
            Browse
          </Link>
          {email && (
            <Link
              href="/browse/mine"
              className="hover:text-foreground transition-colors"
            >
              My listings
            </Link>
          )}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {email ? (
            <>
              {/* The local part is enough to say which account this is, and it
                  keeps a long address from crowding out the buttons. */}
              <span
                className="text-muted-foreground hidden max-w-[14rem] truncate text-sm sm:inline"
                title={email}
              >
                {email.split("@")[0]}
              </span>
              <form action={signout}>
                <Button type="submit" variant="ghost" size="sm">
                  Sign out
                </Button>
              </form>
            </>
          ) : (
            <Button asChild variant="ghost" size="sm">
              <Link href="/auth/login">Sign in</Link>
            </Button>
          )}
          <Button asChild size="sm">
            <Link href="/browse/new">
              <Plus className="size-4" />
              <span className="hidden sm:inline">Post a listing</span>
              <span className="sm:hidden">Post</span>
            </Link>
          </Button>
        </div>
      </div>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer className="mt-16 border-t">
      <div className="text-muted-foreground container mx-auto flex flex-col gap-2 px-4 py-8 text-sm sm:flex-row sm:items-center sm:justify-between">
        <p className="text-pretty">
          Every account is verified against an @vt.edu address, so you are
          trading with someone actually on campus.
        </p>
        <a
          href="https://github.com/mayankgoel214/HokieHub"
          className="hover:text-foreground shrink-0 transition-colors"
          target="_blank"
          rel="noreferrer"
        >
          Source on GitHub
        </a>
      </div>
    </footer>
  );
}
