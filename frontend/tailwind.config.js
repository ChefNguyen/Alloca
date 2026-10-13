/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // EXACT user specification:
        "page-bg": "#F0EDE8",
        "primary-orange": "#E8622A", // Burnt orange primary accent
        "secondary-amber": "#F5A623", // Amber yellow secondary accent
        "card-warm": "#FAFAF8", // Cards: #FAFAF8 with subtle shadow, no visible border
        "text-primary": "#242424", // Text primary: #242424
        "text-secondary": "#8A8070", // Text secondary: #8A8070
        "sidebar-dark": "#242424", // Left sidebar: dark #242424

        // Legacy/Semantic mapping
        "surface-canvas": "#F0EDE8",
        "surface-card": "#FAFAF8",
        "surface-subtle": "#EBE7E1",
        "primary-container": "#E8622A",
        "on-surface": "#242424",
        "text-muted": "#8A8070",
        "border-subtle": "#E5E0D8",
        "status-alert": "#D9381E",
      },
      boxShadow: {
        "card": "0 2px 16px -2px rgba(26, 26, 26, 0.04)",
        "card-hover": "0 8px 24px -4px rgba(232, 98, 42, 0.08)",
        "hero": "0 12px 32px -6px rgba(232, 98, 42, 0.12)",
      },
      borderRadius: {
        // Squarer border-radius overrides
        "card": "8px",
        "btn": "6px",
        "input": "6px",
        "badge": "4px",
        "sm": "4px",
        "md": "6px",
        "lg": "8px",
        "xl": "8px",
        "2xl": "8px",
        "3xl": "12px",
      },
      fontFamily: {
        sans: ["Outfit", "sans-serif"],
        editorial: ["Outfit", "sans-serif"],
      },
      letterSpacing: {
        widest: "0.2em",
        editorial: "0.06em",
      }
    },
  },
  plugins: [],
}
