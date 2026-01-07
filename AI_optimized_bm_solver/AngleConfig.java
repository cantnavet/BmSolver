/**
 * 角度配置类
 * 管理不同角度类型的sin和cos值
 */
public class AngleConfig {
    public float sin;
    public float cos;
    public double runEqualV0;  // 跑1t后速度不变的临界速度
    public double awRun;       // 落地时45度加速的速度增量
    
    public AngleConfig() {
        this.sin = MinecraftPhysicsConstants.DEFAULT_SIN;
        this.cos = MinecraftPhysicsConstants.DEFAULT_COS;
    }
    
    /**
     * 设置角度类型
     * @param type 1:原版45, 2:45.006, 3:原版小半角, 4:原版大半角, 5:其他特殊角度
     */
    public void setAngleType(int type) {
        switch (type) {
            case 1: // 原版45度
                sin = 0.70710677f;
                cos = 0.70710677f;
                break;
            case 2: // 45.006度（更优有效角）
                sin = 0.7071746f;
                cos = 0.707039f;
                break;
            case 3: // 原版小半角
                sin = 0.70710677f;
                cos = 0.7071746f;
                break;
            case 4: // 原版大半角
                sin = 0.70710677f;
                cos = 0.7114322f;
                break;
            case 5: // 其他特殊角度
                sin = 0.8310432f;
                cos = 0.70764905f;
                break;
            default:
                break;
        }
        calculateRunParameters();
    }
    
    /**
     * 计算跑跳相关参数
     */
    private void calculateRunParameters() {
        // 计算落地时45度加速的速度增量
        awRun = (float)((float)(MinecraftPhysicsConstants.LANDING_MOVEMENT_45) * (float)(sin) + 
                        (float)(MinecraftPhysicsConstants.LANDING_MOVEMENT_45) * (float)(cos));
        
        // 计算前置速度
        double fv0 = -1 * (float)(MinecraftPhysicsConstants.FRICTION_GROUND) + awRun;
        double sv0 = 1 * (float)(MinecraftPhysicsConstants.FRICTION_GROUND) + awRun;
        
        // 初始估计RunEqualv0
        runEqualV0 = 2 * (-fv0 + 1) / (sv0 - fv0 + 2) - 1;
        
        // 迭代20次精确计算RunEqualv0（使跑1t后速度不变）
        for (int i = 0; i < 20; i++) {
            double nextV0 = runEqualV0 * (float)(MinecraftPhysicsConstants.FRICTION_GROUND) + awRun;
            runEqualV0 = (runEqualV0 - nextV0) / 2;
        }
    }
}

