package br.com.example;

import br.com.example.statistics.IStatistics;
import br.com.example.bean.Master;
import br.com.example.bean.Slave;

import java.util.*;

/**
 * Created by jordao on 27/11/16.
 */
public class PQSRequestSimulator {

    /** number of centrals connected to the cloud service */
    private final static int MASTERS_AMOUNT = 1;
    /** number of slaves allowed to speak to each central */
    private final static int AMOUNT_OF_SLAVE_PER_MASTER = 2;

    private static List<Master> masters;
    private static List<Slave> slaves;

    /** We create the number of masters defined above */
    public static void executeMasters(){
        masters = new ArrayList<Master>();
        for (int i = 0; i < MASTERS_AMOUNT; i++){
            Master master = new Master("SN-"+i);
            master.init();
            masters.add(master);
            master.start();
        }
    }
    /** for each master we create a new slave accordingly to the amount defined above */
    public static void executeSlaves(){
        slaves = new ArrayList<Slave>();
        for (int i = 0; i < MASTERS_AMOUNT; i++){
            for (int j = 0; j < AMOUNT_OF_SLAVE_PER_MASTER; j++){
                Slave slave = new Slave("APPID-"+j, "SN-"+i);
                slave.init();
                slaves.add(slave);
                slave.start();
            }
        }
    }

    public static void stopMasters() {
        Iterator<Master> masterIterator = masters.iterator();
        while(masterIterator.hasNext()) {
            try {
                masterIterator.next().stop();
            } catch (Exception e) {

            }
        }
    }

    public static void stopSlaves() {
        Iterator<Slave> slaveIterator = slaves.iterator();
        while(slaveIterator.hasNext()) {
            try {
                slaveIterator.next().stop();
            } catch (Exception e) {

            }
        }
    }

    public static void startAll() {
        executeSlaves();
        executeMasters();
    }

    public static void stopAll() {
        stopMasters();
        stopSlaves();
    }

    public static List<IStatistics> collectMasterStatistics() {
        List<IStatistics> masterStatistics = new ArrayList<IStatistics>();
        Iterator<Master> mastersIterator = masters.iterator();
        while(mastersIterator.hasNext()) {
            Master master = mastersIterator.next();
            List<IStatistics> currentMasterStatistics = master.collectStatistics();
            Iterator<IStatistics> currentMasterStatisticsIterator = currentMasterStatistics.iterator();
            while(currentMasterStatisticsIterator.hasNext()) {
                masterStatistics.add(currentMasterStatisticsIterator.next());
            }
        }
        return masterStatistics;
    }

    public static List<IStatistics> collectSlaveStatistics() {
        List<IStatistics> slaveStatistics = new ArrayList<IStatistics>();
        Iterator<Master> slaveIterator = masters.iterator();
        while(slaveIterator.hasNext()) {
            Master master = slaveIterator.next();
            List<IStatistics> currentSlaveStatistics = master.collectStatistics();
            Iterator<IStatistics> currentSlaveStatisticsIterator = currentSlaveStatistics.iterator();
            while(currentSlaveStatisticsIterator.hasNext()) {
                slaveStatistics.add(currentSlaveStatisticsIterator.next());
            }
        }
        return slaveStatistics;
    }

    public static List<IStatistics> collectAllStatistics() {
        List<IStatistics> allStatistics = new ArrayList<IStatistics>(collectMasterStatistics());
        allStatistics.addAll(collectSlaveStatistics());
        return allStatistics;
    }


    /** good and old main method =P */
    public static void main(String[] args) throws InterruptedException {

        startAll();
        // TODO after some N secods we have to stop all threads and collect results.
        Thread.sleep(50000);
        stopAll();
        System.out.println("PQSRequestSimulator Statistics\n");
        System.out.println(collectAllStatistics() + "\n");

    }



}