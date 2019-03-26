package com.xl.framework.webmvc.servlet;

import java.awt.image.ConvolveOp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.xl.framework.annotation.XLAutowored;
import com.xl.framework.annotation.XLController;
import com.xl.framework.annotation.XLRequestMapping;
import com.xl.framework.annotation.XLRequestParam;
import com.xl.framework.annotation.XLServer;


public class XLDispatcherServlet extends HttpServlet {

	
	private Properties contextConfig = new Properties();
	private List<String> classNames = new ArrayList<String>();
	private Map<String,Object> ioc = new HashMap<String, Object>();
	private Map<String,Method> handlerMapping = new HashMap<String, Method>();
	
	private List<Handler> handlerList = new ArrayList<Handler>();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			// 6.等待请求
			doDispatch(req,resp);
		} catch (Exception e) {
			resp.getWriter().write("500 Exception !");
			System.out.println("请求出现异常，异常信息如下：\n"+e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 *  请求处理
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			// 获取到请求信息
			Handler handler = getHandler(req);
			if(handler == null){
				resp.getWriter().write("404 Not Found !");
				return;
			}
			
			
			Class<?>[] parameterTypes = handler.method.getParameterTypes();
			
			Object [] paramValue = new Object[parameterTypes.length];
			Map<String,String[]> params = req.getParameterMap();
			for (Entry<String, String[]> param : params.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");
				
				if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
				int index = handler.paramIndexMapping.get(param.getKey());
				
				//  重新赋值
				paramValue[index] = (value != null?value:null); 
			}
			
			// 赋值 controller 中设置的 HttpServletRequest 与 HttpServletResponse 
			/**
			 * 该步骤可以做多一步处理，及判断控制层是否有使用 HttpServletRequest 和 HttpServletResponse 如果有则自动注入依赖，否则跳过
			 */
			int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValue[reqIndex] = req;
			int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValue[respIndex] = resp;
			
			handler.method.invoke(handler.controller, paramValue);
		} catch (IllegalAccessException e) {
			resp.getWriter().write("【IllegalAccessException】 500 Exception  !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			resp.getWriter().write("【IllegalArgumentException】 500 Exception !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			resp.getWriter().write("【InvocationTargetException】 500 Exception !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		
		// 启动spring
		// 1. 扫描配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		// 2. 扫描所有相关的类
		doScanner(contextConfig.getProperty("scanPackage"));
		
		// 3. init 所有相关的类
		try {
			doInstance();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// 4. 自动注入
		doAutowored();
		
		// ======== 以下为Spring MVC 内容 =========  //
		
		// 5. init HandlerMapping
		initHandlerMapping();
		
		System.out.println("XL Spring init...");
	}
	
	/**
	 *  handler 请求处理
	 */
	private void initHandlerMapping() {
		// TODO Auto-generated method stub
		if(ioc.isEmpty()){
			System.out.println("IOC 容器中暂无需要自动注入的资源！！！");
			return;
		}
		
		for (Entry<String, Object> entry : ioc.entrySet()) {
			Class<? extends Object> clazz = entry.getValue().getClass();
			if(!clazz.isAnnotationPresent(XLController.class)){ continue; }
			
			String baseUrl = "";
			if(clazz.isAnnotationPresent(XLRequestMapping.class)){
				XLRequestMapping xrm = clazz.getAnnotation(XLRequestMapping.class);
				baseUrl = xrm.value();
			}
			
			for (Method method : clazz.getMethods()) {
				if(!method.isAnnotationPresent(XLRequestMapping.class)){ continue; }
				XLRequestMapping xrm = method.getAnnotation(XLRequestMapping.class);
//				String methodUrl = ("/"+baseUrl+xrm.value()).replaceAll("/+", "/");
				String regex = ("/"+baseUrl+xrm.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(regex);
				
				handlerList.add(new Handler(entry.getValue(),method,pattern));
				
//				handlerMapping.put(methodUrl, method);
				
				System.out.println("Mapping："+regex+"，"+method);
			}
			
		}
		
	}

	/**
	 *  依赖注入
	 */
	private void doAutowored() {
		if (ioc.isEmpty()) { 
			System.out.println("IOC 容器中暂无需要自动注入的资源！！！");
			return;
		}
		
		// 循环IOC容器中所有的类，然后对需要自动赋值的属性自动赋值
		for (Entry<String, Object> entry : ioc.entrySet()) {
			
			// 依赖注入，强制性
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			
			for (Field field : fields) {
				
				if(!field.isAnnotationPresent(XLAutowored.class)){continue;}
				
				XLAutowored autowored = field.getAnnotation(XLAutowored.class);
				
				String beanName = autowored.value().trim();
				
				if("".equals(beanName)){
					beanName = field.getType().getName();
				}
				
				field.setAccessible(true);
				
				try {
					System.out.println(beanName);
					System.out.println(entry.getValue()+"  -------  "+ioc.get(beanName));
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (IllegalAccessException e) {
					System.out.println("访问拒绝，自动注入失败！");
					e.printStackTrace();
					continue;
				}
				
				
			}
			
		}
		
	}

	/**
	 * 实例化扫描到的包
	 * @throws Exception
	 */
	private void doInstance() throws Exception {
		if(classNames.isEmpty()){
			System.out.println("暂无自动扫描类！！！");
			return;
		}
		
		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				// 过滤不用加载的类
				
				if(clazz.isAnnotationPresent(XLController.class)){
					
					// key名默认首字母小写
					String beanName = lowerFirstCase(clazz.getName());
					ioc.put(beanName, clazz.newInstance());
					
				}else if(clazz.isAnnotationPresent(XLServer.class)){
					// 1. 默认采取首字母小写
					// 2. 如果有自定义名称，就首先使用自定义名称
					// 3. 采用父接口名称
					XLServer server = clazz.getAnnotation(XLServer.class); 
					String beanName = server.value();
					if("".equals(beanName)){
						beanName = lowerFirstCase(clazz.getName());
					}
					
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					
					for (Class<?> i : clazz.getInterfaces()) {
						try {
							ioc.put(i.getName(), instance);
						} catch (Exception e) {
							System.out.println("进行初始化Class时出错，有可能是 key 重复了！！！");
							e.printStackTrace();
						}
					}
					
				}else{
					continue;
				}
				
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/**
	 * 扫描包
	 * @param scanPackage
	 */
	private void doScanner(String scanPackage) {
		URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.", "/"));
		File classDir = new File(url.getFile());
		for (File file : classDir.listFiles()) {
			if(file.isDirectory()){
				this.doScanner(scanPackage+"."+file.getName());
			}else{
				String className = scanPackage+"."+file.getName().replaceAll(".class", "");
				classNames.add(className);
			}
		}
	}

	/**
	 *  加载配置文件
	 * @param contextConfigLocation
	 */
	private void doLoadConfig(String contextConfigLocation) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
		try {
			contextConfig.load(is);
		} catch (IOException e) {
			System.out.println(String.format("加载配置文件出现异常【{}.{}】：>>>>>>>>>>>>",
					e.getClass(),
					e.getClass().getMethods()));
			e.printStackTrace();
		} finally{
			if(null != is){
				try {
					is.close();
				} catch (IOException e) {
					System.out.println(String.format("加载配置文件出现异常【{}.{}】：>>>>>>>>>>>>",
							e.getClass(),
							e.getClass().getMethods()));
					e.printStackTrace();
				}
			}
		}
		
	}
 
	/**
	 *  首字母转换为小写
	 * @param string
	 * @return
	 */
	private String lowerFirstCase(String string){
		char[] chars = string.toCharArray();
		chars[0] = (String.valueOf(chars[0]).toLowerCase()).toCharArray()[0];
		return String.valueOf(chars);
	}
	
	/**
	 *  handler 存在参数信息
	 * @author Administrator
	 *
	 */
	private class Handler{
		protected Object controller;
		protected Method method;
		protected Pattern pattern;
		protected Map<String,Integer> paramIndexMapping = new HashMap<String, Integer>();
		
		public Handler(Object controller, Method method, Pattern pattern) {
			super();
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
			putParamIndexMapping(method);
		}
		
		private void putParamIndexMapping(Method method){
			Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			for (int i = 0; i < parameterAnnotations.length; i++) {
				for (Annotation a : parameterAnnotations[i]) {
					if(a instanceof XLRequestParam){
						String paramName = ((XLRequestParam) a).value();
						if(!"".equals(paramName.trim())){
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			Class<?>[] types = method.getParameterTypes();
			for (int i = 0; i < types.length; i++) {
				Class<?> clazz = types[i];
				if(clazz == HttpServletRequest.class || 
						clazz == HttpServletResponse.class){
					paramIndexMapping.put(clazz.getName(), i);
				}
			}
			
		}
		
		
		
	}
	
	/**
	 *  获取handler
	 * @param req
	 * @return
	 */
	private Handler getHandler(HttpServletRequest req){
		
		if(handlerList.isEmpty()){return null;}
		String uri = req.getRequestURI();
		String contextPath = req.getContextPath();
		uri = uri.replace(contextPath, "").replaceAll("/+", "/");
		
		for (Handler handler : handlerList) {
			try {
				Matcher matcher = handler.pattern.matcher(uri);
				if(!matcher.matches()){ continue; }
				return handler;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	
}
