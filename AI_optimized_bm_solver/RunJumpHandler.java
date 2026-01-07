/**
 * 跑跳技术处理类
 * 
 * 这个类处理起跳前跑1tick的技术，利用落地时的45度加速来获得额外速度。
 * 
 * 跑跳技术的原理：
 * - 在起跳前先跑1tick，利用落地时的45度加速（0.09192386 * (sin + cos)）
 * - 可以获得比直接起跳更高的起跳速度
 * - 从而能够达到更远的距离
 * 
 * 三种类型：
 * - Type 1：使用负速度跑1t（从-1到0之间）
 *   - 适用于：使用负速度跑1t后，能够达到目标bm
 *   - 计算：从0和-1两个点插值找到最优跑速
 * 
 * - Type 2：使用正速度跑1t，但需要处理移动阻断
 *   - 适用于：使用正速度跑1t后，能够达到目标bm
 *   - 计算：从0和RunEqualv0两个点插值找到最优跑速
 *   - 如果跑速落入移动阻断区间，需要特殊处理
 * 
 * - Type 3：直接计算最优的跑1t速度
 *   - 适用于：前两种类型都不满足
 *   - 计算：从0和1两个点插值找到最优跑速
 * 
 * 判断逻辑：
 * 1. 先检查是否可以使用跑跳（0.1759 + 跑跳后的bm > 目标bm）
 * 2. 按顺序尝试Type 1、Type 2、Type 3
 * 3. 选择第一个满足条件的类型
 */
public class RunJumpHandler {
    private final PhysicsCalculator physicsCalculator;
    private final BlockFixContext blockFixContext;
    private final AngleConfig angleConfig;
    private final JumpOptimizer jumpOptimizer;
    
    // 跑跳结果
    public double runSpeed;           // 跑1t的速度
    public double jumpStartSpeed;    // 起跳速度
    public double distance;          // 距离
    public double pb;                // 容错
    public int runType;              // 跑跳类型：1, 2, 3
    
    public RunJumpHandler(PhysicsCalculator physicsCalculator, BlockFixContext blockFixContext,
                          AngleConfig angleConfig, JumpOptimizer jumpOptimizer) {
        this.physicsCalculator = physicsCalculator;
        this.blockFixContext = blockFixContext;
        this.angleConfig = angleConfig;
        this.jumpOptimizer = jumpOptimizer;
    }
    
    /**
     * 处理跑跳技术（对应原226-337行）
     * 
     * @param targetBM 目标bm
     * @param delayed 是否delayed起跳
     * @return 是否成功找到跑跳方案
     */
    public boolean handleRunJump(double targetBM, boolean delayed) {
        // 检查是否可以使用跑跳
        double testBM = 0.1759 + physicsCalculator.calculateRunJump(0.1759, delayed);
        if (testBM <= targetBM) {
            return false;  // 无法使用跑跳
        }
        
        // Type 1: 使用负速度跑1t
        if (physicsCalculator.calculateRunJump(-angleConfig.runEqualV0, delayed) >= targetBM) {
            handleRunJumpType1(targetBM, delayed);
            return true;
        }
        
        // Type 2: 使用正速度跑1t，但需要处理移动阻断
        if (angleConfig.awRun + physicsCalculator.calculateRunJump(angleConfig.awRun, delayed) >= targetBM) {
            handleRunJumpType2(targetBM, delayed);
            return true;
        }
        
        // Type 3: 直接计算最优的跑1t速度
        handleRunJumpType3(targetBM, delayed);
        return true;
    }
    
