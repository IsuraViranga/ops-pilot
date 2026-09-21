"use client";

import { useCallback, useEffect, useState } from "react";

import type { HealthSnapshot } from "@/lib/health";
import { StatusBadge } from "./StatusBadge";

const POLL_INTERVAL_MS = 5000;

const COMPONENT_LABELS: Record<string, string> = {
  db: "PostgreSQL",
  redis: "Redis",
  diskSpace: "Disk space",
  ping: "Process",
  livenessState: "Liveness",
  readinessState: "Readiness",
  ssl: "SSL certificates",
};

function label(name: string) {
  return COMPONENT_LABELS[name] ?? name;
}

export function HealthPanel({ initial }: { initial: HealthSnapshot }) {
  const [health, setHealth] = useState(initial);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const refresh = useCallback(async () => {
    setIsRefreshing(true);
    try {
      // A 503 here is a valid answer, not a failure, so the body is read either way.
      const response = await fetch("/api/health", { cache: "no-store" });
      setHealth((await response.json()) as HealthSnapshot);
    } catch {
      // The dev server is restarting, or the tab is offline. Keep the last known
      // state on screen rather than blanking the page.
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  const isUp = health.status === "UP";

  return (
    <section className="rounded-xl border border-zinc-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <header className="flex items-center justify-between gap-4 border-b border-zinc-200 px-5 py-4 dark:border-zinc-800">
        <div>
          <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Platform backend
          </h2>
          <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
            Spring Boot &middot; polled every {POLL_INTERVAL_MS / 1000}s
          </p>
        </div>
        <StatusBadge status={health.status} pulse />
      </header>

      {isUp ? (
        <ul className="divide-y divide-zinc-100 dark:divide-zinc-800">
          {health.components.map((component) => (
            <li key={component.name} className="flex items-center justify-between px-5 py-3">
              <span className="text-sm text-zinc-700 dark:text-zinc-300">
                {label(component.name)}
              </span>
              <StatusBadge status={component.status} />
            </li>
          ))}
        </ul>
      ) : (
        <div className="px-5 py-6">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">The backend is not responding.</p>
          {health.error && (
            <p className="mt-1 font-mono text-xs text-zinc-500 dark:text-zinc-500">
              {health.error}
            </p>
          )}
          <div className="mt-4 rounded-lg bg-zinc-50 p-4 text-xs text-zinc-600 dark:bg-zinc-950 dark:text-zinc-400">
            <p className="mb-2 font-medium text-zinc-700 dark:text-zinc-300">Start it with:</p>
            <pre className="overflow-x-auto font-mono leading-relaxed">
              {`cd deployment/docker && docker compose up -d
cd services/platform && ./mvnw -pl platform-app spring-boot:run`}
            </pre>
          </div>
        </div>
      )}

      <footer className="flex items-center justify-between border-t border-zinc-200 px-5 py-3 dark:border-zinc-800">
        <span className="text-xs text-zinc-500 dark:text-zinc-400">
          Checked {new Date(health.checkedAt).toLocaleTimeString()}
        </span>
        <button
          type="button"
          onClick={refresh}
          disabled={isRefreshing}
          className="rounded-md px-2.5 py-1 text-xs font-medium text-zinc-600 transition hover:bg-zinc-100 disabled:opacity-50 dark:text-zinc-400 dark:hover:bg-zinc-800"
        >
          {isRefreshing ? "Checking..." : "Check now"}
        </button>
      </footer>
    </section>
  );
}
