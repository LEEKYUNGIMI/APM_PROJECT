package com.leekyungmi.apm.sdk.interceptor;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.net.InetAddress;

public class ApmInterceptor implements HandlerInterceptor {

    private final ApmEventSender sender;
    private final String appId;

    public ApmInterceptor(ApmEventSender sender, String appId) {
        this.sender = sender;
        this.appId = appId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("ApmInterceptor preHandle");
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("ApmInterceptor postHandle");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        System.out.println("ApmInterceptor afterCompletion");
        long startTime = (Long) request.getAttribute("startTime");
        int durationMs = (int) (System.currentTimeMillis() - startTime);
        int statusCode = response.getStatus();

        String type = ex != null ? "ERROR" : durationMs > 2000 ? "SLOW_QUERY" : "HTTP_REQUEST";

        String serverIp= getServerIp();

        sender.send(appId, type, request.getRequestURI(), request.getMethod(), statusCode, durationMs, ex != null ? ex.getMessage():null, serverIp);
    }

    private String getServerIp () {
        try{
            return InetAddress.getLocalHost().getHostAddress();
        }catch (Exception e){
            return "unknown";
        }
    }
}
