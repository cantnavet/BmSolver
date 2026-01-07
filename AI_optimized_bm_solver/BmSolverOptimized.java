import java.util.Arrays;

/**
 * 优化后的BM求解器
 * 
 * 这是Minecraft直线跳跃求解器的主类，负责协调所有模块完成跳跃计算。
 * 
 * 求解流程分为三个阶段：
 * 1. 阶段1（findOptimalJumpSequence）：确定最优连跳次数
 *    - 从1次连跳开始，逐步增加连跳次数
 *    - 对每个连跳次数，计算达到目标bm所需的最小向后速度
 *    - 如果向后速度落入移动阻断区间，使用Plan 4和Plan 5处理
 *    - 尝试跑跳技术（起跳前跑1tick）看是否能获得更优结果
 *    - 当向后速度<=0时，说明无法达到目标，退出循环
 * 
 * 2. 阶段2（optimizeWithLoop）：Loop优化
 *    - Loop是一种通过反复向后跳再向前跳来积累速度的技术
 *    - 每次loop：向后跳获得更高速度 -> 向前跳消耗速度获得bm
 *    - 循环直到收敛（速度不再变化）或达到最大次数
 *    - 在循环过程中，检查是否可以用满助跑（达到目标bm）
 * 
 * 3. 阶段3（calculateFinalResult）：最终计算和比较
 *    - 分别计算非delayed和delayed起跳的最终距离
 *    - 比较两种起跳方式，选择更优的
 *    - 与连跳满助跑的结果比较
 *    - 与跑跳技术的结果比较
 *    - 输出最终结果
 * 
 * 保持与原版完全一致的逻辑，但提高了可读性和结构
 */
public class BmSolverOptimized {
    // 配置
    private final AngleConfig angleConfig;
    private final PhysicsCalculator physicsCalculator;
    private final BlockFixContext blockFixContext;
    private final BlockFixHandler blockFixHandler;
    private final JumpOptimizer jumpOptimizer;
    private final RunJumpHandler runJumpHandler;
    private final SolverState solverState;
    
    // 求解状态
    private double targetBM;              // 目标助跑长度
    private int[] airtimeSequence;        // 滞空时间序列
    private double initialBackwardSpeed; // 初始向后速度
    
    // 结果
    public double distance;               // 最终距离
    public double pb;                     // 容错（与0.0625整数倍的差距）
    public double jpb;                    // loop第一次达成跳跃时的pb
    public int loops;                     // 非delay起跳的loop极限
    public int deloops;                   // delay起跳的loop极限
    public int jloops;                    // 达成最远距离的loop极限
    public boolean delayedG;              // 是否使用delayed起跳
    
    public BmSolverOptimized() {
        this.angleConfig = new AngleConfig();
        this.blockFixContext = new BlockFixContext();
        this.physicsCalculator = new PhysicsCalculator(angleConfig, blockFixContext);
        this.blockFixHandler = new BlockFixHandler(physicsCalculator, blockFixContext, angleConfig);
        this.jumpOptimizer = new JumpOptimizer(physicsCalculator, blockFixContext, blockFixHandler, angleConfig);
        this.runJumpHandler = new RunJumpHandler(physicsCalculator, blockFixContext, angleConfig, jumpOptimizer);
        this.solverState = new SolverState();
    }
    
    /**
     * 设置角度类型
     */
    public void setAngleType(int type) {
        angleConfig.setAngleType(type);
    }
    
    /**
     * 主求解函数（对应原single）
     * 
     * 这是求解器的入口函数，负责协调三个阶段的计算。
     * 
     * 逻辑流程：
     * 1. 初始化所有状态变量
     * 2. 调用阶段1确定最优连跳次数（同时处理移动阻断和跑跳技术）
     * 3. 调用阶段2进行Loop优化（通过反复向后跳积累速度）
     * 4. 调用阶段3计算最终结果并比较所有方案
     * 
     * @param buildUpAirtime 助跑上的滞空时间（每个助跑跳跃的tick数）
     * @param jumpAirtime 跳跃的滞空时间（最终跳跃的tick数）
     * @param buildUpLength 助跑长度（目标bm，即需要达到的向前距离）
     */
    public void solve(int buildUpAirtime, int jumpAirtime, double buildUpLength) {
        // 初始化：设置目标bm、初始向后速度为0、起始坐标为0
        targetBM = buildUpLength;
        initialBackwardSpeed = 0.0;
        physicsCalculator.setStartCoord(0.0);
        loops = -1;  // -1表示还未确定
        deloops = 0;  // delayed起跳的loop次数
        jpb = MinecraftPhysicsConstants.INVALID_PB;  // 初始化为无效值
        
        // 阶段1: 确定最优连跳次数
        // 从2次连跳开始，逐步增加，找到能够达到目标bm的最小连跳次数
        // 同时处理移动阻断和跑跳技术
        findOptimalJumpSequence(buildUpAirtime, jumpAirtime);
        
        // 阶段2: Loop优化
        // 通过反复向后跳再向前跳来积累速度，获得更高的初始速度
        optimizeWithLoop();
        
        // 阶段3: 最终计算和比较
        // 计算所有方案的最终距离，选择最优方案
        calculateFinalResult();
    }
    
