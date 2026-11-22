import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config';

/**
 * Generates the PWA icons (favicon, 64/192/512 px, maskable, Apple touch icon) from public/logo.svg.
 * Run `npm run icons` after changing the logo; the generated PNGs are committed.
 */
export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...minimal2023Preset,
    // Android masks icons to circles/squircles: keep the glyph inside the safe zone on a brand background.
    maskable: { ...minimal2023Preset.maskable, resizeOptions: { background: '#4263eb' } },
    apple: { ...minimal2023Preset.apple, resizeOptions: { background: '#4263eb' } },
  },
  images: ['public/logo.svg'],
});
