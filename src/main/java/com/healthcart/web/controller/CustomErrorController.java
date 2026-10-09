package com.healthcart.web.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            try {
                int statusCode = Integer.parseInt(status.toString());
                return resolveView(statusCode, model);
            } catch (NumberFormatException ignored) {
            }
        }

        model.addAttribute("errorCode", "500");
        model.addAttribute("errorTitle", "Application Error");
        model.addAttribute("errorMessage", "An error occurred while handling this request.");
        return "error/500";
    }

    @GetMapping("/error/{code:[0-9]{3}}")
    public String handleExplicitErrorCode(@PathVariable int code, Model model) {
        return resolveView(code, model);
    }

    private String resolveView(int statusCode, Model model) {
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            model.addAttribute("errorCode", "404");
            model.addAttribute("errorTitle", "Page Not Found");
            model.addAttribute("errorMessage", "The requested product, page, or resource could not be found.");
            return "error/404";
        } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
            model.addAttribute("errorCode", "403");
            model.addAttribute("errorTitle", "Access Denied");
            model.addAttribute("errorMessage", "You do not have permission to access this administrative or protected route.");
            return "error/403";
        } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
            model.addAttribute("errorCode", "429");
            model.addAttribute("errorTitle", "Rate Limit Exceeded");
            model.addAttribute("errorMessage", "Too many requests. Please wait before making more requests.");
            return "error/429";
        }

        model.addAttribute("errorCode", "500");
        model.addAttribute("errorTitle", "Application Error");
        model.addAttribute("errorMessage", "An error occurred while handling this request.");
        return "error/500";
    }
}