    /**
     * 阶段1: 确定最优连跳次数（对应原single中的144-339行）
     * 
     * 这个阶段的逻辑是：
     * 1. 从2次连跳开始，逐步增加连跳次数
     * 2. 对每个连跳次数，创建滞空时间序列（前面的都是助跑滞空时间，最后一个是跳跃滞空时间）
     * 3. 计算达到目标bm所需的最小向后速度（使用线性插值）
     * 4. 如果向后速度落入移动阻断区间（-0.0091...到0之间），使用Plan 4和Plan 5处理
     * 5. 如果向后速度>0，说明可以达到目标，尝试跑跳技术看是否能获得更优结果
     * 6. 如果向后速度<=0，说明无法达到目标，退出循环
     * 
     * @param buildUpAirtime 助跑上的滞空时间
     * @param jumpAirtime 跳跃的滞空时间
     */
    private void findOptimalJumpSequence(int buildUpAirtime, int jumpAirtime) {
        // 从2次连跳开始，逐步增加连跳次数，直到无法达到目标或达到上限
        for (int jumpCount = 2; jumpCount < 100000; jumpCount++) {
            // 创建滞空时间序列：前面的都是助跑滞空时间，最后一个是跳跃滞空时间
            // 例如：如果jumpCount=3, buildUpAirtime=12, jumpAirtime=22
            // 则sequence = [12, 12, 22]
            int[] sequence = new int[jumpCount];
            Arrays.fill(sequence, buildUpAirtime);
            sequence[jumpCount - 1] = jumpAirtime;
            airtimeSequence = sequence;
            physicsCalculator.setAirtimeSequence(sequence);
            
            // 使用线性插值计算达到目标bm所需的最小向后速度
            // 原理：计算s0=0和s0=-1时的bm，然后线性插值找到使bm=targetBM的s0
            double backwardSpeedNonDelayed = findRequiredBackwardSpeed(targetBM, false);
            double backwardSpeedDelayed = findRequiredBackwardSpeed(targetBM, true);
            
            // 如果向后速度落入移动阻断区间（-0.0091...到0之间），需要特殊处理
            // 移动阻断是MC的一个机制：当速度在很小的范围内时，会被重置为0
            // 这里使用Plan 4和Plan 5两种方案来处理
            handleBlockFixForSequence(backwardSpeedNonDelayed, backwardSpeedDelayed);
            
            // 如果向后速度<=0，说明即使向后速度为0也无法达到目标，退出循环
            if (backwardSpeedNonDelayed <= 0) {
                break;
            }
            
            // 如果delayed的向后速度<=0，标记delayedNotEnough
            // 这会影响后续计算中是否跳过第一个连跳
            if (backwardSpeedDelayed <= 0) {
                physicsCalculator.setDelayedNotEnough(true);
            }
            
            // 尝试跑跳技术：起跳前跑1tick，看是否能获得更优结果
            // 跑跳技术有三种类型，会根据情况自动选择
            handleRunJumpTechniques(backwardSpeedNonDelayed, backwardSpeedDelayed);
        }
    }
    
