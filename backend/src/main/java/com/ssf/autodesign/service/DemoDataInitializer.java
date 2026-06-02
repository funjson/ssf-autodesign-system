package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.ProjectEntity;
import com.ssf.autodesign.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final boolean enabled;
    private final String workspacePath;

    public DemoDataInitializer(ProjectRepository projectRepository,
                               ProjectService projectService,
                               @Value("${ssf.demo.enabled:true}") boolean enabled,
                               @Value("${ssf.demo.workspace-path:../demo/ssf-workspace}") String workspacePath) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.enabled = enabled;
        this.workspacePath = workspacePath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Path demoWorkspace = resolveWorkspacePath();
        if (!Files.isDirectory(demoWorkspace.resolve("instances"))) {
            return;
        }

        ProjectEntity demo = projectRepository.findFirstByDemoProjectTrue()
                .orElseGet(() -> {
                    projectService.createDemoProject("DailyBrief Horizon 演示项目");
                    return projectRepository.findFirstByDemoProjectTrue()
                            .orElseThrow(() -> new IllegalStateException("Demo project was not created"));
                });
        projectService.addManagedWorkspace(demo.getId(), demoWorkspace, "demo-ssf-workspace", "demo");
    }

    private Path resolveWorkspacePath() {
        Path configured = Path.of(workspacePath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(configured).normalize();
    }
}
