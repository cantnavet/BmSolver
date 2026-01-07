/**
 * 测试类：验证优化版本与原版输出的一致性
 * 
 * 使用方法：
 * 1. 先运行原版 BmSolverAngles.java，保存输出
 * 2. 运行优化版 BmSolverOptimized.java，保存输出
 * 3. 使用此测试类对比输出
 */
public class BmSolverTest {
    
    /**
     * 测试用例
     */
    public static class TestCase {
        public final int angleType;
        public final int buildUpAirtime;
        public final int jumpAirtime;
        public final double buildUpLength;
        public final String description;
        
        public TestCase(int angleType, int buildUpAirtime, int jumpAirtime, double buildUpLength, String description) {
            this.angleType = angleType;
            this.buildUpAirtime = buildUpAirtime;
            this.jumpAirtime = jumpAirtime;
            this.buildUpLength = buildUpLength;
            this.description = description;
        }
    }
    
    /**
     * 测试用例列表
     */
    private static final TestCase[] TEST_CASES = {
        new TestCase(5, 12, 12, 15.4375, "基础测试"),
        // 可以添加更多测试用例
    };
    
    /**
     * 运行测试
     */
    public static void main(String[] args) {
        System.out.println("=== BmSolver 优化版本测试 ===\n");
        
        for (TestCase testCase : TEST_CASES) {
            System.out.println("测试: " + testCase.description);
            System.out.println("参数: angleType=" + testCase.angleType + 
                             ", buildUpAirtime=" + testCase.buildUpAirtime + 
                             ", jumpAirtime=" + testCase.jumpAirtime + 
                             ", buildUpLength=" + testCase.buildUpLength);
            
            // 运行优化版本
            BmSolverOptimized optimizedSolver = new BmSolverOptimized();
            optimizedSolver.setAngleType(testCase.angleType);
            optimizedSolver.solve(testCase.buildUpAirtime, testCase.jumpAirtime, testCase.buildUpLength);
            
            System.out.println("优化版结果:");
            System.out.println("  Distance: " + optimizedSolver.distance);
            System.out.println("  PB: " + optimizedSolver.pb);
            System.out.println("  JPB: " + optimizedSolver.jpb);
            System.out.println("  Loops: " + optimizedSolver.loops);
            System.out.println("  Deloops: " + optimizedSolver.deloops);
            System.out.println("  DelayedG: " + optimizedSolver.delayedG);
            System.out.println();
            
            // TODO: 与原版输出对比
            // 需要手动运行原版并对比输出
            System.out.println("请手动运行原版 BmSolverAngles.java 并对比输出");
            System.out.println("----------------------------------------\n");
        }
    }
    
    /**
     * 对比两个结果是否一致（允许小的浮点误差）
     */
    private static boolean compareResults(double value1, double value2, double epsilon) {
        return Math.abs(value1 - value2) < epsilon;
    }
}

