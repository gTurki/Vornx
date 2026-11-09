import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server on 5173: that origin is already allowed by the backend's local CORS settings.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
})
