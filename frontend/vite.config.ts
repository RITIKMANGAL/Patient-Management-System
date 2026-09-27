import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  if (command === "build") {
    const base = env.VITE_API_BASE_URL?.trim();
    if (base !== "/") {
      let url: URL;
      try {
        url = new URL(base ?? "");
      } catch {
        throw new Error("VITE_API_BASE_URL must be an explicit HTTPS API URL or / for same-origin builds");
      }
      if (url.protocol !== "https:" || ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
          || url.username || url.password || url.search || url.hash) {
        throw new Error("Production API URL must use non-local HTTPS without credentials, query or fragment");
      }
    }
    if (env.VITE_DEMO_MODE === "true" && (!env.VITE_DEMO_USERNAME?.trim() || !env.VITE_DEMO_PASSWORD)) {
      throw new Error("Demo builds require VITE_DEMO_USERNAME and VITE_DEMO_PASSWORD");
    }
  }
  return {
  plugins: [react()],
  server: {
    port: 5173
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setupTests.ts"
  }
  };
});
