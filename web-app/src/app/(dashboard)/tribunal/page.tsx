/**
 * Tribunal page — structured review after failed missions.
 * PRD §30 — The Tribunal (mandatory at Warden/Iron, self-initiated at Recruit/Operator).
 * PRD §30.1 — Framing Discipline (all AI output uses Recalibration Voice, not Warden Voice).
 * TODO(prompt4): Implement Tribunal flow, Behavioral Correction Plan generation.
 */
export default function TribunalPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Tribunal</h1>
      <p className="mt-2 text-gray-600">
        Structured review after a failed mission. PRD §30.
      </p>
      <div className="mt-8">
        <p className="mb-4 text-gray-500">
          TODO: Tribunal session list, create new review, AI-generated Behavioral Correction Plan.
          All AI output must use Recalibration Voice (§22.2), never Warden Voice (§22.1).
        </p>
        <p className="text-sm text-gray-400">
          Mandatory at Warden/Iron tiers after Severe/Critical violations.
          Self-initiated at Recruit/Operator.
        </p>
      </div>
    </div>
  );
}
