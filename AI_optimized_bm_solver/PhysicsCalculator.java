/**
 * 物理计算核心类
 * 包含所有MC运动物理的计算逻辑
 */
public class PhysicsCalculator {
    private final AngleConfig angleConfig;
    private final BlockFixContext blockFixContext;
    
    // 计算状态
    private double coord2 = 0.0;           // 起始坐标
    public int[] airtimeSequence;          // 滞空时间序列（对应原ti）- 需要public供其他类访问
    public boolean delayedNotEnough = false; // 对应原dne - 需要public供其他类访问
    
    // 临时计算结果
    public double tempBM = 0.0;            // 临时计算的bm
    public double tempV0 = 0.0;            // 临时计算的速度
    
    public PhysicsCalculator(AngleConfig angleConfig, BlockFixContext blockFixContext) {
        this.angleConfig = angleConfig;
        this.blockFixContext = blockFixContext;
    }
    
    /**
     * 设置滞空时间序列
     */
    public void setAirtimeSequence(int[] sequence) {
        this.airtimeSequence = sequence;
    }
    
    /**
     * 设置起始坐标
     */
    public void setStartCoord(double coord) {
        this.coord2 = coord;
    }
    
    /**
     * 设置delayedNotEnough标志
     */
    public void setDelayedNotEnough(boolean value) {
        this.delayedNotEnough = value;
    }
    
    /**
     * 基础跳跃计算（对应原jump1）
     * 计算从初始向后速度s0开始，经过一系列连跳后，能够达到的向前bm
     * 
     * @param initialBackwardSpeed 初始向后速度
     * @param delayed 是否使用delayed起跳
     * @return 能够达到的向前bm
     */
    public double calculateJumpBM(double initialBackwardSpeed, boolean delayed) {
        double velocity = initialBackwardSpeed;
        double bm = coord2 + initialBackwardSpeed;
        
        // 起跳tick
        if (blockFixContext.fixPlan == 4) {
            velocity = blockFixContext.fixSpeed;
        } else {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                      MinecraftPhysicsConstants.JUMP_BOOST + 
                      MinecraftPhysicsConstants.GROUND_MOVEMENT;
        }
        bm += velocity;
        
        // 确定起始索引（如果delayed且dne，跳过第一个）
        int startIndex = 0;
        if (delayed && delayedNotEnough) {
            startIndex = 1;
        }
        
        // 遍历每个连跳
        for (int i = startIndex; i < airtimeSequence.length - 1; i++) {
            if (i > startIndex) {
                // 普通跳跃tick（落地后起跳）
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          MinecraftPhysicsConstants.JUMP_BOOST + 
                          MinecraftPhysicsConstants.GROUND_MOVEMENT;
                bm += velocity;
            }
            
            // 第一个airtime使用45度加速（如果airtime >= 2）
            if (airtimeSequence[i] >= 2) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
            
            // 后续airtime ticks使用45度加速
            for (int l = 0; l < airtimeSequence[i] - 2; l++) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
        }
        
        // 最后处理
        if (!delayed) {
            bm -= velocity;  // 还没落地，减去最后一tick的速度
        } else {
            // delayed起跳，落地时使用45度加速
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        }
        
        tempV0 = velocity;
        return bm - coord2 - MinecraftPhysicsConstants.PLAYER_WIDTH_HALF;
    }
    
