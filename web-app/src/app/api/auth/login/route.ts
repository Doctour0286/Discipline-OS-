import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { consumePairingCode, issueDeviceToken } from "@/lib/auth";

/**
 * POST /api/auth/login — Exchange pairing code for device token.
 * Design doc §3.1 — pairing flow steps 5-7.
 *
 * Body: { code: string }
 * Returns: { deviceToken: string, accountId: string }
 *
 * The Enforcer sends the 6-digit code obtained from the Console UI.
 * Console validates it, issues a long-lived device token (90-day JWT).
 */
export async function POST(request: NextRequest) {
  const { code } = await request.json();

  if (!code || typeof code !== "string") {
    return NextResponse.json({ error: "code required" }, { status: 400 });
  }

  const result = consumePairingCode(code);
  if (!result) {
    return NextResponse.json(
      { error: "Invalid or expired pairing code" },
      { status: 401 }
    );
  }

  // Create device credentials
  const deviceToken = issueDeviceToken(result.userId);
  await prisma.deviceCredentials.create({
    data: {
      deviceToken,
      accountUserId: result.userId,
    },
  });

  return NextResponse.json({
    deviceToken,
    accountId: result.userId,
  });
}
