package com.dianping.swallow.web.dao.impl;

import com.dianping.swallow.web.dao.AlarmMetaDao;
import com.dianping.swallow.web.model.alarm.AlarmMeta;
import com.mongodb.WriteResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("alarmMetaDao")
public class DefaultAlarmMetaDao extends AbstractWriteDao implements AlarmMetaDao {

	private static final Logger logger = LogManager.getLogger(DefaultAlarmMetaDao.class);

	private static final String ALARMMETA_COLLECTION = "ALARM_META";

	private static final String METAID_FIELD = "metaId";

	@Override
	public boolean insert(AlarmMeta alarmMeta) {
		try {
			mongoTemplate.save(alarmMeta,ALARMMETA_COLLECTION);
			return true;
		} catch (Exception e) {
			logger.error("Error when save swallow alarm setting " + alarmMeta, e);
		}
		return false;
	}

	@Override
	public boolean update(AlarmMeta alarmMeta) {
		return insert(alarmMeta);
	}

	@Override
	public int deleteByMetaId(int metaId) {
		Query query = new Query(Criteria.where(METAID_FIELD).is(metaId));
		WriteResult result = mongoTemplate.remove(query, AlarmMeta.class, ALARMMETA_COLLECTION);
		return result.getN();
	}

	@Override
	public AlarmMeta findByMetaId(int metaId) {
		Query query = new Query(Criteria.where(METAID_FIELD).is(metaId));
		AlarmMeta alarmMeta = mongoTemplate.findOne(query, AlarmMeta.class, ALARMMETA_COLLECTION);
		return alarmMeta;
	}

	@Override
	public List<AlarmMeta> findByPage(int offset, int limit) {
		Query query = new Query();
		query.skip(offset).limit(limit).with(new Sort(new Sort.Order(Direction.ASC, METAID_FIELD)));
		List<AlarmMeta> alarmMetas = mongoTemplate.find(query, AlarmMeta.class, ALARMMETA_COLLECTION);
		return alarmMetas;
	}

}
