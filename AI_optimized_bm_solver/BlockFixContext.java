/**
 * 移动阻断修复上下文
 * 管理移动阻断处理的临时状态
 */
public class BlockFixContext {
    // 阻断检测
    public int inPlace = 0;        // 阻断位置：0=无, 1=地面, 2+l=空中第l个tick
    public int inFix = 0;          // 当前修复的阻断位置
    
    // 修复方案
    public int fixPlan = 0;        // 修复方案：0=无, 1=Plan1, 2=Plan2, 3=Plan3, 4=特殊, 5=特殊2
    public int planSteps = 0;      // Plan步骤：0=初始, 1=第一步, 2=第二步
    
    // 修复速度
    public double fixSpeed = 0.0;  // 修复后的速度
    public double maxFixSpeed = 0.0; // 最大修复速度
    
    // 调试标志
    public boolean finals = false;
    public boolean jFinals = false;
    
    /**
     * 重置所有状态
     */
    public void reset() {
        inPlace = 0;
        inFix = 0;
        fixPlan = 0;
        planSteps = 0;
        fixSpeed = 0.0;
        maxFixSpeed = 0.0;
        finals = false;
        jFinals = false;
    }
}

