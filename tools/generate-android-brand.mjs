#!/usr/bin/env node
// 从 brand/ 下的 Precision Geometry SVG 源生成 Android 品牌 XML 资产。
// 纯 Node（无第三方依赖）、可重复运行、输出逐字节确定（幂等，不含时间戳）。
//
// 用法:
//   node tools/generate-android-brand.mjs          生成/刷新 res 下全部品牌 XML
//   node tools/generate-android-brand.mjs --check  只校验，不写盘（CI / 验收用）
//
// 唯一矢量源: brand/*.svg（从桌面仓库 2pyr-desktop/code/brand/ 只读复制）。
// 单色几何四原则与尺寸分档见 brand/README.md；尺寸分档（不要跨档乱用）:
//   512 画布 mark（stroke 12/9）        >= 64dp  -> brand_mark / brand_mark_white
//   430 视口 favicon（stroke 20/16）    <= 48dp  -> brand_favicon / brand_favicon_white
//   430 视口 micro（stroke 30/24）      16-24dp  -> brand_micro
//   adaptive icon 前景: favicon 加粗稿缩放至 108dp 画布中心，内容 bbox 62dp（安全区 <=66dp）
//   Android 12 Splash: mark 512 视口映射到 190dp 内容安全区，居中于 288dp 画布
//
// 生成物不要手改: 改品牌请改 brand/ 的 SVG 后重跑本脚本。

import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const BRAND = join(ROOT, 'brand');
const RES = join(ROOT, '2fa-android', 'app', 'src', 'main', 'res');
const CHECK_ONLY = process.argv.includes('--check');

// ── 品牌色（唯一来源: brand/*.svg 的 stroke；此处只做 Android 端映射） ──
const INK = '#111111'; // 日间 / 浅色底（与桌面 mark stroke 一致）
const WHITE = '#FFFFFF'; // 深色底（mark-on-dark / favicon-white 的 stroke）
const BLACK = '#000000'; // monochrome alpha mask（颜色不参与渲染，只看 alpha）

// ── 输入（SVG 唯一矢量源） ──
const SVG_FILES = {
  mark: '2-pyramid-mark.svg',
  markWhite: '2-pyramid-mark-on-dark.svg',
  favicon: '2-pyramid-favicon.svg',
  faviconWhite: '2-pyramid-favicon-white.svg',
  micro: '2-pyramid-micro.svg',
};

// ── 尺寸契约（与 mapping §2 / brand/README.md 一致） ──
const MARK_DP = 64; // mark 档: ≥64dp 场景（512 画布）
const FAVICON_DP = 32; // favicon 档: ≤48dp 场景（430 视口）
const MICRO_DP = 24; // micro 档: 16-24dp 场景（430 视口）
const LAUNCHER_CANVAS_DP = 108; // adaptive icon 画布
const LAUNCHER_CONTENT_DP = 62; // 前景内容 bbox（<= 66dp 中心安全区，留 4dp 余量）
const SPLASH_CANVAS_DP = 288; // Android 12 SplashScreen 画布
const SPLASH_CONTENT_DP = 190; // 内容映射区（<= 192dp 图标安全区，留 1dp 余量）

const FAVICON_VIEWPORT = 430; // favicon/micro 的 viewBox 尺寸（viewBox 起点 40 40，需用 group 平移）
const MARK_VIEWPORT = 512;

const GENERATED_NOTE = '由 tools/generate-android-brand.mjs 生成，勿手改。源：';

// ════════════════════════════════════════════════════════════════════
// 解析 brand/*.svg（只依赖 path d / stroke-width / viewBox，无 XML 依赖）
// ════════════════════════════════════════════════════════════════════

function fail(message) {
  throw new Error(`${message}\n  提示: 源文件应为桌面仓库 2pyr-desktop/code/brand/ 下同名文件。`);
}

