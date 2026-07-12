# Minecraft Galgame 剧情系统 — 用户文档

> 适用于 Shade Mod v0.0.3+

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
| **G** | 打开画廊（剧情菜单内） | — |
| **↑↓** | 选择剧本（剧情菜单内） | — |
| **Enter** | 确认选择（剧情菜单内） | — |

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

## 5. 画廊系统

### 5.1 命令

```
/story gallery              — 画廊首页（收集进度）
/story gallery list         — 列出全部条目
/story gallery cg           — 仅显示 CG
/story gallery endings      — 仅显示结局
/story gallery view <id>    — 查看条目详情
```

### 5.2 自动解锁

- 剧情脚本完成时自动解锁对应的画廊条目
- 目前内置 4 个示例条目（2 CG + 2 结局）
- 画廊条目在脚本 JSON 中配置

---

## 6. 触发器系统

触发器可以在满足条件时自动开始剧情，无需手动输入命令。

### 6.1 区域触发

当玩家进入指定矩形区域时自动触发：

```
/story trigger add zone <名称> <脚本> <x1> <z1> <x2> <z2>
```

示例：
```
/story trigger add zone enter_village chapter2 100 100 -100 -100
```

### 6.2 NPC 交互触发

当玩家右键指定实体时触发：

```
/story trigger add npc <名称> <脚本> <实体ID>
```

示例：
```
/story trigger add npc talk_villager chapter2 minecraft:villager
```

### 6.3 物品拾取触发

当玩家拾取指定物品时触发：

```
/story trigger add item <名称> <脚本> <物品ID>
```

示例：
```
/story trigger add item get_book chapter2 minecraft:book
```

系统通过背包快照对比检测新物品。

### 6.4 触发器管理

```
/story trigger list               — 列出所有触发器
/story trigger remove <名称>      — 移除触发器
```

---

## 7. AI 剧情生成

AI 生成器可根据游戏世界状态动态生成剧情内容。

### 7.1 配置

支持两种 AI 后端：

#### DeepSeek（推荐，需 API Key）

```
/story ai provider deepseek
/story ai key sk-你的API密钥
/story ai model deepseek-chat
/story ai enable
```

#### Ollama（本地运行，免费）

```
/story ai provider ollama
/story ai model llama3
/story ai endpoint http://localhost:11434/api/chat
/story ai enable
```

### 7.2 免费服务商推荐

`/story ai recommend` 查看所有推荐服务商：

| 服务商 | 费用 | 推荐模型 | 一键填入 |
|--------|------|----------|----------|
| **智谱 AI** | ✅ 完全免费 | `glm-4-flash` | `/story ai recommend zhipu` |
| **讯飞星火** | ✅ 完全免费 | `lite` | `/story ai recommend xunfei` |
| **DeepSeek** | 💰 新用户赠额 | `deepseek-chat` | `/story ai recommend deepseek` |
| **Mistral AI** | 💰 免费额度 | `mistral-tiny` | `/story ai recommend mistral` |
| **Groq** | 💰 免费额度 | `llama3-70b-8192` | `/story ai recommend groq` |
| **Hugging Face** | 💰 免费额度 | `Qwen2.5-72B` | `/story ai recommend huggingface` |

选择服务商后自动填入 API 地址和模型名：
```
/story ai recommend zhipu          — 自动填入配置
/story ai recommend zhipu open     — 打开官网注册链接
```

### 7.3 使用 AI 生成

#### 方法 1：通过 GUI

按 **R** → 剧情菜单 → 点击 **AI** 按钮 → 输入提示词 → 生成

#### 方法 2：通过命令

```
/story ai generate "生成一段在营地的日常对话"
```

#### 方法 3：脚本中的 AI_GENERATE 节点

在剧情脚本中使用 `AI_GENERATE` 类型节点，遇到时自动触发 AI 生成。

### 7.4 AI 配置命令

```
/story ai status                   — 查看当前配置
/story ai enable/disable           — 启用/禁用
/story ai provider deepseek|ollama — 切换引擎
/story ai key <key>                — 设置 API 密钥
/story ai model <name>             — 设置模型
/story ai endpoint <url>           — 设置 API 端点
/story ai temperature <0.0~2.0>    — 设置温度
/story ai maxtokens <num>          — 设置最大长度
/story ai test                     — 测试连接
/story ai autogen                  — 切换自动生成
```

### 7.5 AI 生成流程

```
玩家输入提示词
    ↓
收集游戏世界状态（所有适配器）
    ↓
构建 Prompt（含世界观、进度、上下文）
    ↓
调用 AI API（DeepSeek / Ollama）
    ↓
解析 JSON 返回 → 校验类型白名单 → ID 唯一性检查
    ↓
注入节点到当前剧情脚本
    ↓
展示给玩家
```

