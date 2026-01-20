import pool from '@/lib/db'
import { User } from '@supabase/supabase-js'

export async function syncUserToDatabase(user: User): Promise<void> {
  try {
    const userId = user.id
    const email = user.email || ''
    const fullName = user.user_metadata?.full_name || email.split('@')[0]

    const existingUser = await pool.query(
      'SELECT id FROM users WHERE id = $1',
      [userId]
    )

    if (existingUser.rows.length > 0) {
      await pool.query(
        'UPDATE users SET email = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
        [email, userId]
      )
    } else {
      await pool.query(
        `INSERT INTO users (id, email, full_name, created_at, updated_at)
         VALUES ($1, $2, $3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`,
        [userId, email, fullName]
      )
    }
  } catch (error) {
    console.error('Error syncing user to database:', error)
    throw error
  }
}

export async function getUserById(userId: string) {
  const result = await pool.query('SELECT * FROM users WHERE id = $1', [
    userId,
  ])

  if (result.rows.length === 0) {
    return null
  }

  return result.rows[0]
}

export async function ensureUserExists(user: User): Promise<void> {
  const existingUser = await getUserById(user.id)

  if (!existingUser) {
    await syncUserToDatabase(user)
  }
}
