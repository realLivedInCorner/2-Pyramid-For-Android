#!/usr/bin/env python3
"""2-Pyramid for Android 版本同步 / 一致性校验（无第三方依赖，Python 3.8+）。

版本号与构建号的约定（与桌面主仓库 2-Pyramid 对齐）：

  * 版本号   2fa-android/app/build.gradle.kts 的 `versionName`（semver MAJOR.MINOR.PATCH）
  * 构建号   仓库根 `BUILD`（整数；gradle 的 versionCode 直接读它，构建只读不写）
  * 徽章     README.md 顶部的 shields 徽章 `badge/version-X.Y.Z`
  * 发版说明 docs/compose/releases/news-<version>.md
  * tag      渠道前缀 `Stable-` / `Safe-` / `Beta-` / `UnStable-`，前缀必须能剥离

用法：

  python tools/set_version.py --check
      只校验仓库内部一致（本地开发 / PR / main 自检都可跑）。
  python tools/set_version.py --check --version Stable-2.1.0
      额外与发布 tag 对齐；CI 里不传 --version 时自动读环境变量 GITHUB_REF_NAME。
  python tools/set_version.py --set 2.1.1
      同步版本号到 build.gradle.kts 与 README 徽章（不写 BUILD）。
  python tools/set_version.py --set 2.1.1 --build 21001
      同时显式推进 BUILD（发布动作；构建过程本身绝不写 BUILD）。

退出码：0 = 检查通过 / 写入成功；1 = 有检查项失败 / 参数错误 / 读写失败。

刻意不依赖任何第三方库：CI（ubuntu runner）与本地 Windows 都能直接用系统 python 跑，
没有 pip 安装步骤，也不会因为网络问题失败。文件一律按 UTF-8 读写，并保留原有换行符。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import List, Optional, Tuple

# ── 仓库内相对路径（唯一事实来源的落点）───────────────────────────────────
GRADLE_REL = Path("2fa-android") / "app" / "build.gradle.kts"
README_REL = Path("README.md")
BUILD_REL = Path("BUILD")
NEWS_DIR_REL = Path("docs") / "compose" / "releases"

# ── 解析规则 ──────────────────────────────────────────────────────────────
# 渠道前缀按长度降序排列：`UnStable-` 必须先于 `Stable-` 匹配，否则会切错。
CHANNEL_PREFIXES = ("UnStable", "Stable", "Safe", "Beta")

SEMVER_RE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
BADGE_RE = re.compile(r"(img\.shields\.io/badge/version-)(\d+\.\d+\.\d+)")
# versionName = "2.1.0"（保留缩进与引号，--set 只替换中间的值）
GRADLE_VERSIONNAME_RE = re.compile(r'(?m)^(\s*versionName\s*=\s*")([^"]*)(")')
# versionCode = buildNumber（表达式必须是动态读取，不能是写死的数字）
GRADLE_VERSIONCODE_RE = re.compile(r"(?m)^\s*versionCode\s*=\s*(.+?)\s*$")
# 发版说明的第一个一级标题应为版本号（如 `# 2.1.0`）
NEWS_HEADING_RE = re.compile(r"(?m)^#\s+(\S+)\s*$")

# Android versionCode 是 int32，越界会在打包期报错，这里提前拦。
VERSION_CODE_MAX = 2_147_483_647


# ── 控制台 / 文件 IO ─────────────────────────────────────────────────────

def _tune_console() -> None:
    """让输出在「被捕获」与「真终端」两种场景下都不乱码。

    * 管道 / 重定向（CI、`pwsh` 捕获、`tee`）：按 UTF-8 输出。CI 与抓取端都以 UTF-8
      解码，否则中文会变成一串替换符（本仓库 Windows 控制台默认代码页是 936）。
    * 真终端（isatty）：保留系统代码页（中文 Windows 为 GBK），Windows 控制台能直接显示。
    * 两种情况都把不可编码字符降级为 '?'，避免一条特殊符号把脚本打断
      （桌面版 set_version.py 踩过 GBK 控制台编不出 ✓ 直接 crash 的坑）。
    也尊重用户显式设置的 PYTHONIOENCODING（此时 reconfigure 会失败，忽略即可）。
    """
    force_utf8 = os.environ.get("PYTHONIOENCODING") is None
    for stream in (sys.stdout, sys.stderr):
        try:
            is_tty = stream.isatty()
        except Exception:
            is_tty = False
        try:
            if force_utf8 and not is_tty:
                stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
            else:
                stream.reconfigure(errors="replace")  # type: ignore[attr-defined]
        except Exception:
            pass


def read_text(path: Path) -> str:
    """UTF-8 读；newline='' 保留文件原有换行符（仓库工作区是 CRLF）。"""
    with path.open("r", encoding="utf-8", newline="") as f:
        return f.read()


def write_text(path: Path, text: str) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        f.write(text)


class Report:
    """检查项打印 + 失败/警告计数。"""

    def __init__(self) -> None:
        self.failures: List[str] = []
        self.warnings: List[str] = []
        self.passes = 0

    @staticmethod
    def _line(mark: str, label: str, detail: str) -> None:
        print(f"  {mark:<7}{label:<16} {detail}".rstrip())

    def ok(self, label: str, detail: str = "") -> None:
        self.passes += 1
        self._line("[OK]", label, detail)

    def skip(self, label: str, detail: str = "") -> None:
        self._line("[--]", label, detail)

    def warn(self, label: str, detail: str = "") -> None:
        self.warnings.append(f"{label}: {detail}")
        self._line("[WARN]", label, detail)

    def fail(self, label: str, detail: str = "") -> None:
        self.failures.append(f"{label}: {detail}")
        self._line("[FAIL]", label, detail)


# ── tag 解析 ─────────────────────────────────────────────────────────────

def parse_tag(value: str) -> Tuple[str, Optional[str]]:
    """把发布 tag 解析成 (版本号, 渠道前缀)。

    支持：
        Stable-2.1.0   -> ("2.1.0", "Stable")
        Safe-2.1.0     -> ("2.1.0", "Safe")
        Beta-2.1.0     -> ("2.1.0", "Beta")
        UnStable-2.1.0 -> ("2.1.0", "UnStable")   # 注意前缀里本身带 '-'，不能简单 split
        v2.1.0         -> ("2.1.0", None)          # 无渠道前缀（兼容桌面更新器的 strip_v）
        2.1.0          -> ("2.1.0", None)

    抛 ValueError：格式非法（例如 Stable- 后没有版本号，或版本号不是 semver）。
    """
    raw = value.strip()
    if not raw:
        raise ValueError("tag 为空")

    for prefix in CHANNEL_PREFIXES:
        head = prefix + "-"
        if raw.startswith(head):
            version = raw[len(head):].strip()
            if not version:
                raise ValueError(f"tag '{value}' 缺少版本号（期望 {prefix}-X.Y.Z）")
            if not SEMVER_RE.match(version):
                raise ValueError(f"tag '{value}' 的版本号 '{version}' 不是 semver（X.Y.Z）")
            return version, prefix

    candidate = raw[1:] if raw.startswith("v") else raw
    if not SEMVER_RE.match(candidate):
        raise ValueError(
            f"无法从 '{value}' 解析出版本号：tag 需为 Stable-/Safe-/Beta-/UnStable- 前缀 + X.Y.Z，"
            "或裸 X.Y.Z / vX.Y.Z"
        )
    return candidate, None


def looks_like_release_tag(value: str) -> bool:
    """判断 GITHUB_REF_NAME 是否值得当发布 tag 校验。

    PR 构建里 GITHUB_REF_NAME 形如 `123/merge`，push main 时是 `main` —— 这些不是
    发布 tag，本地/PR 自检应当跳过 tag 对齐而不是报错。release workflow 由
    `on.push.tags` 限定前缀，tag 一定合法；解析失败仍会在这里报 FAIL（因为
    GITHUB_REF_NAME 已匹配发布 tag 形状）。
    """
    raw = value.strip()
    if not raw:
        return False
    if any(raw.startswith(p + "-") for p in CHANNEL_PREFIXES):
        return True
    candidate = raw[1:] if raw.startswith("v") else raw
    return bool(SEMVER_RE.match(candidate))


# ── 校验 ─────────────────────────────────────────────────────────────────

def check_repo(root: Path, tag_value: Optional[str], tag_from_cli: bool = False) -> int:
    print("== 2-Pyramid for Android 版本一致性检查 ==")
    print(f"仓库根：{root}")
    print()

    report = Report()

    # 1) tag（可选）：渠道前缀 + 版本号
    tag_version: Optional[str] = None
    tag_given = tag_value is not None and tag_value.strip() != ""
    if not tag_given:
        report.skip("tag", "未提供（GITHUB_REF_NAME / --version 都为空）——跳过 tag 对齐")
    elif not looks_like_release_tag(tag_value or ""):
        if tag_from_cli:
            # 显式 --version 写错时应该是错误，而不是静默跳过（CI 里 env 不可能走到这里：
            # release workflow 由 on.push.tags 限定前缀）。
            report.fail(
                "tag",
                f"'{tag_value}' 不是可解析的发布 tag（Stable-/Safe-/Beta-/UnStable- + X.Y.Z，或 X.Y.Z）",
            )
        else:
            report.skip("tag", f"'{tag_value}' 不是发布 tag（PR/main 构建）——跳过 tag 对齐")
    else:
        try:
            tag_version, channel = parse_tag(tag_value or "")
        except ValueError as exc:
            report.fail("tag", str(exc))
        else:
            report.ok("tag", f"{tag_value} -> 版本 {tag_version}，渠道 {channel or '无前缀'}")

    # 2) build.gradle.kts 的 versionName（版本号事实来源）
    gradle_path = root / GRADLE_REL
    gradle_text = ""
    version_name: Optional[str] = None
    if not gradle_path.is_file():
        report.fail("build.gradle.kts", f"文件不存在：{GRADLE_REL.as_posix()}")
    else:
        try:
            gradle_text = read_text(gradle_path)
        except OSError as exc:
            report.fail("build.gradle.kts", f"读取失败：{exc}")
        else:
            matches = list(GRADLE_VERSIONNAME_RE.finditer(gradle_text))
            if len(matches) != 1:
                report.fail(
                    "versionName",
                    f"在 {GRADLE_REL.as_posix()} 里找到 {len(matches)} 处 versionName（期望恰好 1 处）",
                )
            else:
                version_name = matches[0].group(2)
                if not SEMVER_RE.match(version_name):
                    report.fail("versionName", f"'{version_name}' 不是 semver（X.Y.Z）")
                    version_name = None
                elif tag_version is not None and version_name != tag_version:
                    report.fail(
                        "versionName",
                        f"tag 期望 {tag_version}，build.gradle.kts 实际 {version_name} —— 发布前漏改了版本号",
                    )
                else:
                    suffix = "（与 tag 一致）" if tag_version is not None else ""
                    report.ok("versionName", f"{version_name}{suffix}")

    # 3) BUILD（构建号事实来源）
    build_path = root / BUILD_REL
    build_number: Optional[int] = None
    if not build_path.is_file():
        report.fail("BUILD", f"文件不存在：{BUILD_REL.as_posix()}（versionCode 从它读取）")
    else:
        try:
            raw_build = read_text(build_path).strip()
        except OSError as exc:
            report.fail("BUILD", f"读取失败：{exc}")
        else:
            if not raw_build:
                report.fail("BUILD", "文件为空")
            elif not raw_build.isdigit():
                report.fail("BUILD", f"内容不是整数：'{raw_build}'")
            else:
                value = int(raw_build)
                if value <= 0 or value > VERSION_CODE_MAX:
                    report.fail("BUILD", f"{value} 越界（Android versionCode 必须是 1..{VERSION_CODE_MAX} 的整数）")
                else:
                    build_number = value
                    report.ok("BUILD", f"{value}（gradle versionCode 直接读取）")

    # 4) versionCode 必须是动态读 BUILD，且旧的 version.properties 自动 +1 机制已删除
    if gradle_text:
        code_matches = list(GRADLE_VERSIONCODE_RE.finditer(gradle_text))
        problems = []
        if len(code_matches) != 1:
            problems.append(f"versionCode 赋值出现 {len(code_matches)} 处（期望 1 处）")
        else:
            expr = code_matches[0].group(1)
            if expr.isdigit():
                problems.append(f"versionCode 被写死为 {expr}，应读取 BUILD")
        if '"../BUILD"' not in gradle_text and "'../BUILD'" not in gradle_text:
            problems.append("没有找到对仓库根 BUILD 的读取（rootProject.file(\"../BUILD\")）")
        # 只认**代码形态**的引用（注释里提到文件名是正常的，不能误报）：
        #   file("version.properties") / file('version.properties') / versionProps 变量
        if re.search(r"""file\(\s*["']version\.properties["']\s*\)""", gradle_text) or "versionProps" in gradle_text:
            problems.append("仍以代码方式引用 version.properties（自动 +1 机制应已删除）")
        if problems:
            report.fail("versionCode", "；".join(problems))
        else:
            report.ok("versionCode", "动态读取 BUILD（无 version.properties 自增）")

    # 5) README 徽章（与 versionName 一致；也是 --set 会同步的位置）
    readme_path = root / README_REL
    if not readme_path.is_file():
        report.fail("README 徽章", "README.md 不存在")
    else:
        try:
            readme_text = read_text(readme_path)
        except OSError as exc:
            report.fail("README 徽章", f"读取失败：{exc}")
        else:
            badge_matches = list(BADGE_RE.finditer(readme_text))
            if len(badge_matches) != 1:
                report.fail(
                    "README 徽章",
                    f"找到 {len(badge_matches)} 处 version 徽章（期望恰好 1 处：img.shields.io/badge/version-X.Y.Z）",
                )
            else:
                badge_version = badge_matches[0].group(2)
                if version_name is not None and badge_version != version_name:
                    report.fail(
                        "README 徽章",
                        f"徽章是 {badge_version}，build.gradle.kts 是 {version_name} —— 两处不同步",
                    )
                else:
                    report.ok("README 徽章", f"version-{badge_version}")

    # 6) 发版说明 docs/compose/releases/news-<version>.md
    news_version = tag_version or version_name
    if news_version is None:
        report.skip("news 文件", "版本号未确定，无法检查发版说明")
    else:
        news_path = root / NEWS_DIR_REL / f"news-{news_version}.md"
        news_rel = news_path.relative_to(root).as_posix()
        if not news_path.is_file():
            detail = f"缺失 {news_rel}"
            if tag_version is not None:
                report.fail("news 文件", detail + " —— 发布 tag 要求该文件存在（会作为 Release 正文）")
            else:
                report.warn("news 文件", detail + " —— 发布前必须补上")
        else:
            try:
                news_text = read_text(news_path)
            except OSError as exc:
                report.fail("news 文件", f"{news_rel} 读取失败：{exc}")
            else:
                heading = NEWS_HEADING_RE.search(news_text)
                if heading is None:
                    report.fail("news 文件", f"{news_rel} 缺少一级标题（应为 '# {news_version}'）")
                elif heading.group(1) != news_version:
                    report.fail(
                        "news 文件",
                        f"{news_rel} 的一级标题是 '{heading.group(1)}'，应为 '{news_version}'",
                    )
                else:
                    report.ok("news 文件", f"{news_rel}（标题 #{heading.group(1)}）")

    # ── 汇总 ─────────────────────────────────────────────────────────────
    print()
    if report.failures:
        print(f"检查失败：{len(report.failures)} 项未通过（{report.passes} 项通过）。")
        for item in report.failures:
            print(f"  - {item}")
        return 1

    suffix = f"，{len(report.warnings)} 项警告" if report.warnings else ""
    print(f"检查通过：{report.passes} 项通过{suffix}。")
    return 0


# ── 同步（--set）─────────────────────────────────────────────────────────

def set_repo(root: Path, new_version: str, new_build: Optional[int]) -> int:
    print("== 2-Pyramid for Android 版本同步 ==")
    print(f"仓库根：{root}")
    print()

    if not SEMVER_RE.match(new_version):
        print(f"[FAIL] '{new_version}' 不是合法 semver 版本号（MAJOR.MINOR.PATCH）")
        return 1
    if new_build is not None and not (1 <= new_build <= VERSION_CODE_MAX):
        print(f"[FAIL] --build {new_build} 越界（Android versionCode 必须是 1..{VERSION_CODE_MAX} 的整数）")
        return 1

    gradle_path = root / GRADLE_REL
    readme_path = root / README_REL

    # 先把所有替换算好并校验唯一性，全部 OK 才落盘 —— 避免只改一半。
    if not gradle_path.is_file():
        print(f"[FAIL] 文件不存在：{GRADLE_REL.as_posix()}")
        return 1
    if not readme_path.is_file():
        print(f"[FAIL] 文件不存在：{README_REL.as_posix()}")
        return 1

    gradle_text = read_text(gradle_path)
    readme_text = read_text(readme_path)

    gradle_matches = list(GRADLE_VERSIONNAME_RE.finditer(gradle_text))
    if len(gradle_matches) != 1:
        print(f"[FAIL] {GRADLE_REL.as_posix()} 里 versionName 出现 {len(gradle_matches)} 处（期望 1 处），未写入")
        return 1
    old_version = gradle_matches[0].group(2)

    badge_matches = list(BADGE_RE.finditer(readme_text))
    if len(badge_matches) != 1:
        print(f"[FAIL] {README_REL.as_posix()} 里 version 徽章出现 {len(badge_matches)} 处（期望 1 处），未写入")
        return 1

    gradle_new = GRADLE_VERSIONNAME_RE.sub(
        lambda m: m.group(1) + new_version + m.group(3), gradle_text, count=1
    )
    readme_new = BADGE_RE.sub(lambda m: m.group(1) + new_version, readme_text, count=1)

    changed: List[str] = []
    if gradle_new != gradle_text:
        write_text(gradle_path, gradle_new)
        changed.append(GRADLE_REL.as_posix())
        print(f"  [OK]   versionName       {old_version} -> {new_version}（{GRADLE_REL.as_posix()}）")
    else:
        print(f"  [--]   versionName      已是 {new_version}，无变化（{GRADLE_REL.as_posix()}）")

    if readme_new != readme_text:
        write_text(readme_path, readme_new)
        changed.append(README_REL.as_posix())
        print(f"  [OK]   README 徽章       version-{new_version}（{README_REL.as_posix()}）")
    else:
        print(f"  [--]   README 徽章      已是 version-{new_version}，无变化（{README_REL.as_posix()}）")

    if new_build is not None:
        build_path = root / BUILD_REL
        write_text(build_path, f"{new_build}\n")
        changed.append(BUILD_REL.as_posix())
        print(f"  [OK]   BUILD             {new_build}（{BUILD_REL.as_posix()}）")
    else:
        current_build = (root / BUILD_REL).read_text(encoding="utf-8").strip() if (root / BUILD_REL).is_file() else "?"
        print(f"  [--]   BUILD             保持 {current_build}（发布是显式动作，用 --build N 递增）")

    news_path = root / NEWS_DIR_REL / f"news-{new_version}.md"
    news_rel = news_path.relative_to(root).as_posix()
    print()
    if news_path.is_file():
        print(f"  [OK]   news 文件         {news_rel} 已存在")
    else:
        print(f"  [WARN] news 文件         缺失 {news_rel} —— 发布前必须撰写（会作为 Release 正文）")

    print()
    print("改动文件：" + ("、".join(changed) if changed else "无（版本号本来就一致）"))
    print()
    print("发布步骤提醒（版本号与构建号都是显式动作）：")
    print(f"  1. 刷新 CHANGELOG.md 的 [Unreleased] -> [{new_version}] 段")
    print(f"  2. 撰写 {news_rel}")
    print("  3. python tools/set_version.py --check --version Stable-<ver>   # 打 tag 前自检")
    print(f"  4. git tag Stable-{new_version} && git push origin Stable-{new_version}")
    return 0


# ── 入口 ─────────────────────────────────────────────────────────────────

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="set_version.py",
        description="2-Pyramid for Android 版本号 / 构建号同步与一致性校验（无第三方依赖）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例：\n"
            "  python tools/set_version.py --check\n"
            "  python tools/set_version.py --check --version Stable-2.1.0\n"
            "  python tools/set_version.py --set 2.1.1 --build 21001\n"
        ),
    )
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--check", action="store_true", help="只校验仓库内版本一致性，不写文件")
    action.add_argument(
        "--set",
        metavar="X.Y.Z",
        help="把版本号同步到 build.gradle.kts versionName 与 README 徽章",
    )
    parser.add_argument(
        "--build",
        metavar="N",
        type=int,
        default=None,
        help="与 --set 连用：显式推进 BUILD 构建号（发布动作）",
    )
    parser.add_argument(
        "--version",
        metavar="TAG",
        default=None,
        help="与 --check 连用：发布 tag（如 Stable-2.1.0）；默认读环境变量 GITHUB_REF_NAME",
    )
    parser.add_argument(
        "--root",
        metavar="DIR",
        default=None,
        help="仓库根目录（默认：本脚本所在 tools/ 的上一级；自测时可指向副本）",
    )
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    _tune_console()
    args = build_parser().parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parent.parent
    if not root.is_dir():
        print(f"[FAIL] 仓库根目录不存在：{root}")
        return 1

    if args.check:
        if args.build is not None:
            print("[FAIL] --build 只能与 --set 连用")
            return 1
        if args.version is not None:
            return check_repo(root, args.version, tag_from_cli=True)
        return check_repo(root, os.environ.get("GITHUB_REF_NAME"), tag_from_cli=False)

    # --set
    if args.version is not None:
        print("[FAIL] --version 只能与 --check 连用")
        return 1
    return set_repo(root, args.set or "", args.build)


if __name__ == "__main__":
    sys.exit(main())
