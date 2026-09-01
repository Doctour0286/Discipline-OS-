import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/missions — List enforcement sessions for a user.
 * TODO(prompt4): Add auth, filtering, pagination.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");

  const where = userId ? { userId } : {};
  const sessions = await prisma.enforcementSession.findMany({
    where,
    include: { goalMission: true, profile: true },
    orderBy: { actualStart: "desc" },
    take: 50,
  });

  return NextResponse.json(sessions);
}

/**
 * POST /api/missions — Create a new enforcement session.
 * TODO(prompt4): Implement session creation with goal/profile resolution.
 */
export async function POST(request: NextRequest) {
  const data = await request.json();
  const session = await prisma.enforcementSession.create({ data });
  return NextResponse.json(session, { status: 201 });
}
