package jetbrains.buildServer.deployer.agent.ssh;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.artifacts.ArtifactsCollection;
import jetbrains.buildServer.agent.ssh.AgentRunningBuildSshKeyManager;
import jetbrains.buildServer.deployer.agent.BaseDeployerTest;
import jetbrains.buildServer.deployer.common.DeployerRunnerConstants;
import jetbrains.buildServer.deployer.common.SSHRunnerConstants;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.filesystem.NativeFileSystemFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.security.PublicKey;
import java.util.*;

/**
 * Created by User
 * date: 29.07.13.
 */
public class BaseSSHTest extends BaseDeployerTest {

  protected static final int PORT_NUM = 15655;
  protected static final String HOST_ADDR = "127.0.0.1";
  protected final Map<String, String> myRunnerParams = new HashMap<String, String>();
  protected final Map<String, String> myInternalProperties = new HashMap<String, String>();

  protected final InternalPropertiesHolder myInternalPropertiesHolder = new InternalPropertiesHolder() {
    @Nullable
    @Override
    public String getInternalProperty(@NotNull String s, String s2) {
      return myInternalProperties.get(s) != null ? myInternalProperties.get(s) : s2;
    }
  };

  protected File myWorkingDir;
  protected String myUsername = "testuser";
  protected String myPassword = "testpassword";
  protected List<ArtifactsCollection> myArtifactsCollections;
  protected BuildRunnerContext myContext;
  protected File myPassphraselessKey;
  protected File myPrivateKey;
  protected File myRemoteDir = null;
  protected String oldUserDir = null;
  protected SshServer myServer;

  protected AgentRunningBuildSshKeyManager mySshKeyManager;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myRemoteDir = myTempFiles.createTempDir();

    myServer = SshServer.setUpDefaultServer();
    myServer.setPort(BaseSSHTest.PORT_NUM);
    myServer.setCommandFactory(new ScpCommandFactory());
    myServer.setShellFactory(new ProcessShellFactory(new String[]{SystemInfo.isWindows ? "cmd" : "sh"}));
    myServer.setPasswordAuthenticator(new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) {
        return myUsername.equals(username) && myPassword.equals(password);
      }
    });

    final File keyFile = getTestResource("hostkey.pem");
    myPrivateKey = getTestResource("tmp_rsa").getAbsoluteFile();
    myPassphraselessKey = getTestResource("passphraseless").getAbsoluteFile();

    myServer.setPublickeyAuthenticator(new PublickeyAuthenticator() {
      @Override
      public boolean authenticate(String username, PublicKey key, ServerSession session) {
        return true;
      }
    });
    myServer.setKeyPairProvider(new FileKeyPairProvider(new String[]{keyFile.getCanonicalPath()}));
    myServer.setFileSystemFactory(new NativeFileSystemFactory());
    myServer.setSubsystemFactories(Arrays.<NamedFactory<Command>>asList(new SftpSubsystem.Factory()));

    myServer.start();

    Mockery mockeryCtx = new Mockery();
    myContext = mockeryCtx.mock(BuildRunnerContext.class);
    final AgentRunningBuild build = mockeryCtx.mock(AgentRunningBuild.class);
    final BuildProgressLogger logger = new NullBuildProgressLogger();
    myWorkingDir = myTempFiles.createTempDir();

    mySshKeyManager = mockeryCtx.mock(AgentRunningBuildSshKeyManager.class);
    final TeamCitySshKey sshKey = new TeamCitySshKey("Name", FileUtil.loadFileBytes(myPrivateKey), false);

    mockeryCtx.checking(new Expectations() {{
      allowing(myContext).getWorkingDirectory();
      will(returnValue(myWorkingDir));
      allowing(myContext).getBuild();
      will(returnValue(build));
      allowing(myContext).getRunnerParameters();
      will(returnValue(myRunnerParams));
      allowing(build).getBuildLogger();
      will(returnValue(logger));
      allowing(build).getCheckoutDirectory();
      will(returnValue(myWorkingDir));
      allowing(mySshKeyManager).getKey("key_id_value");
      will(returnValue(sshKey));
    }});

    // need to change user.dir, so that NativeFileSystemFactory works inside temp directory
    oldUserDir = System.getProperty("user.dir");
    System.setProperty("user.dir", myRemoteDir.getAbsolutePath());

    myArtifactsCollections = new ArrayList<ArtifactsCollection>();


    myRunnerParams.put(SSHRunnerConstants.PARAM_AUTH_METHOD, SSHRunnerConstants.AUTH_METHOD_USERNAME_PWD);
    myRunnerParams.put(DeployerRunnerConstants.PARAM_USERNAME, myUsername);
    myRunnerParams.put(DeployerRunnerConstants.PARAM_PASSWORD, myPassword);

    myRunnerParams.put(DeployerRunnerConstants.PARAM_TARGET_URL, HOST_ADDR);
    myRunnerParams.put(SSHRunnerConstants.PARAM_PORT, String.valueOf(PORT_NUM));
  }

  @AfterMethod
  @Override
  public void tearDown() throws Exception {
    myServer.stop(true);
    System.setProperty("user.dir", oldUserDir);
    super.tearDown();
  }
}
