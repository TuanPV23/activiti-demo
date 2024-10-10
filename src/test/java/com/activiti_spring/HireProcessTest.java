package com.activiti_spring;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.test.
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.subethamail.wiser.Wiser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@SpringApplicationConfiguration(classes = {ActivitiSpringApplication.class})
@IntegrationTest
public class HireProcessTest {

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private RepositoryService repositoryService;

    private Wiser wiser;
    @Autowired
    private ApplicantRepository applicantRepository;

    @Before
    public void setUp() {
        wiser = new Wiser();
        wiser.setPort(1025);
        wiser.start();
    }

    @After
    public void cleanUp() {
        wiser.stop();
    }

    @Test
    public void testHireProcess() {
        // Create test applicant
        Applicant applicant = new Applicant("Tuan Phi", "tuan2000d@gmail.com", "12345");
        applicantRepository.save(applicant);

        //Start process instance
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicant", applicant);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("hireProcess", variables);

        // First, the "phone interview" should be active
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskCandidateGroup("dev-managers")
                .singleResult();
        Assert.assertEquals("Telephone interview", task.getName());

        // Completing the phone interview with success should trigger 2 new tasks
        Map<String, Object> taskVariables = new HashMap<>();
        taskVariables.put("telephoneInterviewOutcome", true);
        taskService.complete(task.getId(), taskVariables);

        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .orderByTaskName().asc()
                .list();
            Assert.assertEquals(2, tasks.size());
            Assert.assertEquals("Financial negotiation", tasks.get(0).getName());
            Assert.assertEquals("Technical interview", tasks.get(1).getName());

        // Completing both should wrap up the subprocess, send out the 'welcome mail' and end the process instance
        taskVariables = new HashMap<String, Object>();
            taskVariables.put("tech is ok", true);
            taskService.complete(tasks.get(0).getId(), taskVariables);

        taskVariables = new HashMap<String, Object>();
            taskVariables.put("finance is ok", true);
            taskService.complete(tasks.get(1).getId(), taskVariables);

        //Verify Email
        Assert.assertEquals(1, wiser.getMessages().size());
        //Verify process completed
        Assert.assertEquals(1, historyService.createHistoricProcessInstanceQuery().finished().count());
    }
}
