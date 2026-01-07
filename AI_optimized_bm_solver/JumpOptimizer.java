/**
 * 跳跃优化类
 * 
 * 这个类负责优化跳跃序列，找到最优的起跳速度。
 * 
 * 主要功能：
 * 1. optimizeForwardJump：向前跳优化（对应原delayedJumps）
 *    - 找到最优的起跳速度，使得从s0开始，经过连跳后能够达到目标bm
 *    - 使用二分法（线性插值）找到最优起跳速度
 *    - 如果遇到移动阻断，调用BlockFixHandler处理
 * 
 * 2. optimizeBackwardJump：向后跳优化（对应原delayedDelayJumps）
 *    - 与向前跳类似，但用于向后跳（delayed起跳）
 * 
 * 3. convertBackSpeedToFrontBM：向后速度转向前bm
 *    - 计算从向后速度v0开始，通过最优的起跳速度，能够达到的最大向前bm
 *    - 用于判断是否可以用满助跑
 * 
 * 优化原理：
 * - 使用线性插值：计算jSpeed=-1和jSpeed=1时的bm，然后插值找到使bm=targetBM的jSpeed
 * - 如果遇到移动阻断，尝试三种修复方案（Plan 1/2/3），选择最优的
 */
public class JumpOptimizer {
    private final PhysicsCalculator physicsCalculator;
    private final BlockFixContext blockFixContext;
    private final BlockFixHandler blockFixHandler;
    private final AngleConfig angleConfig;
    
    public JumpOptimizer(PhysicsCalculator physicsCalculator, BlockFixContext blockFixContext, 
                        BlockFixHandler blockFixHandler, AngleConfig angleConfig) {
        this.physicsCalculator = physicsCalculator;
        this.blockFixContext = blockFixContext;
        this.blockFixHandler = blockFixHandler;
        this.angleConfig = angleConfig;
    }
    
