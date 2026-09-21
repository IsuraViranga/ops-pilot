import { fetchHealth } from "@/lib/health";

/**
 * Backend-for-frontend proxy for the platform health endpoint.
 *
 * The browser calls this route; this route calls Spring. That indirection is deliberate:
 *
 *  - **No CORS.** The request is same-origin, so no cross-origin configuration is needed
 *    and the backend does not have to trust a browser origin.
 *  - **Actuator stays private.** In production the management port is not exposed to the
 *    internet at all. Only this route is, and it returns names and statuses - never disk
 *    paths, driver versions or connection details.
 *  - **One place to add auth.** When the dashboard becomes authenticated, the session is
 *    checked here rather than in every component.
 */

// Health must reflect the present moment, so this route is never statically rendered.
export const dynamic = "force-dynamic";

export async function GET() {
  const snapshot = await fetchHealth();

  // Mirror Actuator's convention: 503 when not serving, so uptime checks and the
  // browser both see a failure without having to parse the body.
  const httpStatus = snapshot.status === "UP" ? 200 : 503;

  return Response.json(snapshot, {
    status: httpStatus,
    headers: { "Cache-Control": "no-store" },
  });
}
