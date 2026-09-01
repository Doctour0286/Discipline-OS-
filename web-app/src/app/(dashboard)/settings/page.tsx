/**
 * Settings page — account, paired devices, data export/deletion.
 * Design doc §3.1 — device management (revoke tokens, view paired devices).
 * TODO(prompt4): Implement settings UI, device list, data export/deletion.
 */
export default function SettingsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Settings</h1>
      <p className="mt-2 text-gray-600">
        Account settings, paired devices, data management.
      </p>
      <div className="mt-8 space-y-4">
        <div className="rounded border p-4">
          <h2 className="font-semibold">Paired Devices</h2>
          <p className="text-sm text-gray-500 mt-1">
            Manage devices connected to your account. Design doc §3.1.
          </p>
          <p className="text-sm text-gray-400 mt-2">
            TODO: Device list, revoke token, re-pair flow.
          </p>
        </div>
        <div className="rounded border p-4">
          <h2 className="font-semibold">Data Export & Deletion</h2>
          <p className="text-sm text-gray-500 mt-1">
            Export your data or delete your account.
          </p>
          <p className="text-sm text-gray-400 mt-2">
            TODO: Data export (GDPR), account deletion.
          </p>
        </div>
      </div>
    </div>
  );
}
