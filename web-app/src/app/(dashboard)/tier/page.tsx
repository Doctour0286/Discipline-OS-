/**
 * Tier Status page — view current tier, upgrade/downgrade history, calibration.
 * PRD §12 — Adaptive Tiered Enforcement System (Recruit/Operator/Warden/Iron).
 * TODO(prompt4): Implement tier display, upgrade path, calibration gate.
 */
export default function TierPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Tier Status</h1>
      <p className="mt-2 text-gray-600">
        Your enforcement tier and progression. PRD §12.
      </p>
      <div className="mt-8 grid grid-cols-4 gap-4">
        {(["RECRUIT", "OPERATOR", "WARDEN", "IRON"] as const).map((tier) => (
          <div key={tier} className="rounded border p-4 text-center">
            <h2 className="font-semibold">{tier}</h2>
            <p className="text-sm text-gray-500 mt-1">PRD §12</p>
          </div>
        ))}
      </div>
      <div className="mt-8">
        <p className="text-gray-500">
          TODO: Tier history, upgrade recommendations, downgrade signals,
          Iron calibration gate (10-day window). Port from
          <code>web-app-reference/app/.../onboarding/TierSelectionFragment</code> in Prompt 4.
        </p>
      </div>
    </div>
  );
}
