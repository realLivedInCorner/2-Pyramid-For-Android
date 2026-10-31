//! **host 端差分 runner**（`examples/convert_cli.rs`，仅 host；不参与 Android 构建）。
//!
//! 用途：让验证/差分流程能在本机跑「**Android 这份引擎**」的转换产物，与桌面版
//! 2.203.0 CLI（`2-pyramid.exe --convert …`）的参考产物做逐条目差分（L6 核心证据）。
//!
//! 它**不是**另一个引擎实现：内部完全走与 Android 相同的 facade 路径
//! （`convert_zip` → `pack::version_converter::process_zip_timed` → `run_native`），
//! 输出命名/冲突策略（`[Ver][Name]`、` (n)`、Bedrock `.mcpack`）由引擎内
//! `build_output_path` 决定，与桌面一致。
//!
//! ## 用法
//!
//! ```text
//! cargo run --release --example convert_cli -- \
//!     --convert <资源包.zip> --to <55|97|1000|标签> --out <目录> \
//!     [--cit] [--delete-shaders] [--no-item-definitions] [--delete-cit] \
//!     [--no-gen-metadata] [--naming <模板>] [--report <报告.json>] \
//!     [--uimage <UImage目录>] [--progress] [--fast(ignored)]
//! ```
//!
//! 默认值与桌面 GUI/CLI 一致（报告 §2.4）：
//! * `--to` 缺省 **97**（Java 26.3）；
//! * `--out` 缺省 = 输入包所在目录；
//! * `fix_alpha_layers=false`（死开关，无 CLI 参数，与桌面同）；
//! * `adapt_shaders=true`（始终开，与桌面 CLI 一致，无反面参数）；
//! * `run_gui_surgeon=true`（桌面 `--fast` 才关；Android 生产口径固定 true，
//!   因此 `--fast` 只打印警告、不改变行为——差分时**不要**给桌面 CLI 加 `--fast`）；
//! * `delete_shaders=false`、`convert_item_definitions=true`、`convert_cit=false`、
//!   `delete_cit=false`、`write_gen_metadata=true`。
//!
//! ## 与 Android 的两处刻意一致
//! * UImage 默认**指向空目录**（Android 无 UImage assets）→ 7 组依赖 UImage 的生成任务
//!   按既有逻辑 skip。要做「排除 UImage 差异」的严格差分，可显式传
//!   `--uimage <桌面 UImage 目录>` 让两边都用同一份素材。
//! * 进度回调走 `ConvertProgress`（`--progress` 时打印到 stderr），可肉眼确认
//!   extract → 任务名 → output 三段真的在动。

use std::path::{Path, PathBuf};
use std::process::ExitCode;

use convert_core::{
    convert_zip, init_runtime, ConvertOptions, ConvertProgress, ConvertRequest, ConvertReport,
};

const USAGE: &str = "\
2-Pyramid Android 引擎 · host 差分 runner（仅 host 端）

用法:
  cargo run --release --example convert_cli -- \\
      --convert <资源包.zip> --to <版本|pack_format> --out <目录> [选项]

必需:
  --convert <zip>       输入资源包（唯一入口；与桌面 CLI 同名）
  --to <版本|format>    55 / 97 / 1000 / 1.21.5 / 26.3 / bedrock …（缺省 97）
  --out <目录>          输出目录（缺省 = 输入包所在目录）

开关（默认值同桌面 GUI/CLI，报告 §2.4）:
  --cit                 转换 OptiFine CIT（默认关）
  --delete-shaders      删除两棵 shaders 树（默认关）
  --no-item-definitions 不生成 1.21.4+ 物品模型定义（默认生成）
  --delete-cit          删除两棵 CIT 源目录（默认关）
  --no-gen-metadata     不写 2pyr-gen.json（默认写）
  --naming <模板>       输出命名模板，如 \"[Ver][Name]\" / \"[Ver][Time]\"（缺省 [Ver][Name]）

输出:
  --report <file.json>  写机器可读报告（方便与桌面 --report 对账 / 差分脚本消费）
  --uimage <目录>       UImage 素材目录（缺省=空目录，模拟 Android 无 UImage）
  --progress            把进度回调打印到 stderr
  --fast                桌面兼容参数：仅告警不生效（Android 生产口径固定 run_gui_surgeon=true）
  -h, --help            显示本帮助
