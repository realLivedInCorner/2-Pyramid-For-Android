use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::{Arc, RwLock};
use std::sync::atomic::{AtomicUsize, Ordering};

use rayon::prelude::*;

use crate::hurray::context::HurrayContext;
use crate::hurray::error::{EngineError, EngineResult};
use crate::hurray::resolution::ResolutionTransducer;
use crate::hurray::texture::TexturePool;
use crate::hurray::version_table;
use crate::{log_error, log_info};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TaskType {
    Parallel,
    Exclusive,
    Hybrid,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Ord, PartialOrd)]
pub enum TaskTier {
    Eraser = 10,
    Architect = 20,
    Surgeon = 30,
    Closure = 40,
}

type TaskFn = Arc<dyn Fn(&HurrayContext) -> Result<(), String> + Send + Sync>;

#[derive(Clone)]
struct Task {
    name: String,
    task_type: TaskType,
    tier: TaskTier,
    task: TaskFn,
}

type VersionMap = HashMap<(u32, u32), Vec<String>>;

pub struct ConversionMaps {
    pub forward: VersionMap,
    pub reverse: VersionMap,
}

impl ConversionMaps {
    pub fn new() -> Self {
        Self {
            forward: version_table::build_forward_map(),
            reverse: version_table::build_reverse_map(),
        }
    }
}

pub struct Scheduler {
    tasks: Vec<Task>,
    conversion_maps: ConversionMaps,
    task_registry: HashMap<String, Task>,
}

impl Scheduler {
    pub fn new() -> Self {
        Self {
            tasks: Vec::new(),
            conversion_maps: ConversionMaps::new(),
            task_registry: HashMap::new(),
        }
    }

    pub fn register_task<F>(&mut self, name: &str, task_type: TaskType, tier: TaskTier, task: F)
    where
        F: Fn(&HurrayContext) -> Result<(), String> + Send + Sync + 'static,
    {
        let task = Task {
            name: name.to_string(),
            task_type,
            tier,
            task: Arc::new(task),
        };

        self.tasks.push(task.clone());
        self.task_registry.insert(name.to_string(), task);
    }

    pub fn calculate_path(&self, source: u32, target: u32) -> EngineResult<Vec<(u32, u32)>> {
        let maps = if target >= source {
            &self.conversion_maps.forward
        } else {
            &self.conversion_maps.reverse
        };

        let mut graph: HashMap<u32, Vec<u32>> = HashMap::new();
        for &(from, to) in maps.keys() {
            graph.entry(from).or_default().push(to);
        }

        let mut visited = HashSet::new();
        let mut queue = VecDeque::new();
        let mut parent: HashMap<u32, u32> = HashMap::new();

        queue.push_back(source);
        visited.insert(source);

        while let Some(current) = queue.pop_front() {
            if current == target {
                let mut path = Vec::new();
                let mut node = target;
                while node != source {
                    let prev = match parent.get(&node) {
                        Some(prev) => *prev,
                        None => {
                            return Err(EngineError::PathNotFound { source, target });
                        }
                    };
                    path.push((prev, node));
                    node = prev;
                }
                path.reverse();
                return Ok(path);
            }

            if let Some(neighbors) = graph.get(&current) {
                for &neighbor in neighbors {
                    if visited.insert(neighbor) {
                        parent.insert(neighbor, current);
                        queue.push_back(neighbor);
                    }
                }
            }
        }

        Err(EngineError::PathNotFound { source, target })
    }

    pub fn get_tasks_for_path(&self, path: &[(u32, u32)]) -> Vec<String> {
        let mut ordered = Vec::new();
        let mut seen = HashSet::new();

        for &(from, to) in path {
            if let Some(tasks) = self.conversion_maps.forward.get(&(from, to)) {
                for task in tasks {
                    if seen.insert(task.clone()) {
                        ordered.push(task.clone());
                    }
                }
            }
            if let Some(tasks) = self.conversion_maps.reverse.get(&(from, to)) {
                for task in tasks {
                    if seen.insert(task.clone()) {
                        ordered.push(task.clone());
                    }
                }
            }
        }

        ordered
    }

