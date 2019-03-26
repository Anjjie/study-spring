package com.xl.dome.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.xl.dome.service.DomeService;
import com.xl.framework.annotation.XLAutowored;
import com.xl.framework.annotation.XLController;
import com.xl.framework.annotation.XLRequestMapping;
import com.xl.framework.annotation.XLRequestParam;

@XLController
@XLRequestMapping("/dome")
public class DomeController {
	
	@XLAutowored private DomeService domeServer ;
	
	@XLRequestMapping("/query")
	public void query(HttpServletRequest req,HttpServletResponse resp) throws IOException{
		resp.getWriter().write(domeServer.common("I am query Interface !"));
	}
	
	@XLRequestMapping("/add")
	public void add(HttpServletRequest req,HttpServletResponse resp,
			@XLRequestParam("name") String name ) throws IOException{
		resp.getWriter().write(domeServer.common("I am add Interface , Name is "+name));
	}
	
	@XLRequestMapping("/remove")
	public void remove(HttpServletRequest req,HttpServletResponse resp) throws IOException{
		resp.getWriter().write( domeServer.common("I am remove Interface !"));
	}
	
	
	
}
