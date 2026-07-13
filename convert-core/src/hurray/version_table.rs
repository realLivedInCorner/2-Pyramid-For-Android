// =============================================================================
// 2-Pyramid Version Mapping Table
// =============================================================================
// 完整的 Java/Bedrock 资源包 pack_format 映射表
// - 单一数据源（Single Source of Truth）
// - 同时供 scheduler.rs (Rust) 和 invoke_conversion.rs (engine entry) 使用
// - 任何添加版本/修改任务链，请只改本文件
//
// 同步到桌面版 Hurricane (TauriRust) 的 src-tauri/src/hurray/version_table.rs
// =============================================================================

use std::collections::HashMap;

/// pack_format 编号 → 版本标签（输出文件名 / 进度显示用）
pub const PACK_FORMAT_LABELS: &[&str] = &[
    "Java 1.6-1.8",        // 1
    "Java 1.9-1.10",       // 2
    "Java 1.11-1.12",      // 3
    "Java 1.13-1.14",      // 4
    "Java 1.15-1.16.1",    // 5
    "Java 1.16.2-1.16.5",  // 6
    "Java 1.17",            // 7
    "Java 1.18",            // 8
    "Java 1.19-1.19.2",    // 9
    "Java 1.19.3",          // 12
    "Java 1.19.4",          // 13
    "Java 1.20-1.20.1",    // 15
    "Java 1.20.2",          // 18
    "Java 1.20.3-1.20.4",  // 22
    "Java 1.20.5-1.20.6",  // 32
    "Java 1.21-1.21.1",    // 34
    "Java 1.21.2-1.21.3",  // 42
    "Java 1.21.4",          // 46
    "Java 1.21.5",          // 55
    "Java 1.21.6",          // 63
    "Java 1.21.7-1.21.8",  // 64
    "Java 1.21.9-1.21.10", // 69
    "Java 1.21.11",         // 75
    "Java 26.1-26.1.2",    // 84
    "Bedrock Latest",       // 1000
];

/// pack_format → (index, label) 反向索引
/// index 是 PACK_FORMAT_LABELS 里的位置
pub fn pack_format_label(format: u32) -> &'static str {
    match format {
        1 => PACK_FORMAT_LABELS[0],
        2 => PACK_FORMAT_LABELS[1],
        3 => PACK_FORMAT_LABELS[2],
        4 => PACK_FORMAT_LABELS[3],
        5 => PACK_FORMAT_LABELS[4],
        6 => PACK_FORMAT_LABELS[5],
        7 => PACK_FORMAT_LABELS[6],
        8 => PACK_FORMAT_LABELS[7],
        9 => PACK_FORMAT_LABELS[8],
        12 => PACK_FORMAT_LABELS[9],
        13 => PACK_FORMAT_LABELS[10],
        15 => PACK_FORMAT_LABELS[11],
        18 => PACK_FORMAT_LABELS[12],
        22 => PACK_FORMAT_LABELS[13],
        32 => PACK_FORMAT_LABELS[14],
        34 => PACK_FORMAT_LABELS[15],
        42 => PACK_FORMAT_LABELS[16],
        46 => PACK_FORMAT_LABELS[17],
        55 => PACK_FORMAT_LABELS[18],
        63 => PACK_FORMAT_LABELS[19],
        64 => PACK_FORMAT_LABELS[20],
        69 => PACK_FORMAT_LABELS[21],
        75 => PACK_FORMAT_LABELS[22],
        84 => PACK_FORMAT_LABELS[23],
        1000 => PACK_FORMAT_LABELS[24],
        _ => "Unknown",
    }
}

/// 所有已知 pack_format 列表（用于校验 / UI 列出）
pub const ALL_PACK_FORMATS: &[u32] = &[
    1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 15, 18, 22, 32,
    34, 42, 46, 55, 63, 64, 69, 75, 84, 1000,
];

