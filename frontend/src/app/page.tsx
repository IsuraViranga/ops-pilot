import { HealthPanel } from "@/components/HealthPanel";
import { fetchHealth } from "@/lib/health";

// Health is checked at request time, never at build time.
export const dynamic = "force-dynamic";

const PHASES = [
  { phase: "Phase 0", title: "Foundation", state: "current" },
  { phase: "Phase 1", title: "Identity & authorization", state: "upcoming" },
  { phase: "Phase 2", title: "Ticketing core", state: "upcoming" },
  { phase: "Phase 3", title: "Enterprise workflow", state: "upcoming" },
  { phase: "Phase 4", title: "Distributed architecture", state: "upcoming" },
  { phase: "Phase 5", title: "AI service", state: "upcoming" },
] as const;

export default async function Home() {
  // Fetched on the server so the first paint already shows real status, rather than a
  // spinner that resolves a moment later.
  const health = await fetchHealth();

  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-16">
      <header className="mb-10">
        <div className="mb-3 inline-flex items-center rounded-full border border-zinc-200 px-3 py-1 text-xs font-medium text-zinc-600 dark:border-zinc-800 dark:text-zinc-400">
          Phase 0 &middot; Foundation
        </div>
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          OpsPilot
        </h1>
        <p className="mt-2 text-zinc-600 dark:text-zinc-400">
          AI-powered enterprise service management platform.
        </p>
      </header>

      <HealthPanel initial={health} />

      <section className="mt-10">
        <h2 className="mb-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">Roadmap</h2>
        <ol className="space-y-2">
          {PHASES.map((item) => (
            <li
              key={item.phase}
              className="flex items-center gap-3 rounded-lg border border-zinc-200 px-4 py-2.5 dark:border-zinc-800"
            >
              <span
                className={`size-1.5 shrink-0 rounded-full ${
                  item.state === "current" ? "bg-blue-500" : "bg-zinc-300 dark:bg-zinc-700"
                }`}
              />
              <span className="w-16 shrink-0 text-xs font-medium text-zinc-500 dark:text-zinc-500">
                {item.phase}
              </span>
              <span
                className={
                  item.state === "current"
                    ? "text-sm font-medium text-zinc-900 dark:text-zinc-100"
                    : "text-sm text-zinc-500 dark:text-zinc-500"
                }
              >
                {item.title}
              </span>
            </li>
          ))}
        </ol>
      </section>

      <footer className="mt-12 border-t border-zinc-200 pt-6 text-xs text-zinc-500 dark:border-zinc-800 dark:text-zinc-500">
        Next.js 16 &middot; Spring Boot 4 &middot; PostgreSQL 17 &middot; Redis 8
      </footer>
    </main>
  );
}
