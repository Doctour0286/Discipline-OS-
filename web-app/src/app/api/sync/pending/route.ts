import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { verifyDeviceToken } from "@/lib/auth";

/**
 * GET /api/sync/pending — Pull pending actions from Console for the Enforcer.
 * Design doc §2.1: Console → Enforcer push for session updates, tier changes, etc.
 *
 * Query: deviceId
 * Returns: { sessions: [...], tierState: {...}, profileUpdates: [...] }
 *
 * TODO(prompt4): Implement incremental sync with syncSequence for gap detection.
 */
export async function GET(request: NextRequest) {
  const token = request.headers.get("authorization")?.replace("Bearer ", "");
  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const auth = verifyDeviceToken(token);
  if (!auth) {
    return NextResponse.json({ error: "Invalid token" }, { status: 401 });
  }

  // TODO(prompt4): Implement real sync state tracking with syncSequence
  return NextResponse.json({
    sessions: [],
    tierState: null,
    profileUpdates: [],
    goalUpdates: [],
    reconciledEntries: [],
  });
}
