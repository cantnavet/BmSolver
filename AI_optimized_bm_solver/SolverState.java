/**
 * 求解器状态类
 * 存储求解过程中的中间结果
 */
public class SolverState {
    // BWMM移动阻断处理结果（非delayed）
    public int blockFixPlan = -1;            // 阻断修复方案：1, 2
    public double blockFixPB = -1;           // 阻断修复后的容错
    public double blockFixDistance;          // 阻断修复后的距离
    public double blockFixBackwardSpeed;     // 阻断修复后的向后速度
    public double blockFixJumpSpeed;        // 阻断修复后的起跳速度
    
    // BWMM移动阻断处理结果（delayed）
    public int delayedBlockFixPlan = -1;    // 阻断修复方案（delayed）：1, 2
    public double delayedBlockFixPB = -1;    // 阻断修复后的容错（delayed）
    public double delayedBlockFixDistance;   // 阻断修复后的距离（delayed）
    public double delayedBlockFixBackwardSpeed; // 阻断修复后的向后速度（delayed）
    public double delayedBlockFixJumpSpeed;  // 阻断修复后的起跳速度（delayed）
    
    // 跑跳技术结果（非delayed）
    public double runJumpSpeed;              // 跑1t的速度
    public double runJumpStartSpeed;         // 起跳速度
    public double runJumpDistance;           // 距离
    public double runJumpPB;                 // 容错
    public int runJumpType = 0;              // 跑跳类型：1, 2, 3
    
    // 跑跳技术结果（delayed）
    public double delayedRunJumpSpeed;       // 跑1t的速度（delayed）
    public double delayedRunJumpStartSpeed;  // 起跳速度（delayed）
    public double delayedRunJumpDistance;    // 距离（delayed）
    public double delayedRunJumpPB;          // 容错（delayed）
    public int delayedRunJumpType = 0;       // 跑跳类型（delayed）：1, 2, 3
    
    // Loop优化状态
    public boolean infill = false;
    public boolean defill = false;
    public double inspeed = 0;
    public double landSpeed = 0;
    
    /**
     * 重置所有状态
     */
    public void reset() {
        blockFixPlan = -1;
        blockFixPB = -1;
        delayedBlockFixPB = -1;
        delayedBlockFixPlan = -1;
        runJumpType = 0;
        delayedRunJumpType = 0;
        infill = false;
        defill = false;
        inspeed = 0;
        landSpeed = 0;
    }
}

