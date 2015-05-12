package com.dianping.swallow.web.dao.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.dianping.swallow.web.dao.TopicDao;
import com.dianping.swallow.web.model.Topic;
import com.mongodb.WriteResult;
 

/**
 * @author mingdongli
 *
 * 2015年4月20日 下午9:32:19
 */
public class DefaultTopicDao extends AbstractWriteDao implements TopicDao {
 
    //集合名字
    private static final String 			TOPIC_COLLECTION =					"swallowwebtopicc";
    
    private static final String 			NAME =								"name";
    private static final String 			PROP =								"prop";
    private static final String 			DEPT =								"dept";
    private static final String 			ID =								"_id";
     
    @Override
    public Topic readById(String id) {
        Query query = new Query(Criteria.where(ID).is(id));
        return mongoTemplate.findOne(query, Topic.class, TOPIC_COLLECTION);
    }
    
    @Override
    public Topic readByName(String name) {
        Query query = new Query(Criteria.where(NAME).is(name));
        return mongoTemplate.findOne(query, Topic.class, TOPIC_COLLECTION);
    }
 
    @Override
    public void saveTopic(Topic p) {
        mongoTemplate.save(p, TOPIC_COLLECTION);
    }
    
	@Override
	public void updateTopic(String name, String prop, String dept, String time){
    	Topic topic = readByName(name);
    	topic.setProp(prop);
    	topic.setDept(dept);
    	topic.setTime(time);
    	saveTopic(topic);
    	return;
    }
 
    @Override
    public int deleteById(String id) {
        Query query = new Query(Criteria.where(ID).is(id));
        WriteResult result = mongoTemplate.remove(query, Topic.class, TOPIC_COLLECTION);
        return result.getN();
    }
  
    @Override
	public void dropCol(){
    	mongoTemplate.dropCollection(TOPIC_COLLECTION);
    }
    
    @Override
    public List<Topic> findAll(){
    	return mongoTemplate.findAll(Topic.class, TOPIC_COLLECTION);
    }
    
    @Override
	public long countTopic(){
    	Query query = new Query();
    	return mongoTemplate.count(query, TOPIC_COLLECTION);
    }
    
    @Override
    public List<Topic> findFixedTopic(int offset, int limit){
        Query query = new Query();  
        query.skip(offset).limit(limit).with(new Sort(new Sort.Order(Direction.ASC, "name"))); //根据name字段排序
        return mongoTemplate.find(query, Topic.class, TOPIC_COLLECTION);
    }
    
    @Override
	public List<Topic> findSpecific(String name, String prop, String dept){
		List<Topic> records = new ArrayList<Topic>();
	    String namer = name.isEmpty() ? ".*" : name;
	    String propr = prop.isEmpty() ? ".*" : prop;
	    String deptr = dept.isEmpty() ? ".*" : dept;
		Query query = new Query(Criteria.where(NAME).regex("^" + namer).and(PROP).regex(propr).and(DEPT).regex(deptr));
    	records = mongoTemplate.find(query, Topic.class, TOPIC_COLLECTION);
    	return records;
    }
 
}