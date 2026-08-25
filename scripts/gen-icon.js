const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'assets-src');
fs.mkdirSync(outDir, { recursive: true });

const BRAND = '#4338ca';
const INK = '#ffffff';
const INK_LIGHT = '#c7d2fe';
const UNDERLINE = '#e0e7ff';

// A pencil tracing an underline stroke - a simple mark for a handwriting
// practice app. Coordinates match the pencil glyph used in gen-all-icons.ts
// during development; kept here as the reproducible source of truth.
const pencilGlyph = (scale) => `
  <g transform="translate(512 512) scale(${scale}) rotate(-45)">
    <rect x="-8" y="-40" width="16" height="62" rx="3" fill="${INK}"/>
    <polygon points="-8,22 8,22 0,40" fill="${INK}"/>
    <rect x="-8" y="-40" width="16" height="12" fill="${INK_LIGHT}"/>
  </g>
  <path d="M 428 597 Q 512 645 596 597" stroke="${UNDERLINE}" stroke-width="18" stroke-linecap="round" fill="none"/>
`;

const iconSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" rx="225" fill="${BRAND}"/>
  ${pencilGlyph(6.4)}
</svg>
`;

const iconSvgFg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  ${pencilGlyph(4.3)}
</svg>
`;

const iconSvgBg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="${BRAND}"/>
</svg>
`;

const splashSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="${BRAND}"/>
  ${pencilGlyph(8.5).replace(/translate\(512 512\)/, 'translate(1366 1366)')}
</svg>
`;

(async () => {
  await sharp(Buffer.from(iconSvg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon.png'));
  await sharp(Buffer.from(iconSvgFg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon-foreground.png'));
  await sharp(Buffer.from(iconSvgBg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon-background.png'));
  await sharp(Buffer.from(splashSvg)).resize(2732, 2732).png().toFile(path.join(outDir, 'splash.png'));
  await sharp(Buffer.from(splashSvg)).resize(2732, 2732).png().toFile(path.join(outDir, 'splash-dark.png'));
  console.log('done');
})();
