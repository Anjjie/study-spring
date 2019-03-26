package com.xl.dome.service;

import com.xl.framework.annotation.XLServer;

@XLServer
public class DomeService {

	public String common(String content){
		return "hello,"+content+"!";
		
	}
	
}
