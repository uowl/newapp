import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
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