    /**
     * 向前跳优化（对应原delayedJumps）
     * 
     * 找到最优的起跳速度，使得从s0开始，经过连跳后能够达到目标bm，并返回最终的向前速度。
     * 
     * 优化流程：
     * 1. 使用二分法（线性插值）找到最优起跳速度：
     *    - 计算jSpeed=-1时的bm（fbm）
     *    - 计算jSpeed=1时的bm（sbm）
     *    - 使用插值公式：jSpeed = 2 * ((targetBM - fbm) / (sbm - fbm)) - 1
     * 2. 使用计算出的起跳速度进行完整计算
     * 3. 如果遇到移动阻断（inPlace > 0），调用BlockFixHandler处理
     *    - BlockFixHandler会尝试三种修复方案，选择最优的
     * 4. 返回最终的向前速度（tempV0）
     * 
     * @param targetBM 目标bm（需要达到的向前距离）
     * @param initialSpeed 初始速度（s0，向后速度）
     * @return 最终的向前速度（经过连跳后的速度）
     */
    public double optimizeForwardJump(double targetBM, double initialSpeed) {
        blockFixContext.jFinals = false;
        
        // 使用二分法找到使calculateDelayedJumpJumps返回目标bm的起跳速度
        double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, false);
        double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, false);
        double optimalJumpSpeed = 2 * ((targetBM - fbm) / (sbm - fbm)) - 1;
        
        System.out.println("finJS: " + optimalJumpSpeed);
        
        blockFixContext.jFinals = true;
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, optimalJumpSpeed, false);
        
        // 如果遇到移动阻断，尝试三种方案
        if (blockFixContext.inPlace > 0) {
            return blockFixHandler.handleBlockFix(targetBM, initialSpeed, false);
        }
        
        System.out.println(physicsCalculator.tempBM + " finbm  " + physicsCalculator.tempV0 + 
                          " tempv0 " + optimalJumpSpeed + " js plan: 0");
        return physicsCalculator.tempV0;
    }
    
    /**
     * 向后跳优化（对应原delayedDelayJumps）
     * 与optimizeForwardJump类似，但是用于向后跳（delayed起跳）
     * 
     * @param targetBM 目标bm
     * @param initialSpeed 初始速度
     * @return 最终的向后速度
     */
    public double optimizeBackwardJump(double targetBM, double initialSpeed) {
        double fbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, -1, true);
        double sbm = physicsCalculator.calculateDelayedJumpJumps(initialSpeed, 1, true);
        System.out.println("fbm: " + fbm + " sbm: " + sbm);
        
        double optimalJumpSpeed = 2 * ((targetBM - fbm) / (sbm - fbm)) - 1;
        physicsCalculator.calculateDelayedJumpJumps(initialSpeed, optimalJumpSpeed, true);
        System.out.println("run: " + physicsCalculator.tempBM + " finbm  " + optimalJumpSpeed);
        
        // 如果遇到移动阻断，尝试三种方案
        if (blockFixContext.inPlace > 0) {
            return blockFixHandler.handleBlockFix(targetBM, initialSpeed, true);
        }
        
        System.out.println("now s0: " + initialSpeed);
        System.out.println("run: " + physicsCalculator.tempBM + " finbm  " + physicsCalculator.tempV0 + 
                          " tempv0 " + optimalJumpSpeed + " js plan: 0");
        return physicsCalculator.tempV0;
    }
    
    /**
     * 向后速度转向前bm（对应原backSpeedToFront）
     * 计算从向后速度v0开始，通过最优的起跳速度，能够达到的最大向前bm
     * 
     * @param backwardSpeed 向后速度
     * @param delayed 是否delayed
     * @return 能够达到的最大向前bm
     */
    public double convertBackSpeedToFrontBM(double backwardSpeed, boolean delayed) {
        if (physicsCalculator.airtimeSequence.length <= 2) {
            return MinecraftPhysicsConstants.INVALID_BM;
        }
        
        // 计算不同起跳速度下的bm
        double t1 = physicsCalculator.calculateEndToStart(backwardSpeed, -1);
        double t2 = physicsCalculator.calculateEndToStart(backwardSpeed, 1);
        double optimalJumpSpeed = 2 * ((-t1) / (t2 - t1)) - 1;
        
        // 检查是否超过预期起跳速度
        double expectedJumpSpeed = backwardSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                                  MinecraftPhysicsConstants.JUMP_BOOST + 
                                  MinecraftPhysicsConstants.GROUND_MOVEMENT;
        
        if (optimalJumpSpeed > expectedJumpSpeed) {
            // 需要重新计算
            optimalJumpSpeed = 0 * MinecraftPhysicsConstants.FRICTION_GROUND + 
                              MinecraftPhysicsConstants.JUMP_BOOST + 
                              MinecraftPhysicsConstants.GROUND_MOVEMENT;
            t1 = physicsCalculator.calculateEndToStart(0, optimalJumpSpeed);
            optimalJumpSpeed = -2 * MinecraftPhysicsConstants.FRICTION_GROUND + 
                              MinecraftPhysicsConstants.JUMP_BOOST + 
                              MinecraftPhysicsConstants.GROUND_MOVEMENT;
            t2 = physicsCalculator.calculateEndToStart(-2, optimalJumpSpeed);
            backwardSpeed = -2 * ((-t1) / (t2 - t1));
            optimalJumpSpeed = backwardSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                              MinecraftPhysicsConstants.JUMP_BOOST + 
                              MinecraftPhysicsConstants.GROUND_MOVEMENT;
            
            // 迭代33次确保精度
            for (int i = 0; i < 33; i++) {
                t1 = physicsCalculator.calculateEndToStart(0, optimalJumpSpeed);
                t2 = physicsCalculator.calculateEndToStart(-2, optimalJumpSpeed);
                backwardSpeed = -2 * ((-t1) / (t2 - t1));
                optimalJumpSpeed = backwardSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                                  MinecraftPhysicsConstants.JUMP_BOOST + 
                                  MinecraftPhysicsConstants.GROUND_MOVEMENT;
            }
        }
        
        physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
        
        // 如果遇到移动阻断，尝试三种方案
        if (blockFixContext.inPlace > 0) {
            double maxV0 = 0.0;
            blockFixContext.inFix = blockFixContext.inPlace;
            blockFixContext.planSteps = 0;
            
            // Plan 1
            blockFixContext.fixPlan = 1;
            double fbm = physicsCalculator.calculateEndToStart(backwardSpeed, -1);
            double sbm = physicsCalculator.calculateEndToStart(backwardSpeed, 1);
            optimalJumpSpeed = 2 * ((-(float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
            if (blockFixContext.inPlace == 1) {
                optimalJumpSpeed = -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
            }
            blockFixContext.inFix = 0;
            physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
            maxV0 = physicsCalculator.tempV0;
            blockFixContext.inFix = blockFixContext.inPlace;
            
            // Plan 2
            blockFixContext.fixPlan = 2;
            blockFixContext.maxFixSpeed = physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
            blockFixContext.planSteps = 1;
            physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
            fbm = physicsCalculator.tempBM;
            blockFixContext.fixSpeed = -1;
            blockFixContext.planSteps = 2;
            physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
            sbm = physicsCalculator.tempBM;
            blockFixContext.fixSpeed = (blockFixContext.maxFixSpeed + 1) * ((-sbm) / (fbm - sbm)) - 1;
            blockFixContext.fixSpeed = Math.min(blockFixContext.fixSpeed, blockFixContext.maxFixSpeed);
            physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
            maxV0 = Math.max(maxV0, physicsCalculator.tempV0);
            blockFixContext.planSteps = 0;
            
            // Plan 3
            blockFixContext.fixPlan = 3;
            fbm = physicsCalculator.calculateEndToStart(backwardSpeed, -1);
            sbm = physicsCalculator.calculateEndToStart(backwardSpeed, 1);
            optimalJumpSpeed = 2 * (((float)MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR - fbm) / (sbm - fbm)) - 1;
            if (blockFixContext.inPlace == 1) {
                optimalJumpSpeed = MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND;
            }
            
            expectedJumpSpeed = backwardSpeed * MinecraftPhysicsConstants.FRICTION_GROUND + 
                               MinecraftPhysicsConstants.JUMP_BOOST + 
                               MinecraftPhysicsConstants.GROUND_MOVEMENT;
            
            if (expectedJumpSpeed > optimalJumpSpeed) {
                blockFixContext.fixPlan = 2;
                blockFixContext.planSteps = 1;
                physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
                fbm = physicsCalculator.tempBM;
                blockFixContext.fixSpeed = -1;
                blockFixContext.planSteps = 2;
                physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
                sbm = physicsCalculator.tempBM;
                blockFixContext.fixSpeed = (blockFixContext.maxFixSpeed + 1) * ((-sbm) / (fbm - sbm)) - 1;
                physicsCalculator.calculateEndToStart(backwardSpeed, optimalJumpSpeed);
                if (expectedJumpSpeed >= optimalJumpSpeed) {
                    maxV0 = Math.max(maxV0, physicsCalculator.tempV0);
                }
            }
            
            blockFixContext.inPlace = 0;
            blockFixContext.fixPlan = 0;
            blockFixContext.planSteps = 0;
            physicsCalculator.tempV0 = maxV0;
        }
        
        return physicsCalculator.calculateBackToFrontUnit(physicsCalculator.tempV0, delayed);
    }
}

