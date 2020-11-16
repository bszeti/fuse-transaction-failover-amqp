package bszeti.artemis.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Counter {

    private AtomicInteger receiveCounter = new AtomicInteger();
    private int receiveCounterLast = 0;
    private AtomicInteger receiveForwardedCounter = new AtomicInteger();
    private AtomicInteger sendCounter = new AtomicInteger();
    private int sendCounterLast = 0;

    //Collect sent ids, so we can observe missing.
    Map<String,String> sentUUIDs = new ConcurrentHashMap<>();

    //Collect received ids, so we can observe duplicates.
    Map<String,String> receivedUUIDs = new ConcurrentHashMap<>();

    public AtomicInteger getReceiveCounter() {
        return receiveCounter;
    }

    public void setReceiveCounter(AtomicInteger receiveCounter) {
        this.receiveCounter = receiveCounter;
    }

    public int getReceiveCounterLast() {
        return receiveCounterLast;
    }

    public void setReceiveCounterLast(int receiveCounterLast) {
        this.receiveCounterLast = receiveCounterLast;
    }

    public AtomicInteger getReceiveForwardedCounter() {
        return receiveForwardedCounter;
    }

    public void setReceiveForwardedCounter(AtomicInteger receiveForwardedCounter) {
        this.receiveForwardedCounter = receiveForwardedCounter;
    }

    public AtomicInteger getSendCounter() {
        return sendCounter;
    }

    public void setSendCounter(AtomicInteger sendCounter) {
        this.sendCounter = sendCounter;
    }

    public int getSendCounterLast() {
        return sendCounterLast;
    }

    public void setSendCounterLast(int sendCounterLast) {
        this.sendCounterLast = sendCounterLast;
    }

    public Map<String, String> getSentUUIDs() {
        return sentUUIDs;
    }

    public void setSentUUIDs(Map<String, String> sentUUIDs) {
        this.sentUUIDs = sentUUIDs;
    }

    public Map<String, String> getReceivedUUIDs() {
        return receivedUUIDs;
    }

    public void setReceivedUUIDs(Map<String, String> receivedUUIDs) {
        this.receivedUUIDs = receivedUUIDs;
    }
}
