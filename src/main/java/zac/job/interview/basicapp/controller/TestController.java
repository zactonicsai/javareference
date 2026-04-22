package zac.job.interview.basicapp.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/error")
    public String throwError() {
        throw new RuntimeException("Something went wrong!");
    }

    @GetMapping("/bad")
    public String bad() {
        throw new IllegalArgumentException("Bad input!");
    }

    @PostMapping("/validate")
    public String validate(@RequestBody TestRequest request) {
        return "OK";
    }

    record TestRequest(@NotBlank String name) {}
}