import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/goals — List goal missions for a user.
 * TODO(prompt4): Add auth, filtering, pagination.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");

  const where = userId ? { userId } : {};
  const goals = await prisma.goalMission.findMany({
    where,
    include: { sessions: true, milestones: true, triggers: true },
    orderBy: { createdAt: "desc" },
    take: 50,
  });

  return NextResponse.json(goals);
}

/**
 * POST /api/goals — Create a new goal mission.
 * TODO(prompt4): Implement goal creation with validation.
 */
export async function POST(request: NextRequest) {
  const data = await request.json();
  const goal = await prisma.goalMission.create({ data });
  return NextResponse.json(goal, { status: 201 });
}
