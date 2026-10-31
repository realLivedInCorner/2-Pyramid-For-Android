//! **Android 适配层**：桌面 `src/commands/` 的最小内存 shim。
//!
//! 桌面 2.203.0 的 `commands/` 是一大组桌面 UI 命令实现，Android 只用到其中一个
//! 纯数据入口：`pack/version_converter.rs:524` 的
//! `crate::commands::read_config_file()` —— 它只读 `output_naming`（产物命名模板）。
//!
//! 因此这里**不搬**桌面的 serde/落盘配置，只保留内存态：facade 在每次转换前调用
//! [`set_output_naming`] 写入 `ConvertOptions.output_naming`，引擎侧
//! `build_output_path` 通过 [`read_config_file`] 读到同一个值。Android 的设置持久化
//! 由 Kotlin `SettingsRepository`（SharedPreferences）负责。

pub mod config;

pub use config::{read_config_file, set_output_naming, AppConfig};
