//! 全局转换进度（**Android 适配层，桌面版没有对应模块**）。
//!
//! 桌面 2.203.0 的生产管线**没有任何进度回调**：`process_zip_timed` 的
//! `_progress_callback` 参数从未被调用，`ProgressTracker` 只服务 Bedrock 边任务。
//! Android 需要真进度（CONTRACT §4 / 报告 E1），因此这里新建一个**只读旁路**：
//!
//! * 引擎在各阶段调用 [`emit`] / 下面的辅助函数，**不读取返回值、不改变控制流**——
//!   没有注册 sink 时是一次静态分支判断（桌面/单测零影响）。
//! * 权重（CONTRACT §4.3-4）：`extract 0.00–0.12` → `pipeline 0.12–0.90`（phase =
//!   任务名，分母 `plan_len`）→ `output 0.90–1.00`。
//! * **单调不回退**：并行批（rayon）与串行段可能交替上报，这里用全局状态钳制，
//!   任何比上次更小的值都会被抬到上次的值；同一 phase 内还有最小步长节流，
//!   避免 4000+ 条目每条都跨 JNA 回调。
//! * **线程安全**：`Mutex` 保护（回调在持锁期间执行，保证 Kotlin 侧看到的顺序
//!   与这里钳制后的顺序一致；回调本身只做 UI 状态更新，不重入进度模块）。
//! * **清理**：facade 用 [`SinkGuard`] 注册，Drop 时清空——正常返回、`Err` 返回与
//!   panic unwind 三条路径都会清理（release 需 `panic = "unwind"`，见 CONTRACT §9-6）。

use std::sync::{Arc, Mutex, OnceLock};

/// 进度接收端。UniFFI 的 `ConvertProgress` 在 facade 里适配成这个 trait。
pub trait ProgressSink: Send + Sync {
    /// `fraction ∈ [0.0, 1.0]`；`phase` 为短英文串（`extract` / 任务名 / `output`）。
    fn on_progress(&self, fraction: f64, phase: &str);
}

/// extract 阶段权重区间（CONTRACT §4.3-4）。
pub const EXTRACT_START: f64 = 0.0;
pub const EXTRACT_END: f64 = 0.12;
/// pipeline 阶段权重区间。
pub const PIPELINE_START: f64 = 0.12;
pub const PIPELINE_END: f64 = 0.90;
/// 产物序列化/打包阶段权重区间。
pub const OUTPUT_START: f64 = 0.90;
pub const OUTPUT_END: f64 = 1.0;

/// 同一 phase 内两次回调的最小间隔；避免大包几千条目每条都回调一次。
const MIN_DELTA: f64 = 0.002;

struct State {
    last_fraction: f64,
    last_phase: String,
    /// 是否已进入「产物输出」段。`io::repack_resource_pack` 也在 Bedrock 源预检
    /// （b2j 之前）被调用，若那时就上报 output 0.90 会把后面整个 pipeline 段
    /// 单调钳死，因此输出段必须显式开启。
    output_phase: bool,
}

static SINK: OnceLock<Mutex<Option<Arc<dyn ProgressSink>>>> = OnceLock::new();
static STATE: OnceLock<Mutex<State>> = OnceLock::new();

fn sink_slot() -> &'static Mutex<Option<Arc<dyn ProgressSink>>> {
    SINK.get_or_init(|| Mutex::new(None))
}

fn state() -> &'static Mutex<State> {
    STATE.get_or_init(|| {
        Mutex::new(State {
            last_fraction: 0.0,
            last_phase: String::new(),
            output_phase: false,
        })
    })
}

fn lock<'a, T>(m: &'a Mutex<T>) -> std::sync::MutexGuard<'a, T> {
    // 进度是旁路：即使别的线程在回调里 panic 毒化了锁，也要继续工作，
    // 因此统一用 `into_inner` 忽略 poisoning。
    m.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// 注册 sink 并返回清理守卫；守卫 Drop 时自动清空（含错误/ unwind 路径）。
pub fn install(sink: Arc<dyn ProgressSink>) -> SinkGuard {
    *lock(sink_slot()) = Some(sink);
    let mut st = lock(state());
    st.last_fraction = 0.0;
    st.last_phase.clear();
    st.output_phase = false;
    drop(st);
    SinkGuard
}

/// 是否已注册 sink（引擎只在注册时才有可忽略的少量开销）。
pub fn is_active() -> bool {
    lock(sink_slot()).is_some()
}

/// 清空 sink 并复位单调状态。
pub fn clear() {
    *lock(sink_slot()) = None;
    let mut st = lock(state());
    st.last_fraction = 0.0;
    st.last_phase.clear();
    st.output_phase = false;
}

/// RAII 清理守卫。
pub struct SinkGuard;

impl Drop for SinkGuard {
    fn drop(&mut self) {
        clear();
    }
}

/// 上报一次进度（`fraction` 会被钳制到 `[0,1]` 且**单调不回退**）。
///
/// 无 sink 时直接返回 —— 这是所有埋点的公共入口，保证桌面/单测行为零变化。
pub fn emit(fraction: f64, phase: &str) {
    // 先取 sink（短暂持锁），没有就立刻返回。
    let sink = {
        let slot = lock(sink_slot());
        match slot.as_ref() {
            Some(s) => Arc::clone(s),
            None => return,
        }
    };
    let clamped = if fraction.is_finite() {
        fraction.clamp(0.0, 1.0)
    } else {
        0.0
    };
    // 持锁回调：保证 Kotlin 侧看到的顺序 == 钳制后的顺序（并行批不会倒序）。
    let mut st = lock(state());
    if clamped < st.last_fraction {
        // 不回退：按上一次的值上报（同一值可能重复，调用方/Kotlin 侧自行去抖）。
        if st.last_phase == phase {
            return;
        }
    }
    let effective = clamped.max(st.last_fraction);
    if st.last_phase == phase && effective - st.last_fraction < MIN_DELTA && effective < OUTPUT_END {
        return;
    }
    st.last_fraction = effective;
    st.last_phase.clear();
    st.last_phase.push_str(phase);
    drop(st);
    sink.on_progress(effective, phase);
}