/// 完整版本元数据（id, label, abi hint for UI sort）
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct VersionInfo {
    pub pack_format: u32,
    pub label: &'static str,
    pub major_minor: &'static str, // 简称 "1.21.4" "1.20.5" "Bedrock"
    pub tier: VersionTier,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum VersionTier {
    JavaLegacy,    // 1.6 - 1.18 (1-8)
    JavaModern,    // 1.19 - 1.21.x (9, 12, 13, 15-75)
    Java26,        // 26.x (84)
    Bedrock,       // 1000
}

pub const ALL_VERSIONS: &[VersionInfo] = &[
    VersionInfo { pack_format: 1,    label: "Java 1.6-1.8",        major_minor: "1.6-1.8",    tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 2,    label: "Java 1.9-1.10",       major_minor: "1.9-1.10",   tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 3,    label: "Java 1.11-1.12",      major_minor: "1.11-1.12",  tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 4,    label: "Java 1.13-1.14",      major_minor: "1.13-1.14",  tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 5,    label: "Java 1.15-1.16.1",    major_minor: "1.15-1.16",  tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 6,    label: "Java 1.16.2-1.16.5",  major_minor: "1.16.2-1.16.5", tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 7,    label: "Java 1.17",            major_minor: "1.17",       tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 8,    label: "Java 1.18",            major_minor: "1.18",       tier: VersionTier::JavaLegacy },
    VersionInfo { pack_format: 9,    label: "Java 1.19-1.19.2",    major_minor: "1.19-1.19.2", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 12,   label: "Java 1.19.3",          major_minor: "1.19.3",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 13,   label: "Java 1.19.4",          major_minor: "1.19.4",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 15,   label: "Java 1.20-1.20.1",    major_minor: "1.20-1.20.1", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 18,   label: "Java 1.20.2",          major_minor: "1.20.2",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 22,   label: "Java 1.20.3-1.20.4",  major_minor: "1.20.3-1.20.4", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 32,   label: "Java 1.20.5-1.20.6",  major_minor: "1.20.5-1.20.6", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 34,   label: "Java 1.21-1.21.1",    major_minor: "1.21-1.21.1", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 42,   label: "Java 1.21.2-1.21.3",  major_minor: "1.21.2-1.21.3", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 46,   label: "Java 1.21.4",          major_minor: "1.21.4",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 55,   label: "Java 1.21.5",          major_minor: "1.21.5",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 63,   label: "Java 1.21.6",          major_minor: "1.21.6",     tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 64,   label: "Java 1.21.7-1.21.8",  major_minor: "1.21.7-1.21.8", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 69,   label: "Java 1.21.9-1.21.10", major_minor: "1.21.9-1.21.10", tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 75,   label: "Java 1.21.11",         major_minor: "1.21.11",    tier: VersionTier::JavaModern },
    VersionInfo { pack_format: 84,   label: "Java 26.1-26.1.2",    major_minor: "26.1-26.1.2", tier: VersionTier::Java26 },
    VersionInfo { pack_format: 1000, label: "Bedrock Latest",       major_minor: "Bedrock",    tier: VersionTier::Bedrock },
];

// =============================================================================
// 任务链（Task Chains）
// =============================================================================
// 每个 (from, to) 转换段需要执行的任务列表
// 任务名 = convert-core/src/converters/ 下的模块名
// 命名约定：
//   - `delete_*`     → Eraser 层（删旧文件）
//   - `generate_*`    → Architect 层（生成新资源）
//   - `fix_*`         → Surgeon 层（修改现有）
//   - `rename_*`      → Eraser 层（重命名）
//   - `overlay_*`     → Surgeon 层（叠加处理）
//   - `cut_gui`       → Surgeon 层（atlas 拆分）
//   - `reverse_*`     → 逆向任务
// =============================================================================

/// 正向任务链 (from -> to)
pub const FORWARD_CHAIN: &[((u32, u32), &[&str])] = &[
    // ============ Java 1.6-1.8 → 1.9-1.10 (1 → 2) ============
    ((1, 2), &[
        "delete_blockstates_models",
        "generate_tipped_arrow_images",
        "fix_ui_survival",
        "fix_ui_creative",
        "fix_ui_sub_hand",
        "generate_boat",
        "generate_potion_lingering",
        "generate_shulker_box_ui",
        "fix_brewing_stand_ui",
        "fix_clock_compass",
        "overlay_icons",
    ]),
    // ============ 1.9-1.10 → 1.11-1.12 (2 → 3) ============
    ((2, 3), &[
        "generate_shulker_box_ui",
        "delete_horse_folder",
        "fix_horse_ui",
    ]),
    // ============ 1.11-1.12 → 1.13-1.14 (3 → 4) ============
    ((3, 4), &[
        "rename_blocks_items",
        "fix_sign",
        "fix_sign_entities",
        "generate_furnace",
        "fix_machinery_ui",
        "fix_particles",
        "generate_fish_bucket",
        "generate_crossbow",
    ]),
    // ============ 1.13-1.14 → 1.15-1.16.1 (4 → 5) ============
    ((4, 5), &[
        "process_chest_folder",
        "generate_netherite_block",
        "generate_netherite_ingot",
        "delete_enchanted_item_glint",
        "generate_netherite_tools",
        "generate_netherite_armor_models",
        "generate_smithing_ui",
    ]),
    // ============ 1.15-1.16.1 → 1.16.2-1.16.5 (5 → 6) ============
    ((5, 6), &[
        "delete_font_folder",
    ]),
    // ============ 1.16.2-1.16.5 → 1.17 (6 → 7) ============
    ((6, 7), &[
        "generate_snow_bucket",
    ]),
    // ============ 1.17 → 1.18 (7 → 8) ============
    ((7, 8), &[
        "rename_mcpatcher_to_optifine",
    ]),
    // ============ 1.18 → 1.19-1.19.2 (8 → 9) ============
    ((8, 9), &[
        // 占位：基础资源足够
    ]),
    // ============ 1.19-1.19.2 → 1.19.3 (9 → 12) ============
    ((9, 12), &[
        "fix_tabs",
        "generate_redwood_cherry_bamboo_planks",
    ]),
    // ============ 1.19.3 → 1.19.4 (12 → 13) ============
    ((12, 13), &[
        "fix_smithing2_villager2_ui",
        "fix_slider",
    ]),
    // ============ 1.19.4 → 1.20-1.20.1 (13 → 15) ============
    ((13, 15), &[
        // 占位
    ]),
    // ============ 1.20-1.20.1 → 1.20.2 (15 → 18) ============
    ((15, 18), &[
        "cut_gui",
    ]),
    // ============ 1.20.2 → 1.20.3-1.20.4 (18 → 22) ============
    ((18, 22), &[
        // 占位
    ]),
    // ============ 1.20.3-1.20.4 → 1.20.5-1.20.6 (22 → 32) ============
    ((22, 32), &[
        // 占位
    ]),
    // ============ 1.20.5-1.20.6 → 1.21-1.21.1 (32 → 34) ============
    ((32, 34), &[
        "delete_shaders_folder",
    ]),
    // ============ 1.21-1.21.1 → 1.21.2-1.21.3 (34 → 42) ============
    ((34, 42), &[
        "delete_shaders_folder",
    ]),
    // ============ 1.21.2-1.21.3 → 1.21.4 (42 → 46) ============
    ((42, 46), &[
        "fix2_horse_ui",
        "fix_armor_models",
        "generate_pale_planks",
    ]),
    // ============ 1.21.4 → 1.21.5 (46 → 55) ============
    ((46, 55), &[
        // 占位
    ]),
    // ============ 1.21.5 → 1.21.6 (55 → 63) ============
    ((55, 63), &[
        // 占位
    ]),
    // ============ 1.21.6 → 1.21.7-1.21.8 (63 → 64) ============
    ((63, 64), &[
        // 占位
    ]),
    // ============ 1.21.7-1.21.8 → 1.21.9-1.21.10 (64 → 69) ============
    ((64, 69), &[
        "generate_copper_ingot",
        "generate_copper_block",
        "generate_copper_tools",
        "generate_copper_armor_models",
    ]),
    // ============ 1.21.9-1.21.10 → 1.21.11 (69 → 75) ============
    ((69, 75), &[
        // 占位
    ]),
    // ============ 1.21.11 → 26.1-26.1.2 (75 → 84) ============
    ((75, 84), &[
        // 占位
    ]),
    // ============ 26.1-26.1.2 → Bedrock (84 → 1000) ============
    // 暂未实现 Bedrock 转换（保留作为未来扩展点）
    ((84, 1000), &[
        // 占位
    ]),
];

/// 逆向任务链 (to -> from)
/// 注意：逆向任务在 (high, low) 上注册，但执行时按降序遍历
pub const REVERSE_CHAIN: &[((u32, u32), &[&str])] = &[
    // ============ Bedrock → 26.1-26.1.2 (1000 → 84) ============
    ((1000, 84), &[]),
    // ============ 26.1-26.1.2 → 1.21.11 (84 → 75) ============
    ((84, 75), &[]),
    // ============ 1.21.11 → 1.21.9-1.21.10 (75 → 69) ============
    ((75, 69), &[]),
    // ============ 1.21.9-1.21.10 → 1.21.7-1.21.8 (69 → 64) ============
    ((69, 64), &[
        "reverse_generate_copper_ingot",
        "reverse_generate_copper_block",
        "reverse_generate_copper_tools",
        "reverse_generate_copper_armor_models",
    ]),
    // ============ 1.21.7-1.21.8 → 1.21.6 (64 → 63) ============
    ((64, 63), &[]),
    // ============ 1.21.6 → 1.21.5 (63 → 55) ============
    ((63, 55), &[]),
    // ============ 1.21.5 → 1.21.4 (55 → 46) ============
    ((55, 46), &[]),
    // ============ 1.21.4 → 1.21.2-1.21.3 (46 → 42) ============
    ((46, 42), &[
        "reverse_fix_armor_models",
        "reverse_fix2_horse_ui",
        "reverse_generate_pale_planks",
    ]),
    // ============ 1.21.4 → 1.21.2-1.21.3 (46 → 42) ============
    ((42, 34), &[
        "reverse_fix2_horse_ui",
    ]),
    // ============ 1.21.2-1.21.3 → 1.21-1.21.1 (42 → 34) ============
    ((34, 32), &[]),
    // ============ 1.21-1.21.1 → 1.20.5-1.20.6 (34 → 32) ============
    ((32, 22), &[]),
    // ============ 1.20.5-1.20.6 → 1.20.3-1.20.4 (32 → 22) ============
    ((22, 18), &[]),
    // ============ 1.20.3-1.20.4 → 1.20.2 (22 → 18) ============
    ((18, 15), &[
        "reverse_cut_gui",
    ]),
    // ============ 1.20.2 → 1.20-1.20.1 (18 → 15) ============
    ((15, 13), &[]),
    // ============ 1.20-1.20.1 → 1.19.4 (15 → 13) ============
    ((13, 12), &[
        "reverse_fix_smithing2_villager2_ui",
        "reverse_fix_slider",
    ]),
    // ============ 1.19.4 → 1.19.3 (13 → 12) ============
    ((12, 9), &[
        "reverse_generate_redwood_cherry_bamboo_planks",
    ]),
    // ============ 1.19.3 → 1.19-1.19.2 (12 → 9) ============
    ((9, 8), &[]),
    // ============ 1.19-1.19.2 → 1.18 (9 → 8) ============
    ((8, 7), &[
        "reverse_rename_mcpatcher_to_optifine",
    ]),
    // ============ 1.18 → 1.17 (8 → 7) ============
    ((7, 6), &[]),
    // ============ 1.17 → 1.16.2-1.16.5 (7 → 6) ============
    ((6, 5), &[
        "reverse_generate_snow_bucket",
    ]),
    // ============ 1.16.2-1.16.5 → 1.15-1.16.1 (6 → 5) ============
    ((5, 4), &[
        "reverse_process_chest_folder",
        "reverse_generate_netherite_block",
        "reverse_generate_netherite_ingot",
        "reverse_generate_netherite_tools",
        "reverse_generate_netherite_armor_models",
        "reverse_generate_smithing_ui",
    ]),
    // ============ 1.15-1.16.1 → 1.13-1.14 (5 → 4) ============
    ((4, 3), &[
        "reverse_rename_blocks_items",
        "reverse_fix_sign",
        "reverse_fix_sign_entities",
        "reverse_generate_furnace",
        "reverse_fix_machinery_ui",
        "reverse_fix_particles",
        "reverse_generate_fish_bucket",
        "reverse_generate_crossbow",
    ]),
    // ============ 1.13-1.14 → 1.11-1.12 (4 → 3) ============
    ((3, 2), &[
        "reverse_fix_horse_ui",
        "delete_horse_folder",
    ]),
    // ============ 1.11-1.12 → 1.9-1.10 (3 → 2) ============
    ((2, 1), &[
        "delete_blockstates_models",
        "reverse_generate_tipped_arrow_images",
        "reverse_fix_ui_survival",
        "reverse_fix_ui_creative",
        "reverse_fix_ui_sub_hand",
        "reverse_generate_boat",
        "reverse_generate_potion_lingering",
        "reverse_generate_shulker_box_ui",
        "reverse_fix_brewing_stand_ui",
        "reverse_fix_clock_compass",
        "reverse_overlay_icons",
    ]),
];

/// 构建 forward 转换映射
pub fn build_forward_map() -> HashMap<(u32, u32), Vec<String>> {
    let mut map = HashMap::new();
    for ((from, to), tasks) in FORWARD_CHAIN {
        map.insert(
            (*from, *to),
            tasks.iter().map(|s| s.to_string()).collect(),
        );
    }
    map
}

/// 构建 reverse 转换映射
pub fn build_reverse_map() -> HashMap<(u32, u32), Vec<String>> {
    let mut map = HashMap::new();
    for ((from, to), tasks) in REVERSE_CHAIN {
        map.insert(
            (*from, *to),
            tasks.iter().map(|s| s.to_string()).collect(),
        );
    }
    map
}

/// 给定 source/target pack_format，返回完整转换路径（多段）
/// 返回 Vec<(from, to)> 按转换顺序
pub fn calculate_conversion_path(source: u32, target: u32) -> Vec<(u32, u32)> {
    if source == target {
        return vec![];
    }

    let chain: &[((u32, u32), &[&str])] = if target > source {
        FORWARD_CHAIN
    } else {
        REVERSE_CHAIN
    };

    // 收集所有可达节点
    let mut graph: HashMap<u32, Vec<u32>> = HashMap::new();
    for (from, to) in chain.iter().map(|(k, _)| *k) {
        graph.entry(from).or_default().push(to);
    }

    // BFS 找最短路径
    use std::collections::{HashSet, VecDeque};
    let mut visited = HashSet::new();
    let mut queue = VecDeque::new();
    let mut parent: HashMap<u32, u32> = HashMap::new();

    queue.push_back(source);
    visited.insert(source);

    while let Some(node) = queue.pop_front() {
        if node == target {
            // 回溯路径
            let mut path = vec![];
            let mut current = target;
            while current != source {
                if let Some(&p) = parent.get(&current) {
                    path.push((p, current));
                    current = p;
                } else {
                    return vec![]; // 不可达
                }
            }
            path.reverse();
            return path;
        }

        if let Some(neighbors) = graph.get(&node) {
            for &n in neighbors {
                if !visited.contains(&n) {
                    visited.insert(n);
                    parent.insert(n, node);
                    queue.push_back(n);
                }
            }
        }
    }

    vec![] // 不可达
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pack_format_label() {
        assert_eq!(pack_format_label(1), "Java 1.6-1.8");
        assert_eq!(pack_format_label(34), "Java 1.21-1.21.1");
        assert_eq!(pack_format_label(1000), "Bedrock Latest");
        assert_eq!(pack_format_label(999), "Unknown");
    }

    #[test]
    fn test_conversion_path_simple() {
        let path = calculate_conversion_path(1, 2);
        assert_eq!(path, vec![(1, 2)]);
    }

    #[test]
    fn test_conversion_path_multi_step() {
        let path = calculate_conversion_path(1, 8);
        assert_eq!(path, vec![(1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 7), (7, 8)]);
    }

    #[test]
    fn test_conversion_path_reverse() {
        let path = calculate_conversion_path(34, 1);
        // 反向路径，从 34 走到 1
        assert!(!path.is_empty());
        assert_eq!(path[0], (34, 32));
    }

    #[test]
    fn test_forward_and_reverse_maps() {
        let fwd = build_forward_map();
        let rev = build_reverse_map();
        assert!(fwd.contains_key(&(1, 2)));
        assert!(fwd.contains_key(&(34, 42)));
        assert!(fwd.contains_key(&(64, 69)));
        assert!(rev.contains_key(&(46, 42)));
        assert!(rev.contains_key(&(2, 1)));
    }
}
