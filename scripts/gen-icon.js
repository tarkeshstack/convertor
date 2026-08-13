const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'assets-src');
fs.mkdirSync(outDir, { recursive: true });

const iconSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" rx="225" fill="#C8791F"/>
  <text x="512" y="700" font-size="620" text-anchor="middle" fill="#FBF0DF" font-family="Arial, sans-serif" font-weight="bold">&#2309;</text>
</svg>
`;

const iconSvgFg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <text x="512" y="680" font-size="520" text-anchor="middle" fill="#FBF0DF" font-family="Arial, sans-serif" font-weight="bold">&#2309;</text>
</svg>
`;

const iconSvgBg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="#C8791F"/>
</svg>
`;

const splashSvg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#FDFCFA"/>
  <rect x="1116" y="1116" width="500" height="500" rx="110" fill="#C8791F"/>
  <text x="1366" y="1500" font-size="300" text-anchor="middle" fill="#FBF0DF" font-family="Arial, sans-serif" font-weight="bold">&#2309;</text>
</svg>
`;

const splashSvgDark = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2732 2732">
  <rect width="2732" height="2732" fill="#21242E"/>
  <rect x="1116" y="1116" width="500" height="500" rx="110" fill="#C8791F"/>
  <text x="1366" y="1500" font-size="300" text-anchor="middle" fill="#FBF0DF" font-family="Arial, sans-serif" font-weight="bold">&#2309;</text>
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
