import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Production build goes straight into the Spring Boot static folder, so one jar serves API and UI.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' } },
})
