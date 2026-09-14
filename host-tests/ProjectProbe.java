import com.jepongdevxyz.idebuild.core.build.JvmCompatibility;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;

import java.io.File;

public final class ProjectProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ProjectProbe <project-root>");
        ProjectRequirements req = ProjectAnalyzer.analyze(new File(args[0]));
        System.out.println(req.summary());
        System.out.println("wrapperComplete=" + req.isWrapperComplete());
        System.out.println("recommendedJdk=" + JvmCompatibility.recommendedJavaMajor(req.getAgpVersion(), req.getGradleVersion()));
        System.out.println("repositories=" + req.getRepositories());
        for (String warning : req.getWarnings()) System.out.println("warning=" + warning);
    }
}
