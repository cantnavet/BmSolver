/**
 * 移动阻断修复上下文
 * 
 * 这个类管理移动阻断处理过程中的临时状态。
 * 
 * 阻断检测状态：
 * - inPlace：阻断位置
 *   - 0：无阻断
 *   - 1：地面阻断（起跳瞬间）
 *   - 2+l：空中第l个tick的阻断
 * 
 * - inFix：当前修复的阻断位置
 *   - 用于：标记正在处理哪个位置的阻断
 * 
 * 修复方案状态：
 * - fixPlan：修复方案
 *   - 0：无修复方案
 *   - 1：Plan 1（刚好慢于阻断）
 *   - 2：Plan 2（刚好卡在阻断下限）
 *   - 3：Plan 3（刚好快于阻断）
 *   - 4：特殊方案（用于阶段1的阻断处理）
 *   - 5：特殊方案2（用于阶段1的阻断处理）
 * 
 * - planSteps：Plan步骤
 *   - 0：初始状态
 *   - 1：第一步（计算最大修复速度）
 *   - 2：第二步（计算修复速度）
 * 
 * 修复速度：
 * - fixSpeed：修复后的速度
 *   - 用于：Plan 2中，过阻断后使用的速度
 * 
 * - maxFixSpeed：最大修复速度
 *   - 用于：Plan 2中，限制fixSpeed的最大值
 * 
 * 调试标志：
 * - finals：是否在最终计算阶段
 * - jFinals：是否在跳跃最终计算阶段
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

