const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'assets-src');
fs.mkdirSync(outDir, { recursive: true });

// Same script badges shown in the app's own header (script-row): Devanagari,
// Tamil, Kannada, Malayalam, in the app's own accent colors.
const TILES = [
  { glyph: 'अ', color: '#C8791F', font: 'Noto Sans Devanagari' }, // hi
  { glyph: 'அ', color: '#B33C57', font: 'Noto Sans Tamil' },      // ta
  { glyph: 'ಅ', color: '#1C8C6E', font: 'Noto Sans Kannada' },    // kn
  { glyph: 'അ', color: '#2E7D46', font: 'Noto Sans Malayalam' },  // ml
];

// tileSize/gap/originX/originY describe a 2x2 grid centered on a 1024x1024
// canvas; callers pick a smaller span for the adaptive foreground so the
// tiles stay inside Android's circular/squircle safe zone.
function glyphGrid(span, strokeWidth) {
  const tileSize = (span - 40) / 2;
  const gap = 40;
  const originX = (1024 - span) / 2;
  const originY = (1024 - span) / 2;
  const radius = tileSize * 0.22;
  const fontSize = tileSize * 0.56;
  return TILES.map((t, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = originX + col * (tileSize + gap);
    const y = originY + row * (tileSize + gap);
    const cx = x + tileSize / 2;
    const cy = y + tileSize / 2;
    return `
      <rect x="${x}" y="${y}" width="${tileSize}" height="${tileSize}" rx="${radius}" fill="${t.color}"${strokeWidth ? ` stroke="#FFFFFF" stroke-width="${strokeWidth}"` : ''}/>
      <text x="${cx}" y="${cy}" font-size="${fontSize}" text-anchor="middle" dominant-baseline="central" fill="#FFFFFF" font-family="${t.font}" font-weight="bold">${t.glyph}</text>`;
  }).join('');
}

const iconSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" rx="225" fill="#FFFFFF"/>
  ${glyphGrid(760)}
</svg>
`;

// Adaptive icon: keep the mosaic well inside the safe zone (~66% of the
// canvas) since launchers crop the foreground layer to a circle, squircle,
// rounded square, etc.
const iconSvgFg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  ${glyphGrid(560)}
</svg>
`;

const iconSvgBg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="#FFFFFF"/>
</svg>
`;

const splashSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#FFFFFF"/>
  <g transform="translate(854, 854) scale(1.0)">
    ${glyphGrid(1024)}
  </g>
</svg>
`;

const splashSvgDark = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#12151C"/>
  <g transform="translate(854, 854) scale(1.0)">
    ${glyphGrid(1024)}
  </g>
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
