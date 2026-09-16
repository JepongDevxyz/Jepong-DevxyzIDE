import com.jepongdevxyz.idebuild.core.build.ProjectTemplateGenerator;

import java.io.File;

/** Small CI utility that materializes DevxyzIDE's production templates verbatim. */
public final class GenerateTemplateProject {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: GenerateTemplateProject <parent> <java|kotlin> <applicationId>");
        }
        File parent = new File(args[0]);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Could not create output parent");
        ProjectTemplateGenerator.Template template;
        String projectName;
        if ("java".equals(args[1])) {
            template = ProjectTemplateGenerator.Template.MODERN_ANDROIDX_JAVA;
            projectName = "DevxyzJavaSample";
        } else if ("kotlin".equals(args[1])) {
            template = ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN;
            projectName = "DevxyzKotlinSample";
        } else {
            throw new IllegalArgumentException("Unknown template: " + args[1]);
        }
        File project = ProjectTemplateGenerator.create(parent, projectName, args[2], template);
        System.out.println(project.getCanonicalPath());
    }
}
