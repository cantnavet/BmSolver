# BmSolver AI优化版本

(试着找ai帮忙优化了下可读性，貌似真有点效果，试了几个测试样例都没毛病)

## 优化目标

将原始的 `BmSolverAngles.java`（1481行）重构为更易读、易维护的代码，同时**完全保持计算逻辑的一致性**。

## 优化策略

### 1. 模块化设计

将原始的单体类拆分为多个职责清晰的类：

- **MinecraftPhysicsConstants**: 所有物理常数定义
- **AngleConfig**: 角度配置和参数计算
- **BlockFixContext**: 移动阻断处理的状态管理
- **PhysicsCalculator**: 核心物理计算（速度更新、跳跃计算）
- **BlockFixHandler**: 移动阻断处理逻辑
- **BmSolverOptimized**: 主求解器（整合所有模块）

### 2. 命名优化

- `ti` → `airtimeSequence` (滞空时间序列)
- `bm` → `targetBM` / `buildUpLength` (助跑长度)
- `s0` → `initialBackwardSpeed` (初始向后速度)
- `v0` → `velocity` (速度)
- `inPlace` → `blockPosition` (阻断位置)
- `fixPlan` → `fixPlan` (修复方案，保持原意)

### 3. 常量提取

所有魔法数字提取为常量：
- `0.54600006` → `FRICTION_GROUND`
- `0.91` → `FRICTION_AIR`
- `0.009157508093840406` → `BLOCK_THRESHOLD_GROUND`
- `0.005494505` → `BLOCK_THRESHOLD_AIR`

### 4. 代码结构

原始代码使用大量全局静态变量，优化版本使用：
- 实例变量（更好的封装）
- 上下文对象（状态管理）
- 方法参数（减少副作用）

## 当前进度

### ✅ 已完成

1. **常量类** (`MinecraftPhysicsConstants.java`)
   - 所有物理常数定义
   - 移动阻断阈值
   - 特殊标记值

2. **角度配置类** (`AngleConfig.java`)
   - 角度类型设置
   - RunEqualv0计算
   - AWRun计算

3. **物理计算核心类** (`PhysicsCalculator.java`)
   - `calculateJumpBM()` - 对应原 `jump1()`
   - `calculateDelayedJumpJumps()` - 对应原 `delayedJumpJumps()`
   - `calculateFinalJump()` - 对应原 `finaljump()`

4. **移动阻断上下文** (`BlockFixContext.java`)
   - 阻断检测状态
   - 修复方案状态
   - 修复速度

5. **移动阻断处理类** (`BlockFixHandler.java`)
   - Plan 1/2/3 处理逻辑框架

6. **主求解器框架** (`BmSolverOptimized.java`)
   - 基本结构
   - 方法框架

### 🚧 待完成

由于原代码非常复杂（1481行），以下部分需要逐步完善：

1. **主求解器的完整实现**
   - `findOptimalJumpSequence()` - 需要完整实现原144-339行逻辑
   - `handleBlockFixForSequence()` - 需要完整实现原150-218行逻辑
   - `handleRunJumpTechniques()` - 需要完整实现原226-337行逻辑
   - `optimizeWithLoop()` - 需要完整实现原341-564行逻辑
   - `calculateFinalResult()` - 需要完整实现原457-562行逻辑

2. **辅助函数**
   - `bmfind()` - 查找达到目标bm所需的最小向后速度
   - `delayedJumps()` - 向前跳优化（包含阻断处理）
   - `delayedDelayJumps()` - 向后跳优化（包含阻断处理）
   - `backSpeedToFront()` - 向后速度转向前bm
   - `backToFrontUnit()` - 向后转向前单位计算
   - `endMStart()` - 结束到开始的计算
   - `awRunJump()` - 跑跳计算

3. **测试验证**
   - 创建测试类验证输出一致性
   - 对比原版和优化版的输出

## 关键注意事项

### ⚠️ 必须保持完全一致的部分

1. **所有浮点数计算**
   - 必须使用相同的float转换
   - 必须保持相同的精度
   - 例如：`(float) 0.54600006` 不能改为 `0.54600006f`

2. **所有条件判断**
   - 必须保持完全相同的逻辑
   - 包括所有边界条件

3. **所有状态管理**
   - `inPlace`, `inFix`, `fixPlan`, `planSteps` 的使用必须完全一致
   - `dne` (delayedNotEnough) 的设置时机必须一致

4. **所有特殊处理**
   - 移动阻断的三种Plan必须完全一致
   - Loop优化的收敛条件必须一致
   - 跑跳技术的三种类型必须完全一致

## 使用说明

### 编译

```bash
javac optimized_bm_solver/*.java
```

### 运行

```bash
java optimized_bm_solver.BmSolverOptimized
```

### 测试验证

运行测试类对比原版和优化版的输出：

```bash
java optimized_bm_solver.BmSolverTest
```

## 下一步计划

1. 逐步完善主求解器的各个方法
2. 实现所有辅助函数
3. 创建完整的测试套件
4. 验证输出完全一致后，进行进一步优化

## 注意事项

⚠️ **重要**: 在完成所有实现并验证输出完全一致之前，不要修改任何计算逻辑。所有优化仅限于：
- 代码结构
- 命名
- 注释
- 模块划分

计算逻辑必须与原版**完全一致**。

