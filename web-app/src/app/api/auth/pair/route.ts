import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { generatePairingCode, storePairingCode } from "@/lib/auth";

/**
 * POST /api/auth/pair — Generate a pairing code for device enrollment.
 * Design doc §3.1 — pairing flow steps 2-3.
 *
 * Body: { userId: string }
 * Returns: { code: string, expiresAt: string }
 *
 * TODO(prompt4): Add rate limiting and require authenticated session.
 */
export async function POST(request: NextRequest) {
  const { userId } = await request.json();

  if (!userId) {
    return NextResponse.json({ error: "userId required" }, { status: 400 });
  }

  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user) {
    return NextResponse.json({ error: "User not found" }, { status: 404 });
  }

  const { code, expiresAt } = generatePairingCode();
  storePairingCode(userId, code, expiresAt);

  return NextResponse.json({ code, expiresAt: expiresAt.toISOString() });
}