function parseSvg(file) {
  const abs = join(BRAND, file);
  if (!existsSync(abs)) fail(`品牌源缺失: brand/${file}`);
  const text = readFileSync(abs, 'utf8');
  const viewBoxMatch = text.match(/viewBox="([^"]+)"/);
  if (!viewBoxMatch) fail(`brand/${file} 缺少 viewBox`);
  const [minX, minY, width, height] = viewBoxMatch[1].trim().split(/[\s,]+/).map(Number);
  if (![minX, minY, width, height].every(Number.isFinite)) fail(`brand/${file} 的 viewBox 无法解析`);
  const strokeMatch = text.match(/stroke="(#[0-9A-Fa-f]{6})"/);
  if (!strokeMatch) fail(`brand/${file} 缺少 stroke 颜色`);
  const pathCount = (text.match(/<path\b/g) || []).length;
  const paths = [...text.matchAll(/<path\s+d="([^"]+)"\s+stroke-width="([0-9.]+)"\s*\/>/g)].map(
    (m) => ({ d: m[1].trim(), width: Number(m[2]) }),
  );
  if (paths.length === 0) fail(`brand/${file} 未解析到 path（需要 d + stroke-width 属性）`);
  if (paths.length !== pathCount) fail(`brand/${file} 中有 path 缺少 stroke-width（解析 ${paths.length}/${pathCount}）`);
  return { file, minX, minY, width, height, stroke: strokeMatch[1], paths };
}

// 把简单的 M/L/Z 路径拆成顶点（本品牌只有三角形 + 内线，足够用）
function pathPoints(d) {
  const points = [];
  for (const token of d.match(/[MLZ][^MLZ]*/g) || []) {
    const cmd = token[0];
    if (cmd === 'Z') continue;
    const nums = token.slice(1).trim().split(/[\s,]+/).filter(Boolean).map(Number);
    for (let i = 0; i + 1 < nums.length; i += 2) points.push([nums[i], nums[i + 1]]);
  }
  return points;
}