";

#[derive(Default)]
struct Args {
    convert: Option<String>,
    to: Option<String>,
    out: Option<String>,
    report: Option<String>,
    naming: Option<String>,
    uimage: Option<String>,
    progress: bool,
    cit: bool,
    delete_shaders: bool,
    no_item_definitions: bool,
    delete_cit: bool,
    no_gen_metadata: bool,
    fast: bool,
}

fn arg_value(argv: &[String], i: &mut usize, name: &str) -> Result<String, String> {
    // 支持 `--name value` 与 `--name=value`
    let cur = &argv[*i];
    if let Some(v) = cur.strip_prefix(&format!("{name}=")) {
        return Ok(v.to_string());
    }
    *i += 1;
    argv.get(*i)
        .filter(|v| !v.starts_with("--"))
        .cloned()
        .ok_or_else(|| format!("{name} 缺少取值"))
}

fn parse_args() -> Result<Args, String> {
    let argv: Vec<String> = std::env::args().skip(1).collect();
    if argv.is_empty() {
        return Err("缺少 --convert 参数（本 runner 只支持命令行转换，没有 GUI）".into());
    }
    let mut a = Args::default();
    let mut i = 0usize;
    while i < argv.len() {
        let cur = argv[i].clone();
        match cur.as_str() {
            "-h" | "--help" => {
                print!("{USAGE}");
                std::process::exit(0);
            }
            "--convert" => a.convert = Some(arg_value(&argv, &mut i, "--convert")?),
            "--to" => a.to = Some(arg_value(&argv, &mut i, "--to")?),
            "--out" => a.out = Some(arg_value(&argv, &mut i, "--out")?),
            "--report" => a.report = Some(arg_value(&argv, &mut i, "--report")?),
            "--naming" => a.naming = Some(arg_value(&argv, &mut i, "--naming")?),
            "--uimage" => a.uimage = Some(arg_value(&argv, &mut i, "--uimage")?),
            "--progress" => a.progress = true,
            "--cit" => a.cit = true,
            "--delete-shaders" => a.delete_shaders = true,
            "--no-item-definitions" => a.no_item_definitions = true,
            "--delete-cit" => a.delete_cit = true,
            "--no-gen-metadata" => a.no_gen_metadata = true,
            "--fast" => a.fast = true,
            // 桌面历史兼容参数：出现即忽略（不报未知参数，脚本可复用）
            "--no-cit" => {}
            other if other.starts_with("--convert=") => {
                a.convert = Some(cur["--convert=".len()..].to_string())
            }
            other => return Err(format!("未知参数：{other}\n\n{USAGE}")),
        }
        i += 1;
    }
    if a.convert.is_none() {
        return Err("缺少 --convert 参数".into());
    }
    Ok(a)
}

/// `--progress` 时把回调打印到 stderr（带 0.5% 步长去抖，避免刷屏）。
struct StderrProgress {
    last: std::sync::Mutex<f64>,
}

impl ConvertProgress for StderrProgress {
    fn on_progress(&self, progress: f64, phase: String) {
        let mut last = self.last.lock().unwrap_or_else(|e| e.into_inner());
        if progress - *last >= 0.005 || progress >= 1.0 - 1e-9 {
            *last = progress;
            eprintln!("[progress {:6.2}%] {}", progress * 100.0, phase);
        }
    }
}

fn zip_entry_count(path: &Path) -> Option<usize> {
    let f = std::fs::File::open(path).ok()?;
    zip::ZipArchive::new(f).ok().map(|a| a.len())
}

