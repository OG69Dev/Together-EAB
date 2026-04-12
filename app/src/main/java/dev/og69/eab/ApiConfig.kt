package dev.og69.eab

/**
 * Worker base URL (no trailing slash).
 *
 * **Production:** `https://your-worker.your-subdomain.workers.dev`
 *
 * **Wrangler dev on Android Emulator:** use the host loopback alias (not 127.0.0.1):
 * `http://10.0.2.2:8787`
 *
 * **Wrangler dev on a physical phone:** use your PC’s LAN IP, e.g. `http://192.168.1.10:8787`,
 * and run Wrangler so it listens on all interfaces, e.g. `npx wrangler dev --ip 0.0.0.0`.
 *
 * **Cleartext:** HTTP for local dev is allowed via `network_security_config.xml` (emulator host + debug builds).
 */
object ApiConfig {
    const val WORKER_BASE_URL = "https://connect.eab.og69.dev/"
}
