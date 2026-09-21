/**
 * Health types and the server-side call to the platform backend.
 *
 * This module is imported only by server code. The browser never talks to the Spring
 * application directly - see src/app/api/health/route.ts for why.
 */

export type HealthStatus = "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN" | "UNREACHABLE";

export interface ComponentHealth {
  name: string;
  status: HealthStatus;
}

export interface HealthSnapshot {
  status: HealthStatus;
  components: ComponentHealth[];
  /** Populated only when the backend could not be reached at all. */
  error?: string;
  checkedAt: string;
}

/** Shape of the Spring Boot Actuator health response, narrowed to what we use. */
interface ActuatorHealth {
  status?: string;
  components?: Record<string, { status?: string }>;
}

const BACKEND_URL = process.env.OPSPILOT_API_URL ?? "http://localhost:8080";

/** Requests should fail fast: a hanging backend must not hang the dashboard. */
const TIMEOUT_MS = 4000;

function toStatus(value: string | undefined): HealthStatus {
  switch (value) {
    case "UP":
    case "DOWN":
    case "OUT_OF_SERVICE":
      return value;
    default:
      return "UNKNOWN";
  }
}

/**
 * Fetches health from the backend and reduces it to the minimum the UI needs.
 *
 * Actuator's raw response includes disk paths, database vendor and driver details. None of
 * that belongs in a browser payload, so only component names and statuses are passed on.
 */
export async function fetchHealth(): Promise<HealthSnapshot> {
  const checkedAt = new Date().toISOString();

  try {
    const response = await fetch(`${BACKEND_URL}/actuator/health`, {
      signal: AbortSignal.timeout(TIMEOUT_MS),
      // Health is never cached. A cached health check is not a health check.
      cache: "no-store",
      headers: { Accept: "application/json" },
    });

    // Actuator answers 503 when the status is DOWN, so a non-OK response still has
    // a body worth reading.
    const body = (await response.json()) as ActuatorHealth;

    return {
      status: toStatus(body.status),
      components: Object.entries(body.components ?? {})
        .map(([name, component]) => ({ name, status: toStatus(component?.status) }))
        .sort((a, b) => a.name.localeCompare(b.name)),
      checkedAt,
    };
  } catch (error) {
    // The backend is not running, is still starting, or timed out. That is a normal
    // state during development, not an exception the page should crash on.
    return {
      status: "UNREACHABLE",
      components: [],
      error: error instanceof Error ? error.message : "Unknown error",
      checkedAt,
    };
  }
}
