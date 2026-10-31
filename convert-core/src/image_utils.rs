//! 图像工具（引擎与原生实现共用）
//!
//! Android 版只保留桌面引擎实际引用的两个函数 + `get_uimage_path` 的 Android 实现：
//! * [`paste_region`] / [`swap_and_mirror`]：与桌面同签名同实现（`chest_region.rs`、
//!   `natives/reverse/*` 等 10 处引用）。
//! * [`get_uimage_path`]：**Android 适配点**。桌面版经桌面资源解析器 +
//!   `overlay::user_data_root_dir` 多策略解析并在找不到时创建默认目录；Android 无
//!   桌面 UI 框架 / 无 UImage assets，改为只认 `runtime::uimage_dir()`：
//!   * 未注入 runtime / 目录不存在 / 目录为空 → `Err`（引擎 7 个调用点已有
//!     "UImage not available, skip" 分支，见报告 S25 / CONTRACT §4.3-3）；
//!   * **不创建目录、不写 README**（避免污染 app 的 filesDir，与桌面实现的关键差异）；
//!   * 目录存在且至少含 1 个条目才返回 `Ok`（"空目录"对生成任务没有意义）。

use image::{imageops, GenericImage, RgbaImage};

/// 获取 UImage 资源目录（Android 版：只走 runtime 注入，缺失即 `Err`）。
///
/// 7 个调用点全部是 `match get_uimage_path() { Ok(p) => …, Err(e) => log_info!("UImage … skip") }`，
/// 因此返回 `Err` 就是既有的**优雅降级**语义，不是错误状态。
pub fn get_uimage_path() -> Result<std::path::PathBuf, String> {
    let dir = crate::runtime::uimage_dir()
        .ok_or_else(|| "UImage not available: runtime not initialized".to_string())?;
    if !dir.is_dir() {
        return Err(format!(
            "UImage not available: {} is not a directory",
            dir.display()
        ));
    }
    // 空目录 = 没有素材可用（Android 侧当前只 mkdirs 了 filesDir/UImage，是空的）。
    let has_entry = std::fs::read_dir(dir)
        .map_err(|e| format!("UImage not available: failed to read {}: {e}", dir.display()))?
        .next()
        .is_some();
    if !has_entry {
        return Err(format!(
            "UImage not available: {} is empty",
            dir.display()
        ));
    }
    Ok(dir.to_path_buf())
}

/// Copy `src` into `dst` at the given top-left coordinate using **raw RGBA
/// overwrites** (no alpha blending). This matches Pillow's
/// `Image.paste(src, box)` without a mask — every channel of every pixel
/// of `src` replaces the destination byte-for-byte, including the source's
/// alpha values.
///
/// Contrast this with `image::imageops::overlay`, which blends by source
/// alpha. Use this helper when the converter is *moving* a region from one
/// spot to another on the same atlas (or onto a freshly cleared canvas);
/// keep `imageops::overlay` when the intent is to layer a semi-transparent
/// decal on top of an existing pixel (matches `Image.alpha_composite`).
pub fn paste_region(dst: &mut RgbaImage, src: &RgbaImage, dx: u32, dy: u32) -> Result<(), String> {
    dst.copy_from(src, dx, dy).map_err(|e| e.to_string())
}

/// 对应 Python 的 swap_and_mirror
/// 交换两个区域，并各自进行 180 度旋转（镜像翻转）
pub fn swap_and_mirror(
    img: &mut RgbaImage,
    x1: u32, y1: u32, x2: u32, y2: u32, // 区域1和2的起点
    w: u32, h: u32,                   // 区域的宽高
) -> image::ImageResult<()> {
    // 裁剪区域 1 和 2
    let region1 = imageops::crop_imm(img, x1, y1, w, h).to_image();
    let region2 = imageops::crop_imm(img, x2, y2, w, h).to_image();

    // 进行 180 度翻转(水平+垂直)
    let flipped1 = imageops::flip_vertical(&imageops::flip_horizontal(&region1));
    let flipped2 = imageops::flip_vertical(&imageops::flip_horizontal(&region2));

    // 交换粘贴
    img.copy_from(&flipped2, x1, y1)?;
    img.copy_from(&flipped1, x2, y2)?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// **Android 差异点回归**：UImage 缺失/为空必须返回 `Err`（引擎据此跳过依赖 UImage
    /// 的生成任务），且 `get_uimage_path` **不得创建目录/写文件**（桌面实现会创建默认目录）。
    ///
    /// 与 `runtime` 的用例共享进程级 `OnceLock`：若它先注入，则复用其路径并只在
    /// "不存在/为空"时断言（顺序无关、可并行）。
    #[test]
    fn uimage_path_errs_without_content_and_never_creates_dir() {
        if crate::runtime::runtime_paths().is_none() {
            let base = std::env::temp_dir().join("2pyr-uimage-test");
            let _ = std::fs::remove_dir_all(&base);
            let _ = crate::runtime::set_runtime(crate::runtime::RuntimePaths {
                log_dir: base.join("log"),
                app_data_dir: base.join("data"),
                uimage_dir: base.join("UImage"),
                temp_dir: base.join("cache"),
            });
        }
        let dir = crate::runtime::uimage_dir()
            .expect("runtime must be initialized by now")
            .to_path_buf();
        let non_empty = dir
            .is_dir()
            .then(|| std::fs::read_dir(&dir).map(|mut it| it.next().is_some()).unwrap_or(false))
            .unwrap_or(false);
        if non_empty {
            // 已注入一个真实可用的 UImage 目录（例如以后加集成测试），此处不适用。
            assert!(get_uimage_path().is_ok());
            return;
        }
        assert!(
            get_uimage_path().is_err(),
            "缺失/为空的 UImage 目录必须返回 Err 以走引擎的 skip 分支"
        );
        assert!(
            !dir.exists(),
            "get_uimage_path 不得创建 UImage 目录（与桌面实现的差异，见 CONTRACT §4.3-3）"
        );
    }
}