    fn get_tasks_for_path_with_rules(&self, path: &[(u32, u32)], target_version: u32) -> Vec<String> {
        let mut ordered = Vec::new();
        let mut seen = HashSet::new();

        for &(from, to) in path {
            if let Some(tasks) = self.conversion_maps.forward.get(&(from, to)) {
                for task in tasks {
                    if from == 9 && to == 12 && target_version > 15 && task == "fix_tabs" {
                        continue;
                    }
                    if seen.insert(task.clone()) {
                        ordered.push(task.clone());
                    }
                }
            }
            if let Some(tasks) = self.conversion_maps.reverse.get(&(from, to)) {
                for task in tasks {
                    if seen.insert(task.clone()) {
                        ordered.push(task.clone());
                    }
                }
            }
        }

        ordered
    }
    pub fn execute(
        &mut self,
        context: &HurrayContext,
        texture_pool: &mut TexturePool,
        _resolution: &ResolutionTransducer,
    ) -> EngineResult<()> {
        self.execute_tasks(&self.tasks.clone(), context, texture_pool, None)
    }

    pub fn execute_version_conversion(
        &mut self,
        context: &HurrayContext,
        texture_pool: &mut TexturePool,
        source_version: u32,
        target_version: u32,
    ) -> EngineResult<()> {
        log_info!(
            "start version conversion: {} -> {}",
            source_version,
            target_version
        );

        let path = self.calculate_path(source_version, target_version)?;
        log_info!("resolved conversion path: {:?}", path);

        let task_names = self.get_tasks_for_path_with_rules(&path, target_version);
        log_info!("tasks selected: {:?}", task_names);

        let filtered_tasks: Vec<Task> = task_names
            .iter()
            .filter_map(|task_name| self.task_registry.get(task_name).cloned())
            .collect();

        let total_tasks = filtered_tasks.len();
        let pack_name = context.get_data("pack_name").unwrap_or_default();
        let progress = Arc::new(ProgressTracker::new(total_tasks, pack_name));
        self.execute_tasks(&filtered_tasks, context, texture_pool, Some(progress))?;
        texture_pool.clear_unused();

        Ok(())
    }

    pub fn clear(&mut self) {
        self.tasks.clear();
        self.task_registry.clear();
    }

    fn execute_tasks(
        &self,
        tasks: &[Task],
        context: &HurrayContext,
        texture_pool: &mut TexturePool,
        progress: Option<Arc<ProgressTracker>>,
    ) -> EngineResult<()> {
        let mut eraser = Vec::new();
        let mut architect = Vec::new();
        let mut surgeon = Vec::new();
        let mut closure = Vec::new();

        for task in tasks {
            match task.tier {
                TaskTier::Eraser => eraser.push(task.clone()),
                TaskTier::Architect => architect.push(task.clone()),
                TaskTier::Surgeon => surgeon.push(task.clone()),
                TaskTier::Closure => closure.push(task.clone()),
            }
        }

        self.execute_serial_tier("Eraser", &eraser, context, progress.clone())?;
        self.execute_parallel_capable_tier("Architect", &architect, context, false, progress.clone())?;
        self.execute_parallel_capable_tier("Surgeon", &surgeon, context, true, progress.clone())?;
        self.execute_serial_tier("Closure", &closure, context, progress.clone())?;

        texture_pool.commit_all()?;
        Ok(())
    }

