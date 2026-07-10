---
name: Tumugi High-Density
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1c1b1d'
  surface-container: '#201f22'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e5e1e4'
  on-surface-variant: '#c4c7c8'
  inverse-surface: '#e5e1e4'
  inverse-on-surface: '#313032'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c6c6c7'
  primary: '#ffffff'
  on-primary: '#2f3131'
  primary-container: '#e2e2e2'
  on-primary-container: '#636565'
  inverse-primary: '#5d5f5f'
  secondary: '#c6c6cf'
  on-secondary: '#2f3037'
  secondary-container: '#45464e'
  on-secondary-container: '#b4b4bd'
  tertiary: '#ffffff'
  on-tertiary: '#303037'
  tertiary-container: '#e3e1ea'
  on-tertiary-container: '#64646b'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c7'
  on-primary-fixed: '#1a1c1c'
  on-primary-fixed-variant: '#454747'
  secondary-fixed: '#e2e1eb'
  secondary-fixed-dim: '#c6c6cf'
  on-secondary-fixed: '#1a1b22'
  on-secondary-fixed-variant: '#45464e'
  tertiary-fixed: '#e3e1ea'
  tertiary-fixed-dim: '#c7c5ce'
  on-tertiary-fixed: '#1b1b21'
  on-tertiary-fixed-variant: '#46464d'
  background: '#131315'
  on-background: '#e5e1e4'
  surface-variant: '#353437'
typography:
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: '1.4'
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: '1.4'
    letterSpacing: '0'
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: '1'
    letterSpacing: 0.02em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  xs: 0.25rem
  sm: 0.5rem
  md: 0.75rem
  lg: 1.25rem
  xl: 2rem
  gutter: 1rem
  margin-safe: 1.25rem
---

## Brand & Style
The design system evolves into a high-density, modern-utilitarian aesthetic. It maintains the original "紡" (Tumugi) essence—interweaving precision with a structured narrative—but shifts toward a more compact, tactile, and efficient interface. 

The style is **Soft-Minimalism with a Technical Edge**. By blending a dark monochrome palette with subtle rounded geometry, the UI feels both authoritative and approachable. It is designed for expert users who require high information visibility without sacrificing the sophisticated, editorial feel of a premium dark-mode experience.

## Colors
This design system utilizes a rigorous monochrome palette to maintain focus and hierarchy.
- **Primary:** Pure White (#FFFFFF) for high-contrast headers, active states, and primary actions.
- **Secondary:** Muted Zinc (#A1A1AA) for supporting text and secondary icons.
- **Tertiary:** Dark Slate (#3F3F46) for borders, dividers, and inactive states.
- **Neutral/Background:** Deep Obsidian (#09090B) serves as the base layer, providing a void-like canvas that makes typography and containers pop.

## Typography
The typographic scale is tightened to facilitate high information density. We use **Hanken Grotesk** for headlines to provide a sharp, contemporary character. **Inter** handles body copy for maximum legibility at smaller sizes, while **JetBrains Mono** is used for labels and metadata to lean into the technical nature of the system.

Line heights are aggressively reduced (1.1 to 1.4 range) and letter spacing is slightly tightened on headings to ensure that large text blocks remain cohesive even when packed closely together.

## Layout & Spacing
The layout follows a fluid-to-fixed grid model with a reduced spatial rhythm. 
- **Density:** Global padding and margins have been reduced by 25% compared to standard scales.
- **Desktop:** 12-column grid with 16px (1rem) gutters and 20px (1.25rem) side margins.
- **Mobile:** 4-column grid with 12px gutters.

Use `md` (12px) for standard component spacing and `sm` (8px) for internal element grouping. This compact approach ensures that more content is visible above the fold while maintaining a clean, structured alignment.

## Elevation & Depth
Depth is communicated through **Tonal Layering** rather than traditional shadows. 
- **Level 0:** Base background (#09090B).
- **Level 1:** Content cards and containers use a slightly lighter fill (#18181B) with a subtle 1px border (#27272A).
- **Interactive:** Hover states trigger a subtle inner glow or a brightness shift in the background fill.

Avoid heavy shadows. If a shadow is required for a floating menu, use a sharp, low-spread "Technical Shadow": `0px 4px 12px rgba(0, 0, 0, 0.5)`.

## Shapes
The shape language transitions from sharp to **Soft**. 
- Standard components (buttons, inputs, cards) utilize a **4px (0.25rem)** corner radius.
- Larger containers or featured sections use **8px (0.5rem)**.

This subtle rounding breaks the "harshness" of the dark monochrome palette, making the interface feel modern and tactile without losing the precision of a professional tool.

## Components
- **Buttons:** Compact height (32px for standard). High-contrast primary (White bg, Black text) and ghost-style secondary (Border only). 4px radius.
- **Inputs:** Minimalist bottom-border or 1px outlined box with 4px radius. Font: Inter 13px. Focus state uses a 1px white outline.
- **Chips:** Monospaced text (JetBrains Mono) inside 4px rounded containers. Use tertiary colors for background to keep them unobtrusive.
- **Lists:** Tight vertical spacing. 8px padding between items. Use subtle dividers (#27272A) only when necessary for readability.
- **Cards:** No shadows. 1px border (#27272A). Background #18181B. 8px corner radius.
- **Data Tables:** Highly compact. 4px vertical cell padding. Headers in JetBrains Mono 11px uppercase.