---

## 8. Quest 系统

剧情脚本中的 `QUEST_START` 节点会自动创建 Quest。

### 8.1 进度追踪

- **击杀怪物**：自动追踪 `KILL_MOB` 目标进度
- **清空营地**：自动追踪 `OCCUPY_CAMP` 目标进度（需 Camp 系统）
- **到达位置**、**收集物品** 等可通过适配器扩展

### 8.2 Quest 命令

```
/story complete                    — 强制完成当前所有活跃 Quest
/story flag set <key> <value>     — 设置剧情 Flag
```

---

## 9. 命令大全

### 9.1 剧情命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/story start <脚本>` | 开始指定剧情 | 所有人 |
| `/story status` | 查看当前剧情状态 | 所有人 |
| `/story list` | 列出所有可用脚本 | 所有人 |
| `/story advance` | 推进对话 | 所有人 |
| `/story choose <索引>` | 选择选项 | 所有人 |
| `/story complete` | 强制完成 Quest | 所有人 |
| `/story flag set <key> <value>` | 设置 Flag | 所有人 |
| `/story reset` | 重置所有进度 | 所有人 |
| `/story reload` | 热加载脚本 | OP |

### 9.2 画廊命令

| 命令 | 说明 |
|------|------|
| `/story gallery` | 画廊首页 |
| `/story gallery list` | 全部条目 |
| `/story gallery cg` | CG 列表 |
| `/story gallery endings` | 结局列表 |
| `/story gallery view <id>` | 查看条目 |

### 9.3 触发器命令

| 命令 | 说明 |
|------|------|
| `/story trigger list` | 列出触发器 |
| `/story trigger add zone <name> <script> <x1> <z1> <x2> <z2>` | 区域触发 |
| `/story trigger add npc <name> <script> <entity>` | NPC 触发 |
| `/story trigger add item <name> <script> <item>` | 物品触发 |
| `/story trigger remove <name>` | 移除触发器 |

### 9.4 AI 命令

| 命令 | 说明 |
|------|------|
| `/story ai status` | 查看配置 |
| `/story ai enable` / `disable` | 启用/禁用 |
| `/story ai provider <引擎>` | 设置引擎 |
| `/story ai key <key>` | 设置密钥 |
| `/story ai model <模型>` | 设置模型 |
| `/story ai endpoint <url>` | 设置端点 |
| `/story ai temperature <值>` | 设置温度 |
| `/story ai maxtokens <数>` | 设置最大长度 |
| `/story ai test` | 测试连接 |
| `/story ai generate [提示]` | 生成剧情 |
| `/story ai autogen` | 自动生成开关 |
| `/story ai recommend` | 查看免费服务商 |
| `/story ai recommend <id>` | 选择并填入配置 |
| `/story ai recommend <id> open` | 打开注册链接 |

---

## 10. 脚本格式

### 10.1 基本结构

```json
{
    "id": "chapter1_wake_up",
    "title": "第一章：苏醒",
    "description": "你在晨曦营地中醒来...",
    "start_node": "start",
    "nodes": { ... }
}
```

### 10.2 节点类型

| 类型 | 用途 |
|------|------|
| `DIALOG` | 对话文本，点击继续 |
| `CHOICE` | 选项分支 |
| `QUEST_START` | 发布 Quest |
| `QUEST_UPDATE` | 更新 Quest 进度 |
| `QUEST_COMPLETE` | 完成 Quest |
| `CONDITION` | 条件判断跳转 |
| `EVENT` | 游戏事件（传送、给物品等） |
| `END` | 章节结束 |
| `AI_GENERATE` | AI 动态生成（预留） |

### 10.3 示例节点

```json
{
    "id": "npc_intro",
    "type": "DIALOG",
    "speaker": "阿卡娅",
    "portrait": "shade:textures/gui/portrait/avatar11.png",
    "text": "「这里是晨曦营地。」\n她微笑着说道。",
    "next": "world_explain"
}
```

```json
{
    "id": "choice_1",
    "type": "CHOICE",
    "speaker": "系统",
    "text": "你要怎么做？",
    "options": [
        {"label": "答应", "next": "accept", "flags": {"accepted": true}},
        {"label": "拒绝", "next": "decline"}
    ]
}
```

### 10.4 完整脚本参考

见 `assets/shade/story/chapter1_wake_up.json`

---

## 文件结构

```
Shade Mod v0.0.3
├── 服务端 33 文件
├── 客户端 4 文件
└── 资源文件 4 文件
    ├── assets/shade/lang/           — 国际化
    ├── assets/shade/story/          — 剧情脚本
    └── assets/shade/textures/gui/portrait/  — 头像
```
