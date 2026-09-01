/**
 * Reports page — Daily, Weekly, Monthly intelligence reports.
 * PRD §32 — Daily Report (discipline score, reliability, debt, violations).
 * PRD §33 — Weekly Report (mission success rate, dangerous hour/app, trends).
 * PRD §34 — Monthly Intelligence Report (trajectory, behavioral fingerprint, predictions).
 * TODO(prompt4): Implement report generation, display, date range selection.
 */
export default function ReportsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Reports</h1>
      <p className="mt-2 text-gray-600">
        Your accountability intelligence reports.
      </p>
      <div className="mt-8 grid grid-cols-3 gap-4">
        <div className="rounded border p-4">
          <h2 className="font-semibold">Daily Report</h2>
          <p className="text-sm text-gray-500 mt-1">PRD §32</p>
          <p className="text-sm text-gray-400 mt-2">
            Discipline score, reliability, debt, violations, tier changes.
          </p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Weekly Report</h2>
          <p className="text-sm text-gray-500 mt-1">PRD §33</p>
          <p className="text-sm text-gray-400 mt-2">
            Mission success rate, dangerous hour/app, attention fragmentation.
          </p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Monthly Report</h2>
          <p className="text-sm text-gray-500 mt-1">PRD §34</p>
          <p className="text-sm text-gray-400 mt-2">
            Discipline trajectory, behavioral fingerprint, prediction accuracy.
          </p>
        </div>
      </div>
    </div>
  );
}
