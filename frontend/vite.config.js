import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const packageJson = JSON.parse(
  readFileSync(resolve(process.cwd(), "package.json"), "utf-8")
);
const buildVersion = packageJson.version ?? "0.0.0";

export default defineConfig({
  define: {
    __APP_BUILD_VERSION__: JSON.stringify(buildVersion)
  },
  plugins: [
    vue(),
    tailwindcss()   // Vite-native — no postcss.config.js or tailwind.config.js needed
  ],
  build: {
    outDir: "../src/main/resources/public",
    emptyOutDir: true,
    sourcemap: false
  }
});
