//! **Android 适配层**：桌面 `commands/config.rs` 的最小内存 shim（不是桌面实现的拷贝）。
//!
//! 桌面 `AppConfig` 有 20+ 字段 + serde 落盘；引擎侧**唯一**的读取点是
//! `pack/version_converter.rs:524`（输出命名模板），因此这里只保留 `output_naming`，
//! 且只做内存态（Android 设置由 Kotlin 持久化，见 `commands/mod.rs` 的说明）。
//!
//! 语义与桌面 `build_output_path` 对齐：
//! * `None` / `Some("")` / `Some("default")` → 模板 `[Ver][Name]`（默认）；
//! * `Some("timestamp")` → `[Ver][Time]`；`Some("overwrite")` → `[Name]`；
//! * 其它字符串按自定义模板处理（占位符替换在引擎侧 `apply_naming_template`）。

use std::sync::Mutex;

/// 内存态配置（最小子集）。
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct AppConfig {
    /// 产物命名模板；`None` 等同默认 `[Ver][Name]`。
    pub output_naming: Option<String>,
}

static OUTPUT_NAMING: Mutex<Option<String>> = Mutex::new(None);

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// facade 每次转换前写入 `ConvertOptions.output_naming`（内存态，不落盘）。
pub fn set_output_naming(value: Option<String>) {
    *lock(&OUTPUT_NAMING) = value;
}

/// 引擎读取点（签名与桌面一致：`Result<AppConfig, String>`）。
///
/// 桌面版读文件失败会回落默认值；这里永远成功 —— 没有文件可读，内存态就是真源。
pub fn read_config_file() -> Result<AppConfig, String> {
    Ok(AppConfig {
        output_naming: lock(&OUTPUT_NAMING).clone(),
    })
}

/// 复位为默认（单测/facade 收尾用）。
pub fn reset_output_naming() {
    set_output_naming(None);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn output_naming_memory_shim_roundtrip() {
        // 注意：配置是进程级内存态，其它并行用例不会写它（引擎只有 facade 写），
        // 因此这里串行地 set/read/reset 即可。
        reset_output_naming();
        assert_eq!(read_config_file().unwrap().output_naming, None);

        set_output_naming(Some("[Ver][Time]".to_string()));
        assert_eq!(
            read_config_file().unwrap().output_naming.as_deref(),
            Some("[Ver][Time]")
        );

        set_output_naming(Some("timestamp".to_string()));
        assert_eq!(
            read_config_file().unwrap().output_naming.as_deref(),
            Some("timestamp")
        );

        reset_output_naming();
        assert_eq!(read_config_file().unwrap().output_naming, None);
    }
}
