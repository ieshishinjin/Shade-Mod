# Minecraft Galgame 剧情系统 — 用户文档

> 适用于 Shade Mod v0.0.5+

---

## 📖 目录

1. [快速开始](#1-快速开始)
2. [快捷键](#2-快捷键)
3. [剧情菜单](#3-剧情菜单)
4. [对话框 GUI](#4-对话框-gui)
5. [画廊系统](#5-画廊系统)
6. [触发器系统](#6-触发器系统)
7. [AI 剧情生成](#7-ai-剧情生成)
8. [Quest 系统](#8-quest-系统)
9. [命令大全](#9-命令大全)
10. [脚本格式](#10-脚本格式)

---

## 1. 快速开始

### 1.1 开始一个剧情

```
/story start chapter1_wake_up
```

按 **R** 打开剧情菜单，选择剧本后按 Enter 开始。

### 1.2 剧情交互

| 操作 | 效果 |
|------|------|
| 鼠标点击 | 加速打字 / 推进对话 |
| 空格 / Enter | 同上 |
| 数字键 1-9 | 快速选择选项 |
| ESC | 关闭对话框（剧情保留） |
| R | 打开剧情菜单 / 恢复对话框 |

---

## 2. 快捷键

| 快捷键 | 功能 | 可自定义 |
|--------|------|----------|
| **R** | 打开剧情菜单 | ✅ 设置→按键绑定→Shade 模组 |
| **U** | AI 剧情控制面板（详见 [AiGui.md](AiGui.md)） | ✅ 设置→按键绑定→Shade 模组 |
| **L** | 打开任务日志（详见 §9） | ✅ 设置→按键绑定→Shade 模组 |
| **G** | 打开画廊（剧情菜单内） | — |
| **↑↓** | 选择剧本（剧情菜单内） | — |
| **Enter** | 确认选择（剧情菜单内） | — |
| **Shift+右键** | 与 NPC / 命名生物 AI 对话（详见 §10） | — |
| **1~9** | 快速选择对话选项 | — |
| **点击** | 跳过打字机效果 / 继续对话 / 关闭CG | — |

按键绑定可在 Minecraft 设置 → 按键绑定 → "Shade 模组" 分类中修改。

---

## 3. 剧情菜单

按 **R** 打开剧情选择菜单，双栏布局：

```
┌──────────────────────┬──────────────────────────┐
│  ○ 第一章：苏醒      │  第一章：苏醒              │ ← 选中后显示详情
│  ○ 第二章：出发      │  ✦ 已完成                 │
│  ▶ 第三章：战斗      │  你在晨曦营地中醒来，      │ ← 剧情描述
│  √ 第四章：终章      │  失去了所有记忆...         │
│                      │                          │
├───────┬──────┬──────┤ ├─────────────────────────┤
│  画廊  │ AI  │ 关闭  │ │  ▶ 开始 / ▶ 继续        │
└───────┴──────┴──────┘ └─────────────────────────┘
```

- **左栏**：所有剧本列表（○未开始 ▶进行中 √已完成）
- **右栏**：点击左栏剧本后显示详情 + 描述 + 开始按钮
- **底部按钮**：画廊 / AI生成 / 关闭
- 列表垂直居中，随屏幕高度自适应

---

## 4. 对话框 GUI

Galgame 风格对话框，带打字机效果和头像：

```
┌────────────────────────────────────────────┐
│                                            │
│    ┌────────┐  §e§l阿卡娅                    │
│    │ 80×80  │                              │
│    │  头像   │  「你终于醒了。感觉怎么样？」   │ ← 对话白色
│    │         │  一个温和的声音从旁边传来...    │ ← 旁白灰色
│    └────────┘                              │
│                                            │
│    ┌──────────────────────────┐            │
│    │ [1] 「这是哪里？」        │            │ ← 选项按钮
│    ├──────────────────────────┤            │
│    │ [2] （保持沉默）          │            │
│    └──────────────────────────┘            │
│                          §7[ 点击继续 ]      │
└────────────────────────────────────────────┘
```

### 4.1 特性

- **打字机效果**：文字逐字显示，点击加速
- **头像显示**：左侧 80×80 像素 NPC 头像
- **样式区分**：`「」` 内对话为白色，旁白/环境描写为灰色
- **选项按钮**：悬停高亮，数字键快速选择
- **头像对齐**：头像下移 6px 与文字顶部视觉对齐

### 4.2 剧情结束

当剧情到达 END 节点时，自动淡入结束语，点击后淡出关闭：

```
打开：黑色遮罩 100% → 0%（8 ticks 淡入）
关闭：黑色遮罩 0% → 100%（8 ticks 淡出）
```

---

## 5. 任务日志界面

按 **L** 打开任务日志，查看所有活跃任务和已完成任务：

```
┌────────────────────────────────────────────┐
│              ✦ 任务日志                     │
│  ────────────────                          │
│  — 进行中 —                                │
│  ▶ 侦察西边哨站          §6✦ 侦察西边哨站   │
│  ○ 收集材料              §7前往营地以西...   │
│                          ○ 到达西边哨站 [0/1]│
│                          ○ 击杀掠夺者 [2/3] │
│  ──────────────                            │
│  已完成任务: 1             ↑↓ 选择 ESC 关闭 │
└────────────────────────────────────────────┘
```

### 5.1 操作

| 操作 | 方式 |
|------|------|
| 打开 | 按 **L** 键 |
| 选择任务 | 按 **↑ / ↓** |
| 关闭 | 按 **ESC** |

### 5.2 显示内容

- **左侧**：所有进行中任务列表，▶ 表示当前追踪
- **右侧**：选中任务的详情，含描述和 Objective 进度
- **底部**：已完成任务计数

---

## 6. 画廊系统

### 6.1 画廊 GUI（v0.0.6 新增）

按剧情菜单中的 **「画廊」** 按钮打开图形浏览界面，或使用命令操作。

**GUI 操作：**

| 操作 | 说明 |
|------|------|
| **[C] CG / [E] 结局** | 切换分类标签 |
| **鼠标滚轮** | 上下滚动条目 |
| **ESC** | 关闭 |

- 已解锁：✔ + 标题（绿色高亮）
- 未解锁：? + 灰色
- 全收集：右侧显示 `✦ 全收集!`

### 6.2 命令

```
/story gallery              — 画廊首页（收集进度）
/story gallery list         — 列出全部条目
/story gallery cg           — 仅显示 CG
/story gallery endings      — 仅显示结局
/story gallery view <id>    — 查看条目详情
```

### 6.3 自动解锁

- 剧情脚本完成时自动解锁对应的画廊条目
- 目前内置 4 个示例条目（2 CG + 2 结局）+ 第二章新增条目（2 CG + 2 结局）
- 画廊条目在脚本 JSON 中配置

### 6.4 多分支结局

根据玩家在剧情中的选择（Flag），解锁不同的结局条目：

```json
{
    "condition": "accepted_first_quest=true"   // 仅当该 Flag 为 true 时解锁
}
```

- 未达到条件的结局条目在画廊中显示为 `???`
- 每次完成剧情时系统自动检查所有结局条件

---

## 7. CG 展示系统

剧情脚本中使用 `SHOW_CG` 事件类型展示全屏 CG 插画：

```json
{
    "type": "EVENT",
    "event": {
        "type": "SHOW_CG",
        "value": "shade:textures/gui/cg/wake_up.png",
        "params": {
            "title": "苏醒",
            "fadeIn": 20
        }
    },
    "next": "next_node"
}
```

### 7.1 特性

- **全屏展示**：CG 图片铺满整个屏幕
- **淡入淡出**：打开时从黑屏淡入，点击后淡出
- **标题文字**：CG 完全显示后显示标题
- **自动推进**：CG 关闭后自动推进到下一剧情节点

### 7.2 操作

| 操作 | 效果 |
|------|------|
| **点击** | 关闭 CG（已显示完成后） |
| **ESC** | 关闭 CG |

---

## 8. 世界事件系统（v0.0.6 实现）

游戏世界中会随机触发事件，AI 自动生成引导剧情。每位玩家独立冷却（v0.0.6 修复）：

| 事件 | 效果 | 间隔 |
|------|------|------|
| **流星雨** 🌠 | 火焰+烟雾粒子高空爆炸，共 4 波 | ~10 分钟/玩家 |
| **怪物攻城** ⚔ | 玩家周围 15-30 格生成 4-7 只僵尸/骷髅/蜘蛛等，全灭后刷援军 | ~10 分钟/玩家 |
| **贸易车队** 🎪 | 生成流浪商人 + 2 只交易羊驼，高兴村民粒子标记 | ~10 分钟/玩家 |

- 触发时自动检测 AI 是否已配置
- 如 AI 已启用，自动生成相关的引导剧情
- 事件持续 300~600 tick，过期自动清理（v0.0.6 修复）

---

## 9. Quest 系统

剧情脚本中的 QUEST_START 节点会自动创建 Quest。

### 9.1 进度追踪

- 击杀怪物：自动追踪 KILL_MOB / KILL_BOSS 进度（通过原版统计数据）
- 清空营地：自动追踪 OCCUPY_CAMP 进度（通过 Camp 适配器）
- 到达位置：自动追踪 REACH_LOCATION 进度
- 收集物品：自动追踪 COLLECT_ITEM 进度（通过 InventoryTracker）
- 合成物品：自动追踪 CRAFT_ITEM 进度（通过 CraftingMixin + 物品适配器）
- 与村民交易：自动追踪 TRADE_VILLAGER 进度（通过原版交易统计）
- 收集进度适配器在 v0.0.6 新增，支持轮询模式

### 9.2 Quest 放弃（v0.0.6 新增）

```
/story quest list            — 查看当前活跃 Quest
/story quest abort <questId> — 放弃指定 Quest（触发 onQuestFail 节点）
```

- 放弃后触发剧情脚本中定义的 `on_quest_fail` 节点跳转
- 失败后的 Quest 从日志中移除

### 9.3 Quest 超时（v0.0.6 新增）

剧情脚本 JSON 中可为 Quest 配置超时时间（游戏 tick），超时自动失败：

```json
"quest": {
    "quest_id": "timed_quest",
    "quest_name": "限时任务",
    "timeout_ticks": 24000,   // 游戏内 20 分钟后自动失败
    ...
    "on_quest_fail": "fail_node"
}
```

- `timeout_ticks = 0` 表示永不超时（默认）
- 超时后自动跳转到 `on_quest_fail` 节点

### 9.4 Quest 奖励

支持三种奖励方式：

1. 直接奖励：exp + items + flags
2. 随机奖励池：rewardPool 中按 weight 权重随机抽取 poolSize 个
3. 多选一奖励：choiceRewards 的选项列表，玩家点击选择

示例：
```
/story complete              -- 强制完成当前 Quest
/story flag set k v          -- 设置剧情 Flag
/story choose_reward <key>   -- 选择多选一奖励
```

### 9.5 任务日志

按 L 键打开任务日志界面（详见 §5）。


## 10. 触发器系统

触发器可以在满足条件时自动开始剧情，无需手动输入命令。

### 10.1 区域触发

当玩家进入指定矩形区域时自动触发：

```
/story trigger add zone <名称> <脚本> <x1> <z1> <x2> <z2>
```

### 10.2 NPC 交互触发

当玩家右键指定实体时触发：

```
/story trigger add npc <名称> <脚本> <实体ID>
```

### 10.3 物品拾取触发

当玩家拾取指定物品时触发：

```
/story trigger add item <名称> <脚本> <物品ID>
```

### 10.4 触发器管理

```
/story trigger list               -- 列出所有触发器
/story trigger remove <名称>      -- 移除触发器
```

## 11. NPC AI 对话（v0.0.6）

潜行+右键点击村民或已命名的生物，触发 AI 即兴对话：

- 对话内容由当前配置的 AI 引擎实时生成
- 10 秒冷却保护，防止频繁触发
- 自动创建临时对话场景，对话结束后自动关闭
- 需要先启用 AI（`/story ai enable`）
- Shift+右键时不再同时触发系统触发器（v0.0.6 修复）

## 12. AI 剧情生成

按 U 打开 AI 控制面板进行配置。支持 DeepSeek、Ollama、Claude、自定义 OpenAI 兼容 API。

## 13. 命令大全

### 13.1 剧情命令

| 命令 | 说明 | 版本 |
|------|------|------|
| /story start <脚本> | 开始指定剧情 | v0.0.4 |
| /story status | 查看状态 | v0.0.4 |
| /story advance | 推进对话 | v0.0.4 |
| /story choose <索引> | 选择选项 | v0.0.4 |
| /story complete | 强制完成 Quest | v0.0.4 |
| /story flag set <k> <v> | 设置 Flag | v0.0.4 |
| /story choose_reward <key> | 选择多选一奖励 | v0.0.4 |
| /story reset | 重置进度 | v0.0.4 |
| /story reload | 热加载脚本 | v0.0.4 |
| /story quest list | 查看活跃 Quest 列表 | **v0.0.6** |
| /story quest abort <id> | 放弃指定 Quest | **v0.0.6** |

### 13.2 画廊命令

| 命令 | 说明 | 版本 |
|------|------|------|
| /story gallery | 画廊首页 | v0.0.5 |
| /story gallery list | 全部条目 | v0.0.5 |
| /story gallery cg | CG 列表 | v0.0.5 |
| /story gallery endings | 结局列表 | v0.0.5 |
| /story gallery view <id> | 查看条目 | v0.0.5 |

### 13.3 AI 命令

| 命令 | 说明 | 版本 |
|------|------|------|
| /story ai status | 查看配置 | v0.0.4 |
| /story ai enable/disable | 启用/禁用 | v0.0.4 |
| /story ai provider <引擎> | 设置引擎（deepseek/ollama/claude/custom） | **v0.0.6** 新增 claude/custom |
| /story ai key <key> | 设置密钥 | v0.0.4 |
| /story ai model <模型> | 设置模型 | v0.0.4 |
| /story ai endpoint <url> | 设置 API 端点（自定义模式） | v0.0.4 |
| /story ai generate [提示] | 生成剧情 | v0.0.4 |
| /story ai autogen | 自动生成开关 | v0.0.4 |
| /story ai recommend | 查看免费服务商 | v0.0.5 |
| /story ai recommend <id> | 选择并自动填入配置 | **v0.0.6** 修复非 DeepSeek 服务商 |

---

## 文件结构

Shade Mod v0.0.6

**服务端 ~60 文件 | 客户端 ~10 文件 | 资源 ~12 文件**

### v0.0.6 新增/修改文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `aigen/ClaudeProvider.java` | 新增 | Claude (Anthropic) API 提供者 |
| `aigen/OpenAiCompatibleProvider.java` | 新增 | 通用 OpenAI 兼容 API 提供者 |
| `aigen/StoryAiGenerator.java` | 修改 | 修复自定义 Provider 支持 |
| `aigen/AiConfig.java` | 修改 | 新增 claude/custom 字段，Gson 缓存 |
| `aigen/AiCommand.java` | 修改 | 支持 claude/custom 提供者 |
| `aigen/ResponseParser.java` | 不变 | 安全的 JSON 解析 |
| `story/StoryEngine.java` | 修改 | AI 节点安全保护（禁止命令/物品/传送） |
| `story/StoryEventHandler.java` | 修改 | NPC 对话触发器顺序修复，tick 优化 |
| `story/StoryCommand.java` | 修改 | 新增 `/story quest list/abort` |
| `story/StoryManager.java` | 修改 | 脏标记 + 异步刷新 |
| `story/QuestManager.java` | 修改 | 新增 failQuest + 超时检测 |
| `story/RuntimeQuest.java` | 修改 | 新增 startTime/timeoutTicks |
| `story/WorldEventManager.java` | 修改 | 事件效果实现 + 每玩家冷却 |
| `story/InventoryTracker.java` | 修改 | 批量通知 + 性能优化 |
| `story/adapter/InventoryAdapter.java` | 新增 | COLLECT_ITEM/CRAFT_ITEM 适配器 |
| `story/adapter/CombatAdapter.java` | 新增 | KILL_MOB/KILL_BOSS 适配器 |
| `story/adapter/VillagerAdapter.java` | 新增 | TRADE_VILLAGER 适配器 |
| `client/story/GalleryBrowserScreen.java` | 新增 | 画廊图形浏览界面 |
| `client/aigen/AiControlScreen.java` | 修改 | EditBox 输入 + 动态进度 |
| `client/aigen/AiSettingsScreen.java` | 修改 | 新增 Claude 选项卡 |
| `client/ShadeModClient.java` | 修改 | 画廊/进度网络包接收 |
| `resources/story/chapter2_forest_whispers.json` | 新增 | 第二章剧情剧本 |
| `camp/CampManager.java` | 修改 | 性能优化（反向映射、脏标记、异步保存） |
