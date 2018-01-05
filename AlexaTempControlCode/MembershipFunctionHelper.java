import java.text.DecimalFormat;
public class MembershipFunctionHelper {
    private static final double TEMPERATURE_SAMPLING_INTERVAL = 0.1;
    private static final double TIME_SAMPLING_INTERVAL = 0.25;
    
    private static final int TOTAL_TEMP_DATA_POINTS =  (int)(40/TEMPERATURE_SAMPLING_INTERVAL) + 1;
    private static final int TOTAL_FAN_DATA_POINTS = (int)(30/TIME_SAMPLING_INTERVAL) + 1;
    private static final int TOTAL_HEATER_DATA_POINTS = (int)(40/TIME_SAMPLING_INTERVAL) + 1;
    
    
    /* Membership Functions for Temperature Fuzzy Sets */
    //Sampling temperature membership functions every 0.1 degrees from 0 degrees--> 40 degrees (401 data points)
    private static final double [] MF_COLD = new double [TOTAL_TEMP_DATA_POINTS]; // 0 to 10 is sample space
    private static final double [] MF_COOL = new double [TOTAL_TEMP_DATA_POINTS]; // 5 to 20 is sample space
    private static final double [] MF_COMFY = new double [TOTAL_TEMP_DATA_POINTS]; // 15 to 25 is sample space
    private static final double [] MF_WARM = new double [TOTAL_TEMP_DATA_POINTS]; // 20 to 35 is sample space
    private static final double [] MF_HOT = new double [TOTAL_TEMP_DATA_POINTS]; // 30 to 40 is sample space
    
    /* Membership Functions for Fan Control Fuzzy Sets */
    //Sampling temperature membership functions every 0.25 minutes from 0 --> 30 minutes (121 data points)
    private static final double [] MF_FAN_OFF = new double [TOTAL_FAN_DATA_POINTS];
    private static final double [] MF_FAN_WEAK = new double [TOTAL_FAN_DATA_POINTS];
    private static final double [] MF_FAN_MEDIUM = new double [TOTAL_FAN_DATA_POINTS];
    private static final double [] MF_FAN_STRONG = new double [TOTAL_FAN_DATA_POINTS];
    private static final double [] MF_FAN_VERY_STRONG = new double [TOTAL_FAN_DATA_POINTS];
    
    /* Membership Functions for Heater Control Fuzzy Sets */
    //Sampling temperature membership functions every 0.25 minutes from 0 --> 40 minutes (161 data points)
    private static final double [] MF_HEATER_OFF = new double [TOTAL_HEATER_DATA_POINTS];
    private static final double [] MF_HEATER_WEAK = new double [TOTAL_HEATER_DATA_POINTS];
    private static final double [] MF_HEATER_MEDIUM = new double [TOTAL_HEATER_DATA_POINTS];
    private static final double [] MF_HEATER_STRONG = new double [TOTAL_HEATER_DATA_POINTS];
    private static final double [] MF_HEATER_VERY_STRONG = new double [TOTAL_HEATER_DATA_POINTS];

    /* Main & Inference engine, does the fuzzy inference, fuzzifying and defuzzfying */
    
    public static void testCases() {
        for (int testTemp = 3; testTemp<40; testTemp+=5) {
            applyDesiredWarmRuleBase(testTemp);
            applyDesiredHotRuleBase(testTemp);
            applyDesiredComfyRuleBase(testTemp);
            applyDesiredCoolRuleBase(testTemp);
            applyDesiredColdRuleBase(testTemp);
        }
    }
    
