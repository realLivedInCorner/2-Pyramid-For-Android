//! # convert-core（Android）
//!
//! 2-Pyramid for Android（2FA）的 Rust 转换核心。**本 crate 是桌面版 2.203.0（HEAD `3dd0e45`）
//! M3 / A-ROM 引擎的整体移植**，通过 [UniFFI](https://github.com/mozilla/uniffi-rs) 暴露给
//! Kotlin 层调用。与桌面的差异只集中在「适配层」，不重写算法：
//!
//! | 适配点 | 位置 | 说明 |
//! |---|---|---|
//! | 日志宏 | [`logger`] | 桌面同名同签名 4 个宏（`log_*!`），引擎 150 处调用零改动 |
//! | UImage 路径 | [`image_utils::get_uimage_path`] | 走 [`runtime::uimage_dir()`]；无资源返回 `Err` 走既有 skip |
//! | 配置 shim | [`commands::read_config_file`] | 只保留 `output_naming` 的内存态（桌面读 JSON 文件） |
//! | 进度 | [`progress`] | **Android 新增**：全局 `ProgressSink`，extract/pipeline/output 三段权重 |
//! | 临时目录 | [`runtime::temp_dir`] | app cache；`init_runtime` 同时 `set_var("TMPDIR", …)` |
//! | 构建号 | `build.rs` | `cargo:rustc-env=BUILD_NUMBER`（引擎 `env!("BUILD_NUMBER")` 依赖） |
//!
//! ## 模块清单（与桌面引擎源码目录同名）
//! - [`arom`] — A-ROM 对象模型 / 调度器 / 序列化
//! - [`natives`] — 46 个原生转换任务（原桌面 `natives/`，按 eraser/architect/surgeon/reverse 分组）
//! - [`pack`] — 包 I/O、结构分析、差异工具、生产入口 `version_converter`
//! - [`bedrock_convert`] — Java ⇄ Bedrock 结构转换
//! - [`native_run`] — 驱动（按计划逐任务派发 `Tx`）
//! - [`task_registry`] — 任务元数据表（名字 / 并发类型 / 阶段）
//! - [`gen_metadata`] — 产物根 `2pyr-gen.json`
//! - [`color`] / [`chest_region`] / [`scale_factor`] — 原生实现共用的图像工具
//! - [`logger`] / [`runtime`] / [`image_utils`] / [`commands`] / [`progress`] — Android 适配层
//!
//! ## Android 集成
//! 1. Kotlin `Application.onCreate()` 调 `init_runtime(logDir, appDataDir, uimageDir, cacheDir)`
//! 2. 后台 `Service` 调 `convert_zip(ConvertRequest, ConvertProgress)` 触发转换
//! 3. 进度经 UniFFI callback 回到 Kotlin（`on_progress(fraction, phase)`）

pub mod arom;
/// 基岩 ↔ Java 结构转换（Bedrock 目标 / Bedrock 源预检）。
mod bedrock_convert;
/// **Android 适配层**：桌面 `commands/` 的最小内存 shim（只保留输出命名模板）。
mod commands;
/// 资源包 I/O 与分析工具（原桌面 `converters/` 拆分后的生产必需部分）。
mod pack;
mod gen_metadata;
pub mod native_run;
/// **原生实现主体**：46 个任务的 A-ROM 实现（桌面 `natives/` 整体搬迁）。
///
/// 目录按职责分六组（`native/` `eraser/` `architect/` `surgeon/` `reverse/` `tests/`），
/// 模块路径仍是 `crate::natives::<任务名>`（分组用 `#[path]` 声明，不改路径）。
pub mod natives;
mod image_utils;
mod invoke_conversion;
mod logger;
/// 箱子贴图区域变换（供原生实现使用）。
mod chest_region;
/// 颜色/HSV 工具（供原生实现使用）。
mod color;
/// 缩放因子（`determine_scale_factor`）：原生实现与测试共用。
mod scale_factor;
/// **任务元数据表**（名字 / 并发类型 / 阶段）——生产驱动取阶段的唯一来源。
mod task_registry;
/// **Android 适配层**：全局进度上报（桌面版没有对应模块，见模块文档）。
mod progress;
mod runtime;

pub use runtime::{runtime_paths, set_runtime, RuntimePaths};

/// **仅 host 端工具使用**（`examples/convert_cli.rs`）：把命令行版本串解析成 pack_format。
///
/// 非 `#[uniffi::export]`，因此**不会**出现在 Kotlin binding 里（不影响 facade 契约）；
/// 目的只是让 host 差分 runner 与 Android 生产入口共用同一份版本解析逻辑。
#[doc(hidden)]
pub use pack::version_converter::{pack_format_label_for_output, resolve_target_format};

// UniFFI 0.32: setup_scaffolding! 生成 UniFfiTag + NAMESPACE metadata
uniffi::setup_scaffolding!();

