/**
 * 移动阻断处理类
 * 
 * 这个类处理Minecraft中的移动阻断机制。
 * 
 * 移动阻断机制：
 * - MC中，当速度在很小的范围内时，会被重置为0
 * - 地面阻断阈值：±0.009157508093840406（= 0.005 / 0.546）
 * - 空中阻断阈值：±0.005494505（= 0.005 / 0.91）
 * - 这会导致计算出的速度无法使用，需要特殊处理
 * 
 * 三种修复方案：
 * 
 * Plan 1：刚好慢于移动阻断
 * - 策略：使用刚好低于阻断阈值的速度作为起跳速度
 * - 原理：过阻断后全力前进，获得最大速度
 * 
 * Plan 2：刚好卡在阻断下限，调整过阻断后的速度
 * - 策略：使用阻断下限作为起跳速度，然后调整过阻断后的速度
 * - 原理：通过调整过阻断后的速度来达到目标bm
 * 
 * Plan 3：刚好快于移动阻断
 * - 策略：使用刚好高于阻断阈值的速度作为起跳速度
 * - 原理：过阻断后调整速度来达到目标bm
 * 
 * 处理流程：
 * 1. 检测到阻断（inPlace > 0）
 * 2. 依次尝试三种方案
 * 3. 比较三种方案的结果，选择最优的（最终速度最大的）
 * 4. 返回最优方案的最终速度
 */
public class BlockFixHandler {
    private final PhysicsCalculator physicsCalculator;
    private final BlockFixContext context;
    private final AngleConfig angleConfig;
    
    public BlockFixHandler(PhysicsCalculator physicsCalculator, BlockFixContext context, AngleConfig angleConfig) {
        this.physicsCalculator = physicsCalculator;
        this.context = context;
        this.angleConfig = angleConfig;
    }
    
    /**
     * 处理移动阻断，返回最优的起跳速度
     * 对应原delayedJumps中的阻断处理部分
     * 
     * @param targetBM 目标bm
     * @param initialSpeed 初始速度
     * @param finalDelayed 最后是否delayed
     * @return 最优的最终速度
     */
    public double handleBlockFix(double targetBM, double initialSpeed, boolean finalDelayed) {
        if (context.inPlace <= 0) {
            return physicsCalculator.tempV0;  // 没有阻断，直接返回
        }
        
        double maxV0 = 0.0;
        context.inFix = context.inPlace;
        context.planSteps = 0;
        double optimalJumpSpeed = 0.0;
        int bestPlan = 0;
        
        // Plan 1: 刚好慢于移动阻断，过阻断后全力前进
        double plan1V0 = tryPlan1(targetBM, initialSpeed, finalDelayed);
        if (plan1V0 > maxV0) {
            maxV0 = plan1V0;
            optimalJumpSpeed = calculatePlan1JumpSpeed(targetBM, initialSpeed, finalDelayed);
            bestPlan = 1;
        }
        
        // Plan 2: 刚好卡在阻断下限，调整过阻断后的速度
        double plan2V0 = tryPlan2(targetBM, initialSpeed, finalDelayed, optimalJumpSpeed);
        if (plan2V0 > maxV0) {
            maxV0 = plan2V0;
            bestPlan = 2;
        }
        
        // Plan 3: 刚好快于移动阻断，调整过阻断后的速度
        // 对所有阻断（地面和空中）都尝试Plan 3
        double plan3V0 = tryPlan3(targetBM, initialSpeed, finalDelayed);
        if (plan3V0 > maxV0) {
            maxV0 = plan3V0;
            bestPlan = 3;
        }
        
        // 重置状态
        context.inPlace = 0;
        context.fixPlan = 0;
        context.planSteps = 0;
        physicsCalculator.tempV0 = maxV0;
        
        System.out.println(physicsCalculator.tempBM + " finbm  " + maxV0 + " tempv0 " + 
                          optimalJumpSpeed + " js plan: " + bestPlan);
        
        return maxV0;
    }
    