// ── 阶段辅助函数（引擎埋点只调这些，权重口径集中在这里）────────────────────

/// extract 阶段开始。
pub fn extract_started() {
    emit(EXTRACT_START, "extract");
}

/// extract 阶段进度：`done/total` 条目。
pub fn extract_progress(done: usize, total: usize) {
    if total == 0 {
        return;
    }
    let frac = EXTRACT_START + (EXTRACT_END - EXTRACT_START) * (done.min(total) as f64 / total as f64);
    emit(frac, "extract");
}

/// extract 阶段结束（固定落在 0.12，保证 pipeline 起点确定）。
pub fn extract_finished() {
    emit(EXTRACT_END, "extract");
}

/// pipeline 阶段进度：第 `done` / `total` 个任务，`phase` 用任务名。
pub fn pipeline_progress(done: usize, total: usize, phase: &str) {
    if total == 0 {
        return;
    }
    let frac = PIPELINE_START
        + (PIPELINE_END - PIPELINE_START) * (done.min(total) as f64 / total as f64);
    emit(frac, phase);
}

/// pipeline 阶段开始（固定落在 0.12）。
pub fn pipeline_started() {
    emit(PIPELINE_START, "convert");
}

/// pipeline 阶段结束（固定落在 0.90，进入产物输出段）。
pub fn pipeline_finished() {
    emit(PIPELINE_END, "output");
}

/// **进入产物输出段**（由 `native_run` 在序列化/物化之前调用）。
///
/// 只有开启后 [`output_progress`] 才会真的上报：`io::repack_resource_pack` 在
/// Bedrock 源预检阶段（b2j 之前）也会被调用，那时的重打包不是产物输出，
/// 若上报 0.90 会把后面的 pipeline 段单调钳死。
pub fn output_started() {
    let mut st = lock(state());
    st.output_phase = true;
    drop(st);
    emit(OUTPUT_START, "output");
}

fn in_output_phase() -> bool {
    lock(state()).output_phase
}

/// output 阶段进度：已写出 `done/total` 条目（写 zip / 重打包循环里调用）。
pub fn output_progress(done: usize, total: usize) {
    if total == 0 || !in_output_phase() {
        return;
    }
    let frac = OUTPUT_START
        + (OUTPUT_END - OUTPUT_START) * (done.min(total) as f64 / total as f64);
    emit(frac, "output");
}

/// 转换成功收尾：确保最终值到 1.0（幂等）。
pub fn finished() {
    lock(state()).output_phase = true;
    emit(OUTPUT_END, "output");
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 进度旁路回归：权重区间 / 单调不回退 / phase 透传 / guard 清理。
    ///
    /// 注意：本用例是**唯一**注册 sink 的用例（`cargo test` 并行跑其它引擎用例时，
    /// 它们若并发调用 `emit` 只会被单调钳制吸收），因此不按次数断言，只按不变量断言。
    #[test]
    fn progress_sink_maps_phases_and_cleans_up() {
        struct Recorder {
            events: Mutex<Vec<(f64, String)>>,
        }
        impl ProgressSink for Recorder {
            fn on_progress(&self, fraction: f64, phase: &str) {
                lock(&self.events).push((fraction, phase.to_string()));
            }
        }

        let rec = Arc::new(Recorder {
            events: Mutex::new(Vec::new()),
        });
        assert!(!is_active());
        let guard = install(rec.clone());
        assert!(is_active());

        extract_started();
        extract_progress(1, 2);
        extract_finished();
        pipeline_started();
        pipeline_progress(1, 2, "delete_font_folder");
        pipeline_progress(2, 2, "delete_font_folder");
        pipeline_finished();
        output_started();
        output_progress(1, 2);
        finished();

        let events = lock(&rec.events).clone();
        assert!(!events.is_empty(), "sink 应当收到进度事件");
        // 单调不回退（并行批/并发用例可能插入其它事件，但钳制保证有序）。
        for w in events.windows(2) {
            assert!(w[1].0 >= w[0].0, "progress must be monotonic: {:?}", events);
            assert!((0.0..=1.0).contains(&w[1].0));
            assert!(!w[1].1.is_empty());
        }
        // 各阶段 phase 透传。
        let phases: Vec<&str> = events.iter().map(|(_, p)| p.as_str()).collect();
        assert!(phases.contains(&"extract"), "phases={phases:?}");
        assert!(phases.contains(&"delete_font_folder"), "phases={phases:?}");
        assert!(phases.contains(&"output"), "phases={phases:?}");
        // 最终值到 1.0。
        assert!(events.iter().any(|(f, _)| *f >= 1.0), "events={events:?}");

        drop(guard);
        assert!(!is_active(), "guard drop 必须清理 sink");
        let n = lock(&rec.events).len();
        emit(0.5, "after-clear");
        assert_eq!(lock(&rec.events).len(), n, "清理后不得再回调");
    }
}