/// 转换错误。UniFFI 0.32 要求 throw type 必须实现 uniffi::Error，
/// 用 flat_error 把 String 直接暴露给 binding 层。
#[derive(Debug, uniffi::Error)]
#[uniffi(flat_error)]
pub enum ConvertError {
    InputNotFound { path: String },
    #[allow(dead_code)]
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

// ── UniFFI 契约（与 `_sync/CONTRACT.md` §4.1 / Kotlin 侧严格一致）──────

/// 转换选项。字段与默认值口径见 CONTRACT §4.1 / 报告 §2.4（默认值由 Kotlin 侧填入，
/// Rust 端不二次猜测）。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConvertOptions {
    /// 目标 pack_format（如 55 / 97 / 1000=Bedrock）
    pub target_version: u32,
    /// 修复 Alpha 图层。**桌面 2.203.0 起是死开关（no-op）**：字段保留是因为
    /// `2pyr-gen.json` 的开关字段是对外契约；引擎内无消费者（CONTRACT §9-4 / 报告 S14）。
    pub fix_alpha_layers: bool,
    /// 着色器适配（**计划级过滤**：为 false 时不派发 `adapt_java_shaders`，不是任务内早退）
    pub adapt_shaders: bool,
    /// 彻底删除 shaders（破坏性；删 `assets/minecraft/shaders` 与顶层 `shaders/`）
    pub delete_shaders: bool,
    /// 生成 1.21.4+ 物品模型定义（`assets/<ns>/items/<name>.json`）
    pub convert_item_definitions: bool,
    /// 转换 OptiFine CIT（2026-10 止血后默认关；打开时保留源文件）
    pub convert_cit: bool,
    /// 彻底删除 CIT 源目录（破坏性；优先于 `convert_cit`）
    pub delete_cit: bool,
    /// 在 Java 目标产物根写 `2pyr-gen.json`（Bedrock 目标不写）
    pub write_gen_metadata: bool,
    /// 输出命名模板；`None` => `[Ver][Name]`（桌面默认）
    pub output_naming: Option<String>,
}

/// 转换请求。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConvertRequest {
    /// 输入文件路径（Android 端由 SAF/ContentResolver 复制到可读路径后传入）
    pub input_zip: String,
    /// 输出目录（产物写到这下面；目录不存在会被创建）
    pub output_dir: String,
    pub options: ConvertOptions,
}

/// 转换报告（桌面版没有这个结构：`MixedRunReport.skipped/notes` 只写日志，
/// Android 需要它们在结果页展示，见 CONTRACT §4.1 / 报告 E2）。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConvertReport {
    /// 转换后输出文件路径（Java 目标 `.zip`，Bedrock 目标 `.mcpack`）
    pub output_path: String,
    /// 源 pack_format（读输入包 `pack.mcmeta`；Bedrock 源 => 1000）
    pub source_version: u32,
    /// 目标 pack_format（回显请求）
    pub target_version: u32,
    /// 日志文件路径（未初始化 runtime 时为空串）
    pub log_path: String,
    /// `MixedRunReport.skipped`：任务主动跳过的次数
    pub skipped: u32,
    /// `MixedRunReport.notes`：跳过原因与普通进展说明（**notes ≠ 跳过**，UI 不要统一标注）
    pub notes: Vec<String>,
    /// 本次转换总耗时（`ConversionTiming.total_s * 1000`，含 IO）
    pub elapsed_ms: u64,
}

/// 进度回调接口（Kotlin 侧实现它）。
///
/// `progress ∈ [0.0, 1.0]`；`phase` 为短英文串：
/// `extract`（解压）/ 任务名（`convert` 段，如 `delete_font_folder`）/ `output`（打包）。
#[uniffi::export(callback_interface)]
pub trait ConvertProgress: Send + Sync {
    fn on_progress(&self, progress: f64, phase: String);
}

/// 把 UniFFI callback 适配成 crate 内部的 [`progress::ProgressSink`]。
struct UniFfiProgressAdapter {
    inner: Box<dyn ConvertProgress>,
}

impl progress::ProgressSink for UniFfiProgressAdapter {
    fn on_progress(&self, fraction: f64, phase: &str) {
        self.inner.on_progress(fraction, phase.to_string());
    }
}