    public static void applyDesiredColdRuleBase(double temp) {
        double ruleOneWeight = MF_COLD[tempToArrayIndex(temp)];
        double [] ruleOneFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleOneHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleTwoWeight = MF_COOL[tempToArrayIndex(temp)];
        double [] ruleTwoFanInference = clipMF(ruleTwoWeight, MF_FAN_WEAK);
        double [] ruleTwoHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleThreeWeight = MF_COMFY[tempToArrayIndex(temp)];
        double [] ruleThreeFanInference = clipMF(ruleThreeWeight, MF_FAN_MEDIUM);
        double [] ruleThreeHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFourWeight = MF_WARM[tempToArrayIndex(temp)];
        double [] ruleFourFanInference = clipMF(ruleFourWeight, MF_FAN_STRONG);
        double [] ruleFourHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFiveWeight = MF_HOT[tempToArrayIndex(temp)];
        double [] ruleFiveFanInference = clipMF(ruleFiveWeight, MF_FAN_VERY_STRONG);
        double [] ruleFiveHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double [] fanTimeInference = new double [TOTAL_FAN_DATA_POINTS];
        for (int i = 0; i<fanTimeInference.length; i++) {
            fanTimeInference[i] = getMax(ruleOneFanInference[i], ruleTwoFanInference[i], ruleThreeFanInference[i], ruleFourFanInference[i], ruleFiveFanInference[i]);
        }
        
        double [] heatTimeInference = new double [TOTAL_HEATER_DATA_POINTS];
        for (int i = 0; i<heatTimeInference.length; i++) {
            heatTimeInference[i] = getMax(ruleOneHeaterInference[i], ruleTwoHeaterInference[i], ruleThreeHeaterInference[i], ruleFourHeaterInference[i], ruleFiveHeaterInference[i]);
        }
        
        System.out.println("Desired Cold... Heat-Time: " + defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
        System.out.println("Desired Cold... Fan-Time: " + defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
    }
    
    
    private static void applyDesiredCoolRuleBase(double temp) {
        double ruleOneWeight = MF_COLD[tempToArrayIndex(temp)];
        double [] ruleOneFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleOneHeaterInference = clipMF(ruleOneWeight, MF_HEATER_WEAK);
        
        double ruleTwoWeight = MF_COOL[tempToArrayIndex(temp)];
        double [] ruleTwoFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleTwoHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleThreeWeight = MF_COMFY[tempToArrayIndex(temp)];
        double [] ruleThreeFanInference = clipMF(ruleThreeWeight, MF_FAN_WEAK);
        double [] ruleThreeHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFourWeight = MF_WARM[tempToArrayIndex(temp)];
        double [] ruleFourFanInference = clipMF(ruleFourWeight, MF_FAN_MEDIUM);
        double [] ruleFourHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFiveWeight = MF_HOT[tempToArrayIndex(temp)];
        double [] ruleFiveFanInference = clipMF(ruleFiveWeight, MF_FAN_STRONG);
        double [] ruleFiveHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double [] fanTimeInference = new double [TOTAL_FAN_DATA_POINTS];
        for (int i = 0; i<fanTimeInference.length; i++) {
            fanTimeInference[i] = getMax(ruleOneFanInference[i], ruleTwoFanInference[i], ruleThreeFanInference[i], ruleFourFanInference[i], ruleFiveFanInference[i]);
        }
        
        double [] heatTimeInference = new double [TOTAL_HEATER_DATA_POINTS];
        for (int i = 0; i<heatTimeInference.length; i++) {
            heatTimeInference[i] = getMax(ruleOneHeaterInference[i], ruleTwoHeaterInference[i], ruleThreeHeaterInference[i], ruleFourHeaterInference[i], ruleFiveHeaterInference[i]);
        }
        
        System.out.println("Desired Cool... Heat-Time: " + defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
        System.out.println("Desired Cool... Fan-Time: " + defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
    }
    
    
    private static void applyDesiredComfyRuleBase(double temp) {
        double ruleOneWeight = MF_COLD[tempToArrayIndex(temp)];
        double [] ruleOneFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleOneHeaterInference = clipMF(ruleOneWeight, MF_HEATER_MEDIUM);
        
        double ruleTwoWeight = MF_COOL[tempToArrayIndex(temp)];
        double [] ruleTwoFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleTwoHeaterInference = clipMF(ruleTwoWeight, MF_HEATER_WEAK);
        
        double ruleThreeWeight = MF_COMFY[tempToArrayIndex(temp)];
        double [] ruleThreeFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleThreeHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFourWeight = MF_WARM[tempToArrayIndex(temp)];
        double [] ruleFourFanInference = clipMF(ruleFourWeight, MF_FAN_WEAK);
        double [] ruleFourHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFiveWeight = MF_HOT[tempToArrayIndex(temp)];
        double [] ruleFiveFanInference = clipMF(ruleFiveWeight, MF_FAN_MEDIUM);
        double [] ruleFiveHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double [] fanTimeInference = new double [TOTAL_FAN_DATA_POINTS];
        for (int i = 0; i<fanTimeInference.length; i++) {
            fanTimeInference[i] = getMax(ruleOneFanInference[i], ruleTwoFanInference[i], ruleThreeFanInference[i], ruleFourFanInference[i], ruleFiveFanInference[i]);
        }
        
        double [] heatTimeInference = new double [TOTAL_HEATER_DATA_POINTS];
        for (int i = 0; i<heatTimeInference.length; i++) {
            heatTimeInference[i] = getMax(ruleOneHeaterInference[i], ruleTwoHeaterInference[i], ruleThreeHeaterInference[i], ruleFourHeaterInference[i], ruleFiveHeaterInference[i]);
        }
        
        System.out.println("Desired Comfy... Heat-Time: " + defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
        System.out.println("Desired Comfy... Fan-Time: " + defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
    }
    
    
    private static void applyDesiredWarmRuleBase(double temp) {
        double ruleOneWeight = MF_COLD[tempToArrayIndex(temp)];
        double [] ruleOneFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleOneHeaterInference = clipMF(ruleOneWeight, MF_HEATER_STRONG);
        
        double ruleTwoWeight = MF_COOL[tempToArrayIndex(temp)];
        double [] ruleTwoFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleTwoHeaterInference = clipMF(ruleTwoWeight, MF_HEATER_MEDIUM);
        
        double ruleThreeWeight = MF_COMFY[tempToArrayIndex(temp)];
        double [] ruleThreeFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleThreeHeaterInference = clipMF(ruleThreeWeight, MF_HEATER_WEAK);
        
        double ruleFourWeight = MF_WARM[tempToArrayIndex(temp)];
        double [] ruleFourFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleFourHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double ruleFiveWeight = MF_HOT[tempToArrayIndex(temp)];
        double [] ruleFiveFanInference = clipMF(ruleFiveWeight, MF_FAN_WEAK);
        double [] ruleFiveHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double [] fanTimeInference = new double [TOTAL_FAN_DATA_POINTS];
        for (int i = 0; i<fanTimeInference.length; i++) {
            fanTimeInference[i] = getMax(ruleOneFanInference[i], ruleTwoFanInference[i], ruleThreeFanInference[i], ruleFourFanInference[i], ruleFiveFanInference[i]);
        }
        
        double [] heatTimeInference = new double [TOTAL_HEATER_DATA_POINTS];
        for (int i = 0; i<heatTimeInference.length; i++) {
            heatTimeInference[i] = getMax(ruleOneHeaterInference[i], ruleTwoHeaterInference[i], ruleThreeHeaterInference[i], ruleFourHeaterInference[i], ruleFiveHeaterInference[i]);
        }
        
        System.out.println("Desired Warm... Heat-Time: " + defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
        System.out.println("Desired Warm... Fan-Time: " + defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
    }
    
    private static void applyDesiredHotRuleBase(double temp) {
        double ruleOneWeight = MF_COLD[tempToArrayIndex(temp)];
        double [] ruleOneFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleOneHeaterInference = clipMF(ruleOneWeight, MF_HEATER_VERY_STRONG);
        
        double ruleTwoWeight = MF_COOL[tempToArrayIndex(temp)];
        double [] ruleTwoFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleTwoHeaterInference = clipMF(ruleTwoWeight, MF_HEATER_STRONG);
        
        double ruleThreeWeight = MF_COMFY[tempToArrayIndex(temp)];
        double [] ruleThreeFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleThreeHeaterInference = clipMF(ruleThreeWeight, MF_HEATER_MEDIUM);
        
        double ruleFourWeight = MF_WARM[tempToArrayIndex(temp)];
        double [] ruleFourFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleFourHeaterInference = clipMF(ruleFourWeight, MF_HEATER_WEAK);
        
        double ruleFiveWeight = MF_HOT[tempToArrayIndex(temp)];
        double [] ruleFiveFanInference = new double [TOTAL_FAN_DATA_POINTS];
        double [] ruleFiveHeaterInference = new double [TOTAL_HEATER_DATA_POINTS];
        
        double [] fanTimeInference = new double [TOTAL_FAN_DATA_POINTS];
        for (int i = 0; i<fanTimeInference.length; i++) {
            fanTimeInference[i] = getMax(ruleOneFanInference[i], ruleTwoFanInference[i], ruleThreeFanInference[i], ruleFourFanInference[i], ruleFiveFanInference[i]);
        }
        
        double [] heatTimeInference = new double [TOTAL_HEATER_DATA_POINTS];
        for (int i = 0; i<heatTimeInference.length; i++) {
            heatTimeInference[i] = getMax(ruleOneHeaterInference[i], ruleTwoHeaterInference[i], ruleThreeHeaterInference[i], ruleFourHeaterInference[i], ruleFiveHeaterInference[i]);
        }
        
        System.out.println("Desired-Hot... Heat-Time: " + defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
        System.out.println("Desired-Hot... Fan-Time: " + defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL) + ", For test temp: " + temp);
    }
    
    
    private static double [] clipMF(double weight, double [] mf) {
        double [] clipped = new double [mf.length];
        for (int i = 0; i<mf.length; i++) {
            clipped[i] = getMin(weight, mf[i]);
        }
        return clipped;
    }
    
    private static double fuzzyifyIndex(double temp) {
        double quantized = quantizeTemp(temp);
        return tempToArrayIndex(quantized);
    }
    
    private static double defuzzify(double [] mf, double scale) {
        double num = 0;
        double denom = 0;
        
        for (int i = 0; i<mf.length; i++) {
            num+=mf[i]*(i * scale);
            denom+=mf[i];
        }
        
        if (denom == 0) return 0;
        
        return num/denom;
    }
    
    private static double quantizeTemp(double temp) {
        // Rounding to nearest decimal place
        DecimalFormat oneDigit = new DecimalFormat("#,##0.0");//format to 1 decimal place
        return Double.valueOf(oneDigit.format(temp));
    }
    
    /* Functions that digitalize the membership functions that were drawn for the Fuzzy Temperature */
    static {
        System.out.println("Setting up Membership Functions");
        initTemperatureMF();
        initFanMF();
        initHeaterMF();
    }
    
    private static void initTemperatureMF() {
        initCold();
        initCool();
        initComfy();
        initWarm();
        initHot();
    }
    
    private static void initCold() {
        int i = 0;
        while (i < tempToArrayIndex(5)) {
            MF_COLD[i] = 1;
            i++;
        }
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_COLD[i] = 1 - (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initCool() {
        int i = tempToArrayIndex(5);
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_COOL[i] = (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < tempToArrayIndex(15)) {
            MF_COOL[i] = 1;
            i++;
        }
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_COOL[i] = 1 - (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initComfy() {
        int i = tempToArrayIndex(15);
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_COMFY[i] = (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_COMFY[i] = 1 - (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initWarm() {
        int i = tempToArrayIndex(20);
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_WARM[i] = (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i<tempToArrayIndex(30)) {
            MF_WARM[i] = 1;
            i++;
        }
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_WARM[i] = 1 - (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initHot() {
        int i = tempToArrayIndex(30);
        
        for (int j = 0; j<5/TEMPERATURE_SAMPLING_INTERVAL;j++) {
            MF_HOT[i] = (0.2*TEMPERATURE_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < MF_HOT.length) {
            MF_HOT[i] = 1;
            i++;
        }
    }

// ---------------------------------------------------------------------------------------------

    /* Functions that digitalize the membership functions that were drawn for the Fan's Time */
    private static void initFanMF() {
        MF_FAN_OFF[0] = 1;
        initWeakFan();
        initMediumFan();
        initStrongFan();
        initVeryStrongFan();
    }
    
    private static void initWeakFan() {
        int i = 0;
        while (i < timeToArrayIndex(5)) {
            MF_FAN_WEAK[i] = 1;
            i++;
        }
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_WEAK[i] = 1 - (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initMediumFan() {
        int i = timeToArrayIndex(5);
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_MEDIUM[i] = (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < timeToArrayIndex(15)) {
            MF_FAN_MEDIUM[i] = 1;
            i++;
        }
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_MEDIUM[i] = 1 - (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initStrongFan() {
        int i = timeToArrayIndex(15);
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_STRONG[i] = (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < timeToArrayIndex(20)) {
            MF_FAN_STRONG[i] = 1;
            i++;
        }
        
        for (int j = 0; j<5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_STRONG[i] = 1 - (0.2*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initVeryStrongFan() {
        int i = timeToArrayIndex(20);
        
        for (int j = 0; j<5/TIME_SAMPLING_INTERVAL;j++) {
            MF_FAN_VERY_STRONG[i] = (0.2*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < MF_FAN_VERY_STRONG.length) {
            MF_FAN_VERY_STRONG[i] = 1;
            i++;
        }
    }

    //-------------------------------------------------------------------------------------------------

    /* Functions that digitalize the membership functions that were drawn for the Heater's Time */
    private static void initHeaterMF() {
        MF_HEATER_OFF[0] = 1;
        initWeakHeater();
        initMediumHeater();
        initStrongHeater();
        initVeryStrongHeater();
    }
    
    private static void initWeakHeater() {
        int i = 0;
        while (i < timeToArrayIndex(7.5)) {
            MF_HEATER_WEAK[i] = 1;
            i++;
        }
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_WEAK[i] = 1 - (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initMediumHeater() {
        int i = timeToArrayIndex(7.5);
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_MEDIUM[i] = (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < timeToArrayIndex(17.5)) {
            MF_HEATER_MEDIUM[i] = 1;
            i++;
        }
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_MEDIUM[i] = 1 - (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initStrongHeater() {
        int i = timeToArrayIndex(17.5);
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_STRONG[i] = (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < timeToArrayIndex(27.5)) {
            MF_HEATER_STRONG[i] = 1;
            i++;
        }
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_STRONG[i] = 1 - (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
    }
    
    private static void initVeryStrongHeater() {
        int i = timeToArrayIndex(27.5);
        
        for (int j = 0; j<2.5/TIME_SAMPLING_INTERVAL;j++) {
            MF_HEATER_VERY_STRONG[i] = (0.4*TIME_SAMPLING_INTERVAL*j);
            i++;
        }
        
        while (i < MF_HEATER_VERY_STRONG.length) {
            MF_HEATER_VERY_STRONG[i] = 1;
            i++;
        }
    }

    //---------------------------------------------------------------------------------------------

    /* Helper functions for random shit     */
    private static int tempToArrayIndex(double temperature) {
        return (int)(temperature/TEMPERATURE_SAMPLING_INTERVAL);
    }
    
    private static int timeToArrayIndex(double time) {
        return (int)(time/TIME_SAMPLING_INTERVAL);
    }
    
    private static double getMax(double... vals) {
        double max = 0;
        
        for (double d : vals) {
            if (d > max) max = d;
        }
        
        return max;
    }
    
    private static double getMin(double... vals) {
        double min = 1;
        
        for (double d: vals) {
            if (d < min) min = d;
        }
        
        return min;
    }
    
    // For testing via log
    private static void print1DMF(double [] mf, boolean isTime) {
        for (int i = 0; i<mf.length; i++) {
            String title = isTime ? "Time:" : "Temp:";
            double multiplier = isTime ? TIME_SAMPLING_INTERVAL : TEMPERATURE_SAMPLING_INTERVAL;
            System.out.println(title + i*multiplier+ " , MF=" + mf[i]);
        }
    }
    
    // --------------------------------------------------------------------------------------------
    
}