    /**
     * 查找达到目标bm所需的最小向后速度（对应原bmfind）
     * 
     * 使用线性插值法计算：
     * 1. 计算s0=0时的bm（bm0）
     * 2. 计算s0=-1时的bm（bm1）
     * 3. 假设bm与s0是线性关系，使用插值公式：
     *    s0 = -(bm0 - targetBM) / (bm0 - bm1)
     * 
     * 这个公式的推导：
     * 设bm = a * s0 + b
     * 当s0=0时，bm=bm0，所以b=bm0
     * 当s0=-1时，bm=bm1，所以a=bm1-bm0
     * 因此：targetBM = (bm1-bm0) * s0 + bm0
     * 解得：s0 = -(bm0 - targetBM) / (bm0 - bm1)
     * 
     * @param targetBM 目标bm
     * @param delayed 是否使用delayed起跳
     * @return 达到目标bm所需的最小向后速度
     */
    private double findRequiredBackwardSpeed(double targetBM, boolean delayed) {
        // 计算s0=0时的bm
        double bm0 = physicsCalculator.calculateJumpBM(0, delayed);
        // 计算s0=-1时的bm
        double bm1 = physicsCalculator.calculateJumpBM(-1, delayed);
        // 使用线性插值计算所需的向后速度
        double requiredSpeed = -(bm0 - targetBM) / (bm0 - bm1);
        // 使用计算出的速度再次计算，确保tempV0等临时变量被正确设置
        physicsCalculator.calculateJumpBM(requiredSpeed, delayed);
        return requiredSpeed;
    }
    
    /**
     * 处理移动阻断（对应原150-218行）
     * 
     * 当向后速度落入移动阻断区间（-0.0091...到0之间）时，需要特殊处理。
     * 
     * 移动阻断机制：
     * - MC中，当速度在很小的范围内（±0.0091...地面，±0.0054...空中）时，会被重置为0
     * - 这会导致计算出的向后速度无法使用
     * 
     * 处理方案：
     * - Plan 4：使用修复速度（fixSpeed）来绕过阻断
     *   - 先计算fixSpeed=0和fixSpeed=1时的bm
     *   - 使用插值计算最优的fixSpeed
     *   - 限制fixSpeed不超过最大值
     * - Plan 5：尝试使用阻断下限（-0.0091...）
     *   - 直接使用阻断阈值作为向后速度
     *   - 比较Plan 4和Plan 5的结果，选择更优的
     * 
     * @param backwardSpeedNonDelayed 非delayed起跳的向后速度
     * @param backwardSpeedDelayed delayed起跳的向后速度
     */
    private void handleBlockFixForSequence(double backwardSpeedNonDelayed, double backwardSpeedDelayed) {
        // 处理非delayed的阻断（对应原153-185行）
        if (backwardSpeedNonDelayed < 0 && backwardSpeedNonDelayed > -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND) {
            double maxBackwardSpeed;
            double maxJumpSpeed;
            
            // Plan 4: 计算修复速度
            blockFixContext.fixPlan = 4;
            blockFixContext.fixSpeed = 0;
            double bmAtZeroSpeed = physicsCalculator.calculateJumpBM(0, false);
            blockFixContext.fixSpeed = 1;
            double bmAtOneSpeed = physicsCalculator.calculateJumpBM(0, false);
            blockFixContext.fixSpeed = (targetBM + MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND - bmAtZeroSpeed) / (bmAtOneSpeed - bmAtZeroSpeed);
            blockFixContext.fixSpeed = Math.min(blockFixContext.fixSpeed, MinecraftPhysicsConstants.MAX_FIX_SPEED);
            physicsCalculator.calculateJumpBM(0, false);
            maxBackwardSpeed = backwardSpeedNonDelayed;
            maxJumpSpeed = physicsCalculator.tempV0;
            
            // Plan 5: 尝试使用阻断下限
            blockFixContext.fixPlan = 5;
            double testBackwardSpeed = Math.min(-MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND, backwardSpeedNonDelayed);
            physicsCalculator.calculateJumpBM(testBackwardSpeed, false);
            System.out.println("p1: " + maxJumpSpeed + " p2: " + physicsCalculator.tempV0);
            
            if (physicsCalculator.tempV0 > maxJumpSpeed) {
                solverState.blockFixPlan = 2;
                solverState.blockFixBackwardSpeed = -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
                solverState.blockFixJumpSpeed = physicsCalculator.tempV0;
            } else {
                solverState.blockFixPlan = 1;
                solverState.blockFixBackwardSpeed = maxBackwardSpeed;
                solverState.blockFixJumpSpeed = maxJumpSpeed;
            }
            
            // 计算最终跳跃
            blockFixContext.fixPlan = 0;
            PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(solverState.blockFixJumpSpeed, false);
            solverState.blockFixPB = result.pb;
            solverState.blockFixDistance = result.distance;
        }
        
        // 处理delayed的阻断（对应原187-217行）
        if (backwardSpeedDelayed < 0 && backwardSpeedDelayed > -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND) {
            double maxBackwardSpeed;
            double maxJumpSpeed;
            
            // Plan 4: 计算修复速度
            blockFixContext.fixPlan = 4;
            blockFixContext.fixSpeed = 0;
            double bmAtZeroSpeed = physicsCalculator.calculateJumpBM(0, true);
            blockFixContext.fixSpeed = 1;
            double bmAtOneSpeed = physicsCalculator.calculateJumpBM(0, true);
            blockFixContext.fixSpeed = (targetBM + MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND - bmAtZeroSpeed) / (bmAtOneSpeed - bmAtZeroSpeed);
            blockFixContext.fixSpeed = Math.min(blockFixContext.fixSpeed, MinecraftPhysicsConstants.MAX_FIX_SPEED);
            physicsCalculator.calculateJumpBM(0, true);
            maxBackwardSpeed = backwardSpeedDelayed;
            maxJumpSpeed = physicsCalculator.tempV0;
            
            // Plan 5: 尝试使用阻断下限
            blockFixContext.fixPlan = 5;
            double testBackwardSpeed = Math.min(-MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND, backwardSpeedDelayed);
            physicsCalculator.calculateJumpBM(testBackwardSpeed, true);
            
            if (physicsCalculator.tempV0 > maxJumpSpeed) {
                solverState.delayedBlockFixPlan = 2;
                solverState.delayedBlockFixBackwardSpeed = -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
                solverState.delayedBlockFixJumpSpeed = physicsCalculator.tempV0;
            } else {
                solverState.delayedBlockFixPlan = 1;
                solverState.delayedBlockFixBackwardSpeed = maxBackwardSpeed;
                solverState.delayedBlockFixJumpSpeed = maxJumpSpeed;
            }
            
            // 计算最终跳跃
            blockFixContext.fixPlan = 0;
            PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(solverState.delayedBlockFixJumpSpeed, true);
            solverState.delayedBlockFixPB = result.pb;
            solverState.delayedBlockFixDistance = result.distance;
        }
    }
    