/// 单个 zip 转换（Android facade 的生产入口）。
///
/// 流程：校验输入 → 写入命名模板 → 注册进度 sink（RAII，含错误路径清理）→
/// `pack::version_converter::process_zip_timed`（内部：解压 → 预检 → A-ROM 管线 →
/// 序列化/重打包）→ 组装 [`ConvertReport`]。
#[uniffi::export]
pub fn convert_zip(
    req: ConvertRequest,
    progress: Box<dyn ConvertProgress>,
) -> Result<ConvertReport, ConvertError> {
    use std::sync::Arc;

    let input_zip = std::path::Path::new(&req.input_zip);
    if !input_zip.exists() {
        return Err(ConvertError::InputNotFound {
            path: req.input_zip.clone(),
        });
    }

    let opts = &req.options;
    // 命名模板：引擎 `build_output_path` 经 `commands::read_config_file()` 读取（内存态）。
    commands::set_output_naming(opts.output_naming.clone());

    // 进度 sink：guard 在函数出口（Ok / Err / panic unwind）都会清理。
    let sink: Arc<dyn progress::ProgressSink> = Arc::new(UniFfiProgressAdapter { inner: progress });
    let _guard = progress::install(sink);
    progress::extract_started();

    // 与桌面生产入口一致：`run_gui_surgeon` 固定 true（`--fast` 不暴露给 Android），
    // `native = NativeSwitches::all()` 由 `process_zip_timed` 内部显式设置；
    // `adapt_shaders` 是计划级过滤（run_native 的 `plan_task_enabled`），不是任务内早退。
    let result = pack::version_converter::process_zip_timed(
        &req.input_zip,
        opts.target_version,
        // 桌面函数的 `_progress_callback` / `_file_weight` 参数从未被使用（CONTRACT §9-8），
        // Android 的进度走 crate::progress，因此这两个参数保持 None / 1.0。
        None,
        1.0,
        // parent_folder_path：Android 由 Kotlin 侧决定输出目录，传 None。
        None,
        Some(&req.output_dir),
        opts.fix_alpha_layers,
        opts.adapt_shaders,
        // run_gui_surgeon：生产固定 true（与桌面 `commands/conversion.rs` 一致）。
        true,
        opts.delete_shaders,
        opts.convert_item_definitions,
        opts.convert_cit,
        opts.delete_cit,
        opts.write_gen_metadata,
    );

    match result {
        Ok(outcome) => {
            progress::finished();
            Ok(ConvertReport {
                output_path: outcome.output_path,
                source_version: outcome.source_version,
                target_version: opts.target_version,
                log_path: logger::GLOBAL_LOGGER.log_file_path_str().unwrap_or_default(),
                skipped: outcome.skipped as u32,
                notes: outcome.notes,
                // 契约：`ConversionTiming.total_s * 1000`（含 IO 的总时间）。
                elapsed_ms: (outcome.timing.total_s.max(0.0) * 1000.0) as u64,
            })
        }
        Err(msg) => {
            crate::log_error!("conversion failed: {}", msg);
            Err(ConvertError::Conversion { msg })
        }
    }
}

// ── 运行时 / 日志 / 版本（保持旧 binding 兼容，签名按 CONTRACT §4.1）──────

/// 注入运行时路径（`log_dir` / `app_data_dir` / `uimage_dir` / `temp_dir`）。
///
/// **只能调用一次**，重复调用会返回错误。
/// `temp_dir` 是 Android 适配点：`tempfile` / `std::env::temp_dir()` 必须落在 app cache
/// （Android 的 `/tmp` 不存在），这里在注入时同时 `set_var("TMPDIR", …)` 兜住引擎内
/// 直接调用 `std::env::temp_dir()` 的 4 处（桌面版没有这一步）。
#[uniffi::export]
pub fn init_runtime(
    log_dir: String,
    app_data_dir: String,
    uimage_dir: String,
    temp_dir: String,
) -> Result<(), ConvertError> {
    let temp = std::path::PathBuf::from(&temp_dir);
    // §9-7：必须在任何 tempfile 调用之前把 TMPDIR 指到 app cache。
    std::env::set_var("TMPDIR", &temp);
    set_runtime(RuntimePaths {
        log_dir: std::path::PathBuf::from(log_dir),
        app_data_dir: std::path::PathBuf::from(app_data_dir),
        uimage_dir: std::path::PathBuf::from(uimage_dir),
        temp_dir: temp,
    })
    .map_err(|e| ConvertError::Conversion { msg: e })?;
    // §9-21（Android 适配）：引擎在 `run_native` / `scheduler` / `pack::io` 直接用 rayon
    // 全局池；桌面由命令层建池，Android 没有该层 → 这里限 4 线程（min(4, cores)），
    // 避免前台服务转换时吃满所有核。已建池（极少见）则忽略错误。
    let threads = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(4)
        .min(4);
    let _ = rayon::ThreadPoolBuilder::new()
        .num_threads(threads)
        .thread_name(|i| format!("2pyr-rayon-{i}"))
        .build_global();
    Ok(())
}

/// 拿最近一次转换的日志（用于前端展示 / 导出）
#[uniffi::export]
pub fn get_recent_logs(limit: u32) -> Vec<String> {
    let mut logs = logger::GLOBAL_LOGGER.get_logs();
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
    logger::GLOBAL_LOGGER.set_dev_mode(enabled);
}

/// 查询开发者模式
#[uniffi::export]
pub fn is_dev_mode() -> bool {
    logger::GLOBAL_LOGGER.is_dev_mode()
}

/// 版本号（来自 convert-core 的 Cargo.toml，与 App versionName 对齐 2.1.0）
#[uniffi::export]
pub fn convert_core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
