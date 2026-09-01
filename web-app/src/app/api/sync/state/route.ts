import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { verifyDeviceToken } from "@/lib/auth";

/**
 * POST /api/sync/state — Full state refresh for Enforcer.
 * Design doc §2.4: "If cache is very stale, Console sends a full state refresh."
 *
 * Body: { deviceId: string, lastSyncSequence: number }
 * Returns: { user, activeSession, missionProfile, goalMission, ... }
 */
export async function POST(request: NextRequest) {
  const token = request.headers.get("authorization")?.replace("Bearer ", "");
  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const auth = verifyDeviceToken(token);
  if (!auth) {
    return NextResponse.json({ error: "Invalid token" }, { status: 401 });
  }

  const user = await prisma.user.findUnique({
    where: { id: auth.userId },
    include: {
      sessions: { where: { status: "ACTIVE" }, take: 1 },
      profiles: { take: 1 },
      goals: { take: 1 },
    },
  });

  if (!user) {
    return NextResponse.json({ error: "User not found" }, { status: 404 });
  }

  return NextResponse.json({
    user: {
      id: user.id,
      currentTier: user.currentTier,
      tierSelectedAt: user.tierSelectedAt,
      tierActivationAt: user.tierActivationAt,
      calibrationWindowDays: user.calibrationWindowDays,
      debtAccrualPausedUntil: user.debtAccrualPausedUntil,
      tribunalDeferredUntil: user.tribunalDeferredUntil,
      lastExplicitDowngradeAt: user.lastExplicitDowngradeAt,
    },
    activeSession: user.sessions[0] || null,
    missionProfile: user.profiles[0] || null,
    goalMission: user.goals[0] || null,
  });
}
