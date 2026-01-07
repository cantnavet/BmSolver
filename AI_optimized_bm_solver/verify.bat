@echo off
echo ========================================
echo BmSolver 优化版本验证脚本
echo ========================================
echo.

echo [1/3] 编译原版...
javac -encoding UTF-8 ..\BmSolverAngles.java
if %errorlevel% neq 0 (
    echo 原版编译失败！
    pause
    exit /b 1
)
echo 原版编译成功
echo.

echo [2/3] 编译优化版...
javac -encoding UTF-8 *.java
if %errorlevel% neq 0 (
    echo 优化版编译失败！
    pause
    exit /b 1
)
echo 优化版编译成功
echo.

echo [3/3] 运行优化版测试...
java optimized_bm_solver.BmSolverOptimized
echo.

echo ========================================
echo 验证完成！
echo 请对比输出与原版是否一致
echo ========================================
pause

