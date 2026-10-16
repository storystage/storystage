/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import org.alice.serialization.tweedle.IdTaggedEncoder;
import org.lgna.project.Project;
import org.lgna.project.ast.NamedUserType;
import org.lgna.project.code.ProcessableNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The current world as Tweedle source with block ids, for each Prompter request. See
 * docs/spec/01-prompter-protocol.md §2.3.
 */
public final class ProjectText {
  /** Roughly 20,000 tokens at about 4 characters per token. */
  public static final int CHARACTER_BUDGET = 80_000;

  private ProjectText() {
    throw new AssertionError();
  }

  /** Encodes one node (a type or a method) with ids. */
  public static String encode(ProcessableNode node, NodeIds ids) {
    return new IdTaggedEncoder(ids).encode(node);
  }

  /** Encodes the user's own classes, Program first, then Scene, then the rest by name. */
  public static String render(Project project, NodeIds ids) {
    List<NamedUserType> types = new ArrayList<>(project.getNamedUserTypes());
    NamedUserType program = project.getProgramType();
    types.sort(Comparator.comparing((NamedUserType t) -> t != program).thenComparing(t -> !isScene(t)).thenComparing(NamedUserType::getName));
    StringBuilder sb = new StringBuilder();
    for (NamedUserType type : types) {
      String text = encode(type, ids);
      if (sb.length() + text.length() > CHARACTER_BUDGET) {
        sb.append("\n// (").append(type.getName()).append(" left out: world too large; use read_project)\n");
      } else {
        sb.append(text).append('\n');
      }
    }
    return sb.toString();
  }

  private static boolean isScene(NamedUserType type) {
    return type.isAssignableTo(org.lgna.story.SScene.class);
  }
}
