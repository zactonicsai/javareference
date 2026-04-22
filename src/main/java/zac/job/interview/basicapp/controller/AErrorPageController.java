package zac.job.interview.basicapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AErrorPageController {

    @RequestMapping("/aerror")
    public String error() {
        return "redirect:/error.html";
    }
}