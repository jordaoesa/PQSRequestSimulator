package br.com.example.bean;

import br.com.example.statistics.IRequestStatisticallyProfilable;
import br.com.example.statistics.IStatistics;
import br.com.example.statistics.RequestStatistics;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static br.com.example.request.Request.POST;

/**
 * Created by jordao on 27/11/16.
 */
public class Slave implements IRequestStatisticallyProfilable {

    private final static Logger LOGGER = LoggerFactory.getLogger(Slave.class);

    private String applicationID;
    private String masterSerialNumber;
    private final int MINIMUM_PULLING_INTERVAL;
    private final int PULLING_OFFSET;
    private final int MINIMUM_PUSHING_INTERVAL;
    private final int PUSHING_OFFSET;
    private List<Future> runnablePullerFutures;
    private List<Future> runnablePusherFutures;

    private List<IStatistics> requestStatisticsList = new LinkedList<IStatistics>();

    // TODO change this to package attributes;
    /**
     * Ficar imprimindo o status local (lock) após pooling.
     * Ao conectar, enviar mensagem para o master pedindo status.
     * Quando pa do lock for chamando, enviar mensagem para a de trocar o estado do lock.
     * Essa mensagem chega no master. Este processa e envia resposta para slave.
     * O Slave recebe a mensagem do status referente à mensagem enviada e atualiza seu status (lock).
     * Imprimir lock do slave.
     * Fazer o mesmo a cada pooling no Master
     */
    private boolean lock = false; // ficar estado do master;
    private boolean connected = false;
    private final String CONNECT = "7B43";
    private final String LOCK = "7B44";
    private final String UNLOCK = "7B45";

    public Slave(String applicationID, String masterSerialNumber, int minimumPullingInterval, int pullingOffset,
                 int minimumPushingInterval, int pushingOffset) {
        this.applicationID = applicationID;
        this.masterSerialNumber = masterSerialNumber;
        this.MINIMUM_PULLING_INTERVAL = minimumPullingInterval;
        this.PULLING_OFFSET = pullingOffset;
        this.MINIMUM_PUSHING_INTERVAL = minimumPushingInterval;
        this.PUSHING_OFFSET = pushingOffset;
    }

    public Slave(String applicationID, String masterSerialNumber) {
        this(applicationID, masterSerialNumber, 3, 5, 5, 5);
    }

    public void init() {

        LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status connection: [ not connected ]");
        if(connectToCentral()){
            LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status connection: [ connection required ]");
        }

        runnablePullerFutures = new ArrayList<Future>();
        runnablePusherFutures = new ArrayList<Future>();
    }

    public void start() {
        Runnable puller = createPuller();
        Runnable pusher = createPusher();
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        Future<?> pullerFuture = executorService.scheduleAtFixedRate(puller, 0, MINIMUM_PULLING_INTERVAL, TimeUnit.SECONDS);
        Future<?> pusherFuture = executorService.scheduleAtFixedRate(pusher, 0, MINIMUM_PUSHING_INTERVAL, TimeUnit.SECONDS);
        runnablePullerFutures.add(pullerFuture);
        runnablePusherFutures.add(pusherFuture);
    }

