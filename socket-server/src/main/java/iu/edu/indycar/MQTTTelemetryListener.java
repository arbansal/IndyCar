package iu.edu.indycar;

import iu.edu.indycar.models.AnomalyMessage;
import iu.edu.indycar.models.CarPositionMessage;
import iu.edu.indycar.streamer.TimeUtils;
import iu.edu.indycar.tmp.RecordWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class MQTTTelemetryListener {

    private final static Logger LOG = LogManager.getLogger(MQTTTelemetryListener.class);

    private static final String CONNECTION_URL = "tcp://j-093.juliet.futuresystems.org:61613";
    private final String topic;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "xyi5b2YUcw8CHhAE";

    private final long rate = 1000 / 60;// fps

    private long lastRecordTime = 0;

    private final CarPositionMessage carPositionMessage = new CarPositionMessage();

    private ServerBoot serverBoot;

    private RecordWriter recordWriter;

    private int rests = 0;//keeps number of rests

    public MQTTTelemetryListener(ServerBoot serverBoot, String subscription) {
        this.serverBoot = serverBoot;
        this.topic = subscription;
        this.initRecordWriter();
    }

    private void initRecordWriter() {
        try {
            this.recordWriter = new RecordWriter("/tmp/records_out_" + rests++);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        this.carPositionMessage.clear();
        this.lastRecordTime = 0;
        this.recordWriter.close();
        this.initRecordWriter();
    }

    public void start() throws MqttException {
        MqttClient client = new MqttClient(CONNECTION_URL, MqttClient.generateClientId());
        MqttConnectOptions connOpts = setUpConnectionOptions(USERNAME, PASSWORD);

        client.setCallback(new MqttCallback() {

            final HashMap<String, Long> lastTimes = new HashMap<>();


            @Override
            public void connectionLost(Throwable throwable) {

            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                try {
                    String csv = new String(mqttMessage.getPayload());

                    JSONObject jsonObject = new JSONObject(csv);

                    String timeOfDay = jsonObject.getString("timeOfDay");
                    long timeOfDayLong = TimeUtils.convertTimestampToLong(timeOfDay);
                    String carNumber = jsonObject.getString("carNumber");
                    int carNumberInt = Integer.parseInt(carNumber);

                    double lapDistance = jsonObject.getDouble("lapDistance");

                    carPositionMessage.recordPosition(
                            carNumber,
                            lapDistance,
                            timeOfDayLong,
                            timeOfDayLong - lastTimes.getOrDefault(
                                    carNumber, timeOfDayLong
                            )
                    );
                    carPositionMessage.incrementSequence();

                    lastTimes.put(carNumber, timeOfDayLong);

                    if (System.currentTimeMillis() - lastRecordTime >= rate) {
                        serverBoot.publishPositionEvent(carPositionMessage);
                        lastRecordTime = System.currentTimeMillis();
                    }

                    double vehicleSpeed = Double.valueOf(jsonObject.getString("vehicleSpeed"));
                    double throttle = Double.valueOf(jsonObject.getString("throttle"));
                    double rpm = Double.valueOf(jsonObject.getString("engineSpeed"));

                    AnomalyMessage speedAnomaly = new AnomalyMessage();
                    speedAnomaly.setIndex(timeOfDayLong);
                    speedAnomaly.setAnomalyType("SPEED");
                    speedAnomaly.setCarNumber(carNumberInt);
                    speedAnomaly.setRawData(vehicleSpeed);
                    speedAnomaly.setAnomaly(jsonObject.getDouble("vehicleSpeedAnomaly"));


                    AnomalyMessage throttleAnomaly = new AnomalyMessage();
                    throttleAnomaly.setIndex(timeOfDayLong);
                    throttleAnomaly.setAnomalyType("THROTTLE");
                    throttleAnomaly.setCarNumber(carNumberInt);
                    throttleAnomaly.setRawData(throttle);
                    throttleAnomaly.setAnomaly(jsonObject.getDouble("throttleAnomaly"));

                    AnomalyMessage rpmAnomaly = new AnomalyMessage();
                    rpmAnomaly.setIndex(timeOfDayLong);
                    rpmAnomaly.setAnomalyType("RPM");
                    rpmAnomaly.setCarNumber(carNumberInt);
                    rpmAnomaly.setRawData(rpm);
                    rpmAnomaly.setAnomaly(jsonObject.getDouble("engineSpeedAnomaly"));

                    serverBoot.publishAnomalyEvent(speedAnomaly);
                    serverBoot.publishAnomalyEvent(throttleAnomaly);
                    serverBoot.publishAnomalyEvent(rpmAnomaly);

                    String counter = jsonObject.getString("UUID");

                    try {
                        counter = counter.split("_")[1];
                    } catch (Exception e) {
                        LOG.error("Failed to parse counter", e);
                    }

                    recordWriter.write(
                            carNumber,
                            counter,
                            lapDistance,
                            timeOfDay,
                            vehicleSpeed,
                            rpm,
                            throttle
                    );
                } catch (Exception ex) {
                    LOG.error("Skipping a record from pub/sub", ex);
                }
                //LOG.info("Message arrived {} : {}", s, new String(mqttMessage.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
        client.connect(connOpts);

        client.subscribe(topic);
    }

    private static MqttConnectOptions setUpConnectionOptions(String username, String password) {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setUserName(username);
        connOpts.setPassword(password.toCharArray());
        return connOpts;
    }
}
