package com.dianping.swallow.web.model.cmdb;

import org.springframework.data.annotation.Id;

public class IpDesc {

	@Id
	private String id;

	private String ip;

	private String name;

	private String email;

	private String opManager;

	private String opMobile;

	private String opEmail;

	private String dpManager;

	private String dpMobile;

	private String createTime;

	private String updateTime;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getOpManager() {
		return opManager;
	}

	public void setOpManager(String opManager) {
		this.opManager = opManager;
	}

	public String getOpMobile() {
		return opMobile;
	}

	public void setOpMobile(String opMobile) {
		this.opMobile = opMobile;
	}

	public String getOpEmail() {
		return opEmail;
	}

	public void setOpEmail(String opEmail) {
		this.opEmail = opEmail;
	}

	public String getDpManager() {
		return dpManager;
	}

	public void setDpManager(String dpManager) {
		this.dpManager = dpManager;
	}

	public String getDpMobile() {
		return dpMobile;
	}

	public void setDpMobile(String dpMobile) {
		this.dpMobile = dpMobile;
	}

	public String getCreateTime() {
		return createTime;
	}

	public void setCreateTime(String createTime) {
		this.createTime = createTime;
	}

	public String getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(String updateTime) {
		this.updateTime = updateTime;
	}

	@Override
	public String toString() {
		return "IpDesc [id = " + id + ", ip = " + ip + ", name = " + name + ", email = " + email + ", opManager = "
				+ opManager + ", opMobile = " + opMobile + ", opEmail = " + opEmail + ", dpManager = " + dpManager
				+ ", dpMobile = " + dpMobile + ", createTime = " + createTime + ", updateTime = " + updateTime + "]";
	}
	
}
