/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.spi;

import org.lgna.croquet.Composite;
import org.lgna.croquet.ImmutableSplitComposite;
import org.lgna.croquet.views.SplitPane;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.UUID;

/**
 * An editor with the Prompter panel to its right. When the Prompter is off or locked, the panel
 * is hidden and the editor takes the whole width.
 */
public final class PrompterSlotComposite extends ImmutableSplitComposite {
  private static final int PANEL_WIDTH = 340;
  private static final int MIN_PANEL_WIDTH = 200;
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
    ComponentAdapter keepPanelOpen = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        PrompterSlotComposite.this.placeDivider();
      }

      @Override
      public void componentShown(ComponentEvent e) {
        SwingUtilities.invokeLater(PrompterSlotComposite.this::placeDivider);
      }
    };
    rv.getAwtComponent().addComponentListener(keepPanelOpen);
    this.getTrailingComposite().getRootComponent().getAwtComponent().addComponentListener(keepPanelOpen);
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
    view.revalidateAndRepaint();
    if (isShowing) {
      // A split pane ignores divider moves until it has been laid out with the panel visible,
      // so place the divider afterwards (TR1: the panel opened at zero width).
      SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(this::placeDivider));
    }
  }

  /** Gives the panel its width back if it is showing but squeezed against the edge. */
  private void placeDivider() {
    if (!this.provider.isPanelShowing()) {
      return;
    }
    JSplitPane split = this.getView().getAwtComponent();
    int width = split.getWidth();
    if (width <= 0) {
      return;
    }
    int panelWidth = width - split.getDividerLocation() - split.getDividerSize();
    if (panelWidth < MIN_PANEL_WIDTH) {
      split.setDividerLocation(Math.max(width / 2, width - PANEL_WIDTH));
      split.revalidate();
    }
  }
}