fn write_report(
    report_path: &Path,
    status: &str,
    error: Option<&str>,
    input: &Path,
    args: &Args,
    target: u32,
    result: Option<&ConvertReport>,
) -> Result<(), String> {
    let mut obj = serde_json::json!({
        "engine": "convert-core (Android port) host runner",
        "generatedAt": chrono::Local::now().to_rfc3339(),
        "status": status,
        "input": input.to_string_lossy(),
        "targetFormat": target,
        "targetLabel": convert_core::pack_format_label_for_output(target),
        "options": {
            "fixAlphaLayers": false,
            "adaptShaders": true,
            "runGuiSurgeon": true,
            "deleteShaders": args.delete_shaders,
            "convertItemDefinitions": !args.no_item_definitions,
            "convertCit": args.cit,
            "deleteCit": args.delete_cit,
            "writeGenMetadata": !args.no_gen_metadata,
            "outputNaming": args.naming,
        },
    });
    if let Some(e) = error {
        obj["error"] = serde_json::Value::String(e.to_string());
    }
    if let Some(r) = result {
        let out = PathBuf::from(&r.output_path);
        obj["output"] = serde_json::Value::String(r.output_path.clone());
        obj["sourceVersion"] = serde_json::json!(r.source_version);
        obj["targetVersion"] = serde_json::json!(r.target_version);
        obj["skipped"] = serde_json::json!(r.skipped);
        obj["notes"] = serde_json::json!(r.notes);
        obj["elapsedMs"] = serde_json::json!(r.elapsed_ms);
        obj["logPath"] = serde_json::Value::String(r.log_path.clone());
        obj["entries"] = serde_json::json!(zip_entry_count(&out));
        obj["sizeBytes"] = serde_json::json!(std::fs::metadata(&out).ok().map(|m| m.len()));
    }
    if let Some(parent) = report_path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("创建报告目录失败：{e}"))?;
    }
    let text = serde_json::to_string_pretty(&obj).map_err(|e| format!("序列化报告失败：{e}"))?;
    std::fs::write(report_path, text).map_err(|e| format!("写报告失败：{e}"))?;
    Ok(())
}

