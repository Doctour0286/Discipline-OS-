import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/profiles — List mission profiles for a user.
 * TODO(prompt4): Add auth, filtering.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");

  const where = userId ? { userId } : {};
  const profiles = await prisma.missionProfile.findMany({
    where,
    orderBy: { createdAt: "desc" },
    take: 50,
  });

  return NextResponse.json(profiles);
}

/**
 * POST /api/profiles — Create or update a mission profile.
 * Design doc §2.4: profile edits are cached for next session if mission is active.
 */
export async function POST(request: NextRequest) {
  const data = await request.json();
  const profile = await prisma.missionProfile.create({ data });
  return NextResponse.json(profile, { status: 201 });
}
