/**
 * The only place the backend location is configured.
 * <p>
 * The API is always addressed relative to the page's origin. In development `ng serve`
 * proxies `/api` to the Spring Boot backend (see proxy.conf.json); in production the built
 * app is served from the same origin as the API, or behind the same reverse proxy.
 */
export const environment = {
  apiBaseUrl: '/api',
};
