const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'assets-src');
fs.mkdirSync(outDir, { recursive: true });

const INK = '#122040';
const INK_2 = '#1c2f57';
const GOLD = '#A9843F';
const PAPER = '#EDEFE7';

// Full launcher icon (legacy, non-adaptive): navy rounded-square badge with a
// gold ring "UK" emblem, matching the site's own brand mark (.flag / .wemblem).
const iconSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" rx="225" fill="${INK}"/>
  <circle cx="512" cy="512" r="330" fill="none" stroke="${GOLD}" stroke-width="44"/>
  <text x="512" y="512" font-size="260" text-anchor="middle" dominant-baseline="central" fill="${GOLD}" font-family="Georgia, 'Times New Roman', serif" font-weight="700">UK</text>
</svg>
`;

// Adaptive icon foreground: same emblem, transparent background.
const iconSvgFg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <circle cx="512" cy="512" r="330" fill="none" stroke="${GOLD}" stroke-width="44"/>
  <text x="512" y="512" font-size="260" text-anchor="middle" dominant-baseline="central" fill="${GOLD}" font-family="Georgia, 'Times New Roman', serif" font-weight="700">UK</text>
</svg>
`;

// Adaptive icon background: flat navy fill.
const iconSvgBg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="${INK}"/>
</svg>
`;

const splashSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="${PAPER}"/>
  <circle cx="1366" cy="1366" r="260" fill="${INK}" stroke="${GOLD}" stroke-width="14"/>
  <text x="1366" y="1366" font-size="210" text-anchor="middle" dominant-baseline="central" fill="${GOLD}" font-family="Georgia, 'Times New Roman', serif" font-weight="700">UK</text>
</svg>
`;

const splashSvgDark = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#0B1526"/>
  <circle cx="1366" cy="1366" r="260" fill="${INK_2}" stroke="${GOLD}" stroke-width="14"/>
  <text x="1366" y="1366" font-size="210" text-anchor="middle" dominant-baseline="central" fill="${GOLD}" font-family="Georgia, 'Times New Roman', serif" font-weight="700">UK</text>
</svg>
`;

(async () => {
  await sharp(Buffer.from(iconSvg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon.png'));
  await sharp(Buffer.from(iconSvgFg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon-foreground.png'));
  await sharp(Buffer.from(iconSvgBg)).resize(1024, 1024).png().toFile(path.join(outDir, 'icon-background.png'));
  await sharp(Buffer.from(splashSvg)).resize(2732, 2732).png().toFile(path.join(outDir, 'splash.png'));
  await sharp(Buffer.from(splashSvgDark)).resize(2732, 2732).png().toFile(path.join(outDir, 'splash-dark.png'));
  console.log('done');
})();
