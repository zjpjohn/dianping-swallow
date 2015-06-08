package com.dianping.swallow.web.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dianping.swallow.web.dao.AdministratorDao;
import com.dianping.swallow.web.dao.TopicDao;
import com.dianping.swallow.web.model.Administrator;
import com.dianping.swallow.web.service.AbstractSwallowService;
import com.dianping.swallow.web.service.AdministratorListService;
import com.dianping.swallow.web.service.AdministratorService;
import com.dianping.swallow.web.service.FilterMetaDataService;

/**
 * @author mingdongli
 *
 *         2015年5月14日下午8:04:43
 */
@Service("administratorService")
public class AdministratorServiceImpl extends AbstractSwallowService implements
		AdministratorService {

	private static final String ADMIN = "admin";
	private static final String SIZE = "size";

	@Autowired
	private TopicDao topicDao;

	@Autowired
	private AdministratorDao administratorDao;

	@Resource(name = "filterMetaDataService")
	private FilterMetaDataService filterMetaDataService;

	@Resource(name = "administratorListService")
	private AdministratorListService administratorListService;

	@Override
	public Map<String, Object> queryAllRecordFromAdminList(int offset, int limit) {

		return getFixedAdministratorFromExisting(offset, limit);
	}

	private Map<String, Object> getFixedAdministratorFromExisting(int start,
			int span) {
		Long totalNumOfTopic = administratorDao.countAdministrator();
		List<Administrator> administratorList = administratorDao
				.findFixedAdministrator(start, span);

		return buildResponse(administratorList, totalNumOfTopic);
	}

	private Map<String, Object> buildResponse(
			List<Administrator> administratorList, Long adminSize) {
		Map<String, Object> map = new HashMap<String, Object>();

		map.put(SIZE, adminSize);
		map.put(ADMIN, administratorList);
		return map;
	}

	@Override
	public boolean createInAdminList(String name, int auth) {

		if(auth == 0){
			filterMetaDataService.loadAdminSet().add(name);  //create, need add in adminSet in memory
			logger.info(String.format("Add administrator %s to admin list.", name));
		}
		else{
			filterMetaDataService.loadAdminSet().remove(name);  //edit, need remove in adminSet in memory
			logger.info(String.format("Remove administrator %s from admin list.", name));
		}
		return administratorListService.updateAdmin(name, auth);
	}

	@Override
	public boolean removeFromAdminList(String name) {
		filterMetaDataService.loadAdminSet().remove(name);
		int n = administratorDao.deleteByName(name);
		if (n != 1) {
				logger.info("deleteByName is wrong with name: " + name);
				return false;
		} else {
				logger.info("delete administrator with name [" + name + "]");
				return true;
		}
	}


	@Override
	public Object queryAllNameFromAdminList() {
		List<String> adminLists = new ArrayList<String>();
		List<Administrator> admins = administratorDao.findAll();
		for (int i = 0; i < admins.size(); ++i) {
			adminLists.add(admins.get(i).getName());
		}
		return adminLists;
	}

}
