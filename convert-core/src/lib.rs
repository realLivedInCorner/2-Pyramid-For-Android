//! # convert-core
//!
//! 2-Pyramid for Android（2FA）的 Rust 转换核心。
//! 剥离自 Hurricane desktop 版（Tauri shell 已移除），通过 [UniFFI](https://github.com/mozilla/uniffi-rs)
//! 暴露给 Kotlin 层调用。
//!
//! ## 模块清单
//! - [`converters`] — 87 个资源包转换模块（正向/反向）
//! - [`hurray`] — DTD 调度器（Eraser → Architect → Surgeon 三层任务模型）
//! - [`invoke_conversion`] — 转换流水线入口
//! - [`image_utils`] / [`color_utils`] — 图像处理工具
//! - [`overlay`] — 资源包叠加项目（Overlay）管理
//! - [`logger`] — 文件 + 内存双写日志
//! - [`runtime`] — 全局运行时路径（Android 端通过 `init_runtime` 注入）
//!
//! ## Android 集成
//! 1. 在 Kotlin `Application.onCreate()` 调 `init_runtime(logDir, appDataDir, uimageDir)`
//! 2. 后台 `Service` 调 `convert_zip(...)` 触发转换
//! 3. 进度回调走 UniFFI callback interface
//!
//! ## 桌面端直接跑（`cargo run --example smoke`）
//! ```bash
//! cargo run --example smoke -- /path/to/pack.zip 34
//! ```

pub mod color_utils;
pub mod converters;
pub mod hurray;
pub mod image_utils;
pub mod invoke_conversion;
pub mod logger;
pub mod overlay;
pub mod runtime;

pub use runtime::{runtime_paths, set_runtime, RuntimePaths};

// UniFFI 0.32: setup_scaffolding! 生成 UniFfiTag + NAMESPACE metadata
uniffi::setup_scaffolding!();

/// 转换错误。UniFFI 0.32 要求 throw type 必须实现 uniffi::Error，
/// 用 flat_error 把 String 直接暴露给 binding 层。
#[derive(Debug, uniffi::Error)]
#[uniffi(flat_error)]
pub enum ConvertError {
    InputNotFound { path: String },
    BedrockNotSupported,
    Conversion { msg: String },
}

impl std::fmt::Display for ConvertError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConvertError::InputNotFound { path } => write!(f, "input file not found: {}", path),
            ConvertError::BedrockNotSupported => write!(f, "bedrock conversion not supported yet"),
            ConvertError::Conversion { msg } => write!(f, "{}", msg),
        }
    }
}

impl std::error::Error for ConvertError {}

// ── UniFFI 接口 ─────────────────────────────

/// 注入运行时路径（log_dir / app_data_dir / uimage_dir）。
/// **只能调用一次**，重复调用会返回错误。
#[uniffi::export]
pub fn init_runtime(
    log_dir: String,
    app_data_dir: String,
    uimage_dir: String,
) -> Result<(), ConvertError> {
    set_runtime(RuntimePaths {
        log_dir: std::path::PathBuf::from(log_dir),
        app_data_dir: std::path::PathBuf::from(app_data_dir),
        uimage_dir: std::path::PathBuf::from(uimage_dir),
    })
    .map_err(|e| ConvertError::Conversion { msg: e })
}

/// 转换报告
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConvertReport {
    /// 转换后输出 zip 路径
    pub output_path: String,
    /// 源 pack_format（从 pack.mcmeta 读取）
    pub source_version: u32,
    /// 目标 pack_format
    pub target_version: u32,
    /// 日志文件路径
    pub log_path: String,
}

/// 转换请求
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConvertRequest {
    /// 输入 .zip 路径（绝对路径，由 Android 端通过 SAF/ContentResolver 拿到真实路径后传入）
    pub input_zip: String,
    /// 输出目录（生成的 zip 写到这下面）
    pub output_dir: String,
    /// 目标 pack_format（高版本数值，如 34 = 1.21）
    pub target_version: u32,
}

/// 单个 zip 转换。
///
/// 解压 → 读 pack.mcmeta 推断 source_version → 跑 DTD 调度管线 → 重新打包 → 返回输出路径。
#[uniffi::export]
pub fn convert_zip(req: ConvertRequest) -> Result<ConvertReport, ConvertError> {
    use crate::converters::version_converter::process_zip;
    use std::path::Path;

    let input_zip = Path::new(&req.input_zip);
    if !input_zip.exists() {
        return Err(ConvertError::InputNotFound { path: req.input_zip.clone() });
    }
    // Bedrock Latest（1000）已随桌面版同步实现：先按 Java 1.21.11（75）
    // 走完整流水线，再执行 Bedrock 结构重组 + manifest.json（.mcpack）。
    // BedrockNotSupported 变体保留仅为兼容旧 binding，不再主动抛出。
    let output_path = process_zip(
        &req.input_zip,
        req.target_version,
        None,             // progress_callback (TODO: 通过 UniFFI callback 接)
        1.0,              // file_weight
        None,             // parent_folder_path
        Some(&req.output_dir),
    )
    .map_err(|e| ConvertError::Conversion { msg: e })?;

    Ok(ConvertReport {
        output_path,
        source_version: 0,
        target_version: req.target_version,
        log_path: crate::logger::GLOBAL_LOGGER
            .log_file_path_str()
            .unwrap_or_default(),
    })
}

/// 拿最近一次转换的日志（用于前端展示 / 导出）
#[uniffi::export]
pub fn get_recent_logs(limit: u32) -> Vec<String> {
    let mut logs = crate::logger::GLOBAL_LOGGER.get_logs();
    let limit = limit as usize;
    if logs.len() > limit {
        let extra = logs.len() - limit;
        logs.drain(0..extra);
    }
    logs
}

/// 设置开发者模式（开启 Info 级日志）
#[uniffi::export]
pub fn set_dev_mode(enabled: bool) {
    crate::logger::GLOBAL_LOGGER.set_dev_mode(enabled);
}

/// 查询开发者模式
#[uniffi::export]
pub fn is_dev_mode() -> bool {
    crate::logger::GLOBAL_LOGGER.is_dev_mode()
}

/// 版本号（来自 convert-core 的 Cargo.toml）
#[uniffi::export]
pub fn convert_core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
