/**
 * Missions page — list and manage enforcement sessions.
 * PRD §9 — Mission lifecycle (observe → hypothesize → enforce → review).
 * TODO(prompt4): Implement mission list, create, detail views.
 */
export default function MissionsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Missions</h1>
      <p className="mt-2 text-gray-600">
        Manage your enforcement sessions. PRD §9.
      </p>
      <div className="mt-8">
        <p className="text-gray-500">
          TODO: Mission list, create new mission, mission detail views.
          Port from <code>web-app-reference/app/.../mission/</code> in Prompt 4.
        </p>
      </div>
    </div>
  );
}
