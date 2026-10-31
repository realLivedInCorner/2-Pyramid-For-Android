//! **Android facade 端到端 smoke test**（L3 验收 §6.1 第 5 项）。
//!
//! 程序化合成一个 **format 15** 的小资源包（含 `pack.mcmeta` / 可解码 PNG /
//! 46 个带 `custom_model_data` 的 `models/item/*.json` / 两棵 shaders 树 / CIT 源目录），
//! 然后**只经 UniFFI facade** 走完整转换，验证：
//!
//! 1. format 15 → **55**（Java 1.20.5-1.20.6）：`pack.mcmeta.pack_format == 55`、
//!    `assets/minecraft/items/*.json` ≥ 46（物品定义迁移）、`2pyr-gen.json` 存在且字段正确；
//! 2. `write_gen_metadata=false` 时产物根**没有** `2pyr-gen.json`；
//! 3. `delete_shaders=true` 时两棵 shaders 树**整棵消失**（`shaders/` + `assets/minecraft/shaders/`）；
//! 4. format 15 → **97**（26.3）：成功且 `pack.mcmeta` 用 `min_format`/`max_format`（26.x 写法）；
//! 5. format 15 → **1000**（Bedrock）：产物是 `.mcpack` 且根含 `manifest.json`；
//! 6. `ConvertProgress` 回调真的被调用：fraction 单调不回退、phase 覆盖 extract/convert/output。
//!
//! 注意：本文件是**集成测试**（独立进程），因此可以安全地调用一次 `init_runtime`；
//! 所有用例共用一次初始化（`Once`），不污染 `--lib` 单测。

use std::collections::BTreeSet;
use std::io::{Read as _, Write as _};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, Once};

use convert_core::{
    convert_zip, init_runtime, ConvertOptions, ConvertProgress, ConvertRequest,
};

// ── 初始化（每个测试二进制一次）─────────────────────────────────────────

static INIT: Once = Once::new();
static ROOT: Mutex<Option<PathBuf>> = Mutex::new(None);
/// Android 的真机语义是「同一时刻只有一个转换在跑」（前台服务串行消费队列），
/// 而 `crate::progress` 的 sink 是**进程级全局**的；测试并行线程会互相替换 sink，
/// 因此这里用一把串行锁把 facade 用例串起来（只影响测试，不影响生产语义）。
static FACADE_LOCK: Mutex<()> = Mutex::new(());

fn facade_serial() -> std::sync::MutexGuard<'static, ()> {
    FACADE_LOCK.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn root() -> PathBuf {
    let mut guard = ROOT.lock().unwrap();
    if let Some(p) = guard.as_ref() {
        return p.clone();
    }
    let dir = tempfile::Builder::new()
        .prefix("2pyr-smoke-")
        .tempdir()
        .expect("create smoke root")
        .keep();
    *guard = Some(dir.clone());
    dir
}

fn init_once() {
    INIT.call_once(|| {
        let r = root();
        std::fs::create_dir_all(r.join("log")).unwrap();
        std::fs::create_dir_all(r.join("data")).unwrap();
        std::fs::create_dir_all(r.join("uimage")).unwrap(); // 空目录 => UImage 缺失 => 任务 skip
        std::fs::create_dir_all(r.join("cache")).unwrap();
        init_runtime(
            r.join("log").to_string_lossy().to_string(),
            r.join("data").to_string_lossy().to_string(),
            r.join("uimage").to_string_lossy().to_string(),
            r.join("cache").to_string_lossy().to_string(),
        )
        .expect("init_runtime 只应成功一次");
    });
}

// ── 进度收集 ─────────────────────────────────────────────────────────────

#[derive(Clone, Default)]
struct CollectProgress {
    events: Arc<Mutex<Vec<(f64, String)>>>,
}

impl ConvertProgress for CollectProgress {
    fn on_progress(&self, progress: f64, phase: String) {
        self.events.lock().unwrap().push((progress, phase));
    }
}

impl CollectProgress {
    fn events(&self) -> Vec<(f64, String)> {
        self.events.lock().unwrap().clone()
    }
}

// ── fixture 合成 ─────────────────────────────────────────────────────────

fn png_bytes() -> Vec<u8> {
    let img = image::RgbaImage::from_pixel(16, 16, image::Rgba([10, 200, 30, 255]));
    let mut buf = std::io::Cursor::new(Vec::new());
    image::DynamicImage::ImageRgba8(img)
        .write_to(&mut buf, image::ImageFormat::Png)
        .expect("encode png");
    buf.into_inner()
}

