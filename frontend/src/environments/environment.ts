/**
 * The only place the backend location is configured (development and any same-origin
 * deployment).
 *
 * `/api` is relative to the page's origin: `ng serve` proxies it to the Spring Boot
 * backend (see proxy.conf.json), and a reverse proxy in front of both would do the same.
 *
 * Production builds replace this file with environment.generated.ts (see angular.json),
 * which `npm run build` writes from ACME_API_BASE_URL, because a deployed app and its API
 * are usually on different origins.
 */
export const environment = {
  apiBaseUrl: '/api',
};
