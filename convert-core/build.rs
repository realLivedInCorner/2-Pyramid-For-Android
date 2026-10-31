// 让 UniFFI proc-macro 模式 (`#[uniffi::export]`) 自动接管。
// UniFFI 0.29 在该模式下不要求 .udl 文件；Kotlin 端 binding 由 `uniffi-bindgen`
// CLI 单独生成（见 `tools/build-rust-android.ps1`）。

fn main() {
    println!("cargo:rerun-if-changed=src");
    println!("cargo:rerun-if-changed=Cargo.toml");

    // **Android 适配点（桌面差异）**：桌面版的 BUILD_NUMBER 由其构建脚本/版本脚本
    // 在编译期写入（release 数字如 22030）；Android 侧没有那一层，而引擎有 3 处编译期
    // `env!("BUILD_NUMBER")`（`gen_metadata.rs` 的 `generator_build`、
    // `bedrock_convert/metadata.rs` 的 manifest 版本、两处测试断言）——不注入就编译失败。
    //
    // 口径（与桌面一致的是"构建号是固定值、不随编译自增"）：
    //   * 默认 `21000` = Android 2.1.0 的版本号口径；
    //   * 可用环境变量 `BUILD_NUMBER` 覆盖（CI/发布时传 versionCode）。
    println!(
        "cargo:rustc-env=BUILD_NUMBER={}",
        std::env::var("BUILD_NUMBER").unwrap_or_else(|_| "21000".to_string())
    );
    println!("cargo:rerun-if-env-changed=BUILD_NUMBER");
}