    /**
     * 处理跑跳技术（对应原226-337行）
     * 
     * 跑跳技术是指在起跳前先跑1tick，利用落地时的45度加速来获得额外速度。
     * 
     * 判断条件：
     * - 如果0.1759（一个测试速度）+ 跑跳后的bm > 目标bm，说明可以使用跑跳
     * 
     * 三种类型：
     * - Type 1：使用负速度跑1t（从-1到0之间）
     * - Type 2：使用正速度跑1t，但需要处理移动阻断
     * - Type 3：直接计算最优的跑1t速度（从0到1之间）
     * 
     * 逻辑：
     * 1. 先尝试Type 1（如果满足条件）
     * 2. 再尝试Type 2（如果满足条件）
     * 3. 最后使用Type 3
     * 
     * @param backwardSpeedNonDelayed 非delayed起跳的向后速度
     * @param backwardSpeedDelayed delayed起跳的向后速度
     */
    private void handleRunJumpTechniques(double backwardSpeedNonDelayed, double backwardSpeedDelayed) {
        // 处理非delayed的跑跳（对应原226-280行）
        if (0.1759 + physicsCalculator.calculateRunJump(0.1759, false) > targetBM) {
            if (runJumpHandler.handleRunJump(targetBM, false)) {
                solverState.runJumpSpeed = runJumpHandler.runSpeed;
                solverState.runJumpStartSpeed = runJumpHandler.jumpStartSpeed;
                solverState.runJumpDistance = runJumpHandler.distance;
                solverState.runJumpPB = runJumpHandler.pb;
                solverState.runJumpType = runJumpHandler.runType;
            }
        }
        
        // 处理delayed的跑跳（对应原282-337行）
        if (backwardSpeedDelayed > 0 && 0.1759 + physicsCalculator.calculateRunJump(0.1759, true) > targetBM) {
            if (runJumpHandler.handleRunJump(targetBM, true)) {
                solverState.delayedRunJumpSpeed = runJumpHandler.runSpeed;
                solverState.delayedRunJumpStartSpeed = runJumpHandler.jumpStartSpeed;
                solverState.delayedRunJumpDistance = runJumpHandler.distance;
                solverState.delayedRunJumpPB = runJumpHandler.pb;
                solverState.delayedRunJumpType = runJumpHandler.runType;
            }
        }
    }
    
