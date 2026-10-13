# Agent Rules & Behavioral Guidelines

## 1. Browser Automation Policy
- **DO NOT** automatically launch or control browsers (`browser_subagent` tool) to test or preview web pages.
- Only run non-interactive terminal validation (e.g. `npm run build`, `tsc -b`) to verify correctness.
- The user will test and inspect UI changes in their own browser window.

## 2. Design System Guidelines
- Primary orange accent: `#D97757`
- Primary black / charcoal: `#242424`
- Status colors:
  - AVAILABLE: `#4CAF7D` (badge: bg `#E8F5EE`, text `#2E7D32`)
  - PARTIAL: `#E8A020` (badge: bg `#FFF3E0`, text `#E65100`)
  - BOOKED: `#E05252` (badge: bg `#FFEBEE`, text `#C62828`)
