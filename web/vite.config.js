import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    // The build output is served by Spring Boot from classpath:/static/ at the site
    // root, so asset URLs must be root-relative rather than module-relative.
    base: '/',
    server: {
        // Honor the PORT env injected by Claude Code's preview autoPort so the
        // Browser pane's proxy and the dev server agree on a port.
        port: Number(process.env.PORT) || 5173,
        // Dev-only: forward API calls to the real backend so the auth-code flow
        // works against `:fly-narwhal-web:bootRun` instead of 404ing.
        proxy: {
            '/api': {
                target: process.env.FLY_NARWHAL_API || 'http://localhost:5365',
                changeOrigin: true,
            },
        },
    },
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        assetsDir: 'assets',
        // Rollup's default chunk-size warning fires on the single bundled app chunk;
        // there is only one page here, so splitting it further buys nothing.
        chunkSizeWarningLimit: 1200,
    },
})
