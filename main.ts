/**
 * Alias for `server.ts`.
 *
 * Deno Deploy looks for a conventional entrypoint (`main.ts`) when none is
 * configured, and a wrong entrypoint setting fails the deployment after a
 * successful build, which reads as a build problem rather than a configuration
 * one. Importing the server here starts it, so either name works.
 */

import "./server.ts"
