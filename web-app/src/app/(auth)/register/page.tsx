/**
 * Register page — create a new account.
 * Design doc §3.1: "User creates an account on the Console (email + password)."
 * TODO(prompt4): Implement actual registration with password hashing.
 */
export default function RegisterPage() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-96 rounded border p-8">
        <h1 className="text-2xl font-bold text-center">Create Account</h1>
        <p className="mt-2 text-center text-gray-600">
          Set up your DisciplineOS Console account
        </p>
        <form className="mt-6 space-y-4">
          <div>
            <label className="block text-sm font-medium">Email</label>
            <input
              type="email"
              className="mt-1 w-full rounded border px-3 py-2"
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label className="block text-sm font-medium">Password</label>
            <input
              type="password"
              className="mt-1 w-full rounded border px-3 py-2"
              placeholder="••••••••"
            />
          </div>
          <button
            type="submit"
            className="w-full rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Create Account
          </button>
        </form>
      </div>
    </div>
  );
}