    /**
     * Type 1: 使用负速度跑1t
     */
    private void handleRunJumpType1(double targetBM, boolean delayed) {
        // 计算跑1t后的速度（从0开始）
        double runSpeedAfterZero = 0;
        runSpeedAfterZero = runSpeedAfterZero * MinecraftPhysicsConstants.FRICTION_GROUND + 
              (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
               MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        double bmAtZeroRunSpeed = physicsCalculator.calculateRunJump(runSpeedAfterZero, delayed);
        
        // 计算跑1t后的速度（从-1开始）
        double runSpeedAfterMinusOne = -1;
        runSpeedAfterMinusOne = runSpeedAfterMinusOne * MinecraftPhysicsConstants.FRICTION_GROUND + 
              (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
               MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        double bmAtMinusOneRunSpeed = physicsCalculator.calculateRunJump(runSpeedAfterMinusOne, delayed);
        
        // 计算最优跑1t速度
        runSpeed = ((targetBM - bmAtMinusOneRunSpeed) / (bmAtZeroRunSpeed - bmAtMinusOneRunSpeed)) - 1;
        
        // 计算实际跑1t后的速度
        double actualRunSpeed = runSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                               (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                                MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        physicsCalculator.calculateRunJump(actualRunSpeed, delayed);
        jumpStartSpeed = physicsCalculator.tempV0;
        
        // 计算最终跳跃
        PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(jumpStartSpeed, delayed);
        distance = result.distance;
        pb = result.pb;
        runType = 1;
        
        System.out.println("run type1: " + distance + " pb " + pb + " s0 " + runSpeed);
    }
    
    /**
     * Type 2: 使用正速度跑1t，但需要处理移动阻断
     */
    private void handleRunJumpType2(double targetBM, boolean delayed) {
        // 计算跑1t后的速度（从0开始）
        double runSpeedAfterZero = 0;
        runSpeedAfterZero = runSpeedAfterZero * MinecraftPhysicsConstants.FRICTION_GROUND + 
              (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
               MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        double bmAtZeroRunSpeed = runSpeedAfterZero + physicsCalculator.calculateRunJump(runSpeedAfterZero, delayed);
        
        // 计算跑1t后的速度（从RunEqualv0开始）
        double runSpeedAfterRunEqual = angleConfig.runEqualV0;
        runSpeedAfterRunEqual = runSpeedAfterRunEqual * MinecraftPhysicsConstants.FRICTION_GROUND + 
              (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
               MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        double bmAtRunEqualSpeed = physicsCalculator.calculateRunJump(runSpeedAfterRunEqual, delayed);
        
        // 计算最优跑1t速度
        runSpeed = -angleConfig.runEqualV0 * ((targetBM - bmAtRunEqualSpeed) / (bmAtZeroRunSpeed - bmAtRunEqualSpeed)) + angleConfig.runEqualV0;
        
        if (runSpeed < -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND) {
            // 需要处理移动阻断
            double actualRunSpeed = runSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                                   (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                                    MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
            physicsCalculator.calculateRunJump(actualRunSpeed, delayed);
            jumpStartSpeed = physicsCalculator.tempV0;
        } else {
            // 处理移动阻断
            double bmAtZeroWithBlockFix = physicsCalculator.calculateRunJump(0, delayed) - MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
            double bmAtOneWithBlockFix = physicsCalculator.calculateRunJump(1, delayed) - MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND + 1;
            double calculatedRunSpeed = (targetBM - bmAtZeroWithBlockFix) / (bmAtOneWithBlockFix - bmAtZeroWithBlockFix);
            physicsCalculator.calculateRunJump(calculatedRunSpeed, delayed);
            jumpStartSpeed = physicsCalculator.tempV0;
            System.out.println(calculatedRunSpeed);
        }
        
        // 计算最终跳跃
        PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(jumpStartSpeed, delayed);
        distance = result.distance;
        pb = result.pb;
        runType = 2;
        
        System.out.println("run type2: " + distance + " pb " + pb + " s0 " + runSpeed);
    }
    
    /**
     * Type 3: 直接计算最优的跑1t速度
     */
    private void handleRunJumpType3(double targetBM, boolean delayed) {
        double bmAtZeroRunSpeed = physicsCalculator.calculateRunJump(0, delayed);
        double bmAtOneRunSpeed = 1 + physicsCalculator.calculateRunJump(1, delayed);
        runSpeed = (targetBM - bmAtZeroRunSpeed) / (bmAtOneRunSpeed - bmAtZeroRunSpeed);
        
        physicsCalculator.calculateRunJump(runSpeed, delayed);
        jumpStartSpeed = physicsCalculator.tempV0;
        
        // 计算最终跳跃
        PhysicsCalculator.JumpResult result = physicsCalculator.calculateFinalJump(jumpStartSpeed, delayed);
        distance = result.distance;
        pb = result.pb;
        runType = 3;
        
        System.out.println("run type3: " + distance + " pb " + pb + " s0 " + runSpeed + " rjs0: " + jumpStartSpeed);
    }
}

