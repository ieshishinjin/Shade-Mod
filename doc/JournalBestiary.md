# 日记/图鉴系统 — 设计文档

> 适用于 Shade Mod v0.0.7+

---

## 概述

日记/图鉴系统是玩家在游戏中的"收集册"，自动记录剧情历程和发现物。

- **日记** — 自动记录完成的剧情章节、遇到的关键 NPC、经历的重要事件、发现的地点
- **图鉴** — 自动记录首次击杀的生物、首次收集的物品、首次挖掘的方块

---

## 一、UI 界面

按 **J** 键或在剧情菜单中点击「日记」按钮打开，双标签页布局。

### 1.1 布局

```
┌─────────────────────────────────────────────────┐
│       ✦ 日记                         3/6         │  ← 标题 + 统计
│  ┌─────────┐ ┌────────┐ ┌───┐                    │
│  │ ✦ 日记  │ │ 图鉴   │ │关闭│                   │  ← 标签切换
│  └─────────┘ └────────┘ └───┘                    │
│  ┌───────────────┬──────────────────────────┐    │
│  │ ✔ §6[剧情]    │  §l§6第一章：苏醒          │    │  ← 右栏详情
│  │   第一章：苏醒  │  §7剧情记录                │    │
│  │ ✔ §b[人物]    │  ──────────────           │    │
│  │   阿卡娅       │  在晨曦营地中醒来...       │    │
│  │ ✔ §a[地点]    │                          │    │
│  │   晨曦营地     │                          │    │
│  │ ? §e[事件]    │                          │    │
│  │   第一个任务   │                          │    │
│  └───────────────┴──────────────────────────┘    │
│                    §7← 剧情菜单                  │
└─────────────────────────────────────────────────┘
```

- **左栏**：可滚动条目列表，已解锁✔ 未解锁?
- **右栏**：选中条目的详情描述
- **Tab 键**切换标签，↑↓ 导航条目

### 1.2 日记操作

| 操作 | 方式 |
|------|------|
| 打开 | **J** 或剧情菜单 → 「日记」 |
| 切换图鉴 | 点击「图鉴」标签或按 **Tab** |
| 选择条目 | 点击鼠标或 **↑ / ↓** |
| 滚动列表 | **鼠标滚轮** |
| 刷新数据 | 关闭重新打开 |
| 返回剧情菜单 | 点击「← 剧情菜单」 |
| 关闭 | **ESC** |

### 1.3 日记条目类型

| 类型 | 标签色 | 来源 |
|------|--------|------|
| **剧情** SCRIPT | §6金色 | 完成某个剧情章节后自动解锁 |
| **事件** FLAG | §e黄色 | 触发关键剧情 Flag 后解锁 |
| **人物** NPC | §b浅蓝 | 首次与重要 NPC 交互后解锁 |
| **地点** LOCATION | §a绿色 | 发现重要地点后解锁 |

### 1.4 图鉴条目类型

| 类型 | 标签色 | 来源 |
|------|--------|------|
| **生物** MOB | §c红色 | 首次击杀该生物 |
| **物品** ITEM | §e黄色 | 首次获得该物品 |
| **方块** BLOCK | §7灰色 | 首次挖掘该方块 |

---

## 二、技术架构

### 2.1 模块关系

```
客户端                           服务端
─────                           ─────
J 键按下 ──C2S→ JournalRequestPayload ──→ JournalManager.getDisplayData()
                  │                              │
                  │                              ├─ journalDefs（内置定义）
                  │                              └─ unlockedEntries（玩家已解锁）
                  │                              → 加载/创建 JSON 文件
                  │                                    data/shade/journal/<uuid>.json
                  │
                  └S2C← JournalPayload(entries, unlockedIds)
                  │
           JournalScreen.updateJournal()
                  ↓
           渲染双标签页 GUI
```

图鉴系统采用完全相同的架构，只是路径和 Handler 不同：

```
客户端                          服务端
─────                          ─────
J 键按下 ──C2S→ BestiaryRequestPayload ──→ BestiaryManager.getDisplayData()
                  │                               │
                  │                               ├─ bestiaryDefs（内置定义）
                  │                               └─ discoveredEntries（玩家已发现）
                  │                               → data/shade/bestiary/<uuid>.json
                  │
                  └S2C← BestiaryPayload(entries, discoveredIds)
                  │
           JournalScreen.updateBestiary()
```

### 2.2 数据流

1. 客户端按 J → 同时发出 2 个 C2S 请求（Journal + Bestiary）
2. 服务端分别查询对应管理器 → 组装 S2C 响应
3. 客户端接收器 → `JournalScreen.updateJournal()` / `updateBestiary()`
4. 两个请求独立处理，任何一个先到达都可部分渲染

### 2.3 持久化

每个玩家的日记/图鉴数据单独保存为 JSON 文件：

| 系统 | 存储路径 | 格式 |
|------|----------|------|
| 日记 | `<world>/data/shade/journal/<uuid>.json` | `["entry_id1", "entry_id2", ...]` |
| 图鉴 | `<world>/data/shade/bestiary/<uuid>.json` | `["mob_id1", "item_id1", ...]` |

- 格式同画廊系统，仅存储已解锁/已发现的 ID 集合
- 条目定义（标题、描述等）硬编码在管理器中，不随存档保存
- 每次 unlock/discover 时立即写入磁盘（同步），saveAll 用于优雅关闭

