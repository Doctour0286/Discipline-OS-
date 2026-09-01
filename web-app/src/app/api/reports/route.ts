import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * GET /api/reports — Generate reports for a user.
 * PRD §32 (Daily), §33 (Weekly), §34 (Monthly).
 * TODO(prompt4): Implement actual report generation logic.
 * This is a stub returning basic aggregated data.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const userId = searchParams.get("userId");
  const type = searchParams.get("type") || "daily"; // daily | weekly | monthly

  if (!userId) {
    return NextResponse.json({ error: "userId required" }, { status: 400 });
  }

  // TODO(prompt4): Implement real report generation per PRD §32-34
  return NextResponse.json({
    type,
    userId,
    generatedAt: new Date().toISOString(),
    // Stub data — real reports pull from ledger, violations, sessions, etc.
    summary: {
      message: `TODO: Implement ${type} report per PRD §${type === "daily" ? "32" : type === "weekly" ? "33" : "34"}`,
    },
  });
}
