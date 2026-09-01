import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { verifyDeviceToken } from "@/lib/auth";

/**
 * POST /api/sync/violations — Push pending violations from Enforcer.
 * Design doc §2.1: Enforcer → Console push for violations + provisional ledger entries.
 * Design doc §2.3: reconciliation sequence — Console runs authoritative RecordViolationUseCase.
 *
 * Body: { violations: PendingViolation[], provisionalEntries: ProvisionalLedgerEntry[] }
 *
 * TODO(prompt4): Implement full reconciliation logic (shared-cause guard, authoritative
 * consequence calculation, provisional estimate comparison). This is a stub that stores
 * the raw data.
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

  const { violations, provisionalEntries } = await request.json();

  // TODO(prompt4): Implement authoritative RecordViolationUseCase reconciliation
  // For now, log the incoming data
  console.log(`[SYNC] Received ${violations?.length || 0} violations from device`);

  return NextResponse.json({
    status: "received",
    processed: violations?.length || 0,
    // TODO(prompt4): Return reconciled state (confirmed_entries, reversed_entries)
  });
}
