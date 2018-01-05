import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;

import java.text.DecimalFormat;

import com.amazonaws.services.iotdata.AWSIotDataClientBuilder;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.AWSIotData;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.iotdata.model.GetThingShadowResult;
import com.amazonaws.services.iotdata.model.UpdateThingShadowRequest;
import com.amazonaws.services.iotdata.model.UpdateThingShadowResult;
import com.amazonaws.services.iotdata.model.PublishRequest;
import com.amazonaws.services.iotdata.model.PublishResult;
import com.amazonaws.util.StringUtils;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import org.json.JSONObject;
import org.json.JSONTokener;

public class AlexaTemperatureControlHandler implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(AlexaTemperatureControlHandler.class);
    
    /**
     * Constant defining session attribute key for the intent slot key for the room number for temperature actions.
     */
    private static final String SLOT_ROOM = "room";
    
    /**
     * Constant defining session attribute key for the intent slot key for the fuzzy temperature a user wants to set a room to.
     */
    private static final String SLOT_FUZZY = "fuzzy";
    
    /**
     * Constant defining session attribute key for the intent slot key for whether the user wants to make the temp go up/down.
     */
    private static final String SLOT_CHANGE = "change";
    
    /**
     * Wrapper for creating the Ask response from the input strings.
     *
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
                                             String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }
        
        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }
    
    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
    throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                 session.getSessionId());
    }
    
    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
    throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                 session.getSessionId());
        
        return getWelcomeResponse();
    }
    
    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
    throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                 session.getSessionId());
        
        Intent intent = request.getIntent();
        String intentName = intent.getName();
        
        if ("GetTemperatureOfRoomIntent".equals(intentName)) {
            return handleGetTemperatureRequest(intent, session);
        } else if ("SetTemperatureOfRoomConstantIntent".equals(intentName)) {
            return handleSetTemperatureConstRequest(intent, session);
        } else if ("SetTemperatureOfRoomFuzzyVariableIntent".equals(intentName)) {
            return handleSetTemperatureFuzzyRequest(intent, session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            // Create the plain text output.
            String speechOutput =
            "With Alexa Temperature Control, you can set and get the state of the rooms in your home."
            + " For example, you could say, make room three cold."
            + "Now, what do you want to do?";
            
            String repromptText = "What should I do?";
            
            return newAskResponse(speechOutput, false, repromptText, false);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");
            
            return SpeechletResponse.newTellResponse(outputSpeech);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");
            
            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }
    
    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
    throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                 session.getSessionId());
        
        // any session cleanup logic would go here
    }
    
    /**
     * Function to handle the onLaunch skill behavior.
     *
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechOutput = "Welcome to Alexa Temperature Control";
        String repromptText =
        "With Alexa Temperature Control, you can set or get the state of the rooms in your home."
        + "For example, you could say, get me the temperature of room one.";
        
        return newAskResponse(speechOutput, false, repromptText, false);
    }
    
    private static final String GET_TEMP_REPROMPT_TEXT = "With Alexa Temperature Control, you can get the temperature for a specific room."
    + " For example, you could say, what is the temperature of room one."
    + " Now, which room number do you want?";
    
    /**
     * Gets the a fuzzy response for the current temperature of a room to return to the user.
     *
     * @param intent
     *            the intent object which contains the room slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleGetTemperatureRequest(Intent intent, Session session) {
        int roomNumber = Integer.parseInt(intent.getSlot(SLOT_ROOM).getValue());
        
        String roomTemp = sendRequestToPi(RASPBERRY_PI_COMMANDS.GET_TEMP, roomNumber, 0d);
        String alexaResponse = getTempStringResponse(Double.parseDouble(roomTemp), roomNumber);
        
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Current Temperature in Fuzzy Format");
        card.setContent(alexaResponse);
        
        
        SpeechletResponse response = newAskResponse("<speak>" + alexaResponse + "</speak>", true, GET_TEMP_REPROMPT_TEXT, false);
        response.setCard(card);
        return response;
    }
    
    private String getTempStringResponse(double temp, double roomNumber) {
        String fuzzyTemp;
        if (temp >= 0 && temp <= 5) fuzzyTemp = "cold";
        else if (temp > 5 && temp < 10) fuzzyTemp = "slightly cold";
        else if (temp >= 10 && temp <= 17.55) fuzzyTemp = "cool";
        else if (temp > 17.5 && temp < 22.5) fuzzyTemp = "comfortable";
        else if (temp >= 22.5 && temp <= 30) fuzzyTemp = "warm";
        else if (temp > 30 && temp < 35) fuzzyTemp = "slightly hot";
        else if (temp >= 35) fuzzyTemp = "hot";
        else fuzzyTemp = "unknown";
        return "The temperature in room " + roomNumber + " is " + fuzzyTemp;
    }
    
    // Should talk to shadow to get temperature for room 'x'
    private String sendRequestToPi(RASPBERRY_PI_COMMANDS command, int roomNumber, double timeOn) {
        // Talk to raspberry Pi with command.
        BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJLZV22IWPXIFJN3A", "6q4GW4Vh3jtpWDRyR5V+26Tp4XWx9qVkP827MybP");
        AWSStaticCredentialsProvider screds = new AWSStaticCredentialsProvider(credentials);
        
        EndpointConfiguration ec = new EndpointConfiguration("a38diupx41d3lu.iot.us-east-1.amazonaws.com", "us-east-1");
        AWSIotData client = AWSIotDataClientBuilder.standard()
        .withCredentials(screds)
        .withEndpointConfiguration(ec)
        .build();
        System.out.println("IM GOING TO TRY AND GET STUFF NOW");
        
        String resp = null;
        String roomNumStr = "room" + Integer.toString(roomNumber-1);
        String time = Double.toString(timeOn);
        if (command.equals(RASPBERRY_PI_COMMANDS.GET_TEMP)) {
            // PUT CODE FOR GETTING TEMP FROM PI HERE
            GetThingShadowRequest gtsr = new GetThingShadowRequest().withThingName("MyRaspberryPi");
            GetThingShadowResult res = client.getThingShadow(gtsr);
            String s = null;
            try {
                ByteBuffer byteBuffer = res.getPayload();
                Charset charset = Charset.defaultCharset();
                CharsetDecoder decoder = charset.newDecoder();
                CharBuffer charBuffer = decoder.decode(byteBuffer);
                s = charBuffer.toString();
                JSONObject json = new JSONObject(new JSONTokener(s));
                System.out.println("THE JSON IS" + json.toString());
                JSONObject state = json.getJSONObject("state");
                System.out.println("THE STATE IS" + state.toString());
                JSONObject reported = state.getJSONObject("reported");
                System.out.println("THE REPORTED IS" + reported.toString());
                System.out.println("THE ROOM NUMBER IS" + roomNumStr);
                JSONObject roomObj = reported.getJSONObject(roomNumStr);
                System.out.println("THE ROOM IS" + roomObj.toString());
                Double temp = roomObj.getDouble("temp");
                resp = Double.toString(temp);
            } catch (Exception e) {
                
            }
            System.out.println("THE TEMPERATURE IS" + resp);
            return resp;
        } else if (command.equals(RASPBERRY_PI_COMMANDS.HEAT_ON)) {
            // PUT CODE FOR TURNING HEAT ON IN HERE
            String payloadStr= String.format("{\"state\": {\"desired\" : {\"%s\" : { \"mode\": \"HEAT\", \"time\" : %s }}}}", roomNumStr, time);
            byte[] strInBytes = payloadStr.getBytes();
            ByteBuffer strBytesInBuffer = ByteBuffer.wrap(strInBytes);
            UpdateThingShadowRequest utsr = new UpdateThingShadowRequest().withThingName("MyRaspberryPi").withPayload(strBytesInBuffer);
            UpdateThingShadowResult res1 = client.updateThingShadow(utsr);
        } else if (command.equals(RASPBERRY_PI_COMMANDS.FAN_ON)) {
            // PUT CODE FOR TURNING FAN ON IN HERE
            String payloadStr= String.format("{\"state\": {\"desired\" : {\"%s\" : { \"mode\": \"COOL\", \"time\" : %s }}}}", roomNumStr, time);
            byte[] strInBytes = payloadStr.getBytes();
            ByteBuffer strBytesInBuffer = ByteBuffer.wrap(strInBytes);
            UpdateThingShadowRequest utsr = new UpdateThingShadowRequest().withThingName("MyRaspberryPi").withPayload(strBytesInBuffer);
            UpdateThingShadowResult res1 = client.updateThingShadow(utsr);
        }
        return "";
    }
    
    public enum RASPBERRY_PI_COMMANDS {
        HEAT_ON,
        FAN_ON,
        GET_TEMP,
        OFF
    }
    
    public enum USER_REQUEST_TYPE {
        INCREASE_CONST,
        DECREASE_CONST,
        SET_COLD,
        SET_COOL,
        SET_COMFY,
        SET_WARM,
        SET_HOT
    }
    
    private static final String CONT_TEMP_CHANGE_REPROMPT_TEXT = "With Alexa Temperature Control, if you want to increase or decrease the temperature by a constant amount, you need to specify a room number."
    + " For example, you could say one, or two or three."
    + " Now, which room number do you want?";
    
    /**
     * Sets the temperature of a room to a constant value.
     *
     * @param intent
     *            the intent object which contains the room slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleSetTemperatureConstRequest(Intent intent, Session session) {
        String changeType = intent.getSlot(SLOT_CHANGE).getValue(); //EITHER UP/DOWN
        int roomNumber = Integer.parseInt(intent.getSlot(SLOT_ROOM).getValue());
        boolean isIncrease = changeType.equals("up");
        sendRequestToPi((isIncrease ? RASPBERRY_PI_COMMANDS.HEAT_ON : RASPBERRY_PI_COMMANDS.FAN_ON), roomNumber, 3d);
        String responseString = (isIncrease ? "Increasing " : "Decreasing ") + "the temperature of room " + roomNumber + " by a constant value.";
        
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Set the temperature with a constant increase/decrease");
        card.setContent(responseString);
        
        SpeechletResponse response = newAskResponse("<speak>" + responseString + "</speak>", true, CONT_TEMP_CHANGE_REPROMPT_TEXT, false);
        response.setCard(card);
        return response;
    }
    
    private static final String FUZZY_TEMP_CHANGE_REPROMPT_TEXT = "With Alexa Temperature Control, if you want to change the temperature using a fuzzy term, you need to specify a room number."
    + " For example, you could say one, or two or three."
    + " Now, which room number do you want?";
    
    
    /**
     * Sets the temperature of a room to a constant value.
     *
     * @param intent
     *            the intent object which contains the room slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleSetTemperatureFuzzyRequest(Intent intent, Session session) {
        String fuzzyChange = intent.getSlot(SLOT_FUZZY).getValue();
        int roomNumber = Integer.parseInt(intent.getSlot(SLOT_ROOM).getValue());
        
        // 0 --> RPI Command, 1 --> minutes
        String temp = sendRequestToPi(RASPBERRY_PI_COMMANDS.GET_TEMP, roomNumber, 0d);
        String [] fuzzyControlAction = fuzzyInfer(fuzzyChange, Double.parseDouble(temp));
        sendRequestToPi(getRPICommandFromString(fuzzyControlAction[0]), roomNumber, Double.parseDouble(fuzzyControlAction[1]));
        
        String responseString = "Making it feel " + fuzzyChange + " in room " + roomNumber;
        
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Set the temperature with a fuzzy term");
        card.setContent(responseString);
        SpeechletResponse response = newAskResponse("<speak>" + responseString + "</speak>", true, FUZZY_TEMP_CHANGE_REPROMPT_TEXT, false);
        response.setCard(card);
        return response;
    }
    
    private String [] fuzzyInfer(String fuzzyChange, double roomTemp) {
        String [] response = {"off", Integer.toString(0)};
        if (fuzzyChange.equals("cold")) response = applyDesiredColdRuleBase(roomTemp);
        else if (fuzzyChange.equals("cool")) response = applyDesiredCoolRuleBase(roomTemp);
        else if (fuzzyChange.equals("comfortable")) response = applyDesiredComfyRuleBase(roomTemp);
        else if (fuzzyChange.equals("warm")) response = applyDesiredWarmRuleBase(roomTemp);
        else if (fuzzyChange.equals("hot")) response = applyDesiredHotRuleBase(roomTemp);
        
        return response;
    }
    
    private RASPBERRY_PI_COMMANDS getRPICommandFromString(String command) {
        if (command.equals("heat")) return RASPBERRY_PI_COMMANDS.HEAT_ON;
        else if (command.equals("fan")) return RASPBERRY_PI_COMMANDS.FAN_ON;
        else return RASPBERRY_PI_COMMANDS.OFF;
    }
    
    private USER_REQUEST_TYPE getUserRequestTypeFromString(String command) {
        if (command.equals("cold")) return USER_REQUEST_TYPE.SET_COLD;
        if (command.equals("cool")) return USER_REQUEST_TYPE.SET_COOL;
        if (command.equals("comfortable")) return USER_REQUEST_TYPE.SET_COMFY;
        if (command.equals("warm")) return USER_REQUEST_TYPE.SET_WARM;
        if (command.equals("hot")) return USER_REQUEST_TYPE.SET_HOT;
        return USER_REQUEST_TYPE.SET_COMFY;
    }
    
    // —— FUZZY STUFF AND MEMBERSHIP FUNCTION STUFF ————————
    
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
            applyDesiredWarmRuleBase((double)testTemp);
            applyDesiredHotRuleBase((double)testTemp);
            applyDesiredComfyRuleBase((double)testTemp);
            applyDesiredCoolRuleBase((double)testTemp);
            applyDesiredColdRuleBase((double)testTemp);
        }
    }
    
    private static String [] getInferenceResponse(double heatTime, double fanTime) {
        String [] response = new String[2];
        if (heatTime > 0 && fanTime == 0) {
            response[0] = "heat";
            response[1] = Double.toString(heatTime);
        } else if (heatTime == 0 && fanTime > 0) {
            response[0] = "fan";
            response[1] = Double.toString(fanTime);
        }
        return response;
    }
    
    public static String [] applyDesiredColdRuleBase(double temp) {
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
        return getInferenceResponse(defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL),defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL));
    }
    
    
    private static String [] applyDesiredCoolRuleBase(double temp) {
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
        return getInferenceResponse(defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL),defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL));
    }
    
    
    private static String [] applyDesiredComfyRuleBase(double temp) {
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
        return getInferenceResponse(defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL),defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL));
    }
    
    
    private static String [] applyDesiredWarmRuleBase(double temp) {
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
        return getInferenceResponse(defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL),defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL));
    }
    
    private static String [] applyDesiredHotRuleBase(double temp) {
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
        return getInferenceResponse(defuzzify(heatTimeInference, TIME_SAMPLING_INTERVAL),defuzzify(fanTimeInference, TIME_SAMPLING_INTERVAL));
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
    
    /* Helper functions for random stuff */
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
}
