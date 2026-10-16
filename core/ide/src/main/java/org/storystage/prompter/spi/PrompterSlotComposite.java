/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.spi;

import org.lgna.croquet.Composite;
import org.lgna.croquet.ImmutableSplitComposite;
import org.lgna.croquet.views.SplitPane;

import javax.swing.SwingUtilities;
import java.util.UUID;

/**
 * An editor with the Prompter panel to its right. When the Prompter is off or locked, the panel
 * is hidden and the editor takes the whole width.
 */
public final class PrompterSlotComposite extends ImmutableSplitComposite {
  private static final int PANEL_WIDTH = 320;
  private final Composite<?> editor;
  private final PrompterProvider provider;
  private Composite<?> panel;

  PrompterSlotComposite(UUID id, Composite<?> editor, PrompterProvider provider) {
    super(id);
    this.editor = editor;
    this.provider = provider;
    provider.addPanelShowingListener(() -> SwingUtilities.invokeLater(this::updatePanelShowing));
  }

  @Override
  public Composite<?> getLeadingComposite() {
    return this.editor;
  }

  @Override
  public synchronized Composite<?> getTrailingComposite() {
    if (this.panel == null) {
      this.panel = this.provider.createPanel();
    }
    return this.panel;
  }

  @Override
  protected SplitPane createView() {
    SplitPane rv = this.createHorizontalSplitPane();
    // The editor gets any extra space when the window is resized.
    rv.setResizeWeight(1.0);
    return rv;
  }

  @Override
  public void handlePreActivation() {
    super.handlePreActivation();
    this.updatePanelShowing();
  }

  private void updatePanelShowing() {
    boolean isShowing = this.provider.isPanelShowing();
    SplitPane view = this.getView();
    this.getTrailingComposite().getRootComponent().setVisible(isShowing);
    if (isShowing) {
      int width = view.getAwtComponent().getWidth();
      if (width > PANEL_WIDTH * 2) {
        view.setDividerLocation(width - PANEL_WIDTH);
      }
    }
    view.revalidateAndRepaint();
  }
}
