import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/tier-events — List tier transition events for a user.
 * TODO(prompt4): Add auth, filtering.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");

  const where = userId ? { userId } : {};
  const events = await prisma.tierEvent.findMany({
    where,
    orderBy: { occurredAt: "desc" },
    take: 50,
  });

  return NextResponse.json(events);
}
