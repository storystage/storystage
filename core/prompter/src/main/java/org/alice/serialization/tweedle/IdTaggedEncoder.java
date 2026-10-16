/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.alice.serialization.tweedle;

import edu.cmu.cs.dennisc.java.util.logging.Logger;
import org.lgna.project.ast.Statement;
import org.storystage.prompter.context.NodeIds;

/**
 * Alice's Tweedle encoder with a short id comment before every statement, for example
 * {@code /*#a1f3*\/ this.cat.move(...)}. Lives in Alice's package because the encoder's
 * constructors are package-private. Used only to describe worlds to the Prompter; never for saving.
 */
public final class IdTaggedEncoder extends Encoder {
  private final NodeIds ids;

  public IdTaggedEncoder(NodeIds ids) {
    super();
    this.ids = ids;
  }

  @Override
  public void processSingleStatement(Statement stmt, Runnable appender) {
    super.processSingleStatement(stmt, () -> {
      this.tag(stmt);
      appender.run();
    });
  }

  @Override
  protected void appendCodeFlowStatement(Statement stmt, Runnable appender) {
    super.appendCodeFlowStatement(stmt, () -> {
      this.tag(stmt);
      appender.run();
    });
  }

  /** Never a dialog: this encoder runs behind every Prompter request. The type is used as the label. */
  @Override
  protected void reportUnlabeledParameter(String message) {
    Logger.warning(message.replace('\n', ' '));
  }

  private void tag(Statement stmt) {
    this.getCodeStringBuilder().append("/*#").append(this.ids.idOf(stmt)).append("*/ ");
  }
}