    /**
     * Plan 1: 刚好慢于移动阻断
     */
    private double tryPlan1(double targetBM, double initialSpeed, boolean finalDelayed) {
        context.fixPlan = 1;
        
        // 计算阻断位置的起跳速度
        double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, finalDelayed);
        double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, finalDelayed);
        
        double jumpSpeed;
        if (context.inPlace == 1) {
            // 地面阻断，使用地面阻断阈值
            jumpSpeed = -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
        } else {
            // 空中阻断，使用空中阻断阈值
            jumpSpeed = 2 * ((-(float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
        }
        
        context.inFix = 0;
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, jumpSpeed, finalDelayed);
        
        System.out.println("p1v0 " + physicsCalculator.tempV0);
        return physicsCalculator.tempV0;
    }
    
    /**
     * 计算Plan 1的起跳速度
     */
    private double calculatePlan1JumpSpeed(double targetBM, double initialSpeed, boolean finalDelayed) {
        double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, finalDelayed);
        double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, finalDelayed);
        
        if (context.inPlace == 1) {
            return -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
        } else {
            return 2 * ((-(float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
        }
    }
    
    /**
     * Plan 2: 刚好卡在阻断下限，调整过阻断后的速度
     */
    private double tryPlan2(double targetBM, double initialSpeed, boolean finalDelayed, double plan1JumpSpeed) {
        context.fixPlan = 2;
        
        // 第一步：计算最大修复速度
        context.planSteps = 1;
        context.maxFixSpeed = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
        double fbm = physicsCalculator.tempBM;
        
        // 第二步：计算修复速度
        context.fixSpeed = -1;
        context.planSteps = 2;
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
        double sbm = physicsCalculator.tempBM;
        
        // 计算最优修复速度
        context.fixSpeed = (context.maxFixSpeed + 1) * ((targetBM - sbm) / (fbm - sbm)) - 1;
        context.fixSpeed = Math.min(context.fixSpeed, context.maxFixSpeed);
        
        // 第三步：使用修复速度计算
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, plan1JumpSpeed, finalDelayed);
        
        System.out.println("p2v0 " + physicsCalculator.tempV0 + "  fixsp " + context.fixSpeed);
        context.planSteps = 0;
        
        return physicsCalculator.tempV0;
    }
    
    /**
     * Plan 3: 刚好快于移动阻断
     */
    private double tryPlan3(double targetBM, double initialSpeed, boolean finalDelayed) {
        context.fixPlan = 3;
        
        // 计算阻断位置的起跳速度
        double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, finalDelayed);
        double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, finalDelayed);
        
        double jumpSpeed;
        if (context.inPlace == 1) {
            jumpSpeed = MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
        } else {
            jumpSpeed = 2 * (((float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
        }
        
        // 借用Plan 2的功能
        context.fixPlan = 2;
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
        // expectedJumpSpeed是MC物理引擎允许的最大起跳速度（从initialSpeed正常计算出的）
        // 这个条件检查是为了防止起跳速度因为需要超过移动阻断阈值，而在插值后强行超越了MC中的最大起跳速度
        // 如果修正前的策略中为了适配助跑而调小了起跳速度（expectedJumpSpeed较小），
        // 那么Plan 3计算出的jumpSpeed（刚好高于阻断阈值）可能不会超过expectedJumpSpeed，就有机会应用这个Plan
        double expectedJumpSpeed = initialSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                                  MinecraftPhysicsConstants.JUMP_BOOST + 
                                  MinecraftPhysicsConstants.GROUND_MOVEMENT;
        
        System.out.println("p3v0 " + physicsCalculator.tempV0 + "  fixsp " + context.fixSpeed);
        
        if (expectedJumpSpeed >= jumpSpeed) {
            return physicsCalculator.tempV0;  // 满足条件，Plan 3可行
        }
        return 0.0;  // 不满足条件，Plan 3不可行（起跳速度超过了MC允许的最大值）
    }
}

