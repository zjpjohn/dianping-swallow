package com.dianping.swallow.web.monitor.impl;

import com.dianping.swallow.common.internal.action.SwallowAction;
import com.dianping.swallow.common.internal.action.SwallowActionWrapper;
import com.dianping.swallow.common.internal.action.impl.CatActionWrapper;
import com.dianping.swallow.common.internal.exception.SwallowException;
import com.dianping.swallow.common.internal.monitor.Mergeable;
import com.dianping.swallow.common.server.monitor.collector.AbstractCollector;
import com.dianping.swallow.common.server.monitor.data.QPX;
import com.dianping.swallow.common.server.monitor.data.StatisType;
import com.dianping.swallow.common.server.monitor.data.Statisable;
import com.dianping.swallow.common.server.monitor.data.Statisable.QpxData;
import com.dianping.swallow.common.server.monitor.data.statis.AbstractAllData;
import com.dianping.swallow.common.server.monitor.data.statis.AbstractTotalMapStatisable;
import com.dianping.swallow.common.server.monitor.data.statis.CasKeys;
import com.dianping.swallow.common.server.monitor.data.statis.UnfoundKeyException;
import com.dianping.swallow.common.server.monitor.data.structure.MonitorData;
import com.dianping.swallow.common.server.monitor.data.structure.TotalMap;
import com.dianping.swallow.web.monitor.MonitorDataListener;
import com.dianping.swallow.web.monitor.MonitorDataRetriever;
import com.dianping.swallow.web.monitor.StatsData;
import com.dianping.swallow.web.monitor.StatsDataDesc;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * @author mengwenchao
 *         <p/>
 *         2015年4月21日 上午11:04:30
 */