/// 从引擎自带的官方定义参考表里挑物品名（保证 `generate_item_definitions`
/// 对这些名字有模板可写）。挑 60 个 → 断言 ≥46。
fn definition_names() -> Vec<String> {
    let raw = include_str!("../src/natives/architect/item-definitions.json");
    let json: serde_json::Value = serde_json::from_str(raw).expect("parse item-definitions.json");
    let defs = json["profiles"]["46"]["definitions"]
        .as_object()
        .expect("profile 46 definitions");
    let mut names: Vec<String> = defs
        .keys()
        .filter(|n| {
            !n.is_empty()
                && n.chars()
                    .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_')
        })
        .take(60)
        .cloned()
        .collect();
    names.sort();
    assert!(names.len() >= 46, "参考表里的可用物品名不足 46：{}", names.len());
    names
}

/// 写一个 format 15 的小包；`shaders` / CIT 源目录都放进去，供开关用例断言。
fn write_fixture(path: &Path) {
    let file = std::fs::File::create(path).expect("create fixture zip");
    let mut zip = zip::ZipWriter::new(file);
    let opts = zip::write::FileOptions::default();

    zip.start_file("pack.mcmeta", opts).unwrap();
    zip.write_all(br#"{"pack":{"pack_format":15,"description":"android smoke fixture"}}"#)
        .unwrap();
    zip.start_file("pack.png", opts).unwrap();
    zip.write_all(&png_bytes()).unwrap();

    // 46+ 物品模型：每个都带 custom_model_data override（物品定义迁移的输入）。
    for name in definition_names() {
        zip.start_file(format!("assets/minecraft/models/item/{name}.json"), opts)
            .unwrap();
        zip.write_all(
            format!(
                r#"{{"parent":"minecraft:item/generated","textures":{{"layer0":"minecraft:item/{name}"}},"overrides":[{{"predicate":{{"custom_model_data":7}},"model":"mypack:item/{name}_ruby"}}]}}"#
            )
            .as_bytes(),
        )
        .unwrap();
        zip.start_file(format!("assets/minecraft/textures/item/{name}.png"), opts)
            .unwrap();
        zip.write_all(&png_bytes()).unwrap();
    }

    // 两棵 shaders 树（delete_shaders 要整棵删掉的两处）。
    for (p, body) in [
        ("shaders/foo/gbuffers.vsh", "#version 150\n// fixture shaders"),
        ("shaders/foo/gbuffers.fsh", "#version 150\n// fixture shaders"),
        ("assets/minecraft/shaders/core/rendertype_text.vsh", "#version 150\n// core"),
    ] {
        zip.start_file(p, opts).unwrap();
        zip.write_all(body.as_bytes()).unwrap();
    }

    // CIT 两棵源目录（默认 convert_cit=false 时原样保留）。
    for (p, body) in [
        (
            "assets/minecraft/optifine/cit/ruby.properties",
            "type=item\nitems=minecraft:diamond\nmodel=item/ruby\n",
        ),
        (
            "assets/minecraft/mcpatcher/cit/old.properties",
            "type=item\nitems=minecraft:emerald\nmodel=item/old\n",
        ),
    ] {
        zip.start_file(p, opts).unwrap();
        zip.write_all(body.as_bytes()).unwrap();
    }
    zip.start_file("assets/minecraft/optifine/cit/ruby.png", opts)
        .unwrap();
    zip.write_all(&png_bytes()).unwrap();

    zip.finish().unwrap().flush().unwrap();
}

fn default_options(target_version: u32) -> ConvertOptions {
    ConvertOptions {
        target_version,
        fix_alpha_layers: false,
        adapt_shaders: true,
        delete_shaders: false,
        convert_item_definitions: true,
        convert_cit: false,
        delete_cit: false,
        write_gen_metadata: true,
        output_naming: None, // 默认 [Ver][Name]
    }
}

fn run(target_version: u32, options: ConvertOptions, tag: &str) -> (convert_core::ConvertReport, Vec<(f64, String)>) {
    // 进度 sink 是进程级全局（见 FACADE_LOCK 的说明）：整个转换期间持锁。
    let _serial = facade_serial();
    init_once();
    let r = root();
    // 每个用例独立的输入/输出目录（输出命名含 [Ver]，避免互相覆盖）。
    let case = r.join(format!("case-{tag}"));
    std::fs::create_dir_all(&case).unwrap();
    let input = case.join("fixture15.zip");
    if !input.exists() {
        write_fixture(&input);
    }
    let out_dir = case.join("out");
    std::fs::create_dir_all(&out_dir).unwrap();
    let collector = CollectProgress::default();
    let report = convert_zip(
        ConvertRequest {
            input_zip: input.to_string_lossy().to_string(),
            output_dir: out_dir.to_string_lossy().to_string(),
            options,
        },
        Box::new(collector.clone()),
    )
    .expect("conversion must succeed");
    (report, collector.events())
}

// ── zip / json 断言辅助 ──────────────────────────────────────────────────

fn zip_entries(path: &Path) -> Vec<String> {
    let f = std::fs::File::open(path).expect("open output zip");
    let mut ar = zip::ZipArchive::new(f).expect("parse output zip");
    (0..ar.len())
        .map(|i| ar.by_index(i).expect("entry").name().to_string())
        .collect()
}

fn zip_read(path: &Path, name: &str) -> Option<Vec<u8>> {
    let f = std::fs::File::open(path).expect("open output zip");
    let mut ar = zip::ZipArchive::new(f).expect("parse output zip");
    let mut e = ar.by_name(name).ok()?;
    let mut buf = Vec::new();
    e.read_to_end(&mut buf).expect("read entry");
    Some(buf)
}

fn zip_json(path: &Path, name: &str) -> serde_json::Value {
    let bytes = zip_read(path, name).unwrap_or_else(|| panic!("产物缺少 {name}"));
    serde_json::from_slice(&bytes).unwrap_or_else(|e| panic!("{name} 不是合法 JSON: {e}"))
}

fn assert_progress_sane(events: &[(f64, String)]) {
    assert!(!events.is_empty(), "ConvertProgress 回调从未被调用");
    for w in events.windows(2) {
        assert!(
            w[1].0 >= w[0].0,
            "进度必须单调不回退：{:?} -> {:?}",
            w[0],
            w[1]
        );
        assert!((0.0..=1.0).contains(&w[1].0), "进度越界：{:?}", w[1]);
    }
    let phases: BTreeSet<&str> = events.iter().map(|(_, p)| p.as_str()).collect();
    assert!(phases.contains("extract"), "phase 缺少 extract：{phases:?}");
    assert!(phases.contains("output"), "phase 缺少 output：{phases:?}");
    // pipeline 段：至少有一个「任务名」phase（不是三段固定名之一）。
    assert!(
        phases
            .iter()
            .any(|p| !matches!(*p, "extract" | "output" | "convert")),
        "phase 缺少任务名：{phases:?}"
    );
    let last = events.last().unwrap().0;
    assert!(last >= 1.0 - 1e-9, "最终进度必须到 1.0，实际 {last}");
}

// ── 用例 ─────────────────────────────────────────────────────────────────

/// 核心用例：15 → 55，覆盖 pack.mcmeta / 物品定义 / 2pyr-gen / 进度回调。
#[test]
fn facade_converts_format15_to_55_with_item_definitions_and_gen_metadata() {
    let (report, events) = run(55, default_options(55), "to55");
    let out = PathBuf::from(&report.output_path);
    assert!(out.is_file(), "输出不存在：{}", report.output_path);
    assert_eq!(report.source_version, 15, "source_version 必须来自输入包 pack.mcmeta");
    assert_eq!(report.target_version, 55);
    assert!(report.elapsed_ms > 0, "elapsed_ms 必须来自 ConversionTiming");
    assert!(
        report.notes.iter().any(|n| n.contains("物品模型定义")),
        "notes 必须透传任务说明，实际：{:?}",
        report.notes
    );

    // pack.mcmeta：< 69 写 pack_format。
    let meta = zip_json(&out, "pack.mcmeta");
    assert_eq!(meta["pack"]["pack_format"], 55, "pack.mcmeta 目标格式错误：{meta}");

    // 物品定义：items/*.json ≥ 46。
    let entries = zip_entries(&out);
    let items: Vec<&String> = entries
        .iter()
        .filter(|p| p.starts_with("assets/minecraft/items/") && p.ends_with(".json"))
        .collect();
    assert!(
        items.len() >= 46,
        "物品定义数量不足 46，实际 {}：{:?}",
        items.len(),
        &items[..items.len().min(10)]
    );
    // 抽样验证 CMD 表被接进定义（range_dispatch / custom_model_data）。
    let sample = items[0];
    let def = zip_json(&out, sample);
    assert!(
        def.to_string().contains("custom_model_data"),
        "物品定义里没有 CustomModelData dispatch：{def}"
    );

    // 2pyr-gen.json：默认开，字段与契约一致。
    let gen = zip_json(&out, "2pyr-gen.json");
    assert_eq!(gen["source_pack_format"], 15);
    assert_eq!(gen["target_pack_format"], 55);
    assert_eq!(gen["generator"], "2-Pyramid");
    assert_eq!(gen["generator_build"], env!("BUILD_NUMBER"));
    assert_eq!(gen["options"]["convert_item_definitions"], true);
    assert_eq!(gen["options"]["convert_cit"], false);
    assert_eq!(gen["options"]["delete_shaders"], false);

    // shaders 默认保留（开关关）。
    assert!(
        entries.iter().any(|p| p.starts_with("shaders/")),
        "delete_shaders=false 时 shaders/ 必须保留"
    );
    assert!(
        entries.iter().any(|p| p.starts_with("assets/minecraft/shaders/")),
        "delete_shaders=false 时 assets/minecraft/shaders/ 必须保留"
    );

    // 进度：extract / 任务名 / output，单调，到 1.0。
    assert_progress_sane(&events);
}

/// 开关：write_gen_metadata=false → 产物根不得有 2pyr-gen.json。
#[test]
fn facade_gen_metadata_switch_off_writes_no_file() {
    let mut opts = default_options(55);
    opts.write_gen_metadata = false;
    let (report, _) = run(55, opts, "to55_nogen");
    let out = PathBuf::from(&report.output_path);
    assert!(out.is_file());
    assert!(
        zip_read(&out, "2pyr-gen.json").is_none(),
        "write_gen_metadata=false 时不得写 2pyr-gen.json"
    );
    // 物品定义仍应生成（两个开关互不影响）。
    let items = zip_entries(&out)
        .iter()
        .filter(|p| p.starts_with("assets/minecraft/items/") && p.ends_with(".json"))
        .count();
    assert!(items >= 46, "物品定义仍应生成，实际 {items}");
}

/// 开关：delete_shaders=true → 两棵 shaders 树整棵消失。
#[test]
fn facade_delete_shaders_removes_both_roots() {
    let mut opts = default_options(55);
    opts.delete_shaders = true;
    let (report, _) = run(55, opts, "to55_delshaders");
    let out = PathBuf::from(&report.output_path);
    let entries = zip_entries(&out);
    assert!(
        !entries.iter().any(|p| p == "shaders" || p.starts_with("shaders/")),
        "delete_shaders 后仍残留顶层 shaders/：{entries:?}"
    );
    assert!(
        !entries
            .iter()
            .any(|p| p.starts_with("assets/minecraft/shaders")),
        "delete_shaders 后仍残留 assets/minecraft/shaders/：{entries:?}"
    );
}

/// 目标 97（26.3）：成功，pack.mcmeta 用 26.x 的 min_format/max_format。
#[test]
fn facade_converts_format15_to_97_with_min_max_format() {
    let (report, events) = run(97, default_options(97), "to97");
    let out = PathBuf::from(&report.output_path);
    assert!(out.is_file());
    assert_eq!(report.source_version, 15);
    let meta = zip_json(&out, "pack.mcmeta");
    assert_eq!(meta["pack"]["min_format"], serde_json::json!([97, 0]));
    assert_eq!(meta["pack"]["max_format"], serde_json::json!([97, 1]));
    assert!(
        meta["pack"].get("pack_format").is_none(),
        "26.x 不应再写 pack_format：{meta}"
    );
    assert!(meta["pack"].get("supported_formats").is_none());
    assert_progress_sane(&events);
}

/// 目标 1000（Bedrock）：`.mcpack` + 根 `manifest.json`。
#[test]
fn facade_converts_format15_to_1000_mcpack_with_manifest() {
    let (report, events) = run(1000, default_options(1000), "to1000");
    assert!(
        report.output_path.ends_with(".mcpack"),
        "Bedrock 产物扩展名必须是 .mcpack：{}",
        report.output_path
    );
    let out = PathBuf::from(&report.output_path);
    assert!(out.is_file());
    let manifest = zip_json(&out, "manifest.json");
    assert!(
        manifest["header"]["name"].is_string(),
        "manifest.json 结构不完整：{manifest}"
    );
    // Bedrock 目标不写 2pyr-gen.json（Java 中间态也不写；产物根是 Bedrock 结构）。
    // 注：中间态的 2pyr-gen.json 由 Java 产物负责，Bedrock 产物只认 manifest。
    assert!(report.source_version == 15);
    assert_progress_sane(&events);
}

/// 输入不存在 → InputNotFound（契约错误变体）。
#[test]
fn facade_missing_input_returns_input_not_found() {
    let _serial = facade_serial();
    init_once();
    let r = root();
    let collector = CollectProgress::default();
    let err = convert_zip(
        ConvertRequest {
            input_zip: r.join("no-such-file.zip").to_string_lossy().to_string(),
            output_dir: r.join("out-missing").to_string_lossy().to_string(),
            options: default_options(55),
        },
        Box::new(collector),
    )
    .expect_err("缺失输入必须报错");
    match err {
        convert_core::ConvertError::InputNotFound { path } => {
            assert!(path.contains("no-such-file.zip"), "path={path}");
        }
        other => panic!("错误变体必须是 InputNotFound，实际 {other:?}"),
    }
}