    /**
     * 阶段2: Loop优化（对应原341-564行）
     * 
     * Loop是一种通过反复向后跳再向前跳来积累速度的技术。
     * 
     * Loop的工作原理：
     * 1. 向后跳：从当前向后速度s0开始，向后跳获得更高的向后速度
     * 2. 向前跳：从新的向后速度开始，向前跳消耗速度获得bm
     * 3. 重复这个过程，直到收敛（速度不再变化）或达到最大次数
     * 
     * 循环逻辑：
     * 1. 检查delayed状态下是否已经可以用满助跑
     *    - 如果可以，计算最优的落地速度，直接设置s0并退出
     *    - 如果不能，进行向后跳优化
     * 2. 进行向前跳优化，获得最终的向前速度
     * 3. 检查是否可以用满助跑（非delayed和delayed两种情况）
     *    - 如果可以，记录结果并标记infill/defill
     * 4. 检查收敛条件：速度不再变化或达到最大次数
     * 
     * 收敛条件：
     * - 当前向后速度和bm与上一次相同
     * - 或loop次数超过100
     */
    private void optimizeWithLoop() {
        // 计算BWMM速度（对应原342-343行）
        double bwSpeed = findRequiredBackwardSpeed(targetBM, false);
        double deBwSpeed = findRequiredBackwardSpeed(targetBM, true);
        System.out.println("d " + bwSpeed);
        
        double currentBackwardSpeed = initialBackwardSpeed;
        double previousBackwardSpeed = 0;
        double currentBM = 0;
        double previousBM = 0;
        double maxJumpDistance = 0;          // 最大跳跃距离（用于比较）
        double fullBuildUpJumpSpeed = 0;     // 连跳满助跑时的起跳速度
        double optimalLandSpeed = 0;          // 最优落地速度
        double forwardJumpSpeed = 0;         // 向前跳的速度
        double previousForwardJumpSpeed = 0;
        boolean useDelayedJump = true;       // 是否使用delayed起跳
        boolean fullBuildUpUseDelayed = true; // 连跳满助跑时是否使用delayed
        double maxFullBuildUpDistance = 0;   // 连跳满助跑时的最大距离
        
        deloops = 0;
        
        // Loop优化循环（对应原357-453行）
        while (true) {
            // 检测loop时的delayed状态下，当前bw速度是否能够做到完整助跑
            double backToFrontBM = jumpOptimizer.convertBackSpeedToFrontBM(currentBackwardSpeed, true);
            if (backToFrontBM >= targetBM) {
                System.out.println("Delayed时已经用满助跑，已获得最大bwmm速度，无需再loop");
                double bmAtMinSpeed = physicsCalculator.calculateBackToFrontUnit(-1.0, true);
                double bmAtMaxSpeed = physicsCalculator.calculateBackToFrontUnit(1.0, true);
                double calculatedLandSpeed = 2 * ((targetBM - bmAtMinSpeed) / (bmAtMaxSpeed - bmAtMinSpeed)) - 1;
                physicsCalculator.calculateBackToFrontUnit(calculatedLandSpeed, true);
                currentBackwardSpeed = -physicsCalculator.tempV0;
                physicsCalculator.tempBM = targetBM;
            } else {
                currentBackwardSpeed = -jumpOptimizer.optimizeBackwardJump(targetBM, currentBackwardSpeed);
            }
            
            System.out.println("s0 " + currentBackwardSpeed);
            currentBM = physicsCalculator.tempBM;
            forwardJumpSpeed = jumpOptimizer.optimizeForwardJump(currentBM, currentBackwardSpeed);
            
            if (previousForwardJumpSpeed < forwardJumpSpeed) {
                loops = deloops;
            }
            
            // 检查是否可以用满助跑（对应原385-405行）
            if (fullBuildUpJumpSpeed == 0) {
                double testJumpSpeed = currentBackwardSpeed > bwSpeed ? currentBackwardSpeed : bwSpeed;
                testJumpSpeed = jumpOptimizer.optimizeForwardJump(targetBM, testJumpSpeed);
                PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(testJumpSpeed, false);
                if (result.distance - result.pb > maxJumpDistance) {
                    maxJumpDistance = result.distance - result.pb;
                    jloops = deloops;
                    jpb = result.pb;
                }
                
                testJumpSpeed = currentBackwardSpeed > deBwSpeed ? currentBackwardSpeed : deBwSpeed;
                result = physicsCalculator.calculateFinalJump(-testJumpSpeed, true);
                if (result.distance - result.pb > maxJumpDistance) {
                    maxJumpDistance = result.distance - result.pb;
                    jloops = deloops;
                    jpb = result.pb;
                }
            }
            
            System.out.println("s0: " + currentBackwardSpeed + " sbm: " + jumpOptimizer.convertBackSpeedToFrontBM(currentBackwardSpeed, false));
            
            // 检查非delayed是否可以用满助跑（对应原409-425行）
            if (jumpOptimizer.convertBackSpeedToFrontBM(currentBackwardSpeed, false) >= targetBM) {
                System.out.println("连跳已经可以用满助跑，无需再获得更高bwmm速度了");
                double bmAtMinSpeed = physicsCalculator.calculateBackToFrontUnit(-1.0, false);
                double bmAtMaxSpeed = physicsCalculator.calculateBackToFrontUnit(1.0, false);
                double calculatedLandSpeed = 2 * ((targetBM - bmAtMinSpeed) / (bmAtMaxSpeed - bmAtMinSpeed)) - 1;
                physicsCalculator.calculateBackToFrontUnit(calculatedLandSpeed, false);
                PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(physicsCalculator.tempV0, false);
                solverState.infill = true;
                solverState.inspeed = physicsCalculator.tempV0;
                if (maxFullBuildUpDistance < result.distance) {
                    maxFullBuildUpDistance = result.distance;
                    fullBuildUpJumpSpeed = physicsCalculator.tempV0;
                    fullBuildUpUseDelayed = false;
                    optimalLandSpeed = calculatedLandSpeed;
                }
            }
            
            // 检查delayed是否可以用满助跑（对应原427-442行）
            if (jumpOptimizer.convertBackSpeedToFrontBM(currentBackwardSpeed, true) >= targetBM) {
                double bmAtMinSpeed = physicsCalculator.calculateBackToFrontUnit(-1.0, true);
                double bmAtMaxSpeed = physicsCalculator.calculateBackToFrontUnit(1.0, true);
                double calculatedLandSpeed = 2 * ((targetBM - bmAtMinSpeed) / (bmAtMaxSpeed - bmAtMinSpeed)) - 1;
                physicsCalculator.calculateBackToFrontUnit(calculatedLandSpeed, true);
                PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(physicsCalculator.tempV0, true);
                solverState.defill = true;
                if (maxFullBuildUpDistance < result.distance) {
                    maxFullBuildUpDistance = result.distance;
                    fullBuildUpJumpSpeed = physicsCalculator.tempV0;
                    fullBuildUpUseDelayed = true;
                    optimalLandSpeed = calculatedLandSpeed;
                }
            }
            
            // 检查收敛条件（对应原445-452行）
            if ((previousBackwardSpeed == currentBackwardSpeed && previousBM == currentBM) || deloops > 100) {
                break;
            }
            
            deloops++;
            previousBackwardSpeed = currentBackwardSpeed;
            previousBM = currentBM;
            previousForwardJumpSpeed = forwardJumpSpeed;
        }
        
        if (loops < 0) {
            loops = deloops;
        }
        
        // 保存Loop优化结果
        solverState.landSpeed = optimalLandSpeed;
        this.fullBuildUpJumpSpeed = fullBuildUpJumpSpeed;
        this.fullBuildUpUseDelayed = fullBuildUpUseDelayed;
        this.maxFullBuildUpDistance = maxFullBuildUpDistance;
        this.bwSpeed = bwSpeed;
        this.deBwSpeed = deBwSpeed;
        this.currentBackwardSpeed = currentBackwardSpeed;
    }
    