    fn execute_serial_tier(
        &self,
        tier_name: &'static str,
        tasks: &[Task],
        context: &HurrayContext,
        progress: Option<Arc<ProgressTracker>>,
    ) -> EngineResult<()> {
        if tasks.is_empty() {
            return Ok(());
        }

        log_info!("tier start [{}], tasks={}", tier_name, tasks.len());
        let mut failures = Vec::new();

        for task in tasks {
            if let Err(reason) = (task.task)(context) {
                let wrapped = EngineError::Task {
                    task: task.name.clone(),
                    reason,
                }
                .to_string();
                log_error!("{}", wrapped);
                failures.push(wrapped);
            }
            if let Some(progress) = &progress {
                progress.bump(&task.name);
            }
        }

        if failures.is_empty() {
            log_info!("tier done [{}]", tier_name);
            return Ok(());
        }

        Err(EngineError::Tier {
            tier: tier_name,
            failures,
        })
    }

    fn execute_parallel_capable_tier(
        &self,
        tier_name: &'static str,
        tasks: &[Task],
        context: &HurrayContext,
        use_pool_guard: bool,
        progress: Option<Arc<ProgressTracker>>,
    ) -> EngineResult<()> {
        if tasks.is_empty() {
            return Ok(());
        }

        log_info!("tier start [{}], tasks={}", tier_name, tasks.len());

        let (parallel, serial): (Vec<Task>, Vec<Task>) = tasks
            .iter()
            .cloned()
            .partition(|task| matches!(task.task_type, TaskType::Parallel));

        let mut failures = Vec::new();

        let pool_guard = Arc::new(RwLock::new(()));

        let parallel_failures: Vec<String> = parallel
            .par_iter()
            .filter_map(|task| {
                let run = || (task.task)(context).map_err(|reason| EngineError::Task {
                    task: task.name.clone(),
                    reason,
                });

                let result = if use_pool_guard {
                    match pool_guard.read() {
                        Ok(_guard) => run(),
                        Err(_) => Err(EngineError::LockPoisoned("scheduler.texture_pool_guard")),
                    }
                } else {
                    run()
                };

                if let Some(progress) = &progress {
                    progress.bump(&task.name);
                }

                result.err().map(|e| {
                    let msg = e.to_string();
                    log_error!("{}", msg);
                    msg
                })
            })
            .collect();
        failures.extend(parallel_failures);

        for task in serial {
            let task_name = task.name.clone();
            let result = if use_pool_guard {
                match pool_guard.write() {
                    Ok(_guard) => (task.task)(context),
                    Err(_) => Err(EngineError::LockPoisoned("scheduler.texture_pool_guard").to_string()),
                }
            } else {
                (task.task)(context)
            };

            if let Err(reason) = result {
                let wrapped = EngineError::Task {
                    task: task_name.clone(),
                    reason,
                }
                .to_string();
                log_error!("{}", wrapped);
                failures.push(wrapped);
            }
            if let Some(progress) = &progress {
                progress.bump(&task_name);
            }
        }

        if failures.is_empty() {
            log_info!("tier done [{}]", tier_name);
            return Ok(());
        }

        Err(EngineError::Tier {
            tier: tier_name,
            failures,
        })
    }
}

struct ProgressTracker {
    total: usize,
    done: AtomicUsize,
    prefix: String,
}

impl ProgressTracker {
    fn new(total: usize, pack_name: String) -> Self {
        let trimmed = pack_name.trim();
        let prefix = if trimmed.is_empty() {
            String::new()
        } else {
            format!("[{}] ", trimmed)
        };
        Self {
            total: total.max(1),
            done: AtomicUsize::new(0),
            prefix,
        }
    }

    fn bump(&self, task_name: &str) {
        let current = self.done.fetch_add(1, Ordering::SeqCst) + 1;
        // 与桌面版一致的节流：每 50 个模块 + 最后一个模块才写日志，
        // 避免大批量转换时日志洪泛拖慢引擎。
        if current % 50 == 0 || current == self.total {
            let percent = (current * 100) / self.total;
            log_info!(
                "{}Progress: {}/{} ({}%) - {}",
                self.prefix,
                current,
                self.total,
                percent,
                task_name
            );
        }
    }
}