    public void stopPullers() {
        Iterator<Future> runnableFutureIterator = runnablePullerFutures.iterator();
        while(runnableFutureIterator.hasNext()) {
            try {
                runnableFutureIterator.next().cancel(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            runnablePullerFutures.clear();
            runnablePullerFutures = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopPushers() {
        Iterator<Future> runnableFutureIterator = runnablePusherFutures.iterator();
        while(runnableFutureIterator.hasNext()) {
            try {
                runnableFutureIterator.next().cancel(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            runnablePusherFutures.clear();
            runnablePusherFutures = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopAll() {
        stopPullers();
        stopPushers();
    }

    private Runnable createPuller() {
        return new SlavePuller(PULLING_OFFSET);
    }

    private Runnable createPusher() {
        return new SlavePusher(PUSHING_OFFSET);
    }


    /**
     * executes POST /pa for devices
     * @return
     */
    private synchronized String pa(String body){
        long startTimestamp = new Date().getTime();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Serial-Number", masterSerialNumber);
        headers.put("Application-ID", applicationID);
        headers.put("Content-Type", "application/json");


        String response = POST("/pa", headers, body);

        long endTimestamp = new Date().getTime();
        synchronized (requestStatisticsList) {
            RequestStatistics requestStatistics = new RequestStatistics(masterSerialNumber + "_" + applicationID, body == null ? "pa - wbody" : "pa - nobody", startTimestamp, endTimestamp);
            requestStatisticsList.add(requestStatistics);
        }
        return response;
    }

    public String getApplicationID() {
        return applicationID;
    }

    public void setApplicationID(String applicationID) {
        this.applicationID = applicationID;
    }

    public String getMasterSerialNumber() {
        return masterSerialNumber;
    }

    public void setMasterSerialNumber(String centralSerialNumber) {
        this.masterSerialNumber = centralSerialNumber;
    }

    public List<IStatistics> collectStatistics() {
        return requestStatisticsList;
    }

    class SlavePuller extends Thread implements Runnable {
        private volatile boolean shutdown = false;
        private int pullingOffset;
        public SlavePuller(int pullingOffset){
            this.pullingOffset = pullingOffset;
        }
        public void run() {
            if(shutdown) {
                try {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Random rand = new Random();
            int randomInterval = rand.nextInt(pullingOffset + 1);

            try {
                Thread.sleep(randomInterval * 1000);
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
            try {
                String response = pa(null);
                if(response != null && !response.equals("{}")){
                    Message msg = new Gson().fromJson(response, Message.class);
                    String res = msg.getMessage();
                    switch (res) {
                        case CONNECT + "OK":
                            LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status connection: [ connected ].");
                            connected = true;
                            break;
                        case LOCK + "OK":
                            lock = true;
                            LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status lock: [ " + lock + " ].");
                            break;
                        case UNLOCK + "OK":
                            lock = false;
                            LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status lock: [ " + lock + " ].");
                            break;
                        default:
                            LOGGER.warn("#TAG Slave COMMAND + [ " + response + " ] NOT FOUND.");
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //LOGGER.info("PA PULL CENTRAL-SN [" + masterSerialNumber + "] APP-ID [" + applicationID + "]: " + response );
        }

        public void shutdown() {
            shutdown = true;
        }
    }


    class SlavePusher extends Thread implements Runnable {
        private final int pushingOffset;
        private volatile boolean shutdown = false;
        private boolean sleepMode = false;

        public SlavePusher(int pushingOffset, boolean sleepMode) {
            this.pushingOffset = pushingOffset;
            this.sleepMode = sleepMode;
        }

        public SlavePusher(int pushingOffset) {
            this(pushingOffset, false);
        }

        public void run() {
            if(shutdown) {
                try {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(!sleepMode) {
                Random rand = new Random();
                int randomInterval = rand.nextInt(pushingOffset + 1);

                try {
                    Thread.sleep(randomInterval * 1000);
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }

                //String body = "7B4CAAABBBCCCDDDEEEFFF";
                if(connected) {
                    String response = null;
                    if(lock) {
                        response = pa(UNLOCK);
                        LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status lock: [ CHANGE lock REQUIRED ] to [ UNLOCK ].");
                    } else {
                        response = pa(LOCK);
                        LOGGER.warn("#TAG Slave [ " + applicationID + " ]: status lock: [ CHANGE lock REQUIRED ] to [ LOCK ].");
                    }
                    LOGGER.info("PA POST CENTRAL-SN [" + masterSerialNumber + "] APP-ID [" + applicationID + "]: " + response);
                }
            }
        }

        public void shutdown() {
            shutdown = true;
        }
    }

    private boolean connectToCentral(){

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Serial-Number", masterSerialNumber);
        headers.put("Application-ID", applicationID);
        headers.put("Content-Type", "application/json");
        String response = "";
        try {
            response = POST("/sconn", headers, CONNECT);
        }catch (Exception e){
            e.printStackTrace();
        }

        return response.equals("RECEIVED");
    }

}
