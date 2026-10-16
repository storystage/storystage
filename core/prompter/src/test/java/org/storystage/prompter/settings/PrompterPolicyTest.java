/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PrompterPolicyTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private static Properties props(String... keyValues) {
    Properties p = new Properties();
    for (int i = 0; i < keyValues.length; i += 2) {
      p.setProperty(keyValues[i], keyValues[i + 1]);
    }
    return p;
  }

  @Test
  public void missingFileMeansNoPolicy() {
    PrompterPolicy policy = PrompterPolicy.fromFile(this.folder.getRoot().toPath().resolve("absent.properties"));
    assertFalse(policy.isLockedOff());
    assertFalse(policy.isExplainOnly());
    assertTrue(policy.isVoiceInputAllowed());
  }

  @Test
  public void enabledFalseLocksOff() {
    PrompterPolicy policy = PrompterPolicy.fromProperties(props(PrompterPolicy.KEY_ENABLED, "false"), "test");
    assertTrue(policy.isLockedOff());
    assertFalse(policy.isVoiceInputAllowed());
  }

  @Test
  public void explainOnlyAndNoVoice() {
    PrompterPolicy policy = PrompterPolicy.fromProperties(props(PrompterPolicy.KEY_EXPLAIN_ONLY, "true", PrompterPolicy.KEY_VOICE_INPUT, "false"), "test");
    assertFalse(policy.isLockedOff());
    assertTrue(policy.isExplainOnly());
    assertFalse(policy.isVoiceInputAllowed());
  }

  @Test
  public void unclearValuesChooseTheRestriction() {
    PrompterPolicy policy = PrompterPolicy.fromProperties(props(PrompterPolicy.KEY_ENABLED, "maybe"), "test");
    assertTrue(policy.isLockedOff());
    PrompterPolicy explain = PrompterPolicy.fromProperties(props(PrompterPolicy.KEY_EXPLAIN_ONLY, "yes"), "test");
    assertTrue(explain.isExplainOnly());
  }

  @Test
  public void fileIsRead() throws IOException {
    Path file = this.folder.newFile("policy.properties").toPath();
    Files.write(file, "# school policy\nprompter.explainOnly = true\n".getBytes(StandardCharsets.UTF_8));
    PrompterPolicy policy = PrompterPolicy.fromFile(file);
    assertFalse(policy.isLockedOff());
    assertTrue(policy.isExplainOnly());
  }

  @Test
  public void malformedFileLocksOff() throws IOException {
    Path file = this.folder.newFile("policy.properties").toPath();
    // A lone backslash-u escape is invalid in a properties file.
    Files.write(file, "prompter.enabled = \\u00zz\n".getBytes(StandardCharsets.UTF_8));
    assertTrue(PrompterPolicy.fromFile(file).isLockedOff());
  }

  @Test
  public void policyPathPerPlatform() {
    Map<String, String> env = Collections.singletonMap("PROGRAMDATA", "D:\\Data");
    assertTrue(PrompterPolicy.defaultPolicyPath("Windows 11", env).toString().endsWith("policy.properties"));
    assertTrue(PrompterPolicy.defaultPolicyPath("Windows 11", env).toString().contains("StoryStage"));
    assertEquals("/etc/storystage/policy.properties", PrompterPolicy.defaultPolicyPath("Linux", env).toString().replace('\\', '/'));
    assertTrue(PrompterPolicy.defaultPolicyPath("Mac OS X", env).toString().replace('\\', '/').startsWith("/Library/Application Support/StoryStage"));
    assertNull(PrompterPolicy.defaultPolicyPath("Plan 9", env));
  }
}
