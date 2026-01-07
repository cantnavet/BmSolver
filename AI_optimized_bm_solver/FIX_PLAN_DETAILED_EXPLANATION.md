# FixPlan 详细实现说明

本文档详细解释 fixPlan 中每个计划（Plan 1, 2, 3, 4, 5）在代码层面的详细运作和实现方式。
<br>
(你猜对了，这个也是ai干的，不过有些错的太明显的地方手动改了下)

## 目录

1. [移动阻断机制概述](#移动阻断机制概述)
2. [Plan 1：刚好慢于移动阻断](#plan-1刚好慢于移动阻断)
3. [Plan 2：刚好卡在阻断下限，调整过阻断后的速度](#plan-2刚好卡在阻断下限调整过阻断后的速度)
4. [Plan 3：刚好快于移动阻断](#plan-3刚好快于移动阻断)
5. [Plan 4：特殊修复方案（阶段1使用）](#plan-4特殊修复方案阶段1使用)
6. [Plan 5：特殊修复方案2（阶段1使用）](#plan-5特殊修复方案2阶段1使用)
7. [代码执行流程](#代码执行流程)

---

## 移动阻断机制概述

### 什么是移动阻断？

Minecraft中，当玩家的速度在很小的范围内时，会被游戏引擎重置为0。这个机制被称为"移动阻断"。

### 阻断阈值

- **地面阻断阈值**：`±0.009157508093840406` = `0.005 / 0.546`
  - 当起跳速度在这个范围内时，会被重置为0
  - 检测位置：起跳瞬间（jumpSpeed）

- **空中阻断阈值**：`±0.005494505` = `0.005 / 0.91`
  - 当空中速度在这个范围内时，会被重置为0
  - 检测位置：空中（velocity）

### 阻断检测

阻断检测在 `PhysicsCalculator.calculateDelayedJumpJumps()` 中进行：

```java
// 检测地面移动阻断（起跳瞬间）
if (jumpSpeed > -BLOCK_THRESHOLD_GROUND && 
    jumpSpeed < BLOCK_THRESHOLD_GROUND && 
    blockFixContext.inPlace == 0) {
    blockFixContext.inPlace = 1;  // 标记为地面阻断
}

// 检测空中移动阻断（空中第l个tick）
if (velocity > -(float)(BLOCK_THRESHOLD_AIR) && 
    velocity < (float)(BLOCK_THRESHOLD_AIR) && 
    blockFixContext.inPlace == 0) {
    blockFixContext.inPlace = 2 + l;  // 标记为空中第l个tick的阻断
}
```

### 为什么需要修复方案？

当计算出的向后速度落入阻断区间时，如果直接使用这个速度，会被重置为0，导致无法达到目标bm。因此需要特殊处理来绕过阻断。

---

## Plan 1：刚好慢于移动阻断

### 策略

使用刚好低于阻断阈值的速度作为起跳速度，过阻断后全力前进，获得最大速度。

### 适用场景

- 地面阻断（inPlace == 1）
- 空中阻断（inPlace == 2+l）

### 代码实现

#### 1. 调用位置

在 `BlockFixHandler.tryPlan1()` 中实现：

```java
private double tryPlan1(double targetBM, double initialSpeed, boolean finalDelayed) {
    context.fixPlan = 1;  // 设置修复方案为Plan 1
    
    // 计算阻断位置的起跳速度
    double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, finalDelayed);
    double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, finalDelayed);
    
    double jumpSpeed;
    if (context.inPlace == 1) {
        // 地面阻断：直接使用地面阻断阈值的负值
        jumpSpeed = -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
    } else {
        // 空中阻断：使用线性插值计算刚好低于空中阻断阈值的起跳速度
        jumpSpeed = 2 * ((-(float)BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
    }
    
    // 使用计算出的起跳速度进行完整计算
    context.inFix = 0;  // 重置inFix，因为Plan 1不需要在阻断位置特殊处理
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, jumpSpeed, finalDelayed);
    
    return physicsCalculator.tempV0;  // 返回最终速度
}
```

#### 2. 在 PhysicsCalculator 中的处理

在 `calculateDelayedJumpJumps()` 中，Plan 1的处理逻辑：

```java
// Plan 1和Plan 3的早期返回（地面阻断）
if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 1) {
    return velocity;  // 直接返回当前速度（刚好低于阻断阈值）
}

// Plan 1和Plan 3的早期返回（空中阻断）
if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 2 + l && i == 0) {
    return velocity;  // 直接返回当前速度（刚好低于阻断阈值）
}
```

**注意**：Plan 1在 `tryPlan1()` 中已经将 `inFix = 0`，所以这些早期返回实际上不会执行。Plan 1的策略是使用刚好低于阻断阈值的速度，然后正常计算。

### 工作原理

1. **地面阻断**：
   - 起跳速度设置为 `-0.009157508093840406`（刚好低于阻断阈值）
   - 这个速度不会被重置，可以正常使用
   - 过阻断后，速度会逐渐增加，最终获得最大速度

2. **空中阻断**：
   - 使用线性插值计算刚好低于空中阻断阈值的起跳速度
   - 公式：`jumpSpeed = 2 * ((-BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1`
   - 这个速度在起跳时不会被阻断，但需要确保在阻断位置时速度也低于阈值

---

## Plan 2：刚好卡在阻断下限，调整过阻断后的速度

### 策略

使用阻断下限作为起跳速度，然后调整过阻断后的速度来达到目标bm。

### 适用场景

- 地面阻断（inPlace == 1）
- 空中阻断（inPlace == 2+l）

### 代码实现

#### 1. 调用位置

在 `BlockFixHandler.tryPlan2()` 中实现：

```java
private double tryPlan2(double targetBM, double initialSpeed, boolean finalDelayed, double plan1JumpSpeed) {
    context.fixPlan = 2;  // 设置修复方案为Plan 2
    
    // 第一步：计算最大修复速度
    context.planSteps = 1;
    context.maxFixSpeed = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
    double fbm = physicsCalculator.tempBM;  // 使用Plan 1的起跳速度时的bm
    
    // 第二步：计算修复速度（fixSpeed = -1时的bm）
    context.fixSpeed = -1;
    context.planSteps = 2;
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
    double sbm = physicsCalculator.tempBM;  // fixSpeed = -1时的bm
    
    // 计算最优修复速度（使用线性插值）
    context.fixSpeed = (context.maxFixSpeed + 1) * ((targetBM - sbm) / (fbm - sbm)) - 1;
    context.fixSpeed = Math.min(context.fixSpeed, context.maxFixSpeed);  // 限制最大值
    
    // 第三步：使用修复速度进行完整计算
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
    
    context.planSteps = 0;  // 重置步骤
    return physicsCalculator.tempV0;  // 返回最终速度
}
```

#### 2. 在 PhysicsCalculator 中的处理

Plan 2的处理分为三个阶段：

**阶段1（planSteps == 1）：计算最大修复速度**

```java
// 这个阶段只是计算maxFixSpeed，不进行特殊处理
// maxFixSpeed = calculateDelayedJumpJumps(...)的返回值
```

**阶段2（planSteps == 2）：计算修复速度**

在阻断位置设置速度为0，然后使用fixSpeed：

```java
// Plan 2处理：在阻断位置设置速度为0（地面）
if (blockFixContext.inFix == 1 && blockFixContext.fixPlan == 2) {
    velocity = 0;  // 在阻断位置，速度被重置为0
}

// Plan 2处理：在阻断位置设置速度为0（空中）
if (blockFixContext.inFix == 2 + l && blockFixContext.fixPlan == 2 && i == 0) {
    velocity = 0;  // 在阻断位置，速度被重置为0
}

// Plan 2处理：在阻断后设置修复速度（地面）
if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && 
    blockFixContext.inFix == 1 && i == 0) {
    velocity = blockFixContext.fixSpeed;  // 过阻断后，使用修复速度
}

// Plan 2处理：在阻断后设置修复速度（空中）
if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && 
    blockFixContext.inFix == l + 2 && i == 0) {
    velocity = blockFixContext.fixSpeed;  // 过阻断后，使用修复速度
}
```

**阶段3（planSteps == 0）：正常计算**

使用计算出的fixSpeed进行完整计算。

### 工作原理

1. **第一步**：使用Plan 1的起跳速度，计算过阻断后的最大可能速度（maxFixSpeed）
2. **第二步**：
   - 在阻断位置，速度被重置为0（模拟MC的阻断机制）
   - 过阻断后，使用fixSpeed = -1计算bm（sbm）
   - 使用线性插值计算最优的fixSpeed：
     ```
     fixSpeed = (maxFixSpeed + 1) * ((targetBM - sbm) / (fbm - sbm)) - 1
     ```
   - 限制fixSpeed不超过maxFixSpeed
3. **第三步**：使用计算出的fixSpeed进行完整计算，获得最终速度

---

## Plan 3：刚好快于移动阻断

### 策略

使用刚好高于阻断阈值的速度作为起跳速度，然后调整过阻断后的速度来达到目标bm。

### 适用场景

- **支持所有阻断情况**：Plan 3对地面阻断和空中阻断都会尝试
- **条件检查**：Plan 3需要满足条件检查（expectedJumpSpeed >= jumpSpeed），确保起跳速度不超过MC物理引擎允许的最大值

### 代码实现

#### 1. 调用位置

在 `BlockFixHandler.tryPlan3()` 中实现：

```java
private double tryPlan3(double targetBM, double initialSpeed, boolean finalDelayed) {
    context.fixPlan = 3;  // 设置修复方案为Plan 3
    
    // 计算阻断位置的起跳速度
    double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, finalDelayed);
    double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, finalDelayed);
    
    double jumpSpeed;
    if (context.inPlace == 1) {
        // 地面阻断：直接使用地面阻断阈值的正值
        jumpSpeed = MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
    } else {
        // 空中阻断：使用线性插值计算刚好高于空中阻断阈值的起跳速度
        jumpSpeed = 2 * (((float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
    }
    
    // 借用Plan 2的功能
    context.fixPlan = 2;  // 切换到Plan 2的处理逻辑
    context.planSteps = 1;
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, jumpSpeed, finalDelayed);
    fbm = physicsCalculator.tempBM;
    
    context.fixSpeed = -1;
    context.planSteps = 2;
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, jumpSpeed, finalDelayed);
    sbm = physicsCalculator.tempBM;
    
    context.fixSpeed = (context.maxFixSpeed + 1) * ((targetBM - sbm) / (fbm - sbm)) - 1;
    physicsCalculator.calculateDelayedJumpJumps(initialSpeed, jumpSpeed, finalDelayed);
    
    // 检查是否满足条件
    double expectedJumpSpeed = initialSpeed * FRICTION_GROUND + 
                              JUMP_BOOST + 
                              GROUND_MOVEMENT;
    
    if (expectedJumpSpeed >= jumpSpeed) {
        return physicsCalculator.tempV0;  // 满足条件，返回最终速度
    }
    return 0.0;  // 不满足条件，返回0
}
```

#### 2. 在 PhysicsCalculator 中的处理

Plan 3的处理逻辑：

```java
// Plan 3的早期返回（地面阻断）
if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && 
    blockFixContext.inFix == 1) {
    return velocity;  // 直接返回当前速度（刚好高于阻断阈值）
}

// Plan 3的早期返回（空中阻断）
if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && 
    blockFixContext.inFix == 2 + l && i == 0) {
    return velocity;  // 直接返回当前速度（刚好高于阻断阈值）
}
```

**注意**：Plan 3在 `tryPlan3()` 中会切换到Plan 2的处理逻辑，所以这些早期返回实际上不会执行。

### 工作原理

1. **计算起跳速度**：
   - **地面阻断**：使用 `0.009157508093840406`（刚好高于阻断阈值）
   - **空中阻断**：使用线性插值计算刚好高于空中阻断阈值的起跳速度
     - 公式：`jumpSpeed = 2 * ((BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1`
   - 这个速度不会被重置，可以正常使用

2. **借用Plan 2的逻辑**：
   - 切换到Plan 2的处理方式（fixPlan = 2）
   - 在阻断位置设置速度为0，然后调整过阻断后的速度
   - 使用Plan 2的三步计算流程

3. **条件检查**：
   - 检查预期的起跳速度是否大于等于计算出的起跳速度
   - 预期起跳速度 = `initialSpeed * FRICTION_GROUND + JUMP_BOOST + GROUND_MOVEMENT`
   - 这是MC物理引擎允许的最大起跳速度（从initialSpeed正常计算出的）
   - **条件含义**：`expectedJumpSpeed >= jumpSpeed`
     - 这个条件是为了防止起跳速度因为需要超过移动阻断阈值，而在插值后强行超越了MC中的最大起跳速度
     - 如果修正前的策略中为了适配助跑而调小了起跳速度（expectedJumpSpeed较小），那么Plan 3计算出的jumpSpeed（刚好高于阻断阈值）可能不会超过expectedJumpSpeed，就有机会应用这个Plan
   - 如果满足条件，返回最终速度；否则返回0（表示Plan 3不可行）

### 对所有阻断的处理

在 `BlockFixHandler.handleBlockFix()` 中：

```java
// Plan 3: 刚好快于移动阻断，调整过阻断后的速度
// 对所有阻断（地面和空中）都尝试Plan 3
double plan3V0 = tryPlan3(targetBM, initialSpeed, finalDelayed);
if (plan3V0 > maxV0) {
    maxV0 = plan3V0;
    bestPlan = 3;
}
```

**处理原则**：
1. **程序应该处理所有可能的助跑情况**：只要有可能就需要考虑，不区分地面和空中阻断
2. **条件检查的作用**：
   - 地面阻断阈值是 `±0.009157508093840406`，空中阻断阈值是 `±0.005494505`
   - 虽然阈值不同，但两者差距不会大到空中触发的概率比地面小非常多的程度
   - 条件检查（expectedJumpSpeed >= jumpSpeed）确保起跳速度不超过MC物理引擎允许的最大值
   - 当为了适配助跑而调小起跳速度时，expectedJumpSpeed可能较小，Plan 3计算出的jumpSpeed可能不会超过expectedJumpSpeed，就有机会应用Plan 3

---

## Plan 4：特殊修复方案（阶段1使用）

### 策略

在阶段1（findOptimalJumpSequence）中，当向后速度落入阻断区间时，使用修复速度（fixSpeed）来绕过阻断。

### 适用场景

- **仅在阶段1使用**：当向后速度在 `-0.0091...` 到 `0` 之间时
- 用于非delayed和delayed两种情况

### 代码实现

#### 1. 调用位置

在 `BmSolverOptimized.handleBlockFixForSequence()` 中实现：

```java
// Plan 4: 计算修复速度
blockFixContext.fixPlan = 4;
blockFixContext.fixSpeed = 0;
double bmAtZeroSpeed = physicsCalculator.calculateJumpBM(0, false);
blockFixContext.fixSpeed = 1;
double bmAtOneSpeed = physicsCalculator.calculateJumpBM(0, false);
blockFixContext.fixSpeed = (targetBM + BLOCK_THRESHOLD_GROUND - bmAtZeroSpeed) / (bmAtOneSpeed - bmAtZeroSpeed);
blockFixContext.fixSpeed = Math.min(blockFixContext.fixSpeed, MAX_FIX_SPEED);
physicsCalculator.calculateJumpBM(0, false);
```

#### 2. 在 PhysicsCalculator 中的处理

在 `calculateJumpBM()` 中：

```java
// 起跳tick
if (blockFixContext.fixPlan == 4) {
    velocity = blockFixContext.fixSpeed;  // 直接使用修复速度
} else {
    velocity = velocity * FRICTION_GROUND + JUMP_BOOST + GROUND_MOVEMENT;
}
```

### 工作原理

1. **计算修复速度**：
   - 设置 `fixSpeed = 0`，计算bm（bmAtZeroSpeed）
   - 设置 `fixSpeed = 1`，计算bm（bmAtOneSpeed）
   - 使用线性插值计算最优的fixSpeed：
     ```
     fixSpeed = (targetBM + BLOCK_THRESHOLD_GROUND - bmAtZeroSpeed) / (bmAtOneSpeed - bmAtZeroSpeed)
     ```
   - 限制fixSpeed不超过MAX_FIX_SPEED（0.32739998400211334）

2. **使用修复速度**：
   - 在起跳tick，直接使用fixSpeed作为速度
   - 这样可以绕过阻断，因为fixSpeed不在阻断区间内

---

## Plan 5：特殊修复方案2（阶段1使用）

### 策略

在阶段1中，尝试使用阻断下限（-0.0091...）作为向后速度，看是否能获得更好的结果。

### 适用场景

- **仅在阶段1使用**：当向后速度在 `-0.0091...` 到 `0` 之间时
- 用于非delayed和delayed两种情况

### 代码实现

#### 1. 调用位置

在 `BmSolverOptimized.handleBlockFixForSequence()` 中实现：

```java
// Plan 5: 尝试使用阻断下限
blockFixContext.fixPlan = 5;
double testBackwardSpeed = Math.min(-BLOCK_THRESHOLD_GROUND, backwardSpeedNonDelayed);
physicsCalculator.calculateJumpBM(testBackwardSpeed, false);
```

#### 2. 在 PhysicsCalculator 中的处理

Plan 5实际上没有特殊的处理逻辑，只是使用阻断下限作为向后速度进行正常计算。

### 工作原理

1. **计算测试速度**：
   - 使用阻断下限（-0.009157508093840406）或实际的向后速度（取较小值）
   - 这个速度刚好在阻断阈值之外，不会被重置

2. **正常计算**：
   - 使用这个速度进行正常的跳跃计算
   - 获得最终的起跳速度（tempV0）

3. **比较结果**：
   - 与Plan 4的结果比较
   - 选择最终速度更大的方案

---

## 代码执行流程

### 阶段1：findOptimalJumpSequence

当向后速度落入阻断区间时：

```
1. 检测到阻断（backwardSpeed在-0.0091...到0之间）
2. 执行Plan 4：
   - 计算修复速度（fixSpeed）
   - 使用fixSpeed进行跳跃计算
   - 获得maxJumpSpeed
3. 执行Plan 5：
   - 使用阻断下限作为向后速度
   - 进行跳跃计算
   - 获得tempV0
4. 比较Plan 4和Plan 5的结果：
   - 如果Plan 5的tempV0 > Plan 4的maxJumpSpeed
     → 使用Plan 5（blockFixPlan = 2）
   - 否则
     → 使用Plan 4（blockFixPlan = 1）
```

### 阶段2和阶段3：优化过程中的阻断处理

当在优化过程中检测到阻断时：

```
1. 检测到阻断（inPlace > 0）
2. 依次尝试三种方案：
   a. Plan 1：刚好慢于阻断
      - 计算起跳速度（刚好低于阻断阈值）
      - 进行完整计算
      - 获得plan1V0
   
   b. Plan 2：刚好卡在阻断下限，调整过阻断后的速度
      - 使用Plan 1的起跳速度
      - 第一步：计算maxFixSpeed
      - 第二步：计算最优fixSpeed
      - 第三步：使用fixSpeed进行完整计算
      - 获得plan2V0
   
   c. Plan 3：刚好快于阻断（仅在inPlace==1时尝试）
      - 检查是否为地面阻断（inPlace == 1）
      - 如果是，计算起跳速度（刚好高于阻断阈值）
      - 借用Plan 2的逻辑调整过阻断后的速度
      - 检查条件（expectedJumpSpeed >= jumpSpeed）
      - 如果满足条件，获得plan3V0；否则返回0
3. 比较三种方案的结果：
   - 选择最终速度最大的方案
   - 返回最优方案的最终速度
```

### 关键代码位置

1. **阻断检测**：
   - `PhysicsCalculator.calculateDelayedJumpJumps()` (177-182行，241-246行)

2. **Plan 1处理**：
   - `BlockFixHandler.tryPlan1()` (103-124行)
   - `PhysicsCalculator.calculateDelayedJumpJumps()` (190-192行，254-256行)

3. **Plan 2处理**：
   - `BlockFixHandler.tryPlan2()` (143-169行)
   - `PhysicsCalculator.calculateDelayedJumpJumps()` (185-187行，217-219行，232-235行，249-251行，267-270行)

4. **Plan 3处理**：
   - `BlockFixHandler.tryPlan3()` (174-213行)
   - `PhysicsCalculator.calculateDelayedJumpJumps()` (193-195行，257-260行)

5. **Plan 4处理**：
   - `BmSolverOptimized.handleBlockFixForSequence()` (126-136行，167-176行)
   - `PhysicsCalculator.calculateJumpBM()` (87-89行)

6. **Plan 5处理**：
   - `BmSolverOptimized.handleBlockFixForSequence()` (138-142行，178-181行)
   - `PhysicsCalculator.calculateJumpBM()` (正常计算，无特殊处理)

---

## 总结

### 各Plan的特点对比

| Plan | 适用阶段 | 适用场景 | 策略 | 复杂度 |
|------|---------|---------|------|--------|
| Plan 1 | 阶段2/3 | 地面/空中阻断 | 刚好慢于阻断，过阻断后全力前进 | 简单 |
| Plan 2 | 阶段2/3 | 地面/空中阻断 | 刚好卡在阻断下限，调整过阻断后的速度 | 复杂 |
| Plan 3 | 阶段2/3 | 地面/空中阻断 | 刚好快于阻断，调整过阻断后的速度 | 复杂 |
| Plan 4 | 阶段1 | 向后速度在阻断区间 | 使用修复速度绕过阻断 | 简单 |
| Plan 5 | 阶段1 | 向后速度在阻断区间 | 使用阻断下限 | 简单 |

### 选择策略

- **阶段1**：比较Plan 4和Plan 5，选择更优的
- **阶段2/3**：依次尝试Plan 1、Plan 2、Plan 3，选择最终速度最大的

### 关键理解点

1. **阻断机制**：MC会将小范围内的速度重置为0
2. **修复策略**：使用刚好在阻断阈值之外的速度，或调整过阻断后的速度
3. **Plan 2的复杂性**：需要三步计算，精确控制过阻断后的速度
4. **Plan 3的条件检查**：
   - 需要满足条件检查（expectedJumpSpeed >= jumpSpeed）
   - **条件检查的含义**：防止起跳速度超过MC物理引擎允许的最大值
   - **适用性**：当为了适配助跑而调小起跳速度时，expectedJumpSpeed可能较小，Plan 3计算出的jumpSpeed可能不会超过expectedJumpSpeed，就有机会应用Plan 3
5. **Plan 4/5的特殊性**：仅在阶段1使用，处理向后速度的阻断问题





