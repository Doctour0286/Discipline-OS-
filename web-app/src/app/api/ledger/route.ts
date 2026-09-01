import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/ledger — List ledger entries for a user.
 * Design doc §1.3: authoritative ledger history lives on Console.
 * TODO(prompt4): Add auth, filtering by metric/date, pagination.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");
  const metric = searchParams.get("metric");

  const where: any = {};
  if (userId) where.userId = userId;
  if (metric) where.metric = metric;

  const entries = await prisma.ledgerEntry.findMany({
    where,
    orderBy: { appliedAt: "desc" },
    take: 100,
  });

  return NextResponse.json(entries);
}
