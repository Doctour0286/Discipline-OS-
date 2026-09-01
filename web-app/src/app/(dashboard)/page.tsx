/**
 * Home page — Dashboard overview.
 * Shows current tier, active mission, recent violations, debt/reputation summary.
 * TODO(prompt4): Implement real dashboard with data from API.
 */
export default function HomePage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Dashboard</h1>
      <p className="mt-2 text-gray-600">
        DisciplineOS Console — your accountability overview.
      </p>
      <div className="mt-8 grid grid-cols-2 gap-4">
        <div className="rounded border p-4">
          <h2 className="font-semibold">Current Tier</h2>
          <p className="text-3xl font-bold mt-2">—</p>
          <p className="text-sm text-gray-500">PRD §12</p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Active Mission</h2>
          <p className="text-gray-500 mt-2">No active mission</p>
          <p className="text-sm text-gray-500">PRD §9</p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Debt</h2>
          <p className="text-3xl font-bold mt-2">0</p>
          <p className="text-sm text-gray-500">PRD §27 — Debt Ceiling</p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Reputation</h2>
          <p className="text-3xl font-bold mt-2">0</p>
          <p className="text-sm text-gray-500">PRD §35</p>
        </div>
      </div>
    </div>
  );
}
