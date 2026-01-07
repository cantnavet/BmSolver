/**
 * Minecraft物理常数定义
 * 包含所有MC运动相关的物理常数
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

