package com.dianping.swallow.web.monitor.zookeeper;

/**
 * Author   mingdongli
 * 16/2/22  下午6:11.
 */
public class CuratorConfig {

    private String zkConnect;

    private int zkMaxRetry = 100;

    private int baseSleepTimeMs = 100;

    private int maxSleepTimeMs = 1000;

    public CuratorConfig(String zkConnect){
        this.zkConnect = zkConnect;
    }

    public CuratorConfig(String zkConnect, int zkMaxRetry, int baseSleepTimeMs, int maxSleepTimeMs){
        this.zkConnect = zkConnect;
        this.zkMaxRetry = zkMaxRetry;
        this.baseSleepTimeMs = baseSleepTimeMs;
        this.maxSleepTimeMs = maxSleepTimeMs;
    }

    public String getZkConnect() {
        return zkConnect;
    }

    public int getZkMaxRetry() {
        return zkMaxRetry;
    }

    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs;
    }

    public int getMaxSleepTimeMs() {
        return maxSleepTimeMs;
    }

}