// 墨迹 bbox（含 stroke；round cap / join 下顶点外扩 = strokeWidth / 2）
function inkBounds(svg) {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let halfStroke = 0;
  for (const path of svg.paths) {
    for (const [x, y] of pathPoints(path.d)) {
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
    halfStroke = Math.max(halfStroke, path.width / 2);
  }
  if (!Number.isFinite(minX)) fail(`brand/${svg.file} 的 path d 无法解析出坐标`);
  return {
    minX: minX - halfStroke,
    minY: minY - halfStroke,
    maxX: maxX + halfStroke,
    maxY: maxY + halfStroke,
    width: maxX - minX + halfStroke * 2,
    height: maxY - minY + halfStroke * 2,
    halfStroke,
  };
}

// ════════════════════════════════════════════════════════════════════
// XML 拼装（输出顺序固定、换行固定 = 逐字节确定）
// ════════════════════════════════════════════════════════════════════

const XML_HEAD = '<?xml version="1.0" encoding="utf-8"?>';

function fmt(n) {
  const s = n.toFixed(6).replace(/0+$/, '').replace(/\.$/, '');
  return s === '-0' ? '0' : s;
}

function attrLines(indent, attrs) {
  return attrs.map(([key, value]) => `${indent}android:${key}="${value}"`);
}

// group 变换：Android vector drawable 的 group 先 pivot/scale，再 translate；
// pivot 为 0 时等价于 out = scale * in + translate。
function groupAttrs(transform) {
  const attrs = [];
  if (transform.scale !== undefined && transform.scale !== 1) {
    attrs.push(['scaleX', fmt(transform.scale)]);
    attrs.push(['scaleY', fmt(transform.scale)]);
  }
  if (transform.translateX !== undefined && transform.translateX !== 0) {
    attrs.push(['translateX', fmt(transform.translateX)]);
  }
  if (transform.translateY !== undefined && transform.translateY !== 0) {
    attrs.push(['translateY', fmt(transform.translateY)]);
  }
  return attrs;
}

function pathLines(paths, color, indent) {
  const pad = ' '.repeat(indent);
  const lines = [];
  for (const path of paths) {
    lines.push(`${pad}<path`);
    lines.push(`${pad}    android:pathData="${path.d}"`);
    lines.push(`${pad}    android:fillColor="#00000000"`);
    lines.push(`${pad}    android:strokeColor="${color}"`);
    lines.push(`${pad}    android:strokeWidth="${path.width}"`);
    lines.push(`${pad}    android:strokeLineCap="round"`);
    lines.push(`${pad}    android:strokeLineJoin="round" />`);
  }
  return lines;
}

function buildVector({ comment, sizeDp, viewportWidth, viewportHeight, transform, paths, color }) {
  const lines = [XML_HEAD, `<!-- ${comment} -->`, '<vector xmlns:android="http://schemas.android.com/apk/res/android"'];
  lines.push(...attrLines('    ', [
    ['width', `${sizeDp}dp`],
    ['height', `${sizeDp}dp`],
    ['viewportWidth', String(viewportWidth)],
    ['viewportHeight', String(viewportHeight)],
  ]));
  const attrs = groupAttrs(transform || {});
  if (attrs.length > 0) {
    lines[lines.length - 1] += '>';
    lines.push('    <group');
    lines.push(...attrLines('        ', attrs));
    lines[lines.length - 1] += '>';
    lines.push(...pathLines(paths, color, 8));
    lines.push('    </group>');
    lines.push('</vector>');
    return lines.join('\n') + '\n';
  }
  lines[lines.length - 1] += '>';
  lines.push(...pathLines(paths, color, 4));
  lines.push('</vector>');
  return lines.join('\n') + '\n';
}

function buildAdaptiveIcon() {
  const lines = [
    XML_HEAD,
    `<!-- ${GENERATED_NOTE}values/colors.xml 的 launcher_background（白底板）+ drawable/ic_launcher_foreground.xml -->`,
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
    '    <background android:drawable="@color/launcher_background" />',
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />',
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />',
    '</adaptive-icon>',
  ];
  return lines.join('\n') + '\n';
}

// ════════════════════════════════════════════════════════════════════
// 输出清单
// ════════════════════════════════════════════════════════════════════

function makeOutputs(src) {
  // mark: 512 画布直接做 viewport，无需变换
  const markVector = (svg, color) => ({
    viewportWidth: MARK_VIEWPORT,
    viewportHeight: MARK_VIEWPORT,
    transform: {},
    paths: svg.paths,
    color,
  });
  // favicon / micro: viewBox="40 40 430 430" -> viewport 430x430，用 group 平移 -40
  const faviconVector = (svg, color) => ({
    viewportWidth: FAVICON_VIEWPORT,
    viewportHeight: FAVICON_VIEWPORT,
    transform: { translateX: -svg.minX, translateY: -svg.minY },
    paths: svg.paths,
    color,
  });

  // adaptive icon 前景：favicon 加粗稿 + 108dp 画布，墨迹 bbox 缩放到 LAUNCHER_CONTENT_DP 并居中
  const launcherInk = inkBounds(src.favicon);
  const launcherScale = LAUNCHER_CONTENT_DP / launcherInk.width;
  const launcherCenterX = (launcherInk.minX + launcherInk.maxX) / 2;
  const launcherCenterY = (launcherInk.minY + launcherInk.maxY) / 2;
  const launcherTransform = {
    scale: launcherScale,
    translateX: LAUNCHER_CANVAS_DP / 2 - launcherCenterX * launcherScale,
    translateY: LAUNCHER_CANVAS_DP / 2 - launcherCenterY * launcherScale,
  };

  // Android 12 Splash：mark 512 视口映射到 190dp 内容区，居中于 288dp 画布
  const splashScale = SPLASH_CONTENT_DP / MARK_VIEWPORT;
  const splashTransform = {
    scale: splashScale,
    translateX: (SPLASH_CANVAS_DP - SPLASH_CONTENT_DP) / 2,
    translateY: (SPLASH_CANVAS_DP - SPLASH_CONTENT_DP) / 2,
  };

  const outputs = [
    {
      rel: 'drawable/brand_mark.xml',
      kind: 'vector',
      viewport: MARK_VIEWPORT,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-mark.svg（512 画布 / stroke 12·9 / 墨迹约 71.5%）。尺寸档：≥64dp。`,
        sizeDp: MARK_DP,
        ...markVector(src.mark, INK),
      }),
    },
    {
      rel: 'drawable/brand_mark_white.xml',
      kind: 'vector',
      viewport: MARK_VIEWPORT,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-mark-on-dark.svg（mark 白色版，深色底用）。尺寸档：≥64dp。`,
        sizeDp: MARK_DP,
        ...markVector(src.markWhite, WHITE),
      }),
    },
    {
      rel: 'drawable/brand_favicon.xml',
      kind: 'vector',
      viewport: FAVICON_VIEWPORT,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-favicon.svg（430 视口 / stroke 20·16）。尺寸档：≤48dp。`,
        sizeDp: FAVICON_DP,
        ...faviconVector(src.favicon, INK),
      }),
    },
    {
      rel: 'drawable/brand_favicon_white.xml',
      kind: 'vector',
      viewport: FAVICON_VIEWPORT,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-favicon-white.svg（favicon 白色版，深色底用）。尺寸档：≤48dp。`,
        sizeDp: FAVICON_DP,
        ...faviconVector(src.faviconWhite, WHITE),
      }),
    },
    {
      rel: 'drawable/brand_micro.xml',
      kind: 'vector',
      viewport: FAVICON_VIEWPORT,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-micro.svg（430 视口 / stroke 30·24，16-24dp 光学变体）。尺寸档：16-24dp。`,
        sizeDp: MICRO_DP,
        ...faviconVector(src.micro, INK),
      }),
    },
    {
      rel: 'drawable/ic_launcher_foreground.xml',
      kind: 'vector',
      viewport: LAUNCHER_CANVAS_DP,
      content: buildVector({
        comment: `${GENERATED_NOTE}brand/2-pyramid-favicon.svg（加粗稿）缩放至 108dp 画布中心，内容 bbox ${LAUNCHER_CONTENT_DP}dp（安全区 ≤66dp）。`,
        sizeDp: LAUNCHER_CANVAS_DP,
        viewportWidth: LAUNCHER_CANVAS_DP,
        viewportHeight: LAUNCHER_CANVAS_DP,
        transform: launcherTransform,
        paths: src.favicon.paths,
        color: INK,
      }),
    },
    {
      rel: 'drawable/ic_launcher_monochrome.xml',
      kind: 'vector',
      viewport: LAUNCHER_CANVAS_DP,
      content: buildVector({
        comment:
          `${GENERATED_NOTE}ic_launcher_foreground.xml 的 alpha mask（Android 13 单色图标）。` +
          `几何与前景一致，颜色不参与渲染（系统按 alpha 着色）。`,
        sizeDp: LAUNCHER_CANVAS_DP,
        viewportWidth: LAUNCHER_CANVAS_DP,
        viewportHeight: LAUNCHER_CANVAS_DP,
        transform: launcherTransform,
        paths: src.favicon.paths,
        color: BLACK,
      }),
    },
    {
      rel: 'drawable/brand_splash.xml',
      kind: 'vector',
      viewport: SPLASH_CANVAS_DP,
      content: buildVector({
        comment:
          `${GENERATED_NOTE}brand/2-pyramid-mark.svg（512 画布）映射到 ${SPLASH_CONTENT_DP}dp 内容区，` +
          `居中于 ${SPLASH_CANVAS_DP}dp 画布；日间黑 mark，夜间由 drawable-night/brand_splash.xml 覆盖为白 mark。`,
        sizeDp: SPLASH_CANVAS_DP,
        viewportWidth: SPLASH_CANVAS_DP,
        viewportHeight: SPLASH_CANVAS_DP,
        transform: splashTransform,
        paths: src.mark.paths,
        color: INK,
      }),
    },
    {
      rel: 'drawable-night/brand_splash.xml',
      kind: 'vector',
      viewport: SPLASH_CANVAS_DP,
      content: buildVector({
        comment:
          `${GENERATED_NOTE}brand/2-pyramid-mark-on-dark.svg（白色 mark）。` +
          `Android 12 SplashScreen 夜间版本，与 drawable/brand_splash.xml 同几何、仅颜色不同。`,
        sizeDp: SPLASH_CANVAS_DP,
        viewportWidth: SPLASH_CANVAS_DP,
        viewportHeight: SPLASH_CANVAS_DP,
        transform: splashTransform,
        paths: src.markWhite.paths,
        color: WHITE,
      }),
    },
    {
      rel: 'mipmap-anydpi-v26/ic_launcher.xml',
      kind: 'adaptive-icon',
      content: buildAdaptiveIcon(),
    },
    {
      rel: 'mipmap-anydpi-v26/ic_launcher_round.xml',
      kind: 'adaptive-icon',
      content: buildAdaptiveIcon(),
    },
  ];
  return outputs;
}

// ════════════════════════════════════════════════════════════════════
// 自检
// ════════════════════════════════════════════════════════════════════

function expectedPathData(src) {
  return [src.mark, src.markWhite, src.favicon, src.faviconWhite, src.micro].flatMap((svg) =>
    svg.paths.map((path) => path.d),
  );
}

function verifyOutputs(outputs, src) {
  const problems = [];
  const knownPathData = expectedPathData(src);
  for (const out of outputs) {
    const abs = join(RES, out.rel);
    if (!existsSync(abs)) {
      problems.push(`缺少生成文件: res/${out.rel}`);
      continue;
    }
    if (statSync(abs).size === 0) problems.push(`生成文件为空: res/${out.rel}`);
    if (out.kind === 'vector') {
      const text = readFileSync(abs, 'utf8');
      const count = (text.match(/<path\b/g) || []).length;
      if (count !== 2) problems.push(`res/${out.rel} path 数应为 2，实际 ${count}`);
      for (const line of text.split('\n')) {
        const m = line.match(/android:pathData="([^"]+)"/);
        if (m && !knownPathData.includes(m[1])) problems.push(`res/${out.rel} pathData 与品牌源不一致: ${m[1].slice(0, 40)}`);
      }
      if (!text.includes(`android:viewportWidth="${out.viewport}"`)) {
        problems.push(`res/${out.rel} viewport 应为 ${out.viewport}`);
      }
      if (!text.includes('android:strokeColor="')) problems.push(`res/${out.rel} 缺少 strokeColor`);
    }
    if (out.kind === 'adaptive-icon') {
      const text = readFileSync(abs, 'utf8');
      for (const ref of [
        '@color/launcher_background',
        '@drawable/ic_launcher_foreground',
        '@drawable/ic_launcher_monochrome',
      ]) {
        if (!text.includes(ref)) problems.push(`res/${out.rel} 缺少 ${ref}`);
      }
    }
  }
  return problems;
}

// ════════════════════════════════════════════════════════════════════
// main
// ════════════════════════════════════════════════════════════════════

function main() {
  const src = Object.fromEntries(Object.entries(SVG_FILES).map(([key, file]) => [key, parseSvg(file)]));
  const outputs = makeOutputs(src);

  console.log('品牌源: brand/（5 个 SVG 参与生成；2 个 horizontal 与 2 个 PNG 不参与）');
  console.log(`输出目录: 2fa-android/app/src/main/res/${CHECK_ONLY ? '（--check 只校验，不写盘）' : ''}`);

  const changed = [];
  const unchanged = [];
  if (!CHECK_ONLY) {
    for (const out of outputs) {
      const abs = join(RES, out.rel);
      mkdirSync(dirname(abs), { recursive: true });
      const old = existsSync(abs) ? readFileSync(abs, 'utf8') : null;
      if (old === out.content) {
        unchanged.push(out.rel);
      } else {
        writeFileSync(abs, out.content, 'utf8');
        changed.push(out.rel);
      }
    }
  } else {
    for (const out of outputs) {
      const abs = join(RES, out.rel);
      const old = existsSync(abs) ? readFileSync(abs, 'utf8') : null;
      if (old === out.content) unchanged.push(out.rel);
      else changed.push(out.rel);
    }
  }

  for (const out of outputs) {
    const state = changed.includes(out.rel) ? (CHECK_ONLY ? '待更新' : '写入') : '未变化';
    const viewport = out.viewport ? `viewport ${out.viewport}` : 'adaptive-icon';
    console.log(`  [${state}] ${`res/${out.rel}`.padEnd(42)} ${viewport}`);
  }

  const problems = verifyOutputs(outputs, src);
  if (CHECK_ONLY && changed.length > 0) problems.push(`--check: ${changed.length} 个文件与期望内容不一致`);

  if (problems.length > 0) {
    console.error('\n自检失败:');
    for (const problem of problems) console.error(`  ✗ ${problem}`);
    process.exit(1);
  }

  const fg = inkBounds(src.favicon);
  const fgScale = LAUNCHER_CONTENT_DP / fg.width;
  console.log('\n自检通过:');
  console.log(`  文件存在: ${outputs.length}/${outputs.length}（res/drawable 9 + mipmap-anydpi-v26 2）`);
  console.log(`  path 数: 每个 vector 2 条（外三角 + 内斜线）；pathData 与 brand/*.svg 的 d 逐字一致`);
  console.log(
    `  adaptive icon: 108dp 画布 / favicon 墨迹 bbox ${fmt(LAUNCHER_CONTENT_DP)}dp` +
      `（安全区 ≤66dp）/ 缩放 ${fmt(fgScale)}`,
  );
  console.log(
    `  splash: ${SPLASH_CANVAS_DP}dp 画布 / mark 512 视口映射到 ${SPLASH_CONTENT_DP}dp（图标安全区 ≤192dp）`,
  );
  console.log(
    `\n结果: ${CHECK_ONLY ? (changed.length === 0 ? '全部一致' : `${changed.length} 个需更新`) : `${changed.length} 个写入 / ${unchanged.length} 个未变化（未变化的文件逐字节一致）`}`,
  );
}

main();
