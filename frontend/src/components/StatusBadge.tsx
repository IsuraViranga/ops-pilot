import type { HealthStatus } from "@/lib/health";

const STYLES: Record<HealthStatus, { dot: string; text: string; label: string }> = {
  UP: {
    dot: "bg-emerald-500",
    text: "text-emerald-700 dark:text-emerald-400",
    label: "Healthy",
  },
  DOWN: {
    dot: "bg-red-500",
    text: "text-red-700 dark:text-red-400",
    label: "Down",
  },
  OUT_OF_SERVICE: {
    dot: "bg-amber-500",
    text: "text-amber-700 dark:text-amber-400",
    label: "Out of service",
  },
  UNREACHABLE: {
    dot: "bg-red-500",
    text: "text-red-700 dark:text-red-400",
    label: "Unreachable",
  },
  UNKNOWN: {
    dot: "bg-zinc-400",
    text: "text-zinc-600 dark:text-zinc-400",
    label: "Unknown",
  },
};

export function StatusBadge({ status, pulse = false }: { status: HealthStatus; pulse?: boolean }) {
  const style = STYLES[status];

  return (
    <span className={`inline-flex items-center gap-2 text-sm font-medium ${style.text}`}>
      <span className="relative flex size-2.5">
        {pulse && status === "UP" && (
          <span
            className={`absolute inline-flex size-full animate-ping rounded-full opacity-60 ${style.dot}`}
          />
        )}
        <span className={`relative inline-flex size-2.5 rounded-full ${style.dot}`} />
      </span>
      {style.label}
    </span>
  );
}
