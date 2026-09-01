import jwt from "jsonwebtoken";
import { prisma } from "./prisma";

const DEVICE_TOKEN_SECRET = process.env.DEVICE_TOKEN_SECRET || "dev-secret";

/**
 * Generate a short-lived pairing code (6-digit numeric, 5-minute expiry).
 * Design doc §3.1 — pairing flow step 2.
 */
export function generatePairingCode(): { code: string; expiresAt: Date } {
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes
  return { code, expiresAt };
}

/**
 * Issue a long-lived device token after successful pairing.
 * Design doc §3.1 — pairing flow step 6.
 * Token is 90-day JWT, scoped to one account.
 */
export function issueDeviceToken(accountUserId: string): string {
  return jwt.sign({ sub: accountUserId }, DEVICE_TOKEN_SECRET, {
    expiresIn: "90d",
  });
}

/**
 * Verify a device token and return the account user ID.
 */
export function verifyDeviceToken(token: string): { userId: string } | null {
  try {
    const payload = jwt.verify(token, DEVICE_TOKEN_SECRET) as { sub: string };
    return { userId: payload.sub };
  } catch {
    return null;
  }
}

// In-memory pairing code store (production would use Redis or DB)
const pairingCodes = new Map<
  string,
  { code: string; expiresAt: Date; userId: string }
>();

export function storePairingCode(
  userId: string,
  code: string,
  expiresAt: Date
): void {
  pairingCodes.set(code, { code, expiresAt, userId });
}

export function consumePairingCode(
  code: string
): { userId: string } | null {
  const entry = pairingCodes.get(code);
  if (!entry) return null;
  if (new Date() > entry.expiresAt) {
    pairingCodes.delete(code);
    return null;
  }
  pairingCodes.delete(code);
  return { userId: entry.userId };
}
