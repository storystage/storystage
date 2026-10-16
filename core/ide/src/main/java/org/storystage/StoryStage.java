/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage;

import org.lgna.project.ProjectVersion;

/**
 * Product identity for StoryStage. Alice's own {@link ProjectVersion} stays unchanged, because it
 * marks the project file format and must match Alice for worlds to open in both.
 */
public final class StoryStage {
  public static final String NAME = "StoryStage";
  public static final String VERSION = "0.1.0";
  public static final String WEBSITE = "https://storystage.org.uk";
  public static final String REPOSITORY = "https://github.com/storystage/storystage";
  public static final String NEW_ISSUE_URL = REPOSITORY + "/issues/new";

  private StoryStage() {
    throw new AssertionError();
  }

  /** The Alice release StoryStage is based on, for example "3.9.1.0". */
  public static String getAliceBaseVersion() {
    return ProjectVersion.getCurrentVersionText();
  }
}
