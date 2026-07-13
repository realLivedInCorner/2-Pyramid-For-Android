//! 全局运行时上下文
//!
//! Android 端通过 `init_runtime` 注入日志目录、应用数据目录、UImage 路径；
//! 各模块（logger / overlay / converters）通过 RUNTIME.get() 拿到这些路径。
//!
//! 桌面端（直接跑这个 crate 的 bin）也可以调用本 crate 的 init 函数来注入路径；
//! 不调用时各模块会用 `dirs` crate 兜底，保证向后兼容。

use std::path::{Path, PathBuf};
use std::sync::OnceLock;

#[derive(Debug, Clone)]
pub struct RuntimePaths {
    pub log_dir: PathBuf,
    pub app_data_dir: PathBuf,
    pub uimage_dir: PathBuf,
}

static RUNTIME: OnceLock<RuntimePaths> = OnceLock::new();

/// 注入运行时路径。**只能调用一次**，第二次调用会报错。
pub fn set_runtime(paths: RuntimePaths) -> Result<(), String> {
    RUNTIME
        .set(paths)
        .map_err(|_| "runtime already initialized".to_string())
}

/// 拿当前注入的运行时路径，调用方需要问 None 兜底（用 dirs::data_local_dir 等）。
pub fn runtime_paths() -> Option<&'static RuntimePaths> {
    RUNTIME.get()
}

/// 兜底：UImage 目录
pub fn uimage_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.uimage_dir.as_path())
}

/// 兜底：日志目录
pub fn log_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.log_dir.as_path())
}

/// 兜底：app data 目录（overlay projects / 配置 / temp_overlay 等用）
pub fn app_data_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.app_data_dir.as_path())
}