    /**
     * 精确跳跃计算（对应原delayedJumpJumps）
     * 计算从初始速度s0开始，使用指定起跳速度jSpeed，经过连跳后的最终速度或bm
     * 
     * @param initialSpeed 初始速度
     * @param jumpSpeed 起跳速度
     * @param finalDelayed 最后是否delayed
     * @return 如果jSpeed是1或-1返回bm，否则返回最终速度
     */
    public double calculateDelayedJumpJumps(double initialSpeed, double jumpSpeed, boolean finalDelayed) {
        // 重置阻断检测（如果fixPlan==0）
        if (blockFixContext.fixPlan == 0) {
            blockFixContext.inPlace = 0;
        }
        
        double velocity = initialSpeed;
        double bm = coord2 + initialSpeed;
        velocity = jumpSpeed;  // 设置起跳速度
        bm += velocity;
        
        // 检测地面移动阻断（起跳瞬间）
        if (jumpSpeed > -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND && 
            jumpSpeed < MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND && 
            blockFixContext.inPlace == 0) {
            blockFixContext.inPlace = 1;  // 标记为地面阻断
        }
        
        // Plan 2处理：在阻断位置设置速度为0
        if (blockFixContext.inFix == 1 && blockFixContext.fixPlan == 2) {
            velocity = 0;
        }
        
        // Plan 1和Plan 3的早期返回
        if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 1) {
            return velocity;
        }
        if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && blockFixContext.inFix == 1) {
            return velocity;
        }
        
        // 确定起始索引
        int startIndex = 0;
        if (finalDelayed && delayedNotEnough) {
            startIndex = 1;
        }
        
        // 遍历每个连跳
        for (int i = startIndex; i < airtimeSequence.length - 1; i++) {
            if (i > startIndex) {
                // 普通跳跃tick
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          MinecraftPhysicsConstants.JUMP_BOOST + 
                          MinecraftPhysicsConstants.GROUND_MOVEMENT;
                bm += velocity;
                if (blockFixContext.jFinals && blockFixContext.finals) {
                    System.out.println(velocity);
                }
            }
            
            // 第一个airtime使用45度加速
            if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && 
                blockFixContext.inFix == 1 && i == 0) {
                velocity = blockFixContext.fixSpeed;
            } else {
                if (airtimeSequence[i] >= 2) {
                    velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                              (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                               MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                    if (blockFixContext.jFinals && blockFixContext.finals) {
                        System.out.println(velocity);
                    }
                }
            }
            
            // Plan 2的早期返回
            if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 0 && 
                blockFixContext.inFix == 1 && i == 0) {
                return velocity;
            }
            
            bm += velocity;
            
            // 后续airtime ticks
            for (int l = 0; l < airtimeSequence[i] - 2; l++) {
                // 检测空中移动阻断
                if (velocity > -(float)(MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR) && 
                    velocity < (float)(MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR) && 
                    blockFixContext.inPlace == 0) {
                    blockFixContext.inPlace = 2 + l;  // 标记为空中第l个tick的阻断
                }
                
                // Plan 2处理：在阻断位置设置速度为0
                if (blockFixContext.inFix == 2 + l && blockFixContext.fixPlan == 2 && i == 0) {
                    velocity = 0;
                }
                
                // Plan 1和Plan 3的早期返回
                if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 2 + l && i == 0) {
                    return velocity;
                }
                if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && 
                    blockFixContext.inFix == 2 + l && i == 0) {
                    return velocity;
                }
                if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 0 && 
                    blockFixContext.inFix == l + 1 && i == 0) {
                    return velocity;
                }
                
                // Plan 2处理：在阻断后设置修复速度
                if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && 
                    blockFixContext.inFix == l + 2 && i == 0) {
                    velocity = blockFixContext.fixSpeed;
                } else {
                    velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                              (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                               MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                    if (blockFixContext.jFinals && blockFixContext.finals) {
                        System.out.println(velocity);
                    }
                }
                
                bm += velocity;
            }
        }
        
        // 最后处理
        if (finalDelayed) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
            if (blockFixContext.jFinals && blockFixContext.finals) {
                System.out.println(velocity);
            }
        } else {
            bm -= velocity;  // 还没落地
        }
        
        tempV0 = velocity;
        tempBM = bm - coord2 - MinecraftPhysicsConstants.PLAYER_WIDTH_HALF;
        
        // 如果jSpeed是1或-1，返回bm
        if (jumpSpeed == 1 || jumpSpeed == -1) {
            return tempBM;
        }
        return velocity;
    }
    
    /**
     * 最终跳跃计算（对应原finaljump）
     * 计算从起跳速度v0开始，经过最终跳跃后的距离和pb
     * 
     * @param jumpVelocity 起跳速度
     * @param delayed 是否delayed起跳
     * @return 包含distance和pb的结果
     */
    public JumpResult calculateFinalJump(double jumpVelocity, boolean delayed) {
        double distance = coord2 + MinecraftPhysicsConstants.PLAYER_CENTER_OFFSET;
        distance += jumpVelocity;
        
        double velocity = jumpVelocity;
        
        // 起跳tick
        if (delayed) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                      MinecraftPhysicsConstants.JUMP_BOOST + 
                      MinecraftPhysicsConstants.GROUND_MOVEMENT;
        } else {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      MinecraftPhysicsConstants.JUMP_BOOST + 
                      MinecraftPhysicsConstants.GROUND_MOVEMENT;
        }
        distance += velocity;
        
        int lastIndex = airtimeSequence.length - 1;
        
        // 第一个airtime使用45度加速（如果airtime > 2）
        if (airtimeSequence[lastIndex] > 2) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                      (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
            distance += velocity;
        }
        
        // 后续airtime ticks
        for (int l = 0; l < airtimeSequence[lastIndex] - 3; l++) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
            distance += velocity;
        }
        
        double finalV0 = velocity;
        double finalDistance = distance - ((double)coord2 - MinecraftPhysicsConstants.PLAYER_CENTER_OFFSET) - 
                              Math.ulp(distance);
        double pb = finalDistance - MinecraftPhysicsConstants.BLOCK_SIZE * 
                   ((int)(finalDistance / MinecraftPhysicsConstants.BLOCK_SIZE));
        
        return new JumpResult(finalDistance, pb, finalV0);
    }
    
    /**
     * 跳跃结果类
     */
    public static class JumpResult {
        public final double distance;
        public final double pb;
        public final double finalV0;
        
        public JumpResult(double distance, double pb, double finalV0) {
            this.distance = distance;
            this.pb = pb;
            this.finalV0 = finalV0;
        }
    }
    
    /**
     * 结束到开始的计算（对应原endMStart）
     * 计算从向后速度v0开始，使用起跳速度js，经过第一个连跳后的bm
     */
    public double calculateEndToStart(double backwardSpeed, double jumpSpeed) {
        // 重置阻断检测（如果fixPlan==0）
        if (blockFixContext.fixPlan == 0) {
            blockFixContext.inPlace = 0;
        }
        
        double bm = coord2 + backwardSpeed;
        double velocity = jumpSpeed;
        bm += velocity;
        
        // 检测地面移动阻断
        if (jumpSpeed > -MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND && 
            jumpSpeed < MinecraftPhysicsConstants.BLOCK_THRESHOLD_GROUND && 
            blockFixContext.inPlace == 0) {
            blockFixContext.inPlace = 1;
        }
        
        // Plan 2处理
        if (blockFixContext.inFix == 1 && blockFixContext.fixPlan == 2) {
            velocity = 0;
        }
        
        // Plan 1和Plan 3的早期返回
        if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 1) {
            return velocity;
        }
        if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && blockFixContext.inFix == 1) {
            return velocity;
        }
        
        // 第一个airtime使用45度加速
        if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && blockFixContext.inFix == 1) {
            velocity = blockFixContext.fixSpeed;
        } else {
            if (airtimeSequence[0] >= 2) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
            }
        }
        
        // Plan 2的早期返回
        if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 0 && blockFixContext.inFix == 1) {
            return velocity;
        }
        
        bm += velocity;
        
        // 后续airtime ticks
        for (int l = 0; l < airtimeSequence[0] - 2; l++) {
            // 检测空中移动阻断
            if (velocity > -(float)(MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR) && 
                velocity < (float)(MinecraftPhysicsConstants.BLOCK_THRESHOLD_AIR) && 
                blockFixContext.inPlace == 0) {
                blockFixContext.inPlace = 2 + l;
            }
            
            // Plan 2处理
            if (blockFixContext.inFix == 2 + l && blockFixContext.fixPlan == 2) {
                velocity = 0;
            }
            
            // Plan 1和Plan 3的早期返回
            if (blockFixContext.fixPlan == 1 && blockFixContext.inFix == 2 + l) {
                return velocity;
            }
            if (blockFixContext.fixPlan == 3 && blockFixContext.planSteps == 0 && blockFixContext.inFix == 2 + l) {
                return velocity;
            }
            if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 0 && blockFixContext.inFix == l + 1) {
                return velocity;
            }
            
            // Plan 2处理：在阻断后设置修复速度
            if (blockFixContext.fixPlan == 2 && blockFixContext.planSteps == 2 && blockFixContext.inFix == l + 2) {
                velocity = blockFixContext.fixSpeed;
            } else {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
            }
            
            bm += velocity;
        }
        
        bm -= velocity;  // 还没落地，减去最后一tick的速度
        tempV0 = velocity;
        tempBM = bm;
        return bm;
    }
    
    /**
     * 向后转向前单位计算（对应原backToFrontUnit）
     * 计算从向后速度v0开始，经过后续连跳后能够达到的向前bm
     */
    public double calculateBackToFrontUnit(double backwardSpeed, boolean delayed) {
        double bm = backwardSpeed;
        int startIndex = 1;
        if (delayedNotEnough && delayed) {
            startIndex = 2;
        }
        if (startIndex >= airtimeSequence.length - 1) {
            return MinecraftPhysicsConstants.INVALID_BM;
        }
        
        double velocity = backwardSpeed;
        for (int i = startIndex; i < airtimeSequence.length - 1; i++) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      MinecraftPhysicsConstants.JUMP_BOOST + 
                      MinecraftPhysicsConstants.GROUND_MOVEMENT;
            bm += velocity;
            
            // 第一个airtime使用45度加速
            if (airtimeSequence[i] >= 2) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
            
            // 后续airtime ticks
            for (int l = 0; l < airtimeSequence[i] - 2; l++) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
        }
        
        if (delayed) {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        } else {
            bm -= velocity;  // 还没落地
        }
        
        tempV0 = velocity;
        return bm - coord2 - MinecraftPhysicsConstants.PLAYER_WIDTH_HALF;
    }
    
    /**
     * 跑跳计算（对应原awRunJump）
     * 计算从跑1t的速度v0开始，经过连跳后能够达到的bm（不包括跑1t的距离）
     */
    public double calculateRunJump(double run1tSpeed, boolean delayed) {
        double bm = coord2;
        double velocity = run1tSpeed;
        
        // 起跳tick
        if (blockFixContext.fixPlan == 4) {
            velocity = blockFixContext.fixSpeed;
        } else {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                      MinecraftPhysicsConstants.JUMP_BOOST + 
                      MinecraftPhysicsConstants.GROUND_MOVEMENT;
        }
        bm += velocity;
        
        // 遍历每个连跳
        for (int i = 0; i < airtimeSequence.length - 1; i++) {
            if (i > 0) {
                // 普通跳跃tick
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          MinecraftPhysicsConstants.JUMP_BOOST + 
                          MinecraftPhysicsConstants.GROUND_MOVEMENT;
                bm += velocity;
            }
            
            // 第一个airtime使用45度加速
            if (airtimeSequence[i] >= 2) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_GROUND + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
            
            // 后续airtime ticks
            for (int l = 0; l < airtimeSequence[i] - 2; l++) {
                velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                          (MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.sin + 
                           MinecraftPhysicsConstants.AIR_MOVEMENT_45 * angleConfig.cos);
                bm += velocity;
            }
        }
        
        if (!delayed) {
            bm -= velocity;  // 还没落地
        } else {
            velocity = velocity * MinecraftPhysicsConstants.FRICTION_AIR + 
                      (MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.sin + 
                       MinecraftPhysicsConstants.LANDING_MOVEMENT_45 * angleConfig.cos);
        }
        
        tempV0 = velocity;
        return bm - coord2 - MinecraftPhysicsConstants.PLAYER_WIDTH_HALF;
    }
}

