import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/users — List users (Console admin view).
 * TODO(prompt4): Add auth, pagination.
 */
export async function GET() {
  const users = await prisma.user.findMany({ take: 50 });
  return NextResponse.json(users);
}

/**
 * POST /api/users — Create a new user account.
 * Design doc §3.1: "User creates an account on the Console (email + password)."
 * TODO(prompt4): Add password hashing, email validation, session creation.
 */
export async function POST(request: NextRequest) {
  const data = await request.json();
  const user = await prisma.user.create({ data });
  return NextResponse.json(user, { status: 201 });
}
