//! 全局运行时上下文（**Android 适配层**）
//!
//! Android 端通过 `init_runtime` 注入日志目录、应用数据目录、UImage 路径与临时目录；
//! 各模块（logger / image_utils / 引擎）通过 RUNTIME.get() 拿到这些路径。
//!
//! 与桌面版的差异：
//! * 桌面版用 `dirs` / 桌面资源 API 解析路径；Android 无标准 HOME，**永远走注入**。
//! * 新增 `temp_dir`（app cache）：引擎里 `tempfile::tempdir()` 与
//!   `std::env::temp_dir()` 在 Android 会回落到不存在的 `/tmp`（CONTRACT §9-7），
//!   因此 `init_runtime` 在注入的同时 `set_var("TMPDIR", …)`。
//! * 不调用 init 时不再用 `dirs` 兜底（Android 上没有意义；引擎的唯一生产入口一定会先 init）。

use std::path::{Path, PathBuf};
use std::sync::OnceLock;

#[derive(Debug, Clone)]
pub struct RuntimePaths {
    pub log_dir: PathBuf,
    pub app_data_dir: PathBuf,
    pub uimage_dir: PathBuf,
    /// app cache（Kotlin 侧 `context.cacheDir`）：`tempfile` / `TMPDIR` 落点。
    pub temp_dir: PathBuf,
}

static RUNTIME: OnceLock<RuntimePaths> = OnceLock::new();

/// 注入运行时路径。**只能调用一次**，第二次调用会报错。
pub fn set_runtime(paths: RuntimePaths) -> Result<(), String> {
    RUNTIME
        .set(paths)
        .map_err(|_| "runtime already initialized".to_string())
}

/// 拿当前注入的运行时路径，调用方需要问 None 兜底。
pub fn runtime_paths() -> Option<&'static RuntimePaths> {
    RUNTIME.get()
}

/// UImage 目录（缺失 = Android 未打包 UImage，依赖它的任务走 skip 分支）。
pub fn uimage_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.uimage_dir.as_path())
}

/// 日志目录。
pub fn log_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.log_dir.as_path())
}

/// app data 目录（overlay projects / 配置 / temp_overlay 等用）。
pub fn app_data_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.app_data_dir.as_path())
}

/// 临时目录（app cache）。引擎里需要显式落点时可读它；未注入时返回 `None`，
/// 调用方回落到 `std::env::temp_dir()`（测试/桌面形态）。
pub fn temp_dir() -> Option<&'static Path> {
    runtime_paths().map(|r| r.temp_dir.as_path())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 路径注入语义：注入后可读、重复注入报错。
    ///
    /// 注意：`RUNTIME` 是进程级 `OnceLock`；`image_utils` 的用例也可能会先注入，
    /// 因此本用例对「已注入」的情况只断言可读性（顺序无关、可并行）。
    #[test]
    fn runtime_paths_are_injected_once() {
        if runtime_paths().is_none() {
            assert!(uimage_dir().is_none());
            assert!(temp_dir().is_none());
            let root = std::env::temp_dir().join("2pyr-runtime-test");
            set_runtime(RuntimePaths {
                log_dir: root.join("log"),
                app_data_dir: root.join("data"),
                uimage_dir: root.join("uimage"),
                temp_dir: root.join("cache"),
            })
            .expect("first init must succeed");
            assert!(
                set_runtime(RuntimePaths {
                    log_dir: root.join("log2"),
                    app_data_dir: root.join("data2"),
                    uimage_dir: root.join("uimage2"),
                    temp_dir: root.join("cache2"),
                })
                .is_err(),
                "第二次注入必须报错"
            );
        }
        let paths = runtime_paths().expect("runtime must be initialized");
        assert!(paths.temp_dir.file_name().is_some());
        assert!(temp_dir().is_some());
        assert!(log_dir().is_some());
        assert!(app_data_dir().is_some());
    }
}