    // Loop优化过程中的临时变量
    private double fullBuildUpJumpSpeed = 0;     // 连跳满助跑时的起跳速度
    private boolean fullBuildUpUseDelayed = true; // 连跳满助跑时是否使用delayed
    private double maxFullBuildUpDistance = 0;   // 连跳满助跑时的最大距离
    private double bwSpeed;                      // BWMM速度（非delayed）
    private double deBwSpeed;                     // BWMM速度（delayed）
    private double currentBackwardSpeed;          // 当前向后速度
    
    /**
     * 阶段3: 最终计算和比较（对应原457-562行）
     * 
     * 这个阶段负责：
     * 1. 计算非delayed起跳的最终距离
     *    - 如果未使用连跳满助跑（!infill），正常计算
     *    - 如果使用了移动阻断处理，使用阻断处理的结果
     *    - 如果使用了连跳满助跑（infill），直接使用之前的结果
     * 
     * 2. 计算delayed起跳的最终距离
     *    - 逻辑与非delayed类似
     * 
     * 3. 比较两种起跳方式，选择更优的
     *    - 比较距离，选择更大的
     *    - 设置delayedG标志
     * 
     * 4. 处理连跳满助跑的情况
     *    - 如果连跳满助跑的距离更优，更新jpb
     * 
     * 5. 与跑跳技术比较
     *    - 如果跑跳技术的距离更优，使用跑跳技术的结果
     * 
     * 6. 输出最终结果
     */
    private void calculateFinalResult() {
        blockFixContext.finals = true;
        System.out.println("fbws " + bwSpeed);
        
        double finalJumpSpeed = 0;                    // 最终起跳速度
        double nonDelayedJumpDistance = 0;            // 非delayed起跳的距离
        double nonDelayedJumpPB = -1;                  // 非delayed起跳的容错
        double delayedJumpDistance = 0;               // delayed起跳的距离
        double delayedJumpPB = -1;                     // delayed起跳的容错
        
        // 计算非delayed起跳的距离（对应原465-492行）
        if (!solverState.infill) {
            double savedBackwardSpeed = currentBackwardSpeed;
            currentBackwardSpeed = currentBackwardSpeed > bwSpeed ? currentBackwardSpeed : bwSpeed;
            
            if (currentBackwardSpeed <= bwSpeed) {
                System.out.println("bwmm/loop已经可以达到极限收益，bwmm/loop有限次数即可");
            }
            System.out.println("bm " + targetBM + " s0 " + currentBackwardSpeed);
            currentBackwardSpeed = jumpOptimizer.optimizeForwardJump(targetBM, currentBackwardSpeed);
            finalJumpSpeed = currentBackwardSpeed;
            System.out.println("Jump s0: " + currentBackwardSpeed);
            PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(currentBackwardSpeed, false);
            blockFixContext.finals = false;
            System.out.println("normal D: " + result.distance);
            
            nonDelayedJumpDistance = result.distance;
            nonDelayedJumpPB = result.pb;
            
            // 如果使用了移动阻断处理
            if (solverState.blockFixPB > -1) {
                nonDelayedJumpDistance = solverState.blockFixDistance;
                nonDelayedJumpPB = solverState.blockFixPB;
                finalJumpSpeed = solverState.blockFixJumpSpeed;
                loops = 0;
            }
            System.out.println(nonDelayedJumpDistance + " instant jump");
            currentBackwardSpeed = savedBackwardSpeed;
        } else {
            finalJumpSpeed = solverState.inspeed;
        }
        
        // 计算delayed起跳的距离（对应原493-509行）
        if (!solverState.defill) {
            double savedBackwardSpeed = currentBackwardSpeed;
            currentBackwardSpeed = currentBackwardSpeed > deBwSpeed ? currentBackwardSpeed : deBwSpeed;
            currentBackwardSpeed = jumpOptimizer.optimizeBackwardJump(targetBM, currentBackwardSpeed);
            PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(currentBackwardSpeed, true);
            
            delayedJumpDistance = result.distance;
            delayedJumpPB = result.pb;
            
            // 如果使用了移动阻断处理
            if (solverState.delayedBlockFixPB > -1) {
                delayedJumpDistance = solverState.delayedBlockFixDistance;
                delayedJumpPB = solverState.delayedBlockFixPB;
                deloops = 0;
            }
            
            System.out.println(delayedJumpDistance + " delayed jump");
            currentBackwardSpeed = savedBackwardSpeed;
        }
        
        // 比较两种起跳方式（对应原511-523行）
        if (nonDelayedJumpDistance > delayedJumpDistance) {
            delayedG = false;
            if (currentBackwardSpeed < bwSpeed) {
                jpb = nonDelayedJumpPB;
            }
        } else {
            nonDelayedJumpDistance = delayedJumpDistance;
            nonDelayedJumpPB = delayedJumpPB;
            delayedG = true;
            if (currentBackwardSpeed < deBwSpeed) {
                jpb = delayedJumpPB;
            }
        }
        
        // 处理连跳满助跑的情况（对应原524-538行）
        if (fullBuildUpJumpSpeed != 0) {
            PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(fullBuildUpJumpSpeed, fullBuildUpUseDelayed);
            if (nonDelayedJumpDistance < result.distance || delayedG == fullBuildUpUseDelayed) {
                jpb = result.pb;
            } else {
                distance = nonDelayedJumpDistance;
                pb = nonDelayedJumpPB;
                fullBuildUpJumpSpeed = 0;
            }
        } else {
            distance = nonDelayedJumpDistance;
            pb = nonDelayedJumpPB;
        }
        
        // 与跑跳技术比较（对应原539-562行）
        if (distance < solverState.runJumpDistance || distance < solverState.delayedRunJumpDistance) {
            System.out.println("跑几t再起跳的跳法比后跳更优");
            if (solverState.runJumpDistance > solverState.delayedRunJumpDistance) {
                distance = solverState.runJumpDistance;
                pb = solverState.runJumpPB;
                jpb = solverState.runJumpPB;
                loops = 0;
                delayedG = false;
                solverState.landSpeed = solverState.runJumpSpeed;
                finalJumpSpeed = solverState.runJumpStartSpeed;
                System.out.println("起跳时不跑的跑nt跳法更优，跳法种类: " + solverState.runJumpType);
            } else {
                distance = solverState.delayedRunJumpDistance;
                pb = solverState.delayedRunJumpPB;
                jpb = solverState.delayedRunJumpPB;
                deloops = 0;
                delayedG = true;
                solverState.landSpeed = solverState.delayedRunJumpSpeed;
                finalJumpSpeed = solverState.delayedRunJumpStartSpeed;
                System.out.println("起跳时跑的跑nt跳法更优，跳法种类: " + solverState.delayedRunJumpType);
            }
        }
        
        // 输出最终结果（对应原567-582行）
        if (pb != jpb && fullBuildUpJumpSpeed == 0) {
            System.out.println("BMlooped: " + (delayedG ? deloops : loops) + ", max BWspeed: " + currentBackwardSpeed + 
                             ", noDelayv0: " + finalJumpSpeed + ", Maxpb: " + pb);
        } else if (fullBuildUpJumpSpeed == 0) {
            System.out.println("BMloop times: " + (delayedG ? deloops : loops) + ", speed: " + 
                             (delayedG ? deBwSpeed : bwSpeed) + ", noDelayv0: " + finalJumpSpeed + ", Maxpb: " + pb);
        } else if (!(distance == solverState.runJumpDistance || distance == solverState.delayedRunJumpDistance)) {
            System.out.println("BMloop times: " + (delayedG ? deloops : loops) + ", landspeed: " + 
                             solverState.landSpeed + ", noDelayv0: " + finalJumpSpeed + ", Maxpb: " + pb);
        } else {
            System.out.println("runSpeed: " + solverState.landSpeed + ", startv0: " + finalJumpSpeed + ", Maxpb: " + pb);
        }
        
        System.out.println("PB: " + jpb + ", MAXdistance: " + distance + ", loops~" + 
                         ((delayedG ? deloops : loops) != 0 ? (jloops + (jpb == MinecraftPhysicsConstants.INVALID_PB ? 0 : 1)) : 0) + 
                         ", delayed?" + delayedG);
        
        if (delayedG && solverState.delayedBlockFixPlan > -1) {
            System.out.println("bwmm plan: " + solverState.delayedBlockFixPlan);
        } else if (!delayedG && solverState.blockFixPlan > -1) {
            System.out.println("bwmm plan: " + solverState.blockFixPlan);
        }
        
        System.out.println(physicsCalculator.delayedNotEnough);
    }
    
    public static void main(String[] args) {
        BmSolverOptimized solver = new BmSolverOptimized();
        solver.setAngleType(1);
        solver.solve(12, 22, 12.3125);
        
        System.out.println("Distance: " + solver.distance);
        System.out.println("PB: " + solver.pb);
    }
}