fn main() -> ExitCode {
    let args = match parse_args() {
        Ok(a) => a,
        Err(e) => {
            eprintln!("错误：{e}");
            return ExitCode::from(2);
        }
    };

    let input = PathBuf::from(args.convert.clone().expect("checked"));
    if !input.exists() {
        eprintln!("错误：输入不存在：{}", input.display());
        if let Some(rp) = &args.report {
            let _ = write_report(Path::new(rp), "failed", Some("input not found"), &input, &args, 0, None);
        }
        return ExitCode::from(2);
    }
    let target = match convert_core::resolve_target_format(args.to.as_deref().unwrap_or("97")) {
        Ok(n) => n,
        Err(e) => {
            eprintln!("错误：{e}");
            return ExitCode::from(2);
        }
    };
    let target_label = convert_core::pack_format_label_for_output(target);

    // 输出目录：缺省 = 输入包所在目录（与桌面 CLI 的 build_output_path(None, None) 等价）。
    let out_dir: PathBuf = match &args.out {
        Some(d) => PathBuf::from(d),
        None => input
            .parent()
            .map(Path::to_path_buf)
            .unwrap_or_else(|| PathBuf::from(".")),
    };
    if let Err(e) = std::fs::create_dir_all(&out_dir) {
        eprintln!("错误：创建输出目录失败 {}：{e}", out_dir.display());
        return ExitCode::from(2);
    }

    // 运行时路径：host 下用进程私有 temp（不污染输出目录；TMPDIR 也会被 init_runtime 指向它）。
    let run_root = std::env::temp_dir().join(format!("2pyr-host-run-{}", std::process::id()));
    let log_dir = run_root.join("log");
    let app_data = run_root.join("data");
    let tmp_dir = run_root.join("tmp");
    let uimage_dir = match &args.uimage {
        Some(p) => PathBuf::from(p),
        None => run_root.join("uimage"), // 空目录 => 模拟 Android 无 UImage
    };
    for d in [&log_dir, &app_data, &tmp_dir] {
        if let Err(e) = std::fs::create_dir_all(d) {
            eprintln!("错误：创建运行时目录失败 {}：{e}", d.display());
            return ExitCode::from(2);
        }
    }
    if args.uimage.is_none() {
        let _ = std::fs::create_dir_all(&uimage_dir);
    }
    if let Err(e) = init_runtime(
        log_dir.to_string_lossy().to_string(),
        app_data.to_string_lossy().to_string(),
        uimage_dir.to_string_lossy().to_string(),
        tmp_dir.to_string_lossy().to_string(),
    ) {
        eprintln!("错误：init_runtime 失败：{e}");
        return ExitCode::from(2);
    }

    if args.fast {
        eprintln!(
            "警告：--fast 在 Android 生产口径下不生效（run_gui_surgeon 固定 true）。\
             与桌面 CLI 差分时请**不要**给桌面 CLI 传 --fast。"
        );
    }
    if args.uimage.is_none() {
        eprintln!("提示：未指定 --uimage，按 Android 现状使用空 UImage 目录（依赖 UImage 的任务会 skip）。");
    }

    println!("==> 2-Pyramid Android 引擎 host 差分 runner");
    println!("    输入：{}", input.display());
    println!("    目标：{}（pack_format {}）", target_label, target);
    println!("    输出目录：{}", out_dir.display());
    println!(
        "    开关：cit={} delete_shaders={} item_definitions={} delete_cit={} gen_metadata={} naming={:?}",
        args.cit,
        args.delete_shaders,
        !args.no_item_definitions,
        args.delete_cit,
        !args.no_gen_metadata,
        args.naming
    );

    let options = ConvertOptions {
        target_version: target,
        fix_alpha_layers: false, // 死开关（桌面同）
        adapt_shaders: true,     // 桌面 CLI 无反面参数
        delete_shaders: args.delete_shaders,
        convert_item_definitions: !args.no_item_definitions,
        convert_cit: args.cit,
        delete_cit: args.delete_cit,
        write_gen_metadata: !args.no_gen_metadata,
        output_naming: args.naming.clone(),
    };
    let req = ConvertRequest {
        input_zip: input.to_string_lossy().to_string(),
        output_dir: out_dir.to_string_lossy().to_string(),
        options,
    };

    let progress: Box<dyn ConvertProgress> = if args.progress {
        Box::new(StderrProgress {
            last: std::sync::Mutex::new(-1.0),
        })
    } else {
        // 不打印的最小实现（UniFFI 契约要求必须传一个回调）。
        Box::new(NoopProgress)
    };

    let started = std::time::Instant::now();
    let result = convert_zip(req, progress);
    let wall_ms = started.elapsed().as_millis() as u64;

    match result {
        Ok(report) => {
            let out = PathBuf::from(&report.output_path);
            let out_bytes = std::fs::metadata(&out).map(|m| m.len()).unwrap_or(0);
            let in_bytes = std::fs::metadata(&input).map(|m| m.len()).unwrap_or(0);
            println!(
                "    ✓ 转换成功：{} → {}（source={} target={}）",
                report.source_version, report.target_version, report.source_version, report.target_version
            );
            println!(
                "    ✓ 产物：{}（{:.2} MB，{} 条目）",
                report.output_path,
                out_bytes as f64 / 1048576.0,
                zip_entry_count(&out)
                    .map(|n| n.to_string())
                    .unwrap_or_else(|| "?".into())
            );
            println!(
                "    ✓ 耗时：引擎 {:.2}s / 墙钟 {:.2}s；体积 {:.2} MB → {:.2} MB",
                report.elapsed_ms as f64 / 1000.0,
                wall_ms as f64 / 1000.0,
                in_bytes as f64 / 1048576.0,
                out_bytes as f64 / 1048576.0
            );
            println!(
                "    · skipped={} notes={}",
                report.skipped,
                report.notes.len()
            );
            for note in &report.notes {
                println!("      - {note}");
            }
            if let Some(rp) = &args.report {
                if let Err(e) = write_report(
                    Path::new(rp),
                    "ok",
                    None,
                    &input,
                    &args,
                    target,
                    Some(&report),
                ) {
                    eprintln!("错误：{e}");
                    return ExitCode::from(1);
                }
                println!("    · 报告：{}", rp);
            }
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("错误：转换失败：{e}");
            if let Some(rp) = &args.report {
                if let Err(we) = write_report(
                    Path::new(rp),
                    "failed",
                    Some(&e.to_string()),
                    &input,
                    &args,
                    target,
                    None,
                ) {
                    eprintln!("错误：{we}");
                }
            }
            ExitCode::from(1)
        }
    }
}

struct NoopProgress;

impl ConvertProgress for NoopProgress {
    fn on_progress(&self, _progress: f64, _phase: String) {}
}
