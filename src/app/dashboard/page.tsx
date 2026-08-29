import { redirect } from "next/navigation";

/**
 * `/dashboard` used to be the public browse page, which was the wrong word for
 * it — a stranger arriving at a marketplace should not be told they are looking
 * at someone's dashboard. The old path stays as a redirect so links already
 * shared do not break.
 */
export default async function DashboardRedirect({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = await searchParams;
  redirect(q ? `/browse?q=${encodeURIComponent(q)}` : "/browse");
}
