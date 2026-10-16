/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import org.alice.ide.croquet.models.ast.InsertStatementCompletionModel;
import org.lgna.croquet.ActionOperation;
import org.lgna.croquet.Application;
import org.lgna.croquet.history.UserActivity;

import java.util.UUID;

/**
 * The Croquet model behind every Prompter step. Edits committed under it join the project's undo
 * history (PROJECT_GROUP) exactly like drag-and-drop edits. See docs/spec/00-architecture.md §5.
 */
public final class PrompterStepOperation extends ActionOperation implements InsertStatementCompletionModel {
  private static class SingletonHolder {
    private static PrompterStepOperation instance = new PrompterStepOperation();
  }

  public static PrompterStepOperation getInstance() {
    return SingletonHolder.instance;
  }

  private PrompterStepOperation() {
    super(Application.PROJECT_GROUP, UUID.fromString("a7c3e1f5-2b4d-4f6a-8c9e-0d1f2a3b4c5d"));
  }

  @Override
  protected void perform(UserActivity activity) {
    // Steps are applied by WorldTools, never from a menu or button.
    activity.cancel();
  }
}
