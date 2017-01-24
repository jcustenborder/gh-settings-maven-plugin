package com.github.jcustenborder.maven.plugins.github;

import org.apache.maven.model.Developer;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "ghsettings")
public class GHSettingsMojo extends AbstractMojo {

  @Parameter(property = "pomFile", defaultValue = "pom.xml")
  private File pomFile;

  private boolean useIssues = true;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!pomFile.exists()) {
      getLog().error(
          String.format("Pom '%s' does not exist.", this.pomFile)
      );
      return;
    }

    MavenXpp3Reader reader = new MavenXpp3Reader();
    Model pom;
    try (FileReader fileReader = new FileReader(this.pomFile)) {
      pom = reader.read(fileReader);
    } catch (IOException ex) {
      throw new MojoFailureException("Exception thrown while reading pom.", ex);
    } catch (XmlPullParserException ex) {
      throw new MojoFailureException("Exception thrown while reading pom.", ex);
    }

    Pattern pattern = Pattern.compile("^https://github.com/(\\S+)/(\\S+)");
    Matcher matcher;

    if (null == pom.getUrl() || pom.getUrl().isEmpty() ||
        !pom.getUrl().startsWith("https://github.com") ||
        !((matcher = pattern.matcher(pom.getUrl())).find())
        ) {
      throw new IllegalStateException(
          String.format("url value '%s' is invalid. url format must be https://github.com/ownerName/repositoryName", pom.getUrl())
      );
    }

    String ownerName = matcher.group(1);
    String repositoryName = matcher.group(2);

    getLog().info(
        String.format("ownerName = %s repositoryName = %s", ownerName, repositoryName)
    );

    try {
      GitHub gitHub = GitHub.connectAnonymously();

      GHRepository repository = gitHub.getRepository(
          String.format("%s/%s", ownerName, repositoryName)
      );

      if (null == pom.getInceptionYear() || pom.getInceptionYear().isEmpty()) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy");
        pom.setInceptionYear(dateFormat.format(repository.getCreatedAt()));

      }

      if (null == pom.getDevelopers() || pom.getDevelopers().isEmpty()) {
        GHUser owner = repository.getOwner();
        Developer developer = new Developer();
        developer.setUrl(owner.getUrl().toString());
        if (null != owner.getEmail() && !owner.getEmail().isEmpty()) {
          developer.setEmail(owner.getEmail());
        }
        developer.setName(owner.getName());
        developer.setRoles(Arrays.asList("maintainer"));
        pom.setDevelopers(Arrays.asList(developer));
      }

      if (useIssues) {
        IssueManagement issueManagement = pom.getIssueManagement();

        if (null == issueManagement) {
          issueManagement = new IssueManagement();
          pom.setIssueManagement(issueManagement);
        }

        issueManagement.setSystem("github");
        issueManagement.setUrl(repository.getUrl() + "/issues");
      }

      if ((null == pom.getLicenses() || pom.getLicenses().isEmpty()) && null != repository.getLicense()) {
        GHLicense ghLicense = repository.getLicense();
        License license = new License();
        license.setName(ghLicense.getName());
        license.setUrl(ghLicense.getUrl().toString());
        license.setDistribution("repo");
        pom.setLicenses(Arrays.asList(license));
      }


      Scm scm = new Scm();
      scm.setUrl(repository.getUrl().toString());
      scm.setConnection(
          String.format("scm:git:%s", repository.gitHttpTransportUrl())
      );
      scm.setDeveloperConnection(
          String.format("scm:git:%s", repository.getSshUrl())
      );
      pom.setScm(scm);

    } catch (IOException e) {
      throw new MojoFailureException("Exception thrown while communicating with github", e);
    }

    MavenXpp3Writer writer = new MavenXpp3Writer();

    try (FileWriter fileWriter = new FileWriter(this.pomFile)) {
      writer.write(fileWriter, pom);
    } catch (IOException ex) {
      throw new MojoFailureException("Exception thrown while writing pom.", ex);
    }

  }
}
