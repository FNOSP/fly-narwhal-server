/**
 * Base URL the landing page's own API lives under.
 *
 * Opened directly on the server port (http://nas:5365/) the SPA sits at the web
 * root, so `/api/...` is correct. Inside the fnOS desktop it is loaded through
 * the package's CGI entry, /cgi/ThirdParty/<app>/index.cgi/, and that script
 * only forwards paths after `index.cgi` to the server — an absolute `/api/...`
 * would hit fnOS itself (port 5666) and come back as "invalid token". Keep the
 * prefix so every request travels the same proxied route as the assets.
 */
export function apiBase() {
    const m = location.pathname.match(/^(\/cgi\/ThirdParty\/[^/]+\/index\.cgi)/)
    return m ? m[1] : ''
}
