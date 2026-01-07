/**
 * 求解器状态类
 * 
 * 这个类存储求解过程中的中间结果，用于在不同阶段之间传递数据。
 * 
 * BWMM移动阻断处理结果（非delayed）：
 * - blockFixPlan：阻断修复方案（1或2）
 * - blockFixPB：阻断修复后的容错
 * - blockFixDistance：阻断修复后的距离
 * - blockFixBackwardSpeed：阻断修复后的向后速度
 * - blockFixJumpSpeed：阻断修复后的起跳速度
 * 
 * BWMM移动阻断处理结果（delayed）：
 * - delayedBlockFixPlan：阻断修复方案（delayed，1或2）
 * - delayedBlockFixPB：阻断修复后的容错（delayed）
 * - delayedBlockFixDistance：阻断修复后的距离（delayed）
 * - delayedBlockFixBackwardSpeed：阻断修复后的向后速度（delayed）
 * - delayedBlockFixJumpSpeed：阻断修复后的起跳速度（delayed）
 * 
 * 跑跳技术结果（非delayed）：
 * - runJumpSpeed：跑1t的速度
 * - runJumpStartSpeed：起跳速度
 * - runJumpDistance：距离
 * - runJumpPB：容错
 * - runJumpType：跑跳类型（1, 2, 3）
 * 
 * 跑跳技术结果（delayed）：
 * - delayedRunJumpSpeed：跑1t的速度（delayed）
 * - delayedRunJumpStartSpeed：起跳速度（delayed）
 * - delayedRunJumpDistance：距离（delayed）
 * - delayedRunJumpPB：容错（delayed）
 * - delayedRunJumpType：跑跳类型（delayed，1, 2, 3）
 * 
 * Loop优化状态：
 * - infill：非delayed是否可以用满助跑
 * - defill：delayed是否可以用满助跑
 * - inspeed：连跳满助跑时的起跳速度
 * - landSpeed：最优落地速度
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

