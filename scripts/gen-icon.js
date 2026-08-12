const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'assets-src');
fs.mkdirSync(outDir, { recursive: true });

const iconSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" rx="225" fill="#6F4E37"/>
  <text x="512" y="660" font-size="560" text-anchor="middle" fill="#F3E9DD" font-family="Arial, sans-serif" font-weight="bold">&#8644;</text>
</svg>
`;

const iconSvgFg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <text x="512" y="640" font-size="460" text-anchor="middle" fill="#F3E9DD" font-family="Arial, sans-serif" font-weight="bold">&#8644;</text>
</svg>
`;

const iconSvgBg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="#6F4E37"/>
</svg>
`;

const splashSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#FFFFFF"/>
  <rect x="1116" y="1116" width="500" height="500" rx="110" fill="#6F4E37"/>
  <text x="1366" y="1465" font-size="270" text-anchor="middle" fill="#F3E9DD" font-family="Arial, sans-serif" font-weight="bold">&#8644;</text>
</svg>
`;

const splashSvgDark = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#FFFFFF"/>
  <rect x="1116" y="1116" width="500" height="500" rx="110" fill="#6F4E37"/>
  <text x="1366" y="1465" font-size="270" text-anchor="middle" fill="#F3E9DD" font-family="Arial, sans-serif" font-weight="bold">&#8644;</text>
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
