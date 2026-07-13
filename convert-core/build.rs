// 让 UniFFI proc-macro 模式 (`#[uniffi::export]`) 自动接管。
// UniFFI 0.29 在该模式下不要求 .udl 文件；Kotlin 端 binding 由 `uniffi-bindgen`
// CLI 单独生成（见 `tools/build-rust-android.ps1`）。

fn main() {
    println!("cargo:rerun-if-changed=src");
    println!("cargo:rerun-if-changed=Cargo.toml");
}
