import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'
import { syncUserToDatabase } from '@/lib/users'

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url)
  const code = searchParams.get('code')
  const next = searchParams.get('next') ?? '/'

  if (code) {
    const supabase = await createClient()
    const { error } = await supabase.auth.exchangeCodeForSession(code)

    if (!error) {
      try {
        const {
          data: { user },
        } = await supabase.auth.getUser()

        if (user) {
          await syncUserToDatabase(user)
        }
      } catch (syncError) {
        console.error('Error syncing user to database:', syncError)
      }

      return NextResponse.redirect(new URL(next, request.url))
    }
  }

  return NextResponse.redirect(new URL('/auth/login?error=verification_failed', request.url))
}
