# 使用说明

## 编译

代码已经编译成功，所有 `.class` 文件已生成。

如果需要重新编译，使用：

```bash
javac -encoding UTF-8 optimized_bm_solver/*.java
```

## 运行

### 运行优化版本

```bash
java optimized_bm_solver.BmSolverOptimized
```

### 运行测试

```bash
java optimized_bm_solver.BmSolverTest
```

## 代码结构

```
optimized_bm_solver/
├── MinecraftPhysicsConstants.java  # 物理常数定义
├── AngleConfig.java                # 角度配置
├── BlockFixContext.java            # 移动阻断上下文
├── PhysicsCalculator.java          # 核心物理计算
├── BlockFixHandler.java            # 移动阻断处理
├── JumpOptimizer.java              # 跳跃优化
├── RunJumpHandler.java             # 跑跳技术处理
├── SolverState.java                # 求解器状态
├── BmSolverOptimized.java          # 主求解器
└── BmSolverTest.java               # 测试类
```

## 主要改进

### 1. 模块化
- 原版：1个类，1481行
- 优化版：10个类，职责清晰

### 2. 命名优化
- 所有变量和方法使用有意义的名称
- 常量统一管理

### 3. 代码结构
- 使用实例变量替代全局静态变量
- 使用上下文对象管理状态
- 方法参数明确

## 验证输出一致性

### 步骤1：运行原版
```bash
javac BmSolverAngles.java
java BmSolverAngles > original_output.txt
```

### 步骤2：运行优化版
```bash
java optimized_bm_solver.BmSolverOptimized > optimized_output.txt
```

### 步骤3：对比输出
对比两个文件的输出，确保：
- `distance` 值完全一致
- `pb` 值完全一致
- `jpb` 值完全一致
- 其他关键数值一致

## 注意事项

⚠️ **重要**：在验证输出完全一致之前，不要修改任何计算逻辑。

所有优化仅限于：
- 代码结构
- 命名
- 注释
- 模块划分

计算逻辑必须与原版**完全一致**。

