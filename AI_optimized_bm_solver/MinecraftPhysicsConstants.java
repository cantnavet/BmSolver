/**
 * Minecraft物理常数定义
 * 
 * 这个类包含所有Minecraft运动相关的物理常数。
 * 
 * 摩擦系数：
 * - FRICTION_GROUND (0.54600006)：起跳时的摩擦系数
 *   - 用于：起跳tick的速度计算
 *   - 公式：v = v * 0.546 + 其他速度增量
 * 
 * - FRICTION_AIR (0.91)：空中的摩擦系数
 *   - 用于：空中tick的速度计算
 *   - 公式：v = v * 0.91 + 其他速度增量
 * 
 * 速度增量：
 * - JUMP_BOOST (0.2)：跳跃基础速度
 *   - 每次跳跃都会增加这个速度
 * 
 * - GROUND_MOVEMENT (0.12739998)：地面移动速度
 *   - 起跳tick时的额外速度增量
 * 
 * - AIR_MOVEMENT_45 (0.018384775)：45度加速时的移动增量
 *   - 用于：空中使用45度加速时的速度增量
 *   - 公式：v += 0.018384775 * (sin + cos)
 * 
 * - LANDING_MOVEMENT_45 (0.09192386)：落地时45度加速的移动增量
 *   - 用于：落地时使用45度加速时的速度增量
 *   - 公式：v += 0.09192386 * (sin + cos)
 * 
 * 移动阻断阈值：
 * - BLOCK_THRESHOLD_GROUND (0.009157508093840406)：地面阻断阈值
 *   - 计算：0.005 / 0.546
 *   - 当速度在±这个范围内时，会被重置为0
 * 
 * - BLOCK_THRESHOLD_AIR (0.005494505)：空中阻断阈值
 *   - 计算：0.005 / 0.91
 *   - 当速度在±这个范围内时，会被重置为0
 * 
 * 其他常数：
 * - BLOCK_SIZE (0.0625)：方块大小（1/16）
 *   - 用于：计算pb（容错），即距离与方块边界的差距
 * 
 * - MAX_FIX_SPEED (0.32739998400211334)：最大修复速度
 *   - 用于：Plan 4修复方案中，限制fixSpeed的最大值
 */
public class MinecraftPhysicsConstants {
    // 摩擦系数
    public static final float FRICTION_GROUND = 0.54600006f;  // 起跳时的摩擦系数
    public static final float FRICTION_AIR = 0.91f;            // 空中的摩擦系数
    
    // 速度增量
    public static final float JUMP_BOOST = 0.2f;                // 跳跃基础速度
    public static final float GROUND_MOVEMENT = 0.12739998f;    // 地面移动速度
    public static final float AIR_MOVEMENT_45 = 0.018384775f;   // 45度加速时的移动增量
    public static final float LANDING_MOVEMENT_45 = 0.09192386f; // 落地时45度加速的移动增量
    
    // 移动阻断阈值
    public static final double BLOCK_THRESHOLD_GROUND = 0.009157508093840406;  // 地面阻断阈值 = 0.005 / 0.546
    public static final double BLOCK_THRESHOLD_AIR = 0.005494505;               // 空中阻断阈值 = 0.005 / 0.91
    public static final float BLOCK_BASE = 0.005f;                             // 阻断基础值
    
    // 其他常数
    public static final float PLAYER_WIDTH_HALF = 0.6f;        // 玩家宽度的一半
    public static final float PLAYER_CENTER_OFFSET = 0.3f;     // 玩家中心偏移
    public static final double BLOCK_SIZE = 0.0625;            // 方块大小（1/16）
    public static final double MAX_FIX_SPEED = 0.32739998400211334; // 最大修复速度
    
    // 特殊标记值
    public static final double INVALID_BM = -114514.0;        // 无效bm标记
    public static final double INVALID_PB = 114514.0;         // 无效pb标记
    
    // 角度类型（默认45度）
    public static final float DEFAULT_SIN = 0.70710677f;
    public static final float DEFAULT_COS = 0.70710677f;
}

