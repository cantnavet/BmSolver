import java.util.Arrays;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class BmSolverA2 {
    public static double coord2;
    public static double bm;
    public static int loops;   
    public static int deloops; 
    public static int jloops;  
    public static int[] ti;

    public static double pb;
    public static double jpb;  
    public static double distance;
    public static double finv0;
    public static int inPlace;
    public static int inFix;
    public static int fixPlan;
    public static int planSteps;
    public static double fixSpeed;
    public static double maxFixSpeed;
    public static double landSpeed;
    public static int djsplan;
    public static int jsplan;
    
    public static double tempBM;
    public static double tempV0;
    public static double injs = 0;
    public static double starts0;
    public static boolean dne = false;

    public static boolean infill=false;
    public static boolean defill=false;
    public static double inspeed=0;

    //bwmm 移动阻断 part
    public static int bwmmPlan = -1;
    public static int rbwmmPlan = -1;
    public static double sinDPB=-1;
    public static double sinDd0;
    public static double sinDs0;
    public static double sinDjs0;

    public static double sinPB=-1;
    public static double sind0;
    public static double sins0;
    public static double sinjs0;

    //run1t part
    public static double RunEqualv0 = -0.084087943141335;
    public static double AWRun;
    public static double rs0;
    public static double rjs0;
    public static double rd0;
    public static double rpb;
    public static int runType = 0;

    public static double rds0;
    public static double rdjs0;
    public static double rdd0;
    public static double rdpb;
    public static int rrunType = 0;

    //默认45度整
    public static float sin = 0.70710677f;
    public static float cos = 0.70710677f;

    private static JTextArea resultArea;

    public static void main(String[] args) {

        JFrame frame = new JFrame("跳跃计算器");
        frame.setLayout(new BorderLayout(10, 10));

        // 输入面板
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        
        JLabel typeLabel = new JLabel("角度类型 (1:45 2:45.006 3:小半角 4:大半角):");
        JTextField typeField = new JTextField();
        inputPanel.add(typeLabel);
        inputPanel.add(typeField);

        JLabel runLabel = new JLabel("助跑滞空时间 (gt):");
        JTextField runField = new JTextField();
        inputPanel.add(runLabel);
        inputPanel.add(runField);

        JLabel jumpLabel = new JLabel("跳跃滞空时间 (gt):");
        JTextField jumpField = new JTextField();
        inputPanel.add(jumpLabel);
        inputPanel.add(jumpField);

        JLabel lengthLabel = new JLabel("助跑长度 (block):");
        JTextField lengthField = new JTextField();
        inputPanel.add(lengthLabel);
        inputPanel.add(lengthField);

        // 结果面板
        resultArea = new JTextArea(6, 30);
        resultArea.setEditable(false);
        JScrollPane resultScroll = new JScrollPane(resultArea);

        // 操作按钮
        JButton calcButton = new JButton("计算");
        calcButton.addActionListener(e -> {
            try {
                int type = Integer.parseInt(typeField.getText());
                int runTime = Integer.parseInt(runField.getText());
                int jumpTime = Integer.parseInt(jumpField.getText());
                double length = Double.parseDouble(lengthField.getText());

                sinMaker(type);
                String[] results = single(runTime, jumpTime, length);
                
                resultArea.setText("");
                for (String result : results) {
                    resultArea.append(result + "\n");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "输入格式错误", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 组装界面
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(resultScroll, BorderLayout.CENTER);
        frame.add(calcButton, BorderLayout.SOUTH);

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        // //1:原版45，2:45.006，3:原版小半角，4:原版大半角
        // sinMaker(2);

        // //输入格式：助跑滞空时间(gt)，跳跃滞空时间(gt)，助跑长度(block)
        // single(12,6,0.375);

        // //finaljump(delayedJumps(bm, -0.429326), false);
        // ////System.out.println("distance "+distance+" pb "+pb);

    }

    public static void sinMaker(int type){
        switch (type) {
            case 1:
                sin = 0.70710677f;
                cos = 0.70710677f;
                break;
            case 2:
                sin = 0.7071746f;
                cos = 0.707039f;
                break;
            case 3:
                sin = 0.70710677f;
                cos = 0.7071746f;
                break;
            case 4:
                sin = 0.70710677f;
                cos = 0.7114322f;
                break;
            default:
                break;
        }
        //前置速度计算
        AWRun = (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        double fv0 = -1 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        double sv0 = 1 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        RunEqualv0 = 2*(-fv0+1)/(sv0-fv0+2)-1;
        for (int i=0; i<20; i++)
            RunEqualv0 = (RunEqualv0 - (RunEqualv0* (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos))))/2;
    }

    public static String[] single(int bmt, int jt, double bms) {

        int bmTick = bmt;    //助跑上的airtime
        int jumpTick = jt;   //跳跃的airtime
        bm = bms;            //bm   

         coord2 = 0.0;
        loops = 0;    
        deloops = 0;  
        jloops = 0;   
        ti = null;
    
        pb = 0.0;
        jpb = 0.0;   
        distance = 0.0;
        finv0 = 0.0;
        inPlace = 0;
        inFix = 0;
        fixPlan = 0;
        planSteps = 0;
        fixSpeed = 0.0;
        maxFixSpeed = 0.0;
        landSpeed = 0.0;
        
        tempBM = 0.0;
        tempV0 = 0.0;
        injs = 0.0;
        starts0 = 0.0;
        dne = false;
    
        infill = false;
        defill = false;
        inspeed = 0.0;
    
        //bwmm 移动阻断 part
        bwmmPlan = -1; // 保持初始值
        rbwmmPlan = -1;// 保持初始值
        sinDPB = -1;   // 保持初始值
        sinDd0 = 0.0;
        sinDs0 = 0.0;
        sinDjs0 = 0.0;
    
        sinPB = -1;    // 保持初始值
        sind0 = 0.0;
        sins0 = 0.0;
        sinjs0 = 0.0;
    
        //run1t part
        rs0 = 0.0;
        rjs0 = 0.0;
        rd0 = 0.0;
        rpb = 0.0;
        runType = 0;
    
        rds0 = 0.0;
        rdjs0 = 0.0;
        rdd0 = 0.0;
        rdpb = 0.0;
        rrunType = 0;
        
        starts0 = 0;        //初始速度，正常不需要填
        coord2 = 0;         //起始坐标，需要高精度时填
        fixPlan = 0;
        deloops = 0;
        loops = -1;
        jpb = 114514;

        for (int i=2; i<100000; i++){
            int[] t = new int[i];
            Arrays.fill(t, bmTick);
            t[i-1]=jumpTick;
            ti=t;

            //bwmm 移动阻断处理区
            double bmf = bmfind(bm, false);
            double bmfd = bmfind(bm, true);
            if (bmf<0 && bmf > -0.009157508093840406) {
                double maxs0;
                double maxjs0;

                fixPlan = 4;
                fixSpeed = 0;
                double fbm = jump1(0, false);
                fixSpeed = 1;
                double sbm = jump1(0, false);
                fixSpeed=(bm+0.009157508093840406-fbm)/(sbm-fbm);
                fixSpeed = Math.min(fixSpeed, 0.32739998400211334);
                jump1(0, false);
                maxs0=bmf;
                maxjs0=tempV0;

                fixPlan = 5;
                jump1(Math.min(-0.009157508093840406,bmf), false);
                //System.out.println("p1: "+maxjs0+" p2: "+tempV0);
                if (tempV0>maxjs0) {
                    bwmmPlan = 2;
                    sins0 = -0.009157508093840406;
                    sinjs0 = tempV0;
                }else{
                    bwmmPlan = 1;
                    sins0 = maxs0;
                    sinjs0 = maxjs0;
                }

                fixPlan = 0;
                finaljump(sinjs0, false);
                sinPB = pb;
                sind0 = distance;
             
            }
            if (bmfd<0 && bmfd > -0.009157508093840406) {
                double maxs0;
                double maxjs0;

                fixPlan = 4;
                fixSpeed = 0;
                double fbm = jump1(0, true);
                fixSpeed = 1;
                double sbm = jump1(0, true);
                fixSpeed=(bm+0.009157508093840406-fbm)/(sbm-fbm);
                fixSpeed = Math.min(fixSpeed, 0.32739998400211334);
                jump1(0, true);
                maxs0=bmfd;
                maxjs0=tempV0;

                fixPlan = 5;
                jump1(Math.min(-0.009157508093840406,bmfd), true);
                if (tempV0>maxjs0) {
                    rbwmmPlan = 2;
                    sinDs0 = -0.009157508093840406;
                    sinDjs0 = tempV0;
                }else{
                    rbwmmPlan = 1;
                    sinDs0 = maxs0;
                    sinDjs0 = maxjs0;
                }

                fixPlan = 0;
                finaljump(sinDjs0, true);
                sinDPB = pb;
                sinDd0 = distance;
            }
            if (bmf<=0){
                break;
            }else{
                if (bmfd<=0) {
                    dne = true;
                }
        
                if(0.1759+awRunJump(0.1759, false)>bm){
                    if (awRunJump(-RunEqualv0, false)>=bm) {
                        double tv0 = 0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double fbm = awRunJump(tv0, false);
                        tv0 = -1;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double sbm = awRunJump(tv0, false);
                        rs0=((bm-sbm)/(fbm-sbm))-1;
                        awRunJump(rs0* (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos)), false);
                        rjs0=tempV0;
    
                        finaljump(rjs0, false);
                        rpb = pb;
                        rd0 = distance;
                        runType = 1;
                        //System.out.println("run type1: "+rd0+" pb "+rpb +" s0 "+rs0);
                    }else if (AWRun+awRunJump(AWRun, false)>=bm){
                        double tv0 = 0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double fbm = tv0+awRunJump(tv0, false);
                        tv0 = RunEqualv0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double sbm = awRunJump(tv0, false);
                        rs0=-RunEqualv0*((bm-sbm)/(fbm-sbm))+RunEqualv0;
                        if (rs0<-0.009157508093840406) {
                            awRunJump(rs0* (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos)), false);
                            rjs0=tempV0;
                        }else{
                            fbm = awRunJump(0, false)-0.009157508093840406;
                            sbm = awRunJump(1, false)-0.009157508093840406+1;
                            tv0 = (bm-fbm)/(sbm-fbm);
                            awRunJump(tv0, false);
                            rjs0=tempV0;
                            //System.out.println(tv0);
                        }                        
                        finaljump(rjs0, false);
                        rpb = pb;
                        rd0 = distance;
                        runType = 2;
                        //System.out.println("run type2: "+rd0+" pb "+rpb +" s0 "+rs0);
                    }else{
                        double fbm = awRunJump(0, false);
                        double sbm = 1+awRunJump(1, false);
                        double rs0 = (bm-fbm)/(sbm-fbm);
                        awRunJump(rs0, false);
                        rjs0=tempV0;
    
                        finaljump(rjs0, false);
                        rpb = pb;
                        rd0 = distance;
                        runType = 3;
                        //System.out.println("run type3: "+rd0+" pb "+rpb +" s0 "+rs0+" rjs0: "+rjs0);
                    }
                }

                if(bmfd > 0 && 0.1759+awRunJump(0.1759, true)>bm){
                    if (awRunJump(-RunEqualv0, true)>=bm) {
                        double tv0 = 0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double fbm = awRunJump(tv0, true);
                        tv0 = -1;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double sbm = awRunJump(tv0, true);
                        rds0=((bm-sbm)/(fbm-sbm))-1;
                        awRunJump(rds0* (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos)), true);
                        rdjs0=tempV0;
    
                        finaljump(rdjs0, true);
                        rdpb = pb;
                        rdd0 = distance;
                        rrunType = 1;
                        //System.out.println("derun type1: "+rdd0+" pb "+rdpb +" s0 "+rds0);
                    }else if (AWRun+awRunJump(AWRun, true)>=bm){
                        double tv0 = 0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double fbm = tv0+awRunJump(tv0, true);
                        tv0 = RunEqualv0;
                        tv0 = tv0 * (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
                        double sbm = awRunJump(tv0, true);
                        rds0=-RunEqualv0*((bm-sbm)/(fbm-sbm))+RunEqualv0;
                        if (rds0<-0.009157508093840406) {
                            awRunJump(rds0* (float)(0.54600006) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos)), true);
                            rdjs0=tempV0;
                        }else{
                            fbm = awRunJump(0, true)-0.009157508093840406;
                            sbm = awRunJump(1, true)-0.009157508093840406+1;
                            tv0 = (bm-fbm)/(sbm-fbm);
                            awRunJump(tv0, true);
                            rdjs0=tempV0;
                            //System.out.println(tv0);
                        }                        
                        finaljump(rdjs0, true);
                        rdpb = pb;
                        rdd0 = distance;
                        rrunType = 2;
                        //System.out.println("derun type2: "+rdd0+" pb "+rdpb +" s0 "+rds0);
                    }else{
                        double fbm = awRunJump(0, true);
                        double sbm = 1+awRunJump(1, true);
                        double rds0 = (bm-fbm)/(sbm-fbm);
                        awRunJump(rds0, true);
                        rdjs0=tempV0;
    
                        finaljump(rdjs0, true);
                        rdpb = pb;
                        rdd0 = distance;
                        rrunType = 3;
                        //System.out.println("derun type3: "+rdd0+" pb "+rdpb +" s0 "+rds0+" rdjs0: "+rdjs0);
                    }
                }
            }
        }

        //loop区域 
        double BwSpeed=bmfind(bm,false);
        double DeBwSpeed=bmfind(bm,true);
        //System.out.println("d "+BwSpeed);
        double s0 = starts0;
        double pres0 = 0;
        double bm2 = 0;
        double prebm2 = 0;
        double jd = 0;
        double justJump = 0;
        double landSpeed = 0;
        double nDs0 = 0;
        double prenDs0 = 0;
        boolean delayedG = true;
        boolean delayedG2 = true;
        double fillBmDistance = 0;
        while (true) {
            
            //检测loop时的delayed状态下，当前bw速度是否能够做到完整助跑
            if (backSpeedToFront(s0,true)>=bm) {
                //System.out.println("Delayed时已经用满助跑，已获得最大bwmm速度，无需再loop");
                double t1 = backToFrontUnit(-1,true);
                double t2 = backToFrontUnit(1,true);
                ////System.out.println("full mm s0 "+s0+" bs0 "+backSpeedToFront(s0,true));
                double tLandSpeed = 2*((bm-t1)/(t2-t1))-1;
                backToFrontUnit(tLandSpeed,true);
                s0 = -tempV0;
                tempBM = bm;
            }else{
                s0 = -delayedDelayJumps(bm, s0);
            }

            //System.out.println("s0 "+s0);
            bm2 = tempBM;
            nDs0 = delayedJumps(bm2, s0);

            if (prenDs0 < nDs0) {
                loops = deloops;
            }

            ////System.out.println(DeBwSpeed);
            if (justJump==0) {
                double tempV= s0>BwSpeed?s0:BwSpeed;
                ////System.out.println("------------");
                tempV = delayedJumps(bm,tempV);
                ////System.out.println("------------");
                finaljump(tempV,false);
                ////System.out.println(jd+" "+pb);
                if (distance-pb>jd) {
                    jd = distance-pb;
                    jloops = deloops;
                    jpb = pb;
                    ////System.out.println(jd+" "+pb);
                }
                tempV = s0>DeBwSpeed?s0:DeBwSpeed;
                finaljump(-tempV,true);
                if (distance-pb>jd) {
                    jd = distance-pb;
                    jloops = deloops;
                    jpb = pb;
                }
            }
            
            //System.out.println("s0: "+s0+" sbm: "+backSpeedToFront(s0,false));
            ////System.out.println("s0: "+-0.01+" sbm: "+backSpeedToFront(-0.01,false));
            if (backSpeedToFront(s0,false)>=bm) {
                double t1 = backToFrontUnit(-1,false);
                double t2 = backToFrontUnit(1,false);
                ////System.out.println("s0 "+s0+" bs0 "+backSpeedToFront(s0,false));
                double tLandSpeed = 2*((bm-t1)/(t2-t1))-1;
                backToFrontUnit(tLandSpeed,false);
                finaljump(tempV0, false);
                infill = true;
                inspeed = tempV0;
                if (fillBmDistance<distance) {
                    fillBmDistance = distance;
                    justJump = tempV0;
                    delayedG2 = false;
                    landSpeed = tLandSpeed;
                }
            }

            if (backSpeedToFront(s0,true)>=bm) {
                double t1 = backToFrontUnit(-1,true);
                double t2 = backToFrontUnit(1,true);
                ////System.out.println("full mm s0 "+s0+" bs0 "+backSpeedToFront(s0,true));
                double tLandSpeed = 2*((bm-t1)/(t2-t1))-1;
                backToFrontUnit(tLandSpeed,true);

                finaljump(tempV0, true);
                defill = true;
                if (fillBmDistance<distance) {
                    fillBmDistance = distance;
                    justJump = tempV0;
                    delayedG2 = true;
                    landSpeed = tLandSpeed;
                }
            }
            
            if ((pres0 == s0 && prebm2 == bm2)||deloops>100) {
                break;
            }
            
            deloops++;
            pres0 = s0;
            prebm2 = bm2;
            prenDs0 = nDs0;
        }
        if (loops<0) {
            loops = deloops;
        }
        double finalv0 = 0;
        double d1 = 0;
        double prepb = -1;
        double d12 = 0;
        double prepb2 = -1;
        if (!infill) {          
            double ts0 = s0;
            s0 = s0>BwSpeed?s0:BwSpeed;
            //System.out.println("bm "+bm+" s0 "+s0);
            s0 = delayedJumps(bm,s0);
            finalv0 = s0;
            finaljump(s0,false);
            
            d1 = distance;
            prepb = pb;
            if (sinPB>-1) {
                d1=sind0;
                prepb = sinPB;
                finalv0 = sinjs0;
                loops = 0;
            }
            //System.out.println(d1+" instant jump");
            s0 = ts0;
        }else{
            finalv0 = inspeed;
        }
        if (!defill) {
            double ts0 = s0;
            s0 = s0>DeBwSpeed?s0:DeBwSpeed;
            s0 = delayedDelayJumps(bm,s0);
            finaljump(s0,true);

            d12 = distance;
            prepb2 = pb;
            if (sinDPB>-1) {
                d12 = sinDd0;
                prepb2 = sinDPB;
                deloops = 0;
            }

            //System.out.println(d12+" delayed jump");
            s0 = ts0;
        }   

        // 如果d1比d12难时触发(x
        // 其实是判断起跳时跑1t是否有优势的其中一部分
        if (d1>d12) {
            delayedG = false;
            if (s0<BwSpeed) {
                jpb = prepb;
            }
        }else{
            d1 = d12;
            prepb = prepb2;
            delayedG = true;
            if (s0<DeBwSpeed) {
                jpb = prepb;
            }
        }
        
        // 在可以让数次全速连跳完美铺满助跑时触发
        if(justJump != 0){
            ////System.out.println("is j");
            finaljump(justJump, delayedG2);
            if (d1<distance || delayedG == delayedG2) {
                jpb = pb;
                delayedG = delayedG2;
                // //System.out.println("Speed included all mm");
            }else{
                distance = d1;
                pb = prepb;
                justJump = 0;
            }
        }else{
            distance = d1;
            pb = prepb;
        }
        String rOrJ;
        if (justJump == 0) {
            rOrJ = "推荐使用后跳，凑出 "+(s0>(delayedG?DeBwSpeed:BwSpeed)?s0:(delayedG?DeBwSpeed:BwSpeed))+" 的向后速度";
        }else{
            rOrJ = "推荐使用后跳，连跳能用满助跑，在连跳开始时，凑出 "+landSpeed+" 的落地速度";
        }
        String Oplan = "";

        int tempplan = (delayedG?djsplan:jsplan);
        switch (tempplan) {
            case 1:
                Oplan = "注意移动阻断，碰到移动阻断时请刚好慢于它";
                break;
            case 2:
                Oplan = "注意移动阻断，碰到移动阻断时请刚好在它的下限，通过阻断时可能需减速以不超出助跑";
                break;
            case 3:
                Oplan = "注意移动阻断，碰到移动阻断时请刚好快于它，通过阻断时需减速以不超出助跑";
                break;
            default:
                break;
        }


        // System.out.println(s0);
        // System.out.println((delayedG?DeBwSpeed:BwSpeed));
        int rplans = 0;
        // 比较后跳与跑跳之间的距离
        if (distance < rd0 || distance < rdd0) {
            // //System.out.println(rd0+" instant jump");
            // //System.out.println(rdd0+" delayed jump");
            if (rd0>rdd0) {
                distance = rd0;
                pb = rpb;
                jpb = rpb;
                loops = 0;
                delayedG = false;
                landSpeed = rs0;
                finalv0 = rjs0;
                rplans = runType;
            }else{
                distance = rdd0;
                pb = rdpb;
                jpb = rdpb;
                deloops = 0;
                delayedG = true;
                landSpeed = rds0;
                finalv0 = rdjs0;
                rplans = rrunType;
            }
            rOrJ = "推荐使用跑跳";
            Oplan = "";
        }
        
        String rr = delayedG?"起跳时跑1t":"起跳时无需跑1t";
        String[] res = {"容错: "+pb,"跳跃距离: "+distance,rOrJ,rr,Oplan};
        


        return res;
    }

    public static double delayedJumps(double bmGoal, double s0) {
        double fbm = delayedJumpJumps(s0,-1,false);
        double sbm = delayedJumpJumps(s0,1,false);
        double finJs = 2*((bm-fbm)/(sbm-fbm))-1;
        delayedJumpJumps(s0, finJs,false);

        double js = finJs;
        int plan = 0;
        if (inPlace>0) {
            double maxV0 = 0;
            inFix = inPlace;
            planSteps = 0;

            //plan 1
            fixPlan=1;
            fbm = delayedJumpJumps(s0,-1,false);
            sbm = delayedJumpJumps(s0,1,false);
            finJs = 2*((-(float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=-0.009157508093840406;
            }
            inFix = 0;
            delayedJumpJumps(s0,finJs,false);
            maxV0 = tempV0;
            js = finJs;
            plan = 1;
            inFix = inPlace;
             //System.out.println("p1v0 "+tempV0);


            //plan 2 (这里可以直接照抄plan1的finJS，修改fixplan=2时的判定即可)
            fixPlan=2;
            maxFixSpeed = delayedJumpJumps(s0,finJs,false);
            planSteps=1;
            delayedJumpJumps(s0,finJs,false);
            fbm = tempBM;
            ////System.out.println("fsp: "+maxFixSpeed);
            ////System.out.println("tbm: "+tempBM);
            fixSpeed = -1;
            planSteps=2;
            delayedJumpJumps(s0,finJs,false);
            sbm = tempBM;
            fixSpeed = (maxFixSpeed+1)*((bm-sbm)/(fbm-sbm))-1;
            // //System.out.println("fspeed "+fixSpeed);
            fixSpeed = Math.min(fixSpeed, maxFixSpeed);
            delayedJumpJumps(s0,finJs,false);
            // //System.out.println("bm: "+tempBM);
            if(maxV0<tempV0){
                maxV0 = tempV0;
                js = finJs;
                plan = 2;
            }
             //System.out.println("p2v0 "+tempV0 +"  fixsp "+fixSpeed);
            planSteps=0;


            //plan 3
            fixPlan=3;
            fbm = delayedJumpJumps(s0,-1,false);
            sbm = delayedJumpJumps(s0,1,false);
            finJs = 2*(((float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=0.009157508093840406;
            }
            fixPlan=2; //借用p2的功能
                planSteps=1;
                delayedJumpJumps(s0,finJs,false);
                fbm = tempBM;
                fixSpeed = -1;
                planSteps=2;
                delayedJumpJumps(s0,finJs,false);
                sbm = tempBM;
                fixSpeed = (maxFixSpeed+1)*((bm-sbm)/(fbm-sbm))-1;
                delayedJumpJumps(s0,finJs,false);
                //  //System.out.println("fsp: "+fixSpeed);
                //  //System.out.println("p3bm: "+tempBM);
                 //System.out.println("p3v0 "+tempV0+"  fixsp "+fixSpeed);
            if (s0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998>=finJs) {
                if(maxV0<tempV0){
                    maxV0 = tempV0;
                    js = finJs;
                    plan = 3;
                }
            }

            inPlace=0;
            fixPlan=0;
            planSteps=0;
            tempV0 = maxV0;

            
            
        }

        //System.out.println(tempBM +" finbm  "+tempV0+" tempv0 "+js+" js" + " plan: "+ plan);
        jsplan = plan;
        return tempV0;
    }


    //input bm, sn, output sn+1
    public static double delayedDelayJumps(double bmGoal, double s0) {
        double fbm = delayedJumpJumps(s0,-1,true);
        double sbm = delayedJumpJumps(s0,1,true);
        double finJs = 2*((bm-fbm)/(sbm-fbm))-1;
        delayedJumpJumps(s0,finJs,true);

        double js = finJs;
        int plan = 0;
        if (inPlace>0) {
            double maxV0 = 0;
            inFix = inPlace;
            planSteps = 0;

            //plan 1
            fixPlan=1;
            fbm = delayedJumpJumps(s0,-1,true);
            sbm = delayedJumpJumps(s0,1,true);
            finJs = 2*((-(float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=-0.009157508093840406;
            }
            inFix = 0;
            delayedJumpJumps(s0,finJs,true);
            maxV0 = tempV0;
            js = finJs;
            plan = 1;
            inFix = inPlace;
             //System.out.println("rp1v0 "+tempV0);


            //plan 2 (这里可以直接照抄plan1的finJS，修改fixplan=2时的判定即可)
            fixPlan=2;
            maxFixSpeed = delayedJumpJumps(s0,finJs,true);
            planSteps=1;
            delayedJumpJumps(s0,finJs,true);
            fbm = tempBM;
            ////System.out.println("fsp: "+maxFixSpeed);
            ////System.out.println("tbm: "+tempBM/bm);
            fixSpeed = -1;
            planSteps=2;
            delayedJumpJumps(s0,finJs,true);
            sbm = tempBM;
            fixSpeed = (maxFixSpeed+1)*((bm-sbm)/(fbm-sbm))-1;
            // //System.out.println("fspeed "+fixSpeed);
            fixSpeed = Math.min(fixSpeed, maxFixSpeed);
            delayedJumpJumps(s0,finJs,true);
            // //System.out.println("bm: "+tempBM);
            if(maxV0<tempV0){
                maxV0 = tempV0;
                js = finJs;
                plan = 2;
            }
             //System.out.println("rp2v0 "+tempV0);
            planSteps=0;


            //plan 3
            fixPlan=3;
            fbm = delayedJumpJumps(s0,-1,true);
            sbm = delayedJumpJumps(s0,1,true);
            finJs = 2*(((float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=0.009157508093840406;
            }
            fixPlan=2; //借用p2的功能
                planSteps=1;
                delayedJumpJumps(s0,finJs,true);
                fbm = tempBM;
                fixSpeed = -1;
                planSteps=2;
                delayedJumpJumps(s0,finJs,true);
                sbm = tempBM;
                fixSpeed = (maxFixSpeed+1)*((bm-sbm)/(fbm-sbm))-1;
                delayedJumpJumps(s0,finJs,true);
                //  //System.out.println("fsp: "+fixSpeed);
                //  //System.out.println("p3bm: "+tempBM);
                 //System.out.println("rp3v0: "+tempV0);
            if (s0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998>=finJs) {
                if(maxV0<tempV0){
                    maxV0 = tempV0;
                    js = finJs;
                    plan = 3;
                }
            }

            inPlace=0;
            fixPlan=0;
            planSteps=0;
            tempV0 = maxV0;

            
            
        }
        //System.out.println("now s0: "+s0);
        //System.out.println("run: "+tempBM +" finbm  "+tempV0+" tempv0 "+js+" js" + " plan: "+ plan);
        djsplan = plan;
        return tempV0;
    }

    //input s0, jumpspeed, output bm
    public static double delayedJumpJumps(double s0, double jSpeed, boolean finDelay) {
        if (fixPlan==0) {
            inPlace = 0;
        }
        double v0 = s0;
        double bm = coord2+s0;
        v0 = jSpeed;
        bm+=v0;

        //FIX p1
        
        if (jSpeed>-0.009157508093840406 && jSpeed<0.009157508093840406 && inPlace==0){  //bwmm移动阻断
            inPlace = 1;
        }
        if (inFix==1 && fixPlan==2){
            v0=0;
        }
        if (fixPlan==1 && inFix==1) {
            return v0;
        }
        if (fixPlan==3 && planSteps==0 && inFix==1) {
            return v0;
        }
        
        int starti = 0;
        if (finDelay && dne) {
            starti = 1;
        }
        for (int i = starti; i < ti.length - 1; i++) {
            if (i>starti){
                //normal jump tick
                v0 = v0 * (float) 0.91 + (float) 0.2 + (float) 0.12739998; 
                bm+=v0;
            }
            //first airtime 45

            if (fixPlan==2 && planSteps==2 &&inFix==1 && i==0) {
                v0=fixSpeed;
            }else{
                if (ti[i]>=2) {
                    v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                }
            }
            if (fixPlan==2 && planSteps==0 &&inFix==1 && i==0) {
                return v0;
            }
            
            
            bm+=v0;
            for (int l = 0; l < ti[i] - 2; l++) {
                //airtime 45
                //FIX p3
                if (v0>-(float)(0.005494505) & v0<(float)0.005494505&& inPlace==0){  //bwmm移动阻断
                    inPlace = 2+l;
                }
                if (inFix==2+l && fixPlan==2 && i==0){
                    v0=0;
                }
                if (fixPlan==1 && inFix==2+l && i==0) {
                    return v0;
                }
                if (fixPlan==3 && planSteps==0 && inFix==2+l && i==0) {
                    return v0;
                }
                if (fixPlan==2 && planSteps==0 && inFix==l+1 && i==0) {
                    return v0;
                }
                if (fixPlan==2 && planSteps==2 && inFix==l+2 && i==0) {
                    v0=fixSpeed;
                }else{
                    v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                }
                
                ////System.out.println("running: "+v0);
                bm+=v0;
            }

        }
        if (finDelay) {
            v0 = v0*(float)(0.91) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
            // if (v0>-0.009157508093840406 & v0<0){  //bwmm移动阻断
            //     v0=0;
            // }
        }else{
            bm-=v0;
        }
        tempV0 = v0;
        tempBM = bm-coord2-(float)0.6;
        if (jSpeed == 1 || jSpeed == -1) {
            return bm-coord2-(float)0.6;
        }
        return v0;
    }

    public static void finaljump(double v0, boolean delayed){
        
        double d0 = coord2+(float)0.3;
        d0+=v0;
        if (delayed) {
            v0 = v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
        }else{
            v0 = v0 * (float) 0.91 + (float) 0.2 + (float) 0.12739998;
        }
        
        d0 += v0;
        int i = ti.length - 1;

        if (ti[i]>2) {
            v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
            d0 += v0;
        }

        for (int l = 0; l < ti[i] - 3; l++) {
            v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
            d0 += v0;
        }


        finv0 = v0;
        distance =d0- ((double)coord2- (float)0.3) -Math.ulp(d0);

        pb = distance - 0.0625 * ((int) (distance / 0.0625));
    }



    public static double bmfind(double bm,boolean d) {
        double bm0=jump1(0,d);
        double bm1=jump1(-1,d);
        jump1(-(bm0-bm)/(bm0-bm1), d);
        return -(bm0-bm)/(bm0-bm1);
    }

    //input delayed speed, use ti+speed to output bm 
    public static double jump1(double s0, boolean delayed) {
        double v0 = s0;
        double bm = coord2+s0;
        
        if (fixPlan==4) {
            v0 = fixSpeed;
        }else{
            v0 = v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
        }
        
        bm+=v0;
        int starti = 0;
        if (delayed && dne) {
            starti = 1;
        }
        for (int i = starti; i < ti.length - 1; i++) {
            if (i>starti){
                //normal jump tick
                v0 = v0 * (float) 0.91 + (float) 0.2 + (float) 0.12739998; 
                
                bm+=v0;
            }

            //first airtime 45
            if (ti[i]>=2) {
                v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                
                bm+=v0;
            }
            for (int l = 0; l < ti[i] - 2; l++) {
                //airtime 45
                v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                
                bm+=v0;
            }
            
        }
        if (!delayed) {
            bm-=v0; 
        }else{
            v0 = v0*(float)(0.91) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        }
        
        tempV0 = v0;
        ////System.out.println("bms "+(bm-coord2-(float)0.6));
        return bm-coord2-(float)0.6;
    }

    public static double backSpeedToFront(double v0, boolean delayed){
        if (ti.length<=2) {
            return -114514;
        }
        double t1=endMStart(v0,-1);
        double t2=endMStart(v0,1);
        double finJs = 2*((-t1)/(t2-t1))-1;
        if (finJs>v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998) {
            finJs = 0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
            t1 = endMStart(0, finJs);
            finJs = -2 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
            t2 = endMStart(-2, finJs);
            v0 = -2*((-t1)/(t2-t1));
            finJs = v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
            //确保精度措施，去掉后起始精度会下降至e-9
            for (int i = 0; i < 33; i++) {
                t1 = endMStart(0, finJs);
                t2 = endMStart(-2, finJs);
                v0 = -2*((-t1)/(t2-t1));
                finJs = v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
            }
        }
        endMStart(v0, finJs);
        if (inPlace>0) {
            double maxV0 = 0;
            inFix = inPlace;
            planSteps = 0;

            //plan 1
            fixPlan=1;
            double fbm = endMStart(v0,-1);
            double sbm = endMStart(v0,1);
            finJs = 2*((-(float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=-0.009157508093840406;
            }
            inFix = 0;
            endMStart(v0,finJs);
            maxV0 = tempV0;
            inFix = inPlace;
            // //System.out.println("p1v0 "+tempV0);


            //plan 2 (这里可以直接照抄plan1的finJS，修改fixplan=2时的判定即可)
            fixPlan=2;
            maxFixSpeed = endMStart(v0,finJs);
            planSteps=1;
            endMStart(v0,finJs);
            fbm = tempBM;
            ////System.out.println("fsp: "+maxFixSpeed);
            ////System.out.println("tbm: "+tempBM/bm);
            fixSpeed = -1;
            planSteps=2;
            endMStart(v0,finJs);
            sbm = tempBM;
            fixSpeed = (maxFixSpeed+1)*((-sbm)/(fbm-sbm))-1;
            // //System.out.println("fspeed "+fixSpeed);
            fixSpeed = Math.min(fixSpeed, maxFixSpeed);
            endMStart(v0,finJs);
            // //System.out.println("bm: "+tempBM);
            maxV0 = Math.max(maxV0, tempV0);
            // //System.out.println("p2v0 "+tempV0);
            planSteps=0;


            //plan 3
            fixPlan=3;
            fbm = endMStart(v0,-1);
            sbm = endMStart(v0,1);
            finJs = 2*(((float)0.005494505-fbm)/(sbm-fbm))-1;
            if (inPlace==1) {
                finJs=0.009157508093840406;
            }
            if (v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998>finJs) {
                fixPlan=2; //借用p2的功能
                planSteps=1;
                endMStart(v0,finJs);
                fbm = tempBM;
                fixSpeed = -1;
                planSteps=2;
                endMStart(v0,finJs);
                sbm = tempBM;
                fixSpeed = (maxFixSpeed+1)*((-sbm)/(fbm-sbm))-1;
                endMStart(v0,finJs);
                //  //System.out.println("fsp: "+fixSpeed);
                //  //System.out.println("p3bm: "+tempBM);
                // //System.out.println("p3v0: "+tempV0);
                if (v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998>=finJs) {
                    maxV0 = Math.max(maxV0, tempV0);
                }
            }
            

            inPlace=0;
            fixPlan=0;
            planSteps=0;
            tempV0 = maxV0;
        }
        ////System.out.println("tv0: "+tempV0);
        return backToFrontUnit(tempV0,delayed);
    }

    public static double endMStart(double v0, double js){
        if (fixPlan==0) {
            inPlace = 0;
        }
        double bm = coord2+v0;
        v0 = js;
        bm+=v0;

        //FIX p1
        if (js>-0.009157508093840406 && js<0.009157508093840406 && inPlace==0){  //bwmm移动阻断
            inPlace = 1;
        }
        if (inFix==1 && fixPlan==2){
            v0=0;
        }
        if (fixPlan==1 && inFix==1) {
            return v0;
        }
        if (fixPlan==3 && planSteps==0 && inFix==1) {
            return v0;
        }

        if (fixPlan==2 && planSteps==2 &&inFix==1) {
            v0=fixSpeed;
        }else{
            if (ti[0]>=2) {
                v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
            }
            
        }
        if (fixPlan==2 && planSteps==0 &&inFix==1) {
            return v0;
        }
        bm+=v0;
        for (int l = 0; l < ti[0] - 2; l++) {
            //airtime 45
            //FIX p3
            if (v0>-(float)(0.005494505) & v0<(float)0.005494505&& inPlace==0){  //bwmm移动阻断
                inPlace = 2+l;
            }
            if (inFix==2+l && fixPlan==2){
                v0=0;
            }
            if (fixPlan==1 && inFix==2+l) {
                return v0;
            }
            if (fixPlan==3 && planSteps==0 && inFix==2+l) {
                return v0;
            }
            if (fixPlan==2 && planSteps==0 && inFix==l+1) {
                return v0;
            }
            if (fixPlan==2 && planSteps==2 && inFix==l+2) {
                v0=fixSpeed;
            }else{
                v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
            }
            bm+=v0;
        }
        bm-=v0;
        tempV0 = v0;
        tempBM = bm;
        return bm;
    }

    public static double backToFrontUnit(double v0,boolean delayed){
        double bm2 = v0;
        int starti = 1;
        if (dne && delayed) {
            starti = 2;            
        }
        if (starti >= ti.length - 1) {
            return -114514;
        }
        for (int i = starti; i < ti.length - 1; i++) {
            v0 = v0 * (float) 0.91 + (float) 0.2 + (float) 0.12739998; 
            bm2+=v0;
            //
            if (ti[i]>=2) {
                v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                bm2+=v0;
            }
            
            for (int l = 0; l < ti[i] - 2; l++) {
                //
                v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                bm2+=v0;
                
            }
        }
        if (delayed) {
            v0 = v0*(float)(0.91) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        }else{
            bm2-=v0;
        }
        tempV0 = v0;
        return bm2-coord2-(float)0.6;
    }

    //input run 1t speed, output jump bm (not include the run1t)
    public static double awRunJump(double v0, boolean delayed){
        double bm = coord2;
        if (fixPlan==4) {
            v0 = fixSpeed;
        }else{
            v0 = v0 * (float) 0.54600006 + (float) 0.2 + (float) 0.12739998;
        }
        bm+=v0;

        for (int i = 0; i < ti.length - 1; i++) {
            if (i>0){
                //normal jump tick
                v0 = v0 * (float) 0.91 + (float) 0.2 + (float) 0.12739998; 
                bm+=v0;
            }

            //first airtime 45
            if (ti[i]>=2) {
                v0 = v0 * (float) 0.54600006 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                bm+=v0;
            }
            for (int l = 0; l < ti[i] - 2; l++) {
                //airtime 45
                v0 = v0 * (float) 0.91 + (float) ((float)(0.018384775) * (float)(sin) + (float)(0.018384775) * (float)(cos));
                bm+=v0;
            }
            
        }
        if (!delayed) {
            bm-=v0; 
        }else{
            v0 = v0*(float)(0.91) + (float)((float)(0.09192386) * (float)(sin) + (float)(0.09192386) * (float)(cos));
        }
        tempV0 = v0;
        return bm-coord2-(float)0.6;
    }
}
