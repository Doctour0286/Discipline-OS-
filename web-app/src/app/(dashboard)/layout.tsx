import Link from "next/link";

/**
 * Dashboard layout — sidebar navigation for all Console features.
 * Each link maps to a PRD section.
 */
export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen">
      <nav className="w-64 border-r bg-gray-50 p-4">
        <h1 className="mb-8 text-lg font-bold">DisciplineOS Console</h1>
        <ul className="space-y-2">
          <li>
            <Link href="/" className="block rounded px-3 py-2 hover:bg-gray-200">
              Home
            </Link>
          </li>
          <li>
            <Link href="/missions" className="block rounded px-3 py-2 hover:bg-gray-200">
              Missions
            </Link>
          </li>
          <li>
            <Link href="/goals" className="block rounded px-3 py-2 hover:bg-gray-200">
              Goals
            </Link>
          </li>
          <li>
            <Link href="/tier" className="block rounded px-3 py-2 hover:bg-gray-200">
              Tier Status
            </Link>
          </li>
          <li>
            <Link href="/tribunal" className="block rounded px-3 py-2 hover:bg-gray-200">
              Tribunal
            </Link>
          </li>
          <li>
            <Link href="/reports" className="block rounded px-3 py-2 hover:bg-gray-200">
              Reports
            </Link>
          </li>
          <li>
            <Link href="/settings" className="block rounded px-3 py-2 hover:bg-gray-200">
              Settings
            </Link>
          </li>
        </ul>
      </nav>
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
}
