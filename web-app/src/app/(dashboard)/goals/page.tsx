/**
 * Goals page — manage goal missions and their lifecycle.
 * PRD §8 — Goal definition, lifecycle stages (OBSERVING → HYPOTHESIZING → ENFORCING → REVIEWING).
 * TODO(prompt4): Implement goal list, create, detail views.
 */
export default function GoalsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Goals</h1>
      <p className="mt-2 text-gray-600">
        Define and track your accountability goals. PRD §8.
      </p>
      <div className="mt-8">
        <p className="text-gray-500">
          TODO: Goal list, create new goal, lifecycle stage management, milestones, triggers.
          Port from <code>web-app-reference/app/.../onboarding/</code> in Prompt 4.
        </p>
      </div>
    </div>
  );
}