---

## 三、条目定义

### 3.1 内置日记条目

| ID | 类型 | 标题 | 解锁条件 |
|----|------|------|----------|
| journal_chapter1 | SCRIPT | 第一章：苏醒 | 完成 chapter1_wake_up |
| journal_chapter2 | SCRIPT | 第二章：林中迷途 | 完成 chapter2_forest_whispers |
| journal_npc_akaya | NPC | 阿卡娅 | 首次与村民交互 |
| journal_loc_camp | LOCATION | 晨曦营地 | 自动解锁 |
| journal_flag_first_quest | FLAG | 第一个任务 | flag accepted_first_quest=true |
| journal_flag_quest_complete | FLAG | 首次任务完成 | flag completed_recon=true |

### 3.2 内置图鉴条目

| ID | 类型 | 分类 | 标题 |
|----|------|------|------|
| bestiary_zombie | MOB | hostile | 僵尸 |
| bestiary_skeleton | MOB | hostile | 骷髅 |
| bestiary_spider | MOB | hostile | 蜘蛛 |
| bestiary_creeper | MOB | hostile | 苦力怕 |
| bestiary_pillager | MOB | hostile | 掠夺者 |
| bestiary_husk | MOB | hostile | 尸壳 |
| bestiary_stray | MOB | hostile | 流浪者 |
| bestiary_witch | MOB | hostile | 女巫 |
| bestiary_villager | MOB | passive | 村民 |
| bestiary_wandering_trader | MOB | passive | 流浪商人 |
| bestiary_iron_ingot | ITEM | resource | 铁锭 |
| bestiary_gold_ingot | ITEM | resource | 金锭 |
| bestiary_diamond | ITEM | resource | 钻石 |
| bestiary_bread | ITEM | food | 面包 |
| bestiary_golden_apple | ITEM | food | 金苹果 |
| bestiary_stone | BLOCK | building | 石头 |
| bestiary_oak_log | BLOCK | building | 橡木原木 |
| bestiary_coal_ore | BLOCK | resource | 煤矿 |
| bestiary_iron_ore | BLOCK | resource | 铁矿 |

---

## 四、自动解锁机制

### 4.1 日记自动解锁

| 触发点 | 解锁逻辑 | 代码位置 |
|--------|----------|----------|
| `StoryEngine.endStory()` | 按 scriptId 匹配 SCRIPT 类型条目 | `JournalManager.unlockByScript(player, scriptId, flags)` |
| NPC 交互 | 按 entityId 匹配 NPC 类型条目 | `JournalManager.unlockNpc(player, entityId)` |

### 4.2 图鉴自动解锁

| 触发点 | 解锁逻辑 | 代码位置 |
|--------|----------|----------|
| `StoryEventHandler.handleMobKill()` | 按 entityId 映射到图鉴 ID | `BestiaryManager.discoverByMobKill(player, entityId)` |
| `InventoryTracker.scan()` | 检测新物品 → 按 itemId 映射 | `BestiaryManager.discoverByItemCollect(player, itemId)` |

条目 ID 映射表（Minecraft ID → 图鉴 ID）：

```java
case "minecraft:zombie"        → "bestiary_zombie"
case "minecraft:iron_ingot"    → "bestiary_iron_ingot"
case "minecraft:stone"         → "bestiary_stone"
// ... 完整映射见 BestiaryManager.mapEntityToBestiaryId()
// 和 BestiaryManager.mapRegistryToBestiaryId()
```

---

## 五、包结构

```
src/main/java/io/github/shade/story/journal/
├── JournalEntry.java        # 日记数据模型
├── BestiaryEntry.java       # 图鉴数据模型
├── JournalManager.java      # 日记管理器 + 持久化
└── BestiaryManager.java     # 图鉴管理器 + 持久化

src/client/java/io/github/shade/client/story/
└── JournalScreen.java       # 日记/图鉴双标签 GUI

src/main/java/io/github/shade/story/network/StoryPayloads.java
    ├── JournalPayload       # S2C 日记数据包
    ├── JournalRequestPayload  # C2S 日记请求包
    ├── BestiaryPayload      # S2C 图鉴数据包
    └── BestiaryRequestPayload # C2S 图鉴请求包
```

---

## 六、扩展指南

### 6.1 添加新日记条目

在 `JournalManager.registerDefaultEntries()` 中添加：

```java
addDefinition(JournalEntry.script("journal_chapter3", "第三章：探索",
    "穿越迷雾森林，发现了隐藏在深处的古代遗迹。", "chapter3_exploration"));
```

### 6.2 添加新图鉴条目

在 `BestiaryManager.registerDefaultEntries()` 中添加：

```java
addDefinition(BestiaryEntry.mob("bestiary_enderman", "末影人",
    "来自末地的神秘生物，会瞬移且搬运方块。不要直视它们的眼睛。", "hostile"));
```

### 6.3 添加实体映射

如果条目 ID 的命名规则与 Minecraft 实体 ID 不匹配，需要在映射表中添加：

```java
// 在 mapEntityToBestiaryId() 中添加
case "minecraft:enderman" → "bestiary_enderman"
```

### 6.4 添加物品/方块映射

```java
// 在 mapRegistryToBestiaryId() 中添加
case "minecraft:ender_pearl" → "bestiary_ender_pearl"
```