public abstract class AbstractMonitorDataRetriever<M extends Mergeable, T extends TotalMap<M>, S extends AbstractTotalMapStatisable<M, T>, V extends MonitorData>
        extends AbstractRetriever implements MonitorDataRetriever {

    private List<MonitorDataListener> statisListeners = new ArrayList<MonitorDataListener>();

    protected AbstractAllData<M, T, S, V> statis;

    private int intervalCount;

    @PostConstruct
    public void postAbstractMonitorDataStats() {

        keepInMemoryCount = keepInMemoryHour * 3600 / AbstractCollector.SEND_INTERVAL;
        intervalCount = getSampleIntervalCount();

        statis = createServerStatis();
    }

    public String getDebugInfo(String server) {

        return statis.toString(server);
    }

    @Override
    protected void doBuild() {

        if (getKey(lastBuildTime) >= getKey(current)) {
            logger.warn("[doBuild][lastBuildTime key >= current key]" + lastBuildTime + "," + current);
            return;
        }

        SwallowActionWrapper catWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName() + "-doBuild");
        catWrapper.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {

                statis.build(QPX.SECOND, getKey(lastBuildTime), getKey(current), intervalCount);
            }
        });
        // 通知监听者
        doChangeNotify();
    }

    @Override
    protected void doRemove(final long toKey) {

        SwallowActionWrapper catWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName() + "-doRemove");
        catWrapper.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {
                statis.removeBefore(toKey);
            }
        });
    }

    protected abstract AbstractAllData<M, T, S, V> createServerStatis();

    protected StatsData getQpxInDb(String topic, StatisType type, long start, long end) {

        return getQpxInMemory(topic, type, start, end);
    }

    protected StatsData getDelayInDb(String topic, StatisType type, long start, long end) {

        return getDelayInMemory(topic, type, start, end);
    }

    protected StatsData getDelayInMemory(String topic, StatisType type, long start, long end) {

        NavigableMap<Long, Long> rawData = statis.getDelayForTopic(topic, type);
        if (rawData != null) {
            rawData = rawData.subMap(getKey(start), true, getKey(end), true);
        }
        return createStatsData(createDelayDesc(topic, type), rawData, start, end);
    }

    protected StatsData getIpDelayInMemory(String topic, String ip, StatisType type, long start, long end) {
        NavigableMap<Long, Long> rawData = statis.getDelayValue(new CasKeys(TOTAL_KEY, topic, ip), type);
        if (rawData != null) {
            rawData = rawData.subMap(getKey(start), true, getKey(end), true);
        }
        return createStatsData(createDelayDesc(topic, type), rawData, start, end);
    }

    protected StatsData getQpxInMemory(String topic, StatisType type, long start, long end) {

        NavigableMap<Long, Long> rawData = convertQpxData(statis.getQpxForTopic(topic, type));
        if (rawData != null) {
            rawData = rawData.subMap(getKey(start), true, getKey(end), true);
        }
        return createStatsData(createQpxDesc(topic, type), rawData, start, end);
    }

    protected StatsData getIpQpxInMemory(String topic, String ip, StatisType type, long start, long end) {
        NavigableMap<Long, QpxData> ipQpx = statis.getQpsValue(new CasKeys(TOTAL_KEY, topic, ip), type);
        NavigableMap<Long, Long> rawData = convertQpxData(ipQpx);
        if (rawData != null) {
            rawData = rawData.subMap(getKey(start), true, getKey(end), true);
        }
        return createStatsData(createDelayDesc(topic, type), rawData, start, end);
    }

    protected Map<String, StatsData> getServerQpxInMemory(QPX qpx, StatisType type, long start, long end) {

        Map<String, StatsData> result = new HashMap<String, StatsData>();

        Map<String, NavigableMap<Long, QpxData>> serversQpx = statis.getQpxForServers(type);

        for (Entry<String, NavigableMap<Long, QpxData>> entry : serversQpx.entrySet()) {

            String serverIp = entry.getKey();
            NavigableMap<Long, Long> serverQpx = convertQpxData(entry.getValue());
            if (serverQpx != null) {
                serverQpx = serverQpx.subMap(getKey(start), true, getKey(end), true);
            }
            result.put(serverIp, createStatsData(createServerQpxDesc(serverIp, type), serverQpx, start, end));
        }

        return result;
    }

    protected abstract StatsDataDesc createServerQpxDesc(String serverIp, StatisType type);

    protected abstract StatsDataDesc createServerDelayDesc(String serverIp, StatisType type);

    protected abstract StatsDataDesc createDelayDesc(String topic, StatisType type);

    //protected abstract StatsDataDesc createDelayDesc(String topic, String ip, StatisType type);

    protected abstract StatsDataDesc createQpxDesc(String topic, StatisType type);

    /**
     * 以发送消息的时间间隔为间隔，进行时间对齐
     *
     * @param currentTime
     * @return
     */
    protected static Long getCeilingTime(long currentTime) {

        return currentTime / 1000 / AbstractCollector.SEND_INTERVAL;
    }

    protected Set<String> getTopicsInMemory(long start, long end) {

        return statis.getTopics(true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void add(final MonitorData monitorData) {

        SwallowActionWrapper catWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName() + "-doAdd");
        catWrapper.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {

                statis.add(monitorData.getKey(), (V) monitorData);
            }
        });
    }

    @Override
    public void registerListener(MonitorDataListener statisListener) {
        statisListeners.add(statisListener);
    }

    protected void doChangeNotify() {
        SwallowActionWrapper catWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName() + "-doChangeNotify");
        catWrapper.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {

                for (MonitorDataListener statisListener : statisListeners) {
                    statisListener.achieveMonitorData();
                }
            }
        });

    }

    @Override
    public Set<String> getKeys(CasKeys keys, StatisType type) {
        try {
            return statis.getKeys(keys, type);
        } catch (UnfoundKeyException e) {
            return null;
        }
    }

    @Override
    public NavigableMap<Long, Long> getDelayValue(CasKeys keys, StatisType type) {
        try {
            return statis.getDelayValue(keys, type);
        } catch (UnfoundKeyException e) {
            return null;
        }
    }

    @Override
    public NavigableMap<Long, Statisable.QpxData> getQpsValue(CasKeys keys, StatisType type) {

        try {
            return statis.getQpsValue(keys, type);
        } catch (UnfoundKeyException e) {
            return null;
        }
    }

    @Override
    public Set<String> getKeys(CasKeys keys) {
        return getKeys(keys, null);
    }

    protected NavigableMap<Long, Long> convertQpxData(NavigableMap<Long, QpxData> qpxDatas) {
        if (qpxDatas != null) {
            NavigableMap<Long, Long> qpxMap = new ConcurrentSkipListMap<Long, Long>();
            for (Entry<Long, QpxData> entry : qpxDatas.entrySet()) {
                qpxMap.put(entry.getKey(), entry.getValue().getQpx());
            }
            return qpxMap;
        }
        return null;
    }
}
