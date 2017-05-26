package com.dsdl.eidea.common.web.controller;

import com.dsdl.eidea.base.def.OperatorDef;
import com.dsdl.eidea.base.entity.bo.UserBo;
import com.dsdl.eidea.base.entity.bo.UserSessionBo;
import com.dsdl.eidea.base.service.PageMenuService;
import com.dsdl.eidea.base.service.UserService;
import com.dsdl.eidea.base.service.UserSessionService;
import com.dsdl.eidea.base.web.content.UserOnlineContent;
import com.dsdl.eidea.base.entity.bo.UserContent;
import com.dsdl.eidea.base.web.vo.UserResource;
import com.dsdl.eidea.core.entity.bo.LanguageBo;
import com.dsdl.eidea.core.i18n.DbResourceBundle;
import com.dsdl.eidea.core.service.LanguageService;
import com.dsdl.eidea.core.service.MessageService;
import com.dsdl.eidea.core.web.def.WebConst;
import com.dsdl.eidea.core.web.result.JsonResult;
import com.dsdl.eidea.core.web.result.def.ErrorCodes;
import com.dsdl.eidea.core.web.result.def.ResultCode;
import com.dsdl.eidea.util.LocaleHelper;
import com.dsdl.eidea.util.StringUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.util.*;

@Slf4j
@RestController
public class UserLoginController {
    private Gson gson=new Gson();
    private static final Logger logger = Logger.getLogger(UserLoginController.class);
    @Autowired
    private UserService userService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private PageMenuService pageMenuService;
    @Autowired
    private HttpServletResponse response;
    @Autowired
    private HttpServletRequest request;
    @Autowired
    private LanguageService languageService;
    @Autowired
    private UserSessionService userSessionService;


    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public JsonResult<String> login(@RequestBody UserBo loginBo) {
        UserResource resource = (UserResource) request.getSession().getAttribute(WebConst.SESSION_RESOURCE);
        if (loginBo == null) {
            return JsonResult.fail(ResultCode.FAILURE.getCode(), resource.getMessage("user.msg.name.password.is.not.null"));
        } else {
            if (StringUtil.isEmpty(loginBo.getUsername())) {
                return JsonResult.fail(ResultCode.FAILURE.getCode(), resource.getMessage("user.msg.name.is.not.null"));
            }
            if (StringUtil.isEmpty(loginBo.getPassword())) {
                return JsonResult.fail(ResultCode.FAILURE.getCode(), resource.getMessage("user.msg.password.is.not.null"));
            }
        }
        Subject subject = SecurityUtils.getSubject();
        UsernamePasswordToken token = new UsernamePasswordToken(loginBo.getUsername(), loginBo.getPassword());
        try {
            subject.login(token);
            UserBo userBo=userService.getUserByUsername(loginBo.getUsername());
            userInitCommon(loginBo);
            userBo.setCode(loginBo.getCode());
            userInit(userBo, false, request);
            return JsonResult.success(resource.getMessage("user.msg.user.login.successful"));
        } catch (IncorrectCredentialsException | UnknownAccountException e) {
            return JsonResult.fail(ErrorCodes.NO_LOGIN.getCode(), resource.getMessage("user.msg.name.password.is.error"));
        } catch (LockedAccountException e) {
            return JsonResult.fail(ErrorCodes.NO_LOGIN.getCode(), resource.getMessage("user.msg.user.is.disable"));
        }catch (AuthenticationException e){
            return JsonResult.fail(ErrorCodes.NO_LOGIN.getCode(), resource.getMessage("user.msg.name.password.is.error"));
        }





    }

    private void userInitCommon(UserBo loginBo) {

        Cookie cookie = new Cookie("userName", loginBo.getUsername());
        cookie.setMaxAge(60 * 60 * 24 * 7);
        response.addCookie(cookie);
        HttpSession session = request.getSession();
        try {
            InetAddress addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress().toString();//获得本机IP
            String address = addr.getHostName().toString();//获得本机名称
            session.setAttribute("localIpAddress", ip);
            session.setAttribute("localHostName", address);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void userInit(UserBo loginUser, boolean beSub, HttpServletRequest request) {
        HttpSession session = request.getSession();
        /*Get user privileges*/

        if (beSub == false)
            session.setAttribute(WebConst.SESSION_LOGINUSER, loginUser);
        session.setAttribute(WebConst.SESSION_ACTUALUSER, loginUser);
        String ip = null;
        String address = null;
        try {
            InetAddress addr = InetAddress.getLocalHost();
            ip = addr.getHostAddress().toString();//获得本机IP
            address = addr.getHostName().toString();//获得本机名称
            session.setAttribute("localIpAddress", ip);
            session.setAttribute("localHostName", address);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Map<String, List<OperatorDef>> privilegesMap = userService.getUserPrivileges(loginUser.getId());
        String token = userService.generateToken(loginUser);
        UserSessionBo userSessionBo = new UserSessionBo();
        userSessionBo.setLoginDate(new Date());
        userSessionBo.setRemoteAddr(ip);
        userSessionBo.setSessionId(request.getSession().getId());
        userSessionBo.setRemoteHost(address);
        userSessionBo.setUsername(loginUser.getUsername());
        userSessionBo.setUserId(loginUser.getId());
        userSessionBo.setToken(token);
        UserOnlineContent.addUser(session.getId(), userSessionBo);
        userSessionBo = userService.saveUserSessionBo(userSessionBo);
        List<Integer> orgIdList = userService.getCanAccessOrgList(loginUser.getId());
        UserContent userContent = new UserContent(privilegesMap, userSessionBo, token, orgIdList);
        session.setAttribute(WebConst.SESSION_USERCONTENT, userContent);
        String contextPath = request.getServletContext().getContextPath();

        Locale locale = LocaleHelper.parseLocale(loginUser.getCode());
        if (request.getSession().getAttribute(WebConst.SESSION_RESOURCE) == null) {

            DbResourceBundle dbResourceBundle = messageService.getResourceBundle(loginUser.getCode());
            UserResource userResource = new UserResource(locale, dbResourceBundle);
            session.setAttribute(WebConst.SESSION_RESOURCE, userResource);
        }
        String leftMenuStr = pageMenuService.getLeftMenuListByUserId(loginUser.getId(), contextPath,locale.toString());
        session.setAttribute(WebConst.SESSION_LEFTMENU, leftMenuStr);

    }

    /**
     * 登出系统
     *
     * @param session
     * @return
     */
    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public ModelAndView logout(HttpSession session) {
        UserResource resource = (UserResource) request.getSession().getAttribute(WebConst.SESSION_RESOURCE);
        session.removeAttribute(WebConst.SESSION_LOGINUSER);
        session.removeAttribute(WebConst.SESSION_USERCONTENT);
        session.removeAttribute(WebConst.SESSION_RESOURCE);
        session.removeAttribute(WebConst.SESSION_LEFTMENU);
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated()) {
            subject.logout(); // session 会销毁，在SessionListener监听session销毁，清理权限缓存
            if (log.isDebugEnabled()) {
                log.debug(resource.getMessage("user.msg.user") + subject.getPrincipal() + resource.getMessage("user.msg.user.log.out"));
            }
        }
        ModelAndView modelAndView = new ModelAndView("redirect:/login.jsp");
        return modelAndView;
    }

    /**
     * getLanguageForActivated:登录页面语种
     *
     * @return
     */
    @RequestMapping(value = "/languages", method = RequestMethod.GET)
    public JsonResult<List<LanguageBo>> getLanguage() {
        List<LanguageBo> languageList = languageService.getLanguageForActivated();
        return JsonResult.success(languageList);
    }
}