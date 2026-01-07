import java.util.Arrays;

/**
 * 优化后的BM求解器
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
     * @param buildUpAirtime 助跑上的滞空时间
     * @param jumpAirtime 跳跃的滞空时间
     * @param buildUpLength 助跑长度
     */
    public void solve(int buildUpAirtime, int jumpAirtime, double buildUpLength) {
        // 初始化
        targetBM = buildUpLength;
        initialBackwardSpeed = 0.0;
        physicsCalculator.setStartCoord(0.0);
        loops = -1;
        deloops = 0;
        jpb = MinecraftPhysicsConstants.INVALID_PB;
        
        // 阶段1: 确定最优连跳次数
        findOptimalJumpSequence(buildUpAirtime, jumpAirtime);
        
        // 阶段2: Loop优化
        optimizeWithLoop();
        
        // 阶段3: 最终计算和比较
        calculateFinalResult();
    }
    
    /**
     * 阶段1: 确定最优连跳次数（对应原single中的144-339行）
     */
    private void findOptimalJumpSequence(int buildUpAirtime, int jumpAirtime) {
        for (int jumpCount = 2; jumpCount < 100000; jumpCount++) {
            // 创建滞空时间序列
            int[] sequence = new int[jumpCount];
            Arrays.fill(sequence, buildUpAirtime);
            sequence[jumpCount - 1] = jumpAirtime;
            airtimeSequence = sequence;
            physicsCalculator.setAirtimeSequence(sequence);
            
            // 计算达到目标bm所需的最小向后速度
            double backwardSpeedNonDelayed = findRequiredBackwardSpeed(targetBM, false);
            double backwardSpeedDelayed = findRequiredBackwardSpeed(targetBM, true);
            
            // 处理移动阻断（对应原150-218行）
            handleBlockFixForSequence(backwardSpeedNonDelayed, backwardSpeedDelayed);
            
            // 如果无法达到，退出循环
            if (backwardSpeedNonDelayed <= 0) {
                break;
            }
            
            // 处理跑跳技术（对应原226-337行）
            if (backwardSpeedDelayed <= 0) {
                physicsCalculator.setDelayedNotEnough(true);
            }
            handleRunJumpTechniques(backwardSpeedNonDelayed, backwardSpeedDelayed);
        }
    }
    
    /**
     * 查找达到目标bm所需的最小向后速度（对应原bmfind）
     */
    private double findRequiredBackwardSpeed(double targetBM, boolean delayed) {
        double bm0 = physicsCalculator.calculateJumpBM(0, delayed);
        double bm1 = physicsCalculator.calculateJumpBM(-1, delayed);
        double requiredSpeed = -(bm0 - targetBM) / (bm0 - bm1);
        physicsCalculator.calculateJumpBM(requiredSpeed, delayed);
        return requiredSpeed;
    }
    
    /**
     * 处理移动阻断（对应原150-218行）
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

