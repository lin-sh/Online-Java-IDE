package com.example.online_java_ide.controller;

import com.example.online_java_ide.service.ExecuteStringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RunCodeController {
    private Logger logger = LoggerFactory.getLogger(RunCodeController.class);

    @Autowired
    private ExecuteStringService executeStringService;

    private static final String defaultSource = "public class Run {\n"
            + "    public static void main(String[] args) {\n"
            + "        \n"
            + "    }\n"
            + "}";

    @RequestMapping(path = {"/"}, method = RequestMethod.GET)
    public String entry(Model model) {
        model.addAttribute("lastSource", defaultSource);
        return "ide";
    }

    @RequestMapping(path = {"/run"}, method = RequestMethod.POST)
    public String runCode(@RequestParam("source") String source,
                          @RequestParam("systemIn") String systemIn, Model model) {
        String runResult = executeStringService.executeString(source, systemIn);
        runResult = runResult.replaceAll(System.lineSeparator(), "<br/");

        model.addAttribute("lastSource", source);
        model.addAttribute("lastSystemIn", systemIn);
        model.addAttribute("runResult", runResult);
        return "ide";
    }

}